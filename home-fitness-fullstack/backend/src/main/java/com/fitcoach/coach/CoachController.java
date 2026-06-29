package com.fitcoach.coach;

import com.fitcoach.common.ApiResult;
import com.fitcoach.common.PageResult;
import com.fitcoach.infra.ai.ChatTurn;
import com.fitcoach.infra.vision.FormCritique;
import com.fitcoach.security.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * AI 教练 Controller — 全部委托给 CoachService。
 */
@Tag(name = "04. AI 教练", description = "AI 驱动的训练点评、建议、计划与陪伴聊天")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/coach")
@RequiredArgsConstructor
public class CoachController {

    private final CoachService coachService;

    @Operation(summary = "生成训练后反馈")
    @PostMapping("/feedback")
    public ApiResult<FeedbackResponse> feedback(@Valid @RequestBody FeedbackRequest req) {
        Long userId = SecurityUtil.currentUserId();
        return ApiResult.ok(coachService.feedback(userId, req.getSessionId()));
    }

    @Operation(summary = "获取综合训练建议（基于近 7 次）")
    @GetMapping("/suggestion")
    public ApiResult<FeedbackResponse> suggestion() {
        Long userId = SecurityUtil.currentUserId();
        return ApiResult.ok(coachService.suggestion(userId));
    }

    @Operation(summary = "生成本周训练计划")
    @GetMapping("/weekly-plan")
    public ApiResult<FeedbackResponse> weeklyPlan() {
        Long userId = SecurityUtil.currentUserId();
        return ApiResult.ok(coachService.weeklyPlan(userId));
    }

    @Operation(summary = "动作视觉点评 — 上传 1-3 帧训练画面，JoyAI-VL 给动作反馈")
    @PostMapping(value = "/form-critique", consumes = "multipart/form-data")
    public ApiResult<FormCritique> formCritique(
            @RequestParam("action") String action,
            @RequestParam(value = "reps", required = false) Integer reps,
            @RequestParam(value = "score", required = false) Integer score,
            @RequestParam("frames") List<MultipartFile> frames) {
        Long userId = SecurityUtil.currentUserId();
        return ApiResult.ok(coachService.formCritique(userId, action, reps, score, frames));
    }

    @Operation(summary = "历史反馈列表（分页）")
    @GetMapping("/history")
    public ApiResult<PageResult<FeedbackResponse>> history(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = SecurityUtil.currentUserId();
        return ApiResult.ok(coachService.history(userId, page, size));
    }

    @Operation(summary = "陪伴聊天 — 多轮 + RAG 记忆唤醒")
    @PostMapping("/chat")
    public ApiResult<ChatResponse> chat(@Valid @RequestBody ChatRequest req) {
        Long userId = SecurityUtil.currentUserId();
        return ApiResult.ok(coachService.chat(userId, req.getMessage(), req.getHistory()));
    }

    @Operation(summary = "叙旧 — 按时间近回顾最近的对话")
    @PostMapping("/reminisce")
    public ApiResult<ChatResponse> reminisce() {
        Long userId = SecurityUtil.currentUserId();
        return ApiResult.ok(coachService.reminisce(userId));
    }

    @Data
    public static class FeedbackRequest {
        @NotNull
        private Long sessionId;
    }

    @Data
    public static class ChatRequest {
        @NotBlank
        @Size(max = 1000, message = "单条消息最长 1000 字")
        private String message;

        /** 最近若干轮，服务端会再截到 8 轮以内；可为 null */
        @Size(max = 30, message = "history 最多 30 轮")
        private List<ChatTurn> history;
    }
}
