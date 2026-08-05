package com.fitcoach.asr;

import com.fitcoach.common.ApiResult;
import com.fitcoach.infra.asr.AsrResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "12. 语音识别 ASR", description = "服务端语音转文字 - 移动端浏览器无原生识别能力时的唯一可用路径")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/asr")
@RequiredArgsConstructor
public class AsrController {

    private final AsrService service;

    @Operation(summary = "查询语音识别是否可用",
            description = "前端在开启畅聊前调用；false 时应降级为文字输入，而不是让用户对着不工作的麦克风说话")
    @GetMapping("/status")
    public ApiResult<AsrStatus> status() {
        return ApiResult.ok(new AsrStatus(service.isEnabled()));
    }

    @Operation(summary = "音频转文字",
            description = "接受 wav / mp3（上游网关白名单）。前端须用 AudioContext 采 PCM 自行编码 WAV —— "
                    + "MediaRecorder 的原生输出 webm/mp4 不被接受。识别不出内容时返回空 text，属正常结果。")
    @PostMapping("/transcribe")
    public ApiResult<AsrResult> transcribe(@RequestParam("file") MultipartFile file) {
        return ApiResult.ok(service.transcribe(file));
    }

    @Data
    public static class AsrStatus {
        /** 服务端识别是否可用；false 时前端降级为文字输入。 */
        private final boolean enabled;
    }
}
