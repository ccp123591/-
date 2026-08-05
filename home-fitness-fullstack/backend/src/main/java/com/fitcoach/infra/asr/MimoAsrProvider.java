package com.fitcoach.infra.asr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fitcoach.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * MiMo ASR — 走小米 MiMo 的 /v1/chat/completions（model=mimo-v2.5-asr）。
 *
 * 与 TTS 同理，不走 OpenAI 标准路径：/v1/audio/transcriptions 实测返回 404。
 *
 * Body 形态（经实测确认，网关约束严格，偏离即 400）：
 *   {
 *     "model": "mimo-v2.5-asr",
 *     "messages": [
 *       {"role": "user", "content": [
 *         {"type": "input_audio", "input_audio": {"data": "<base64>", "format": "wav"}}
 *       ]}
 *     ]
 *   }
 *
 * 网关强制约束 —— 三条都踩过 400，改动前请先复核：
 *   1. 不得包含任何 text part。带上会报
 *      "ASR request must not include text parts; text prompt is injected by the gateway"。
 *      即提示词由网关注入，调用方无法自定义。
 *   2. 必须恰好一个 input_audio part（"ASR requires exactly one input_audio part"）。
 *   3. format 仅接受 wav / mp3。webm / mp4 / opus / pcm 全部拒绝 —— 这直接决定了前端
 *      不能用 MediaRecorder 的原生输出（Android Chrome 出 webm/opus，iOS Safari 出 mp4/aac），
 *      必须由前端采 PCM 自行编码 WAV 后上传。
 *
 * 响应：choices[0].message.content 即转写文本。
 *
 * 配置：
 *   ai.asr.mimo.api-key          （默认复用 MIMO_API_KEY）
 *   ai.asr.mimo.base-url         （默认 https://token-plan-cn.xiaomimimo.com/v1）
 *   ai.asr.mimo.model            （默认 mimo-v2.5-asr）
 *   ai.asr.mimo.timeout-seconds  （默认 30；含音频上传耗时，移动端弱网需留余量）
 *
 * api-key 空时 isAvailable() 返回 false → AsrService 抛 503，前端降级为文字输入。
 */
@Slf4j
@Component
public class MimoAsrProvider implements AsrProvider {

    /** 上游网关白名单，与错误信息 "must be one of: wav, mp3" 一致。 */
    private static final Set<String> SUPPORTED_FORMATS = Set.of("wav", "mp3");

    /** WAV 头 ByteRate 字段偏移量（标准 44 字节头）。 */
    private static final int WAV_BYTE_RATE_OFFSET = 28;

    /** mp3 时长估算兜底：按 128kbps 计 16000 字节/秒。 */
    private static final double MP3_BYTES_PER_SEC = 16000.0;

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final RestClient restClient;

    public MimoAsrProvider(
            @Value("${ai.asr.mimo.api-key:}") String apiKey,
            @Value("${ai.asr.mimo.base-url:https://token-plan-cn.xiaomimimo.com/v1}") String baseUrl,
            @Value("${ai.asr.mimo.model:mimo-v2.5-asr}") String model,
            @Value("${ai.asr.mimo.timeout-seconds:30}") int timeoutSeconds,
            RestTemplateBuilder builder) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.model = model;
        // 与 MimoCoachProvider 一致：用 RestTemplateBuilder 构造底层 RestTemplate，
        // 以便 @RestClientTest 替换 ClientHttpRequestFactory 为 MockRestServiceServer。
        this.restClient = RestClient.builder(
                builder.setReadTimeout(Duration.ofSeconds(timeoutSeconds)).build()).build();
    }

    @PostConstruct
    void init() {
        log.info("[asr:mimo] init: baseUrl={} model={} api-key={}",
                baseUrl, model, apiKey.isBlank() ? "<empty, ASR 不可用>" : "<set>");
    }

    @Override
    public String name() {
        return "mimo";
    }

    @Override
    public boolean isAvailable() {
        return !apiKey.isBlank();
    }

    @Override
    public AsrResult transcribe(byte[] audio, String format) {
        if (!isAvailable()) {
            throw new BusinessException(503, "MiMo ASR unavailable: api-key 未配置");
        }
        if (audio == null || audio.length == 0) {
            throw new BusinessException(400, "音频内容为空");
        }
        String fmt = format == null ? "" : format.trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_FORMATS.contains(fmt)) {
            throw new BusinessException(400, "不支持的音频格式：" + format + "（仅 wav / mp3）");
        }

        // 注意：content 数组里只能有这一个 input_audio，不能再加 text part（网关约束 1、2）
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(Map.of(
                "role", "user",
                "content", List.of(Map.of(
                        "type", "input_audio",
                        "input_audio", Map.of(
                                "data", Base64.getEncoder().encodeToString(audio),
                                "format", fmt)))
        )));

        try {
            JsonNode root = restClient.post()
                    .uri(baseUrl + "/chat/completions")
                    .headers(h -> h.setBearerAuth(apiKey))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            if (root == null) {
                throw new BusinessException(503, "MiMo ASR 返回空响应");
            }
            // 静音/纯噪声时网关返回空 content —— 这是正常结果，不是错误，交由上层静默丢弃
            String text = root.path("choices").path(0).path("message").path("content").asText("").trim();

            return AsrResult.builder()
                    .text(text)
                    .provider(name())
                    .durationSec(estimateDuration(audio, fmt))
                    .build();
        } catch (RestClientException e) {
            log.warn("[asr:mimo] HTTP failure: {}", e.getMessage());
            throw new BusinessException(503, "MiMo ASR unavailable");
        }
    }

    /**
     * 估算音频时长。WAV 直接读头部 ByteRate 求准确值；非标准头或 mp3 用码率兜底。
     */
    private static Double estimateDuration(byte[] audio, String format) {
        if ("wav".equals(format) && audio.length > 44
                && audio[0] == 'R' && audio[1] == 'I' && audio[2] == 'F' && audio[3] == 'F') {
            long byteRate = (audio[WAV_BYTE_RATE_OFFSET] & 0xFFL)
                    | ((audio[WAV_BYTE_RATE_OFFSET + 1] & 0xFFL) << 8)
                    | ((audio[WAV_BYTE_RATE_OFFSET + 2] & 0xFFL) << 16)
                    | ((audio[WAV_BYTE_RATE_OFFSET + 3] & 0xFFL) << 24);
            if (byteRate > 0) {
                return Math.round((audio.length - 44) / (double) byteRate * 10) / 10.0;
            }
        }
        return Math.round(audio.length / MP3_BYTES_PER_SEC * 10) / 10.0;
    }
}
