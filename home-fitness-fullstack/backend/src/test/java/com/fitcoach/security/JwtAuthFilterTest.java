package com.fitcoach.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 安全回归：JwtAuthFilter 只接受 type=access 的 token —
 * 30 天有效期的 refresh token 绝不能直接当登录凭证使用。
 */
class JwtAuthFilterTest {

    private JwtUtil jwtUtil;
    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(new MockEnvironment());
        ReflectionTestUtils.setField(jwtUtil, "secret",
                "unit-test-secret-key-at-least-32-bytes-long!");
        ReflectionTestUtils.setField(jwtUtil, "accessTokenExpireHours", 2L);
        ReflectionTestUtils.setField(jwtUtil, "refreshTokenExpireDays", 30L);
        filter = new JwtAuthFilter(jwtUtil);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void doFilter(String token) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer " + token);
        ReflectionTestUtils.invokeMethod(filter, "doFilterInternal",
                req, new MockHttpServletResponse(), mock(FilterChain.class));
    }

    @Test
    void access_token_authenticates() throws Exception {
        String access = jwtUtil.generateAccessToken(7L, "Tom", "USER");
        doFilter(access);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(7L);
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
    }

    @Test
    void refresh_token_must_not_authenticate() throws Exception {
        String refresh = jwtUtil.generateRefreshToken(7L);
        doFilter(refresh);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void garbage_token_does_not_authenticate() throws Exception {
        doFilter("not-a-jwt");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
