package com.fitcoach.security;

import com.fitcoach.infra.ratelimit.RateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitFilterTest {

    @Test
    void ignores_forwarded_headers_by_default() throws Exception {
        RateLimiter limiter = mock(RateLimiter.class);
        when(limiter.tryAcquire(eq("auth.login"), eq("10.0.0.8"), anyInt(), anyInt())).thenReturn(true);
        RateLimitFilter filter = new RateLimitFilter(limiter);

        MockHttpServletRequest request = request();
        request.setRemoteAddr("10.0.0.8");
        request.addHeader("X-Real-IP", "203.0.113.9");
        request.addHeader("X-Forwarded-For", "198.51.100.7");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        verify(limiter).tryAcquire("auth.login", "10.0.0.8", 5, 60);
    }

    @Test
    void trusts_proxy_overwritten_real_ip_only_when_enabled() throws Exception {
        RateLimiter limiter = mock(RateLimiter.class);
        when(limiter.tryAcquire(eq("auth.login"), eq("203.0.113.9"), anyInt(), anyInt())).thenReturn(true);
        RateLimitFilter filter = new RateLimitFilter(limiter);
        ReflectionTestUtils.setField(filter, "trustProxyHeaders", true);

        MockHttpServletRequest request = request();
        request.setRemoteAddr("172.20.0.5");
        request.addHeader("X-Real-IP", "203.0.113.9");
        request.addHeader("X-Forwarded-For", "198.51.100.7");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        verify(limiter).tryAcquire("auth.login", "203.0.113.9", 5, 60);
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login/email");
        request.setServletPath("/api/auth/login/email");
        return request;
    }
}
