package com.fitcoach.asr;

import com.fitcoach.exception.BusinessException;
import com.fitcoach.infra.asr.AsrResult;
import com.fitcoach.infra.asr.MimoAsrProvider;
import com.fitcoach.infra.storage.UploadValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * ASR 编排：校验上传音频 → 调 provider 转写。
 *
 * 与 TtsService 的差别：TTS 有 browser fallback 所以永不抛 503；ASR 没有等价的降级路径
 * （浏览器端识别正是本功能要绕开的东西），provider 不可用时直接抛 503，
 * 由前端降级为文字输入。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsrService {

    /** 短于此长度的音频不值得发起一次云端调用，直接当作静音。 */
    private static final int MIN_AUDIO_BYTES = 2048;

    @Value("${ai.asr.provider:none}")
    private String configured;        // mimo | none

    private final MimoAsrProvider mimoProvider;
    private final UploadValidator uploadValidator;

    /** 当前是否具备服务端识别能力 —— 前端据此决定用语音还是文字输入。 */
    public boolean isEnabled() {
        return "mimo".equalsIgnoreCase(configured) && mimoProvider.isAvailable();
    }

    /**
     * 音频转文字。
     *
     * @throws BusinessException 503 服务未启用 / 上游不可用；400 音频非法
     */
    public AsrResult transcribe(MultipartFile file) {
        if (!isEnabled()) {
            throw new BusinessException(503, "语音识别未启用");
        }
        String format = uploadValidator.validateAudio(file);

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            log.warn("[asr] 读取上传音频失败: {}", e.getMessage());
            throw new BusinessException(400, "音频读取失败");
        }

        // 过短音频直接返回空文本：既省一次云端调用，也避免把误触当成发言
        if (bytes.length < MIN_AUDIO_BYTES) {
            return AsrResult.builder().text("").provider(mimoProvider.name()).durationSec(0.0).build();
        }

        AsrResult result = mimoProvider.transcribe(bytes, format);
        log.debug("[asr] 转写完成: {}字节 {}s -> {}字",
                bytes.length, result.getDurationSec(),
                result.getText() == null ? 0 : result.getText().length());
        return result;
    }
}
