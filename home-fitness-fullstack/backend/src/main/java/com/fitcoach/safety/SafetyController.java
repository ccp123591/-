package com.fitcoach.safety;

import com.fitcoach.common.ApiResult;
import com.fitcoach.infra.notify.MailSender;
import com.fitcoach.infra.notify.SmsSender;
import com.fitcoach.security.SecurityUtil;
import com.fitcoach.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 训练安全告警 — 跌倒预警超时未确认时通知紧急联系人（家属）。
 * 邮箱/手机号由前端在触发时随请求带上（存于用户本地设置），
 * 发送走 MailSender/SmsSender 抽象：dev 为日志 mock，prod 邮件走 SMTP 真实发送。
 */
@Slf4j
@Tag(name = "12. 安全告警", description = "跌倒检测超时未确认时通知紧急联系人")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/safety")
@RequiredArgsConstructor
public class SafetyController {

    private final MailSender mailSender;
    private final SmsSender smsSender;
    private final UserRepository userRepo;

    @Operation(summary = "跌倒告警 — 通知紧急联系人")
    @PostMapping("/fall-alert")
    public ApiResult<Map<String, Object>> fallAlert(@Valid @RequestBody FallAlertRequest req) {
        Long userId = SecurityUtil.currentUserId();
        boolean hasEmail = req.getContactEmail() != null && !req.getContactEmail().isBlank();
        boolean hasPhone = req.getContactPhone() != null && !req.getContactPhone().isBlank();
        if (!hasEmail && !hasPhone) {
            return ApiResult.ok(Map.of("notified", false, "reason", "未配置紧急联系人"));
        }

        String nickname = userRepo.findById(userId)
                .map(u -> u.getNickname() == null ? "您的家人" : u.getNickname())
                .orElse("您的家人");
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String subject = "【FitCoach 安全告警】" + nickname + " 训练中疑似跌倒";
        String body = String.format(
                "%s 在 %s 使用 FitCoach 居家训练时被检测到疑似跌倒，且超过等待时间未确认安全。%s请尽快联系确认其状况。",
                nickname, time,
                req.getNote() == null || req.getNote().isBlank() ? "" : "（" + req.getNote().strip() + "）");

        boolean delivered = false;
        String mailProvider = hasEmail ? mailSender.name() : "none";
        String smsProvider = hasPhone ? smsSender.name() : "none";
        if (hasEmail) {
            mailSender.send(req.getContactEmail().strip(), subject, body);
            delivered = isExternalProvider(mailProvider);
        }
        if (hasPhone) {
            smsSender.send(req.getContactPhone().strip(), subject + "，请尽快联系确认。", "fall-alert");
            delivered = delivered || isExternalProvider(smsProvider);
        }
        log.warn("[safety] fall-alert user={} email={} phone={}", userId, hasEmail, hasPhone);
        return ApiResult.ok(Map.of(
                "notified", delivered,
                "mailProvider", mailProvider == null ? "unknown" : mailProvider,
                "smsProvider", smsProvider == null ? "unknown" : smsProvider,
                "reason", delivered ? "sent" : "development log provider only"));
    }

    private static boolean isExternalProvider(String provider) {
        return provider != null && !provider.startsWith("log-");
    }

    @Data
    public static class FallAlertRequest {
        @Email(message = "紧急联系人邮箱格式不正确")
        @Size(max = 120)
        private String contactEmail;
        @Size(max = 30)
        private String contactPhone;
        @Size(max = 200)
        private String note;
    }
}
