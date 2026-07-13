package com.fitcoach.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitcoach.common.ApiResult;
import com.fitcoach.infra.ratelimit.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 简单基于 IP 的限流过滤器，按 path 前缀分类：
 *   /api/auth/login/**            → 5 次 / 60s / IP
 *   /api/auth/{sms|email}/send    → 5 次 / 60s / IP
 *   /api/auth/register            → 3 次 / 60s / IP
 *   /api/auth/password/**         → 3 次 / 60s / IP
 *   /api/safety/fall-alert        → 3 次 / 60s / IP
 *   其它放行（业务接口由认证保护，不在这里拦截）。
 *
 * 拦截到限流时返回 429 + Retry-After: 60 + JSON ApiResult。
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int WINDOW = 60;

    private final RateLimiter limiter;

    @Value("${security.trust-proxy-headers:false}")
    private boolean trustProxyHeaders;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        Capacity c = resolveCapacity(path);
        if (c != null) {
            String ip = clientIp(request);
            if (!limiter.tryAcquire(c.scope, ip, c.limit, WINDOW)) {
                writeTooManyRequests(response);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private static Capacity resolveCapacity(String path) {
        if (path == null) return null;
        if (path.startsWith("/api/auth/login/")) return new Capacity("auth.login", 5);
        if (path.matches("^/api/auth/(sms|email)/send$")) return new Capacity("auth.send", 5);
        if (path.equals("/api/safety/fall-alert")) return new Capacity("safety.fall-alert", 3);
        if (path.equals("/api/auth/register")) return new Capacity("auth.register", 3);
        if (path.startsWith("/api/auth/password/")) return new Capacity("auth.password", 3);
        return null;
    }

    private String clientIp(HttpServletRequest req) {
        // 仅在部署明确启用受信任反向代理时读取 X-Real-IP。
        // nginx 会覆盖该头；不采信客户端可自行拼接的 X-Forwarded-For。
        if (trustProxyHeaders) {
            String real = req.getHeader("X-Real-IP");
            if (StringUtils.hasText(real)) return real.trim();
        }
        return req.getRemoteAddr();
    }

    private static void writeTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(WINDOW));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiResult<Void> body = ApiResult.fail(429, "请求过于频繁，请稍后再试");
        response.getWriter().write(MAPPER.writeValueAsString(body));
    }

    private record Capacity(String scope, int limit) {}
}
