package com.fitcoach.config;

import com.fitcoach.user.User;
import com.fitcoach.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final Environment env;

    @Override
    public void run(String... args) {
        boolean isProd = Arrays.asList(env.getActiveProfiles()).contains("prod");
        // 生产环境必须通过 ADMIN_INIT_PASSWORD 显式提供初始密码，否则跳过种子账号（防默认弱口令）
        String password = env.getProperty("ADMIN_INIT_PASSWORD", isProd ? "" : "admin123");
        if (password.isBlank()) {
            log.info("[seed] prod 未配置 ADMIN_INIT_PASSWORD，跳过种子账号创建");
            return;
        }
        upsert("admin@fitcoach.com", "管理员", "ADMIN", 100, password);
        if (!isProd) {
            upsert("demo@fitcoach.com", "演示用户", "USER", 50, password);
        }
    }

    private void upsert(String email, String nickname, String role, int weeklyGoal, String password) {
        userRepo.findByEmail(email).orElseGet(() -> {
            User u = User.builder()
                    .email(email)
                    .passwordHash(encoder.encode(password))
                    .nickname(nickname)
                    .role(role)
                    .loginType("email")
                    .status("ACTIVE")
                    .weeklyGoal(weeklyGoal)
                    .build();
            log.info("种子用户创建: {} ({})", email, role);
            return userRepo.save(u);
        });
    }
}
