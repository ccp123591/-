package com.fitcoach.auth;

import com.fitcoach.common.ApiResult;
import com.fitcoach.security.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "01. 认证", description = "登录 / 注册 / 登出 / Token 刷新")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "邮箱登录")
    @PostMapping("/login/email")
    public ApiResult<Map<String, Object>> loginByEmail(@RequestBody LoginEmailDto dto) {
        return ApiResult.ok(authService.loginByEmail(dto.getEmail(), dto.getPassword()));
    }

    @Operation(summary = "手机验证码登录（占位 · 未接短信）")
    @PostMapping("/login/phone")
    public ApiResult<Map<String, Object>> loginByPhone(@RequestBody LoginPhoneDto dto) {
        return ApiResult.fail(501, "短信登录未接入");
    }

    @Operation(summary = "微信一键登录（占位 · 未接 OpenAPI）")
    @PostMapping("/login/wechat")
    public ApiResult<Map<String, Object>> loginByWechat(@RequestBody Map<String, String> body) {
        return ApiResult.fail(501, "微信登录未接入");
    }

    @Operation(summary = "游客登录（设备 ID）")
    @PostMapping("/login/guest")
    public ApiResult<Map<String, Object>> loginAsGuest(@RequestBody Map<String, String> body) {
        return ApiResult.ok(authService.loginAsGuest(body.get("deviceId")));
    }

    @Operation(summary = "发送短信验证码（占位）")
    @PostMapping("/sms/send")
    public ApiResult<Void> sendSms(@RequestBody Map<String, String> body) {
        return ApiResult.fail(501, "短信服务未接入");
    }

    @Operation(summary = "发送邮箱验证码（占位）")
    @PostMapping("/email/send")
    public ApiResult<Void> sendEmail(@RequestBody Map<String, String> body) {
        return ApiResult.fail(501, "邮件服务未接入");
    }

    @Operation(summary = "注册账号")
    @PostMapping("/register")
    public ApiResult<Map<String, Object>> register(@RequestBody RegisterDto dto) {
        return ApiResult.ok(authService.register(dto.getEmail(), dto.getPassword(), dto.getNickname()));
    }

    @Operation(summary = "刷新 Access Token")
    @PostMapping("/refresh")
    public ApiResult<Map<String, Object>> refresh(@RequestBody Map<String, String> body) {
        return ApiResult.ok(authService.refresh(body.get("refreshToken")));
    }

    @Operation(summary = "登出")
    @PostMapping("/logout")
    public ApiResult<Void> logout() {
        // 无状态 JWT：客户端丢弃 token 即可；后续如需加 refresh 黑名单再扩展
        return ApiResult.ok(null, "已退出");
    }

    @Operation(summary = "获取当前用户")
    @GetMapping("/me")
    public ApiResult<Map<String, Object>> me() {
        return ApiResult.ok(authService.currentUser(SecurityUtil.currentUserId()));
    }

    @Data public static class LoginEmailDto  { private String email; private String password; }
    @Data public static class LoginPhoneDto  { private String phone; private String code; }
    @Data public static class RegisterDto    { private String email; private String password; private String nickname; }
}
