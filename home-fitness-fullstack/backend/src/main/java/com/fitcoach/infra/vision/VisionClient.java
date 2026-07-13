package com.fitcoach.infra.vision;

import com.fitcoach.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/**
 * 调用 vision-svc 推理服务（Python FastAPI）。
 * dev 默认 base-url=http://localhost:8081；docker-compose 内为 http://vision-svc:8081。
 * 服务不可用时透出 503 BusinessException，由全局异常处理器统一响应。
 */
@Slf4j
@Component
public class VisionClient {

    private final RestClient restClient;
    private final String baseUrl;
    private final int timeoutSeconds;

    public VisionClient(
            @Value("${vision.base-url:http://localhost:8081}") String baseUrl,
            @Value("${vision.timeout-seconds:30}") int timeoutSeconds,
            RestTemplateBuilder builder) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.timeoutSeconds = timeoutSeconds;
        this.restClient = RestClient.builder(
                builder.setConnectTimeout(Duration.ofSeconds(5))
                        .setReadTimeout(Duration.ofSeconds(timeoutSeconds))
                        .build()
        ).build();
    }

    @PostConstruct
    void init() {
        log.info("[vision] client init: baseUrl={} timeout={}s", baseUrl, timeoutSeconds);
    }

    /** POST /infer multipart frames → RoomFeatures。 */
    public RoomFeatures infer(List<MultipartFile> frames) {
        if (frames == null || frames.isEmpty()) {
            throw new BusinessException(400, "未上传任何帧");
        }
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        for (MultipartFile f : frames) {
            body.add("frames", toResource(f));
        }
        try {
            return restClient.post()
                    .uri(baseUrl + "/infer")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(RoomFeatures.class);
        } catch (RestClientException e) {
            log.warn("[vision] /infer 失败: {}", e.getMessage());
            throw new BusinessException(503, "环境识别服务暂不可用");
        }
    }

    /**
     * POST /critique multipart（action + 1-3 frames，可带 reps/score）→ FormCritique。
     * 动作视觉点评：把训练关键帧交给 JoyAI-VL 给自然语言反馈。
     */
    public FormCritique critique(String action, Integer reps, Integer score, List<MultipartFile> frames) {
        if (action == null || action.isBlank()) {
            throw new BusinessException(400, "缺少动作类型");
        }
        if (frames == null || frames.isEmpty()) {
            throw new BusinessException(400, "未上传任何帧");
        }
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("action", action);
        if (reps != null) body.add("reps", String.valueOf(reps));
        if (score != null) body.add("score", String.valueOf(score));
        for (MultipartFile f : frames) {
            body.add("frames", toResource(f));
        }
        try {
            return restClient.post()
                    .uri(baseUrl + "/critique")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(FormCritique.class);
        } catch (RestClientException e) {
            log.warn("[vision] /critique 失败: {}", e.getMessage());
            throw new BusinessException(503, "动作点评服务暂不可用");
        }
    }

    /**
     * POST /scene multipart（1-3 frames）→ SceneSummary。
     * 视频畅聊场景摘要：把畅聊抓帧交给 JoyAI-VL 描述"用户在干嘛"。
     */
    public SceneSummary scene(List<MultipartFile> frames) {
        if (frames == null || frames.isEmpty()) {
            throw new BusinessException(400, "未上传任何帧");
        }
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        for (MultipartFile f : frames) {
            body.add("frames", toResource(f));
        }
        try {
            return restClient.post()
                    .uri(baseUrl + "/scene")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(SceneSummary.class);
        } catch (RestClientException e) {
            log.warn("[vision] /scene 失败: {}", e.getMessage());
            throw new BusinessException(503, "场景识别服务暂不可用");
        }
    }

    /** GET /healthz — 用于启动期/admin 检测。 */
    public boolean isHealthy() {
        try {
            String body = restClient.get().uri(baseUrl + "/healthz").retrieve().body(String.class);
            return body != null && body.contains("ok");
        } catch (Exception e) {
            return false;
        }
    }

    private static ByteArrayResource toResource(MultipartFile f) {
        try {
            return new ByteArrayResource(f.getBytes()) {
                @Override
                public String getFilename() {
                    return f.getOriginalFilename() == null ? "frame.jpg" : f.getOriginalFilename();
                }
                @Override
                public long contentLength() {
                    return f.getSize();
                }
            };
        } catch (IOException e) {
            throw new BusinessException(400, "图片读取失败");
        }
    }

    /** 启动期日志钩子用 — 不暴露给业务。 */
    public HttpEntity<MultiValueMap<String, Object>> _buildEntityForTest(MultiValueMap<String, Object> body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        return new HttpEntity<>(body, h);
    }
}
