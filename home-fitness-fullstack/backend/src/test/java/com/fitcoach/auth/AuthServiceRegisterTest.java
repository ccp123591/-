package com.fitcoach.auth;

import com.fitcoach.exception.BusinessException;
import com.fitcoach.security.JwtUtil;
import com.fitcoach.security.LoginAttemptService;
import com.fitcoach.security.RefreshTokenStore;
import com.fitcoach.user.User;
import com.fitcoach.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

/**
 * 注册必须执行 PasswordPolicy（≥8 位且含字母+数字），与找回密码口径一致。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceRegisterTest {

    @Mock UserRepository userRepo;
    @Mock PasswordEncoder encoder;
    @Mock JwtUtil jwtUtil;
    @Mock VerifyCodeService verifyCode;
    @Mock RefreshTokenStore refreshTokenStore;
    @Mock LoginAttemptService loginAttempts;

    @InjectMocks AuthService service;

    @Test
    void register_rejects_short_password() {
        assertThatThrownBy(() -> service.register("a@b.com", "ab1", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("8 位");
    }

    @Test
    void register_rejects_password_without_digit() {
        assertThatThrownBy(() -> service.register("a@b.com", "abcdefgh", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("字母与数字");
    }

    @Test
    void register_accepts_policy_compliant_password() {
        given(userRepo.existsByEmail("a@b.com")).willReturn(false);
        given(encoder.encode(anyString())).willReturn("hash");
        given(userRepo.save(any(User.class))).willAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });
        given(jwtUtil.generateAccessToken(any(), any(), any())).willReturn("at");
        given(jwtUtil.generateRefreshToken(any())).willReturn("rt");

        var resp = service.register("a@b.com", "abcd1234", "小明");

        assertThat(resp).containsKeys("accessToken", "refreshToken", "user");
    }
}
