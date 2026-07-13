package com.fitcoach.auth;

import com.fitcoach.exception.BusinessException;
import com.fitcoach.security.JwtUtil;
import com.fitcoach.security.RefreshTokenStore;
import com.fitcoach.user.User;
import com.fitcoach.user.UserRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceRefreshTest {

    @Mock UserRepository userRepo;
    @Mock PasswordEncoder encoder;
    @Mock JwtUtil jwtUtil;
    @Mock VerifyCodeService verifyCode;
    @Mock RefreshTokenStore refreshTokenStore;
    @Mock Claims claims;

    @InjectMocks AuthService service;

    private void primeValidRefresh(String jti, Long userId) {
        given(jwtUtil.isTokenValid("RT")).willReturn(true);
        given(jwtUtil.parseToken("RT")).willReturn(claims);
        when(claims.get("type")).thenReturn("refresh");
        when(claims.getId()).thenReturn(jti);
        when(claims.getSubject()).thenReturn(String.valueOf(userId));
        when(claims.getExpiration()).thenReturn(new Date(System.currentTimeMillis() + 86_400_000));
    }

    @Test
    void refresh_returns_new_access_when_jti_not_revoked() {
        primeValidRefresh("jti-1", 7L);
        given(refreshTokenStore.isRevoked("jti-1")).willReturn(false);
        User u = User.builder().id(7L).nickname("Tom").role("USER").status("ACTIVE").build();
        given(userRepo.findById(7L)).willReturn(Optional.of(u));
        given(jwtUtil.generateAccessToken(eq(7L), eq("Tom"), eq("USER"))).willReturn("new-access");

        var r = service.refresh("RT");

        assertThatCode(() -> {}).doesNotThrowAnyException();
        verify(refreshTokenStore).isRevoked("jti-1");
        org.assertj.core.api.Assertions.assertThat(r).containsEntry("accessToken", "new-access");
    }

    @Test
    void refresh_rejects_disabled_user() {
        primeValidRefresh("jti-disabled", 7L);
        given(refreshTokenStore.isRevoked("jti-disabled")).willReturn(false);
        given(userRepo.findById(7L)).willReturn(Optional.of(
                User.builder().id(7L).nickname("Tom").role("USER").status("DISABLED").build()));

        assertThatThrownBy(() -> service.refresh("RT"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(403);
        verify(jwtUtil, never()).generateAccessToken(any(), any(), any());
    }

    @Test
    void refresh_rejects_revoked_jti_with_401() {
        primeValidRefresh("jti-2", 7L);
        given(refreshTokenStore.isRevoked("jti-2")).willReturn(true);

        assertThatThrownBy(() -> service.refresh("RT"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("吊销");
        verify(userRepo, never()).findById(any());
    }

    @Test
    void logout_revokes_refresh_jti() {
        primeValidRefresh("jti-3", 7L);

        service.logout("RT");

        verify(refreshTokenStore).revoke(eq("jti-3"), eq(7L), any(Date.class), eq("user-logout"));
    }

    @Test
    void logout_silent_on_invalid_token() {
        given(jwtUtil.parseToken("garbage")).willThrow(new RuntimeException("bad"));
        assertThatCode(() -> service.logout("garbage")).doesNotThrowAnyException();
        verify(refreshTokenStore, never()).revoke(any(), any(), any(), any());
    }

    @Test
    void logout_skips_when_token_blank() {
        service.logout("");
        verify(refreshTokenStore, never()).revoke(any(), any(), any(), any());
    }
}
