package com.fitcoach.infra.storage;

import com.fitcoach.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;

/**
 * 上传安全校验：扩展名白名单 + Tika 真实 MIME 检测 + 大小上限。
 * 调用方拿到 sanitized extension（小写），用于落盘时拼接 — 不信任客户端文件名。
 */
@Slf4j
@Component
public class UploadValidator {

    private static final Set<String> IMAGE_EXTS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> IMAGE_MIMES = Set.of("image/jpeg", "image/png", "image/webp");

    /** ASR 上游网关只认 wav / mp3，此处与之对齐，非法格式在进后端时就挡掉。 */
    private static final Set<String> AUDIO_EXTS = Set.of("wav", "mp3");
    /** Tika 对 WAV 的命名不唯一（vnd.wave / x-wav），全部纳入。 */
    private static final Set<String> AUDIO_MIMES = Set.of(
            "audio/vnd.wave", "audio/x-wav", "audio/wav", "audio/wave", "audio/mpeg");

    @Value("${upload.max-image-size-bytes:5242880}")  // 5 MB default
    private long maxImageBytes;

    @Value("${upload.max-audio-size-bytes:4194304}")  // 4 MB default，约 2 分钟 16kHz 单声道 WAV
    private long maxAudioBytes;

    /**
     * 校验并返回安全的扩展名（小写、不带点）。
     */
    public String validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "未上传文件");
        }
        if (file.getSize() > maxImageBytes) {
            throw new BusinessException(413, "文件过大（限 " + (maxImageBytes / 1024) + " KB）");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            throw new BusinessException(400, "缺少文件扩展名");
        }
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!IMAGE_EXTS.contains(ext)) {
            throw new BusinessException(400, "不支持的扩展名：" + ext);
        }

        // Tika 真实 MIME 嗅探（前 N 字节）—— 防止把 EXE 改名为 PNG 上传
        String detected = detectMime(file, name);
        if (!IMAGE_MIMES.contains(detected)) {
            log.warn("[upload] MIME 嗅探失败：name={} detected={}", name, detected);
            throw new BusinessException(400, "文件内容与扩展名不匹配");
        }
        // 标准化 jpeg → jpg
        return "jpeg".equals(ext) ? "jpg" : ext;
    }

    /**
     * 校验上传音频并返回安全的格式名（小写，wav / mp3），供 ASR provider 声明 input_audio.format。
     */
    public String validateAudio(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "未上传音频");
        }
        if (file.getSize() > maxAudioBytes) {
            throw new BusinessException(413, "音频过大（限 " + (maxAudioBytes / 1024) + " KB）");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            throw new BusinessException(400, "缺少文件扩展名");
        }
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!AUDIO_EXTS.contains(ext)) {
            throw new BusinessException(400, "不支持的音频格式：" + ext + "（仅 wav / mp3）");
        }

        String detected = detectMime(file, name);
        if (!AUDIO_MIMES.contains(detected)) {
            log.warn("[upload] 音频 MIME 嗅探失败：name={} detected={}", name, detected);
            throw new BusinessException(400, "文件内容与扩展名不匹配");
        }
        return ext;
    }

    private static String detectMime(MultipartFile file, String name) {
        try (InputStream is = new BufferedInputStream(file.getInputStream())) {
            Metadata md = new Metadata();
            md.set(TikaCoreProperties.RESOURCE_NAME_KEY, name);
            MediaType mt = new DefaultDetector().detect(is, md);
            return mt == null ? "application/octet-stream" : mt.toString();
        } catch (IOException e) {
            throw new BusinessException(400, "文件读取失败");
        }
    }
}
