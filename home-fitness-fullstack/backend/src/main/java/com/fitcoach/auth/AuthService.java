package com.fitcoach.auth;

import com.fitcoach.exception.BusinessException;
import com.fitcoach.security.JwtUtil;
import com.fitcoach.security.LoginAttemptService;
import com.fitcoach.security.RefreshTokenStore;
import com.fitcoach.user.User;
import com.fitcoach.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final JwtUtil jwtUtil;
    private final VerifyCodeService verifyCode;
    private final RefreshTokenStore refreshTokenStore;
    private final LoginAttemptService loginAttempts;

    /** 邮箱密码登录 */
    public Map<String, Object> loginByEmail(String email, String password) {
        if (email == null || password == null) throw new BusinessException(400, "邮箱和密码不能为空");
        String identifier = email.trim();
        if (loginAttempts.isLocked(identifier)) {
            throw new BusinessException(423, "账号已锁定，请稍后再试");
        }
        User u = userRepo.findByEmail(identifier).orElse(null);
        if (u == null) {
            loginAttempts.recordFailure(identifier);
            throw new BusinessException(400, "账号或密码错误");
        }
        if (!"ACTIVE".equals(u.getStatus())) throw new BusinessException(403, "账号已被禁用");
        if (!encoder.matches(password, u.getPasswordHash())) {
            loginAttempts.recordFailure(identifier);
            throw new BusinessException(400, "账号或密码错误");
        }
        loginAttempts.clear(identifier);
        return issueTokens(u);
    }

    /** 手机验证码登录 — 校验通过后查/建用户。 */
    @Transactional
    public Map<String, Object> loginByPhone(String phone, String code) {
        if (phone == null || phone.isBlank()) throw new BusinessException(400, "phone 不能为空");
        if (!verifyCode.verify("sms", phone, "login", code)) {
            throw new BusinessException(400, "验证码无效或已过期");
        }
        User u = userRepo.findByPhone(phone).orElseGet(() -> {
            User fresh = User.builder()
                    .phone(phone)
                    .nickname("用户_" + phone.substring(Math.max(0, phone.length() - 4)))
                    .role("USER")
                    .loginType("phone")
                    .status("ACTIVE")
                    .weeklyGoal(50)
                    .passwordHash(encoder.encode(UUID.randomUUID().toString()))
                    .build();
            return userRepo.save(fresh);
        });
        if (!"ACTIVE".equals(u.getStatus())) throw new BusinessException(403, "账号已被禁用");
        return issueTokens(u);
    }

    /** 发送验证码：channel ∈ {sms, email}, purpose ∈ {login, reset}。 */
    public void sendCode(String channel, String target, String purpose) {
        verifyCode.generate(channel, target, purpose);
    }

    /** 注册 */
    @Transactional
    public Map<String, Object> register(String email, String password, String nickname) {
        if (email == null || email.isBlank()) {
            throw new BusinessException(400, "邮箱不能为空");
        }
        PasswordPolicy.validate(password);   // ≥8 位且含字母+数字，与找回密码口径一致
        if (userRepo.existsByEmail(email)) throw new BusinessException(400, "该邮箱已注册");
        User u = User.builder()
                .email(email.trim())
                .passwordHash(encoder.encode(password))
                .nickname(nickname == null || nickname.isBlank() ? email.split("@")[0] : nickname)
                .role("USER")
                .loginType("email")
                .status("ACTIVE")
                .weeklyGoal(50)
                .build();
        u = userRepo.save(u);
        log.info("新用户注册: {}", email);
        return issueTokens(u);
    }

    /** 游客登录（deviceId 稳定） */
    @Transactional
    public Map<String, Object> loginAsGuest(String deviceId) {
        String did = (deviceId == null || deviceId.isBlank())
                ? UUID.randomUUID().toString() : deviceId;
        User u = userRepo.findByDeviceId(did).orElseGet(() -> {
            User fresh = User.builder()
                    .deviceId(did)
                    .nickname("游客_" + did.substring(0, Math.min(6, did.length())))
                    .role("GUEST")
                    .loginType("guest")
                    .status("ACTIVE")
                    .weeklyGoal(30)
                    .passwordHash(encoder.encode(UUID.randomUUID().toString()))
                    .build();
            return userRepo.save(fresh);
        });
        return issueTokens(u);
    }

    /** 刷新 access token */
    public Map<String, Object> refresh(String refreshToken) {
        if (refreshToken == null || !jwtUtil.isTokenValid(refreshToken)) {
            throw new BusinessException(401, "refresh token 无效或已过期");
        }
        var claims = jwtUtil.parseToken(refreshToken);
        if (!"refresh".equals(claims.get("type"))) throw new BusinessException(401, "非 refresh token");
        String jti = claims.getId();
        if (refreshTokenStore.isRevoked(jti)) {
            throw new BusinessException(401, "refresh token 已被吊销");
        }
        Long uid = Long.valueOf(claims.getSubject());
        User u = userRepo.findById(uid).orElseThrow(() -> new BusinessException(401, "用户不存在"));
        Map<String, Object> r = new HashMap<>();
        r.put("accessToken", jwtUtil.generateAccessToken(u.getId(), u.getNickname(), u.getRole()));
        return r;
    }

    /** 登出：撤销 refresh token 的 jti（如果调用方携带）。 */
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) return;
        try {
            var claims = jwtUtil.parseToken(refreshToken);
            if (!"refresh".equals(claims.get("type"))) return;
            refreshTokenStore.revoke(claims.getId(),
                    Long.valueOf(claims.getSubject()),
                    claims.getExpiration(),
                    "user-logout");
        } catch (Exception ignored) {
            // 无效 token 静默忽略：登出永远不应 4xx
        }
    }

    public Map<String, Object> currentUser(Long userId) {
        User u = userRepo.findById(userId).orElseThrow(() -> new BusinessException(401, "用户不存在"));
        return toPublicUser(u);
    }

    /* ---------- helpers ---------- */

    private Map<String, Object> issueTokens(User u) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("accessToken",  jwtUtil.generateAccessToken(u.getId(), u.getNickname(), u.getRole()));
        resp.put("refreshToken", jwtUtil.generateRefreshToken(u.getId()));
        resp.put("user", toPublicUser(u));
        return resp;
    }

    private Map<String, Object> toPublicUser(User u) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", u.getId());
        m.put("nickname", u.getNickname());
        m.put("email", u.getEmail());
        m.put("phone", u.getPhone());
        m.put("avatar", u.getAvatar() == null ? "" : u.getAvatar());
        m.put("role", u.getRole());
        m.put("weeklyGoal", u.getWeeklyGoal());
        return m;
    }
}
