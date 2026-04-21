package com.fitcoach.config;

import com.fitcoach.user.User;
import com.fitcoach.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepo;
    private final PasswordEncoder encoder;

    @Override
    public void run(String... args) {
        upsert("admin@fitcoach.com", "管理员", "ADMIN", 100);
        upsert("demo@fitcoach.com",  "演示用户", "USER",  50);
    }

    private void upsert(String email, String nickname, String role, int weeklyGoal) {
        userRepo.findByEmail(email).orElseGet(() -> {
            User u = User.builder()
                    .email(email)
                    .passwordHash(encoder.encode("admin123"))
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
