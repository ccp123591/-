# FitCoach Backend Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the FitCoach Spring Boot backend from "competition demo with many placeholders" into a single-host production-ready backend covering every contract the Vue PWA frontend consumes — adding real Xiaomi MiMo AI integration, Redis-backed security, Flyway migrations, full service layer, DTOs, tests, and prod hardening.

**Architecture:** Spring Boot 3.2 + Java 17. Layered: Controller (thin, DTO mapping) → Service (business) → Repository (JPA). Cross-cutting via `config/`, `security/`, `exception/`, `infra/` (storage, ai, sms, mail, ratelimit). Redis hard dependency for blacklist/ratelimit/cache/verify-codes. Flyway in prod, `ddl-auto: update` + `data.sql` in dev (H2).

**Tech Stack:** Spring Boot 3.2.5, Spring Security 6, Spring Data JPA + Redis, Flyway, JJWT 0.12, Lombok, SpringDoc OpenAPI 2.3, Apache Tika, JUnit 5 + MockMvc + Mockito + Testcontainers (Redis).

**Spec:** `docs/superpowers/specs/2026-05-13-fitcoach-backend-design.md`

**Working dir:** `home-fitness-fullstack/backend/` (all paths below are relative to this unless noted).

---

## File Structure (target end state)

```
src/main/java/com/fitcoach/
├── FitCoachApplication.java                                  # cleaned: OpenAPI annots moved out
├── common/
│   ├── ApiResult.java                                        # unchanged
│   ├── PageResult.java                                       # unchanged
│   └── Constants.java                                        # NEW: shared keys
├── config/
│   ├── CorsConfig.java                                       # unchanged
│   ├── OpenApiConfig.java                                    # NEW
│   ├── WebMvcConfig.java                                     # NEW: static handler + multipart
│   ├── RedisConfig.java                                      # NEW
│   ├── FlywayConfig.java                                     # NEW: profile-aware
│   ├── JpaConfig.java                                        # NEW: EnableJpaAuditing (moved off App)
│   └── DataInitializer.java                                  # existing, made idempotent
├── security/
│   ├── SecurityConfig.java                                   # add RateLimitFilter, headers
│   ├── JwtUtil.java                                          # add jti, fail-fast secret
│   ├── JwtAuthFilter.java                                    # consult blacklist
│   ├── SecurityUtil.java                                     # unchanged
│   ├── PasswordPolicy.java                                   # NEW
│   ├── LoginAttemptService.java                              # NEW: Redis counter + DB audit
│   ├── RateLimitFilter.java                                  # NEW
│   ├── RefreshTokenStore.java                                # NEW: Redis primary, DB fallback
│   └── RefreshTokenBlacklist.java                            # NEW: JPA entity + repo
├── exception/
│   ├── BusinessException.java                                # unchanged
│   └── GlobalExceptionHandler.java                           # extended
├── infra/
│   ├── ai/
│   │   ├── AiCoachProvider.java                              # NEW interface
│   │   ├── CoachContext.java                                 # NEW DTO (input)
│   │   ├── CoachAiResponse.java                              # NEW DTO (output, distinct from entity)
│   │   ├── MockCoachProvider.java                            # NEW
│   │   ├── MimoCoachProvider.java                            # NEW: OpenAI-compatible RestClient
│   │   └── CoachPromptTemplates.java                         # NEW: system+user prompt builders
│   ├── sms/
│   │   ├── SmsSender.java                                    # NEW interface
│   │   └── MockSmsSender.java                                # NEW
│   ├── mail/
│   │   ├── MailSender.java                                   # NEW interface
│   │   └── MockMailSender.java                               # NEW
│   ├── storage/
│   │   ├── StorageService.java                               # NEW interface
│   │   └── LocalStorageService.java                          # NEW
│   └── ratelimit/
│       └── RateLimiter.java                                  # NEW: Redis token bucket
├── auth/
│   ├── AuthController.java                                   # refactored: real SMS/phone/forgot/reset
│   ├── AuthService.java                                      # extended
│   ├── VerifyCodeService.java                                # NEW: codes via Redis
│   ├── PasswordResetService.java                             # NEW
│   └── dto/
│       ├── LoginEmailRequest.java                            # NEW
│       ├── LoginPhoneRequest.java                            # NEW
│       ├── LoginGuestRequest.java                            # NEW
│       ├── RegisterRequest.java                              # NEW
│       ├── SendCodeRequest.java                              # NEW (phone or email)
│       ├── ForgotPasswordRequest.java                        # NEW
│       ├── ResetPasswordRequest.java                         # NEW
│       ├── RefreshRequest.java                               # NEW
│       ├── AuthResponse.java                                 # NEW (tokens + user)
│       └── PublicUser.java                                   # NEW (no password hash)
├── user/                                                     # existing + dto/
├── session/                                                  # existing + dto/SessionRequest
├── coach/
│   ├── CoachController.java                                  # thin, uses DTOs
│   ├── CoachService.java                                     # NEW
│   ├── CoachFeedback.java                                    # existing entity
│   ├── CoachFeedbackRepository.java                          # existing + paged query
│   └── dto/
│       ├── FeedbackRequest.java                              # NEW
│       └── FeedbackResponse.java                             # NEW
├── plan/
│   ├── PlanController.java                                   # thin
│   ├── PlanService.java                                      # NEW
│   ├── Plan.java, UserPlan.java                              # entities
│   ├── PlanRepository.java, UserPlanRepository.java          # unchanged
│   └── dto/PlanRequest.java, PlanResponse.java, ProgressRequest.java
├── badge/
│   ├── BadgeController.java                                  # thin
│   ├── BadgeService.java                                     # existing (already a service)
│   └── dto/BadgeResponse.java
├── leaderboard/
│   ├── LeaderboardController.java                            # thin
│   └── LeaderboardService.java                               # NEW, Redis-cached
├── social/
│   ├── SocialController.java                                 # thin
│   ├── SocialService.java                                    # NEW
│   ├── Post.java, PostComment.java, PostLike.java            # entities
│   └── dto/PostRequest.java, PostResponse.java, CommentRequest.java
├── challenge/                                                # NEW package
│   ├── Challenge.java
│   ├── ChallengeParticipant.java
│   ├── ChallengeRepository.java
│   ├── ChallengeParticipantRepository.java
│   ├── ChallengeService.java
│   ├── ChallengeController.java
│   └── dto/ChallengeResponse.java, ChallengeRankRow.java
├── exercise/
│   ├── ExerciseController.java                               # thin
│   ├── ExerciseService.java                                  # NEW
│   └── dto/ExerciseRequest.java
└── admin/
    ├── AdminController.java                                  # thin
    ├── AdminService.java                                     # NEW
    └── dto/DashboardResponse.java, AdminUserResponse.java, AnalyticsResponse.java

src/main/resources/
├── application.yml                                           # extended: redis, multipart, ai.coach.mimo, actuator
├── application-dev.yml                                       # flyway off
├── application-prod.yml                                      # flyway on, secrets-from-env, headers
├── application-test.yml                                      # NEW
├── data.sql                                                  # dev seed (existing, fixed)
├── logback-spring.xml                                        # NEW (prod rolling, dev console)
└── db/migration/
    ├── V1__init_schema.sql                                   # NEW: all tables (old + new)
    ├── V2__seed_exercises.sql
    ├── V3__seed_plans.sql                                    # real itemsJson
    ├── V4__seed_badges.sql
    └── V5__seed_challenges.sql

src/test/java/com/fitcoach/
├── auth/AuthServiceTest.java, AuthControllerIT.java, VerifyCodeServiceTest.java
├── coach/CoachServiceTest.java, MimoCoachProviderTest.java, MockCoachProviderTest.java
├── plan/PlanServiceTest.java, PlanControllerIT.java
├── session/SessionServiceTest.java, SessionRepositoryTest.java
├── social/SocialServiceTest.java
├── leaderboard/LeaderboardServiceTest.java
├── challenge/ChallengeServiceTest.java, ChallengeControllerIT.java
├── badge/BadgeServiceTest.java
├── admin/AdminServiceTest.java
├── security/JwtUtilTest.java, PasswordPolicyTest.java, RateLimiterTest.java
└── flow/FitnessFlowIT.java                                   # end-to-end
```

---

## Phase Plan (44 tasks across 10 phases)

| Phase | Tasks | Theme |
|---|---|---|
| 1. Infra foundation | T1 – T7 | deps, Redis, static, Flyway, exceptions, Actuator |
| 2. AI Coach (MiMo) | T8 – T12 | provider abstraction + real Coach |
| 3. Auth completion | T13 – T18 | SMS, phone, forgot/reset, blacklist, ratelimit |
| 4. Service refactor | T19 – T23 | plan, social, leaderboard, admin, exercise |
| 5. Challenge | T24 | new module end-to-end |
| 6. Cross-cutting | T25 – T27 | upload security, request log, seed fix |
| 7. Tests | T28 – T31 | repo, service, controller, e2e |
| 8. Prod hardening | T32 – T33 | prod yml, headers, docker-compose, README |
| 9. User profile + AI memory | T34 – T38 | t_user_profile, async extraction, MiMo prompt enrichment |
| 10. Vector memory (RAG) | T39 – T44 | Redis Stack, EmbeddingProvider, KNN over sessions, rebuild endpoint |

Conventions for every task:
- `cd home-fitness-fullstack/backend` before maven commands
- `mvn -q test` to compile & run tests; `mvn -q -Dtest=ClassName test` for one class
- Commit message format: `feat(<scope>): ...`, `fix(<scope>): ...`, `refactor(<scope>): ...`, `test(<scope>): ...`, `chore(<scope>): ...`
- After every task with code: run tests, then commit. Each task's last step is "commit"
- If a Maven command needs network for new deps and fails locally, document in commit message — do NOT skip


---

## Phase 1 — Infrastructure foundation

### Task T1: Add new Maven dependencies

**Files:**
- Modify: `home-fitness-fullstack/backend/pom.xml`

- [ ] **Step 1: Add deps inside `<dependencies>`**

```xml
<!-- Redis -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>

<!-- Actuator -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

<!-- Flyway -->
<dependency>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-mysql</artifactId>
</dependency>

<!-- Apache Tika (upload MIME detection) -->
<dependency>
  <groupId>org.apache.tika</groupId>
  <artifactId>tika-core</artifactId>
  <version>2.9.2</version>
</dependency>

<!-- Test: Testcontainers Redis -->
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>junit-jupiter</artifactId>
  <version>1.19.7</version>
  <scope>test</scope>
</dependency>
```

- [ ] **Step 2: Verify resolves** — `mvn -q -DskipTests compile` should be BUILD SUCCESS.
- [ ] **Step 3: Commit** — `git add pom.xml && git commit -m "chore(deps): add redis, actuator, flyway, tika, testcontainers"`

---

### Task T2: Redis configuration

**Files:**
- Create: `src/main/java/com/fitcoach/config/RedisConfig.java`
- Modify: `src/main/resources/application.yml`

- [ ] **Step 1: Add Redis section to `application.yml`** under `spring:`

```yaml
  data:
    redis:
      host: ${SPRING_DATA_REDIS_HOST:localhost}
      port: ${SPRING_DATA_REDIS_PORT:6379}
      password: ${SPRING_DATA_REDIS_PASSWORD:}
      timeout: 3s
      lettuce:
        pool:
          max-active: 16
          max-idle: 8
          min-idle: 2
```

- [ ] **Step 2: Create `RedisConfig.java`**

```java
package com.fitcoach.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RedisConfig {
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory cf) {
        return new StringRedisTemplate(cf);
    }
}
```

- [ ] **Step 3: Smoke test** — start Redis (`docker run --rm -p 6379:6379 redis:7-alpine`), then `mvn -q spring-boot:run`. App should start; logs show Lettuce connecting. Ctrl-C.
- [ ] **Step 4: Commit** — `git add ... && git commit -m "feat(config): add Redis configuration with StringRedisTemplate"`

---

### Task T3: WebMvc — static handler + multipart + 404 throw

**Files:**
- Create: `src/main/java/com/fitcoach/config/WebMvcConfig.java`
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/java/com/fitcoach/security/SecurityConfig.java`

- [ ] **Step 1: Add to `application.yml`**

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 5MB
      max-request-size: 10MB
  mvc:
    throw-exception-if-no-handler-found: true
  web:
    resources:
      add-mappings: false
```

- [ ] **Step 2: Create `WebMvcConfig.java`**

```java
package com.fitcoach.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${upload.dir:./uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path dir = Paths.get(uploadDir).toAbsolutePath();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + dir + "/")
                .setCachePeriod(3600);
        registry.addResourceHandler("/swagger-ui/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/swagger-ui/");
    }
}
```

- [ ] **Step 3: Permit `/uploads/**` in `SecurityConfig`** — add to the existing permitAll matchers list.
- [ ] **Step 4: Smoke test** — `curl -I http://localhost:8080/uploads/nonexistent.png` → 404 (handler exists).
- [ ] **Step 5: Commit** — `git commit -m "feat(config): serve /uploads/** + multipart 5MB + 404 throw"`

---

### Task T4: OpenApiConfig + JpaConfig extraction

**Files:**
- Create: `src/main/java/com/fitcoach/config/OpenApiConfig.java`
- Create: `src/main/java/com/fitcoach/config/JpaConfig.java`
- Modify: `src/main/java/com/fitcoach/FitCoachApplication.java`

- [ ] **Step 1: Create `OpenApiConfig.java`** — move all OpenAPI annotations from `FitCoachApplication` into this `@Configuration` class.
- [ ] **Step 2: Create `JpaConfig.java`** with `@Configuration @EnableJpaAuditing`.
- [ ] **Step 3: Strip `@OpenAPIDefinition`, `@SecurityScheme`, `@EnableJpaAuditing` from `FitCoachApplication`** — keep only `@SpringBootApplication`.
- [ ] **Step 4: Verify Swagger UI loads** at http://localhost:8080/swagger-ui.html, scheme `bearerAuth` shown.
- [ ] **Step 5: Commit** — `git commit -m "refactor(config): extract OpenAPI + JpaAuditing into dedicated config classes"`

---

### Task T5: Flyway migrations (profile-aware)

**Files:**
- Create: `src/main/resources/db/migration/V1__init_schema.sql`
- Create: `src/main/resources/db/migration/V2__seed_exercises.sql`
- Create: `src/main/resources/db/migration/V3__seed_plans.sql`
- Create: `src/main/resources/db/migration/V4__seed_badges.sql`
- Create: `src/main/resources/db/migration/V5__seed_challenges.sql`
- Modify: `src/main/resources/application-dev.yml`
- Modify: `src/main/resources/application-prod.yml`

- [ ] **Step 1: Write `V1__init_schema.sql`** — full MySQL DDL covering every current entity PLUS 6 new tables: `t_refresh_token_blacklist`, `t_verify_code`, `t_password_reset`, `t_challenge`, `t_challenge_participant`, `t_login_attempt`. See spec §3.2 for column lists.

The full DDL (copyable):

```sql
CREATE TABLE t_user ( id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, email VARCHAR(64) UNIQUE, phone VARCHAR(20), password_hash VARCHAR(128), nickname VARCHAR(32) NOT NULL, avatar VARCHAR(256), role VARCHAR(16) NOT NULL DEFAULT 'USER', login_type VARCHAR(16), open_id VARCHAR(128), device_id VARCHAR(128), status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE', weekly_goal INT DEFAULT 50, created_at DATETIME, updated_at DATETIME, INDEX idx_email (email), INDEX idx_phone (phone)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE t_user_follow ( id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, follower_id BIGINT NOT NULL, following_id BIGINT NOT NULL, created_at DATETIME, UNIQUE KEY uk_follow (follower_id, following_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE t_session ( id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, action VARCHAR(32) NOT NULL, action_label VARCHAR(32), reps INT NOT NULL, target_reps INT, duration INT NOT NULL, score INT, rhythm_score INT, stability_score INT, depth_score INT, symmetry_score INT, completion_score INT, session_date VARCHAR(32), notes VARCHAR(512), created_at DATETIME, INDEX idx_user_date (user_id, session_date), INDEX idx_action (action)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE t_exercise ( code VARCHAR(32) NOT NULL PRIMARY KEY, name VARCHAR(32) NOT NULL, description VARCHAR(256), kind VARCHAR(32), icon VARCHAR(256), video_url VARCHAR(512), landmarks_json TEXT, default_threshold_down INT, default_threshold_up INT, enabled BOOLEAN DEFAULT TRUE, sort_order INT DEFAULT 0) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE t_plan ( id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, title VARCHAR(64) NOT NULL, description VARCHAR(256), level VARCHAR(16), cover VARCHAR(128), days INT, items_json TEXT, official BOOLEAN DEFAULT FALSE, published BOOLEAN DEFAULT TRUE, author_id BIGINT, adopt_count INT DEFAULT 0, created_at DATETIME) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE t_user_plan ( id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, plan_id BIGINT NOT NULL, progress_day INT NOT NULL DEFAULT 0, status VARCHAR(16) DEFAULT 'ACTIVE', adopted_at DATETIME, updated_at DATETIME, UNIQUE KEY uk_user_plan (user_id, plan_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE t_coach_feedback ( id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, session_id BIGINT, review VARCHAR(500), suggestion VARCHAR(500), encouragement VARCHAR(300), next_goal VARCHAR(200), provider VARCHAR(32), tokens_used INT DEFAULT 0, created_at DATETIME, INDEX idx_user_created (user_id, created_at)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE t_badge ( code VARCHAR(32) NOT NULL PRIMARY KEY, name VARCHAR(32) NOT NULL, description VARCHAR(128), icon VARCHAR(16), criteria_json TEXT, sort_order INT DEFAULT 0) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE t_user_badge ( id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, badge_code VARCHAR(32) NOT NULL, unlocked_at DATETIME, UNIQUE KEY uk_user_badge (user_id, badge_code)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE t_post ( id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, session_id BIGINT, content VARCHAR(1000), likes INT DEFAULT 0, comments_count INT DEFAULT 0, visibility VARCHAR(16) DEFAULT 'PUBLIC', created_at DATETIME, INDEX idx_post_user_created (user_id, created_at)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE t_post_comment ( id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, post_id BIGINT NOT NULL, user_id BIGINT NOT NULL, content VARCHAR(500) NOT NULL, created_at DATETIME, INDEX idx_pc_post_created (post_id, created_at)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE t_post_like ( id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, post_id BIGINT NOT NULL, user_id BIGINT NOT NULL, created_at DATETIME, UNIQUE KEY uk_post_like (post_id, user_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE t_refresh_token_blacklist ( id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, jti VARCHAR(64) NOT NULL, user_id BIGINT NOT NULL, expires_at DATETIME NOT NULL, reason VARCHAR(64), created_at DATETIME, UNIQUE KEY uk_jti (jti), INDEX idx_user (user_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE t_verify_code ( id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, target VARCHAR(128) NOT NULL, channel VARCHAR(16) NOT NULL, purpose VARCHAR(32) NOT NULL, code_hash VARCHAR(128) NOT NULL, expires_at DATETIME NOT NULL, consumed_at DATETIME, created_at DATETIME, INDEX idx_target (target)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE t_password_reset ( id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, token_hash VARCHAR(128) NOT NULL, expires_at DATETIME NOT NULL, used_at DATETIME, created_at DATETIME, UNIQUE KEY uk_token (token_hash)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE t_login_attempt ( id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, identifier VARCHAR(128) NOT NULL, ip VARCHAR(64), outcome VARCHAR(16) NOT NULL, created_at DATETIME, INDEX idx_identifier (identifier)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE t_challenge ( id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, title VARCHAR(64) NOT NULL, description VARCHAR(256), action VARCHAR(32) NOT NULL, target_reps INT NOT NULL, start_date VARCHAR(32), end_date VARCHAR(32) NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE', cover VARCHAR(128), created_at DATETIME) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE t_challenge_participant ( id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, challenge_id BIGINT NOT NULL, user_id BIGINT NOT NULL, joined_at DATETIME, progress_reps INT DEFAULT 0, completed BOOLEAN DEFAULT FALSE, UNIQUE KEY uk_chal_user (challenge_id, user_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- [ ] **Step 2: `V2__seed_exercises.sql`** — copy the current `INSERT INTO t_exercise ...` block from `data.sql`.
- [ ] **Step 3: `V3__seed_plans.sql`** — insert 4 plans with real `items_json` arrays (length = `days`, each entry `{"day":N,"items":[...]}`). For day-30 pushup challenge, ramp from 10 → 100.
- [ ] **Step 4: `V4__seed_badges.sql`** — copy current badge INSERTs.
- [ ] **Step 5: `V5__seed_challenges.sql`** — two example challenges (squat 1000 / pushup 300).
- [ ] **Step 6: Profile flags** — `application-dev.yml`: `spring.flyway.enabled: false`; `application-prod.yml`: `enabled: true`, `baseline-on-migrate: true`, `ddl-auto: validate`.
- [ ] **Step 7: Verify dev** — `mvn -q spring-boot:run` (dev) still uses H2 + `data.sql`; Flyway logs "disabled".
- [ ] **Step 8: Commit** — `git commit -m "feat(db): add Flyway profile-aware migrations (V1 + V2-V5 seeds)"`

---

### Task T6: Extend GlobalExceptionHandler

**Files:**
- Modify: `src/main/java/com/fitcoach/exception/GlobalExceptionHandler.java`

- [ ] **Step 1: Add handlers** for `NoHandlerFoundException`, `HttpRequestMethodNotSupportedException`, `HttpMessageNotReadableException`, `MethodArgumentTypeMismatchException`, `MissingServletRequestParameterException`, `MaxUploadSizeExceededException`, `DataIntegrityViolationException`. Each returns the appropriate HTTP status with `ApiResult.fail(code, msg)`.
- [ ] **Step 2: Smoke test** — `curl -s http://localhost:8080/api/nonexistent` returns `{"code":404,"message":"接口不存在",...}`.
- [ ] **Step 3: Commit** — `git commit -m "feat(exception): handle 404/405/400/413/409 globally"`

---

### Task T7: Actuator exposure + docker healthcheck

**Files:**
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/java/com/fitcoach/security/SecurityConfig.java`
- Modify: `home-fitness-fullstack/docker-compose.yml`

- [ ] **Step 1: `application.yml`** add `management.endpoints.web.exposure.include: health,info,metrics`.
- [ ] **Step 2: SecurityConfig** permit `/actuator/health` and `/actuator/info`; `/actuator/metrics` requires ROLE_ADMIN.
- [ ] **Step 3: docker-compose** change backend healthcheck to `wget -qO- http://localhost:8080/actuator/health`.
- [ ] **Step 4: Verify** — `curl -s http://localhost:8080/actuator/health` → `{"status":"UP"}`.
- [ ] **Step 5: Commit** — `git commit -m "feat(actuator): expose health/info/metrics; switch docker healthcheck"`


---

## Phase 2 — AI Coach (Xiaomi MiMo)

### Task T8: AiCoachProvider interface + DTOs

**Files:**
- Create: `src/main/java/com/fitcoach/infra/ai/AiCoachProvider.java`
- Create: `src/main/java/com/fitcoach/infra/ai/CoachContext.java`
- Create: `src/main/java/com/fitcoach/infra/ai/CoachAiResponse.java`
- Create: `src/main/java/com/fitcoach/infra/ai/CoachPromptTemplates.java`

- [ ] **Step 1: Interface**

```java
package com.fitcoach.infra.ai;

public interface AiCoachProvider {
    String name();
    CoachAiResponse feedback(CoachContext ctx);
    CoachAiResponse suggestion(CoachContext ctx);
    CoachAiResponse weeklyPlan(CoachContext ctx);
}
```

- [ ] **Step 2: `CoachContext`** — Lombok `@Builder @Data`. Fields: `userId, nickname, action, actionLabel, reps, targetReps, duration, score, rhythmScore, stabilityScore, depthScore, symmetryScore, completionScore, recentAvgScore, recentTotalReps, recentSessions (list of {date,action,reps,score})`.
- [ ] **Step 3: `CoachAiResponse`** — Lombok `@Data @Builder`. Fields: `review, suggestion, encouragement, nextGoal, provider, tokensUsed`.
- [ ] **Step 4: `CoachPromptTemplates`** — static helpers building the system + user prompts. System prompt instructs strict-JSON output with the 4 keys; user prompt is a compact JSON dump of `CoachContext`.
- [ ] **Step 5: Compile** — `mvn -q -DskipTests compile`.
- [ ] **Step 6: Commit** — `git commit -m "feat(coach): add AiCoachProvider interface + context/response DTOs"`

---

### Task T9: MockCoachProvider

**Files:**
- Create: `src/main/java/com/fitcoach/infra/ai/MockCoachProvider.java`
- Create: `src/test/java/com/fitcoach/coach/MockCoachProviderTest.java`

- [ ] **Step 1: Write failing test**

```java
@Test
void mock_provider_returns_deterministic_feedback_from_context() {
    var p = new MockCoachProvider();
    var ctx = CoachContext.builder().action("squat").reps(15).score(82).build();
    var r = p.feedback(ctx);
    assertThat(r.getReview()).contains("深蹲").contains("15");
    assertThat(r.getProvider()).isEqualTo("mock");
    assertThat(r.getTokensUsed()).isZero();
}
```

- [ ] **Step 2: Run test, expect FAIL** (class missing).
- [ ] **Step 3: Implement** — `@Component @ConditionalOnProperty(name="ai.coach.provider", havingValue="mock", matchIfMissing=true)`. Builds short Chinese feedback by string-formatting the context's action, reps, score; same shape for `suggestion` and `weeklyPlan`.
- [ ] **Step 4: Run test** → PASS.
- [ ] **Step 5: Commit** — `git commit -m "feat(coach): add MockCoachProvider with deterministic feedback"`

---

### Task T10: MimoCoachProvider (RestClient + OpenAI-compatible)

**Files:**
- Create: `src/main/java/com/fitcoach/infra/ai/MimoCoachProvider.java`
- Create: `src/test/java/com/fitcoach/coach/MimoCoachProviderTest.java`
- Modify: `src/main/resources/application.yml`

- [ ] **Step 1: Add MiMo config to `application.yml`**

```yaml
ai:
  coach:
    enabled: true
    provider: ${AI_COACH_PROVIDER:mock}   # mock | mimo
    mimo:
      api-key: ${MIMO_API_KEY:}
      base-url: ${MIMO_BASE_URL:https://api.xiaomimimo.com/v1}
      model: ${MIMO_MODEL:mimo-v2-flash}
      max-tokens: 600
      temperature: 0.7
      timeout-seconds: 20
```

(Remove old `ai.coach.claude` block.)

- [ ] **Step 2: Write failing test with MockRestServiceServer**

```java
@RestClientTest(MimoCoachProvider.class)
class MimoCoachProviderTest {
  @Autowired MimoCoachProvider provider;
  @Autowired MockRestServiceServer server;

  @Test
  void parses_chat_completion_json_response() {
    server.expect(requestTo("https://api.xiaomimimo.com/v1/chat/completions"))
          .andExpect(method(POST))
          .andRespond(withSuccess(
              "{\"choices\":[{\"message\":{\"content\":\"{\\\"review\\\":\\\"good\\\",\\\"suggestion\\\":\\\"x\\\",\\\"encouragement\\\":\\\"y\\\",\\\"nextGoal\\\":\\\"z\\\"}\"}}],\"usage\":{\"total_tokens\":120}}",
              MediaType.APPLICATION_JSON));
    var r = provider.feedback(CoachContext.builder().action("squat").reps(10).build());
    assertThat(r.getReview()).isEqualTo("good");
    assertThat(r.getTokensUsed()).isEqualTo(120);
    assertThat(r.getProvider()).isEqualTo("mimo");
  }
}
```

- [ ] **Step 3: Implement `MimoCoachProvider`** — `@Component @ConditionalOnProperty(name="ai.coach.provider", havingValue="mimo")`. Uses `org.springframework.web.client.RestClient`. Builds OpenAI-compatible body: `model`, `messages` (system + user), `max_tokens`, `temperature`, `response_format: {"type":"json_object"}`. Header `Authorization: Bearer <api-key>`. On JSON-parse failure of `content`, fall back to wrap in `review`. On HTTP error, throw `BusinessException(503, "AI coach unavailable")`.
- [ ] **Step 4: `@PostConstruct` validation** — if `provider=mimo` AND `api-key.isBlank()` log WARN + advise switching to mock; do NOT throw (we want graceful degradation).
- [ ] **Step 5: Run test** → PASS.
- [ ] **Step 6: Commit** — `git commit -m "feat(coach): add MimoCoachProvider (RestClient, OpenAI-compatible)"`

---

### Task T11: CoachService — orchestrate provider + persist

**Files:**
- Create: `src/main/java/com/fitcoach/coach/CoachService.java`
- Create: `src/test/java/com/fitcoach/coach/CoachServiceTest.java`
- Modify: `src/main/java/com/fitcoach/coach/CoachFeedbackRepository.java` (add paginated query)

- [ ] **Step 1: Repo addition**

```java
Page<CoachFeedback> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
```

- [ ] **Step 2: Write failing service test**

```java
@ExtendWith(MockitoExtension.class)
class CoachServiceTest {
  @Mock SessionRepository sessionRepo;
  @Mock CoachFeedbackRepository fbRepo;
  @Mock AiCoachProvider provider;
  @InjectMocks CoachService service;

  @Test
  void feedback_reads_session_and_persists_response() {
    Session s = new Session(); s.setId(1L); s.setUserId(7L);
    s.setAction("squat"); s.setReps(15); s.setScore(80);
    given(sessionRepo.findById(1L)).willReturn(Optional.of(s));
    given(sessionRepo.findByUserIdOrderBySessionDateDesc(7L)).willReturn(List.of(s));
    given(provider.name()).willReturn("mock");
    given(provider.feedback(any())).willReturn(
        CoachAiResponse.builder().review("r").suggestion("s").encouragement("e").nextGoal("g").provider("mock").tokensUsed(0).build());

    var resp = service.feedback(7L, 1L);
    assertThat(resp.getReview()).isEqualTo("r");
    verify(fbRepo).save(argThat(f -> f.getUserId().equals(7L) && f.getSessionId().equals(1L)));
  }

  @Test
  void feedback_403_when_session_belongs_to_other_user() {
    Session s = new Session(); s.setId(1L); s.setUserId(999L);
    given(sessionRepo.findById(1L)).willReturn(Optional.of(s));
    assertThatThrownBy(() -> service.feedback(7L, 1L))
        .isInstanceOf(BusinessException.class);
  }
}
```

- [ ] **Step 3: Implement `CoachService`** — methods `feedback(userId, sessionId)`, `suggestion(userId)`, `weeklyPlan(userId)`, `history(userId, page, size)`. Build `CoachContext` from session + last 7 sessions aggregate. Persist `CoachFeedback`. Wrap `CoachAiResponse` into a `FeedbackResponse` DTO for controllers.
- [ ] **Step 4: Run tests** → PASS.
- [ ] **Step 5: Commit** — `git commit -m "feat(coach): add CoachService (provider orchestration + persistence)"`

---

### Task T12: Refactor CoachController to use service + DTOs

**Files:**
- Create: `src/main/java/com/fitcoach/coach/dto/FeedbackRequest.java`
- Create: `src/main/java/com/fitcoach/coach/dto/FeedbackResponse.java`
- Modify: `src/main/java/com/fitcoach/coach/CoachController.java`

- [ ] **Step 1: DTOs** — `FeedbackRequest { @NotNull Long sessionId }`, `FeedbackResponse { String review, suggestion, encouragement, nextGoal, String provider, Integer tokensUsed, Long id, java.time.LocalDateTime createdAt }`.
- [ ] **Step 2: Controller becomes thin** — all four endpoints delegate to `CoachService`. Wire `@Valid` on `FeedbackRequest`. `/history` becomes paginated: `@RequestParam page, size`, returns `PageResult<FeedbackResponse>`.
- [ ] **Step 3: Integration test** — `@SpringBootTest` + MockMvc; assert `/api/coach/feedback` with mock provider returns provider="mock", response shape stable.
- [ ] **Step 4: Run all tests** → PASS.
- [ ] **Step 5: Commit** — `git commit -m "refactor(coach): thin controller using DTOs + CoachService"`


---

## Phase 3 — Auth completion

### Task T13: VerifyCodeService + SmsSender/MailSender infra

**Files:**
- Create: `src/main/java/com/fitcoach/infra/sms/SmsSender.java`
- Create: `src/main/java/com/fitcoach/infra/sms/MockSmsSender.java`
- Create: `src/main/java/com/fitcoach/infra/mail/MailSender.java`
- Create: `src/main/java/com/fitcoach/infra/mail/MockMailSender.java`
- Create: `src/main/java/com/fitcoach/auth/VerifyCodeService.java`
- Create: `src/test/java/com/fitcoach/auth/VerifyCodeServiceTest.java`

- [ ] **Step 1: Sender interfaces**

```java
public interface SmsSender { void send(String phone, String message); String name(); }
public interface MailSender { void send(String email, String subject, String body); String name(); }
```

- [ ] **Step 2: Mock implementations** — `@Component`, `@Primary` (only default impl); log `INFO` line with redacted target, return immediately.
- [ ] **Step 3: Write failing test** for `VerifyCodeService.generate(channel,target,purpose)` returns code, `verify(channel,target,purpose,code)` consumes once. Cooldown 60s prevents re-send.
- [ ] **Step 4: Implement `VerifyCodeService`** — Redis-only happy path. Keys: `verify:<channel>:<target>` TTL 5min, `verify:sent:<channel>:<target>` TTL 60s. Hashed code stored (SHA-256). On verify, GET + compare hash, then DEL.
- [ ] **Step 5: Run test** → PASS.
- [ ] **Step 6: Commit** — `git commit -m "feat(auth): add VerifyCodeService + Sms/Mail sender infra (mock)"`

---

### Task T14: Real SMS/Email send + phone login endpoints

**Files:**
- Modify: `src/main/java/com/fitcoach/auth/AuthService.java`
- Modify: `src/main/java/com/fitcoach/auth/AuthController.java`
- Create: `src/main/java/com/fitcoach/auth/dto/{SendCodeRequest,LoginPhoneRequest,LoginEmailRequest,RegisterRequest,RefreshRequest,LoginGuestRequest,AuthResponse,PublicUser}.java`

- [ ] **Step 1: DTOs with `@Valid` constraints** — `LoginEmailRequest { @NotBlank @Email String email; @NotBlank @Size(min=8) String password; }` etc. `SendCodeRequest { @NotBlank String target; @Pattern(regexp="sms|email") String channel; @Pattern(regexp="login|reset") String purpose; }`.
- [ ] **Step 2: AuthService methods** — `sendCode(channel,target,purpose)` (delegate to VerifyCodeService + sender), `loginByPhone(phone,code)` (consume code, find or create user by phone, issue tokens), `loginByEmail` (re-typed signature).
- [ ] **Step 3: Controller** — remove `loginByWechat` entirely (and its DTO if any). Real `loginByPhone`, `sendSms`, `sendEmail`. All endpoints use `@Valid` DTOs instead of raw Maps.
- [ ] **Step 4: Tests** — happy + 400 (bad code) + 429 (cooldown).
- [ ] **Step 5: Commit** — `git commit -m "feat(auth): real phone login + SMS/email code endpoints; drop WeChat"`

---

### Task T15: Password reset (forgot + reset)

**Files:**
- Create: `src/main/java/com/fitcoach/auth/PasswordResetService.java`
- Create: `src/main/java/com/fitcoach/auth/dto/{ForgotPasswordRequest,ResetPasswordRequest}.java`
- Create: `src/main/java/com/fitcoach/security/PasswordPolicy.java`
- Modify: `src/main/java/com/fitcoach/auth/AuthController.java`

- [ ] **Step 1: `PasswordPolicy`** — static helper `validate(String pw)` throws `BusinessException(400,...)` if len<8 or no letter+digit.
- [ ] **Step 2: Failing test for `PasswordResetService.forgot(email)`** stores a token in Redis (TTL 30min) and triggers MailSender.
- [ ] **Step 3: Implement** — `forgot`: generate `UUID.randomUUID()`, store sha256 hash in `pwreset:<userId>` Redis + `t_password_reset` audit row. Email link form is `https://<frontend-base>/reset?token=<raw>`. `reset(token, newPassword)`: re-hash token, find user, apply `PasswordPolicy.validate`, save bcrypt-hashed password, mark `t_password_reset.used_at`.
- [ ] **Step 4: Controller endpoints** — `POST /api/auth/password/forgot` `{email}`, `POST /api/auth/password/reset` `{token, newPassword}`. Permit both in SecurityConfig.
- [ ] **Step 5: Tests** — happy path; reset with stale token → 400.
- [ ] **Step 6: Commit** — `git commit -m "feat(auth): forgot/reset password + PasswordPolicy"`

---

### Task T16: Refresh token blacklist

**Files:**
- Modify: `src/main/java/com/fitcoach/security/JwtUtil.java` (add jti)
- Create: `src/main/java/com/fitcoach/security/RefreshTokenBlacklist.java` (JPA entity + repo in same file pair)
- Create: `src/main/java/com/fitcoach/security/RefreshTokenStore.java`
- Modify: `src/main/java/com/fitcoach/security/JwtAuthFilter.java`
- Modify: `src/main/java/com/fitcoach/auth/AuthService.java`
- Modify: `src/main/java/com/fitcoach/auth/AuthController.java`

- [ ] **Step 1: Add `jti` claim to refresh tokens only** in `generateRefreshToken` (use `UUID.randomUUID().toString()`). Access tokens stay jti-less — they expire in 2h, so revocation is not required and we avoid a Redis lookup on every authenticated request. Provide `getJti(String token)`.
- [ ] **Step 2: Entity `RefreshTokenBlacklist`** maps `t_refresh_token_blacklist`. Repo: `existsByJti`, `deleteByExpiresAtBefore`.
- [ ] **Step 3: `RefreshTokenStore`** — `revoke(jti, userId, exp, reason)`: write Redis `rt:black:<jti>` TTL=remaining + insert DB row. `isRevoked(jti)`: Redis primary; on Redis exception, fallback to DB.
- [ ] **Step 4: `JwtAuthFilter` consult** — *only when the claim `type == "refresh"`* (set on refresh tokens), look up the jti via `RefreshTokenStore.isRevoked`. Access tokens skip the lookup. Refresh tokens reaching this filter are rare (only the `/refresh` endpoint reads them), so the Redis hit is bounded.
- [ ] **Step 5: `AuthService.refresh`** — also reject when jti revoked.
- [ ] **Step 6: `AuthController.logout`** — read `Authorization` header, parse refresh token (if present in body or header), revoke its jti.
- [ ] **Step 7: Tests** — login → logout → refresh with old token returns 401.
- [ ] **Step 8: Commit** — `git commit -m "feat(auth): refresh token blacklist via Redis + DB fallback"`

---

### Task T17: LoginAttemptService + account lockout

**Files:**
- Create: `src/main/java/com/fitcoach/security/LoginAttemptService.java`
- Modify: `src/main/java/com/fitcoach/auth/AuthService.java`

- [ ] **Step 1: Service** — `recordFailure(identifier)` increments Redis `login:fail:<identifier>` (TTL 15 min); on 5th failure, write `t_login_attempt` row with outcome=LOCKED. `isLocked(identifier)` returns true while counter ≥ 5. `clear(identifier)` on success and removes the key.
- [ ] **Step 2: `AuthService.loginByEmail` integration** — check `isLocked` first → `BusinessException(423, "账号已锁定，请 15 分钟后再试")`. On password mismatch, `recordFailure`. On success, `clear`.
- [ ] **Step 3: Test** — 5 wrong passwords → 6th attempt returns 423 even with correct password.
- [ ] **Step 4: Commit** — `git commit -m "feat(auth): account lockout after 5 failed logins"`

---

### Task T18: RateLimitFilter (Redis token bucket)

**Files:**
- Create: `src/main/java/com/fitcoach/infra/ratelimit/RateLimiter.java`
- Create: `src/main/java/com/fitcoach/security/RateLimitFilter.java`
- Modify: `src/main/java/com/fitcoach/security/SecurityConfig.java`
- Modify: `src/main/java/com/fitcoach/exception/GlobalExceptionHandler.java` (new exception → 429)

- [ ] **Step 1: `RateLimiter.tryAcquire(scope, id, capacity, refillPerMinute)`** — Redis Lua script for atomic token-bucket. Returns true/false.
- [ ] **Step 2: Failing test** — 5 calls in a row succeed; 6th in same minute fails.
- [ ] **Step 3: `RateLimitFilter`** — `OncePerRequestFilter`. Maps path to scope:
  - `/api/auth/login/**` / `/api/auth/*/send` → 5/min/IP + 10/min/identifier
  - `/api/auth/register` → 3/min/IP
  - other `/api/**` authenticated → 60/min/user
  - exceeded → write 429 + `Retry-After: 60` header + `ApiResult.fail(429,"请求过快")` JSON body
- [ ] **Step 4: Register in SecurityConfig** before `JwtAuthFilter`.
- [ ] **Step 5: Integration test** — hammer `/api/auth/login/email` 6× → 6th returns 429.
- [ ] **Step 6: Commit** — `git commit -m "feat(auth): Redis token-bucket rate limit on auth endpoints"`


---

## Phase 4 — Service layer refactor

> Pattern for every task in this phase: (1) write characterization test that hits the existing controller and asserts current observable behavior; (2) extract logic into a new `*Service` bean; (3) keep controller as thin delegation; (4) the same characterization test must still pass.

### Task T19: PlanService

**Files:**
- Create: `src/main/java/com/fitcoach/plan/PlanService.java`
- Create: `src/main/java/com/fitcoach/plan/dto/{PlanRequest,PlanResponse,ProgressRequest}.java`
- Modify: `src/main/java/com/fitcoach/plan/PlanController.java`
- Create: `src/test/java/com/fitcoach/plan/PlanServiceTest.java`
- Create: `src/test/java/com/fitcoach/plan/PlanControllerIT.java`

- [ ] **Step 1: Characterization test** — MockMvc `GET /api/plans/official` returns 4 plans; `POST /api/plans/{id}/adopt` after auth returns "已采用".
- [ ] **Step 2: Extract** `PlanService` with `list`, `official`, `market`, `detail`, `create(uid, PlanRequest)`, `update(uid, id, PlanRequest)`, `delete(uid, id)`, `mine(uid)`, `adopt(uid, id)`, `abandon(uid, id)`, `progress(uid, id, ProgressRequest)`. Authorization (admin or author) lives in the service.
- [ ] **Step 3: Controller is now a 1-line delegate per endpoint**, accepting `@Valid` DTOs.
- [ ] **Step 4: Run both tests** → PASS.
- [ ] **Step 5: Commit** — `git commit -m "refactor(plan): extract PlanService; add request/response DTOs"`

### Task T20: SocialService

**Files:** analogous in `social/`.

- [ ] **Step 1: Characterization tests** — feed pagination, like idempotency, comment count increments.
- [ ] **Step 2: Extract `SocialService`** — `feed(page,size,me)`, `publish(uid, PostRequest)`, `detail(id,me)`, `delete(uid,id)`, `like/unlike(uid,id)`, `comment(uid,id,CommentRequest)`, `comments(id)`. Move `toPostMap` into the service as `toResponse(post, me)` returning `PostResponse` DTO. Move challenge stubs **out** (will be replaced in T24).
- [ ] **Step 3: Thin controller**.
- [ ] **Step 4: Commit** — `git commit -m "refactor(social): extract SocialService; PostResponse/PostRequest DTOs"`

### Task T21: LeaderboardService (cached)

**Files:**
- Create: `src/main/java/com/fitcoach/leaderboard/LeaderboardService.java`
- Modify: `src/main/java/com/fitcoach/leaderboard/LeaderboardController.java`
- Create: `src/test/java/com/fitcoach/leaderboard/LeaderboardServiceTest.java`

- [ ] **Step 1: Service** — `weekly()`, `monthly()`, `friends(uid)`. Each computes from `SessionRepository.aggregateSince`, then caches the JSON-serialized list to Redis at `cache:lb:weekly` / `cache:lb:monthly` / `cache:lb:friends:<uid>` with TTL 60s.
- [ ] **Step 2: Cache invalidation** — `evictAll()` for admin (used by adminService later).
- [ ] **Step 3: Test** — first call hits DB, second call within 60s hits cache (verify by mocking sessionRepo to throw after the first call still returns cached data).
- [ ] **Step 4: Commit** — `git commit -m "refactor(leaderboard): extract LeaderboardService with 60s Redis cache"`

### Task T22: AdminService

**Files:** analogous in `admin/`.

- [ ] **Step 1: Characterization test** — `GET /api/admin/dashboard` returns shape `{users, sessions, todaySessions, dau, pv7d}`; `POST /api/admin/users/{id}/ban` flips status.
- [ ] **Step 2: Extract `AdminService.dashboard()`, `users(page,size,keyword)`, `ban(id)`, `unban(id)`, `sessions(page,size)`, `analytics(metric)`**. Fix the `pv7d` loop: use `SessionRepository.countByDateRange(start,end)` (add a query) instead of repeated `>=` subtraction.
- [ ] **Step 3: Tests** → PASS.
- [ ] **Step 4: Commit** — `git commit -m "refactor(admin): extract AdminService; fix pv7d aggregation"`

### Task T23: ExerciseService

**Files:** analogous in `exercise/`.

- [ ] **Step 1: Service** — `list()`, `detail(code)`, `create(req)`, `update(code,req)`, `delete(code)`. Code-conflict (409) goes through `DataIntegrityViolationException` and is caught by global handler.
- [ ] **Step 2: DTO** `ExerciseRequest { @NotBlank code, @NotBlank name, ... }`.
- [ ] **Step 3: Test + Commit** — `git commit -m "refactor(exercise): extract ExerciseService; ExerciseRequest DTO"`

---

## Phase 5 — Challenge module

### Task T24: Challenge end-to-end

**Files:**
- Create: `src/main/java/com/fitcoach/challenge/{Challenge,ChallengeParticipant,ChallengeRepository,ChallengeParticipantRepository,ChallengeService,ChallengeController}.java`
- Create: `src/main/java/com/fitcoach/challenge/dto/{ChallengeResponse,ChallengeRankRow}.java`
- Modify: `src/main/java/com/fitcoach/social/SocialController.java` (remove obsolete challenge stubs)
- Modify: `src/main/java/com/fitcoach/config/DataInitializer.java` (idempotent challenge seed for dev)
- Create: `src/test/java/com/fitcoach/challenge/ChallengeServiceTest.java`
- Create: `src/test/java/com/fitcoach/challenge/ChallengeControllerIT.java`

- [ ] **Step 1: Entities** — `Challenge` maps `t_challenge` (with `@EntityListeners(AuditingEntityListener.class)`). `ChallengeParticipant` maps `t_challenge_participant`.
- [ ] **Step 2: Repos** — `ChallengeRepository.findByStatus(String)`, `findById`. `ChallengeParticipantRepository.findByChallengeId`, `existsByChallengeIdAndUserId`, `findByChallengeIdOrderByProgressRepsDesc`.
- [ ] **Step 3: Failing service tests** — `list()` returns ACTIVE only; `join(uid, id)` idempotent; `rank(id)` orders by participant `progressReps` desc.
- [ ] **Step 4: Implement `ChallengeService`** — `list()`, `detail(id)`, `join(uid, id)`, `rank(id)`. `rank` re-computes `progressReps` per participant from sessions matching challenge's `action` between `startDate..endDate`, persists back to participant rows, then sorts.
- [ ] **Step 5: `ChallengeController`** — `GET /api/challenges`, `GET /api/challenges/{id}`, `POST /api/challenges/{id}/join`, `GET /api/challenges/{id}/rank`. Permit `GET /api/challenges/**` for anonymous in SecurityConfig.
- [ ] **Step 6: Remove old challenge stubs** from `SocialController` (the 3 methods `challenges`, `join`, `challengeRank`).
- [ ] **Step 7: Dev seed** — `DataInitializer` upserts 2 example challenges if `ChallengeRepository.count() == 0`.
- [ ] **Step 8: Run all tests** → PASS.
- [ ] **Step 9: Commit** — `git commit -m "feat(challenge): full module (entities, service, controller, seed)"`

---

## Phase 6 — Cross-cutting

### Task T25: Upload security (Tika + extension + size)

**Files:**
- Create: `src/main/java/com/fitcoach/infra/storage/StorageService.java`
- Create: `src/main/java/com/fitcoach/infra/storage/LocalStorageService.java`
- Create: `src/main/java/com/fitcoach/infra/storage/UploadValidator.java`
- Modify: `src/main/java/com/fitcoach/user/UserService.java` (use storage + validator)
- Create: `src/test/java/com/fitcoach/infra/storage/UploadValidatorTest.java`

- [ ] **Step 1: `UploadValidator.validateImage(MultipartFile)`** — check extension in `{jpg,jpeg,png,webp}`; load Tika `DefaultDetector.detect(InputStream, Metadata)`; verify detected MIME starts with `image/`. Throw `BusinessException(400, "不支持的文件类型")`.
- [ ] **Step 2: `StorageService.save(category, userId, file)` returns relative URL**. `LocalStorageService` writes to `${upload.dir}/<category>/u<userId>-<rand>.<ext>` with randomized 12-char hex suffix; never trusts client filename.
- [ ] **Step 3: `UserService.saveAvatar`** delegates to validator + storage.
- [ ] **Step 4: Tests** — fake-PNG-with-EXE-bytes rejected; valid PNG accepted.
- [ ] **Step 5: Commit** — `git commit -m "feat(storage): validated upload pipeline (Tika MIME + extension + random name)"`

### Task T26: RequestLoggingFilter + MDC

**Files:**
- Create: `src/main/java/com/fitcoach/security/RequestLoggingFilter.java`
- Modify: `src/main/java/com/fitcoach/security/SecurityConfig.java`

- [ ] **Step 1: `OncePerRequestFilter`** — generate or read `X-Request-Id` (UUID short), MDC.put("requestId", id) + ("userId", currentUserId or "anon"). Wrap chain.doFilter in try/finally that logs `[<rid>] <method> <path> status=<n> duration=<ms>ms user=<id>`. Skip `/actuator/**`, `/swagger-ui/**`, `/uploads/**`, `/v3/api-docs/**`.
- [ ] **Step 2: Set `X-Request-Id` on response**.
- [ ] **Step 3: Register filter first in chain (before RateLimitFilter + JwtAuthFilter)**.
- [ ] **Step 4: Smoke test** — make any request, observe log + response header.
- [ ] **Step 5: Commit** — `git commit -m "feat(observability): request logging filter + X-Request-Id"`

### Task T27: Seed data + DataInitializer idempotency

**Files:**
- Modify: `src/main/resources/data.sql`
- Modify: `src/main/java/com/fitcoach/config/DataInitializer.java`

- [ ] **Step 1: `data.sql`** — replace the 4 `t_plan` rows: each gets a real `items_json` matching the day count. Add a `MERGE INTO t_exercise ...` pattern so re-runs don't error (or wrap inserts with `WHERE NOT EXISTS`).
- [ ] **Step 2: `DataInitializer`** — also seed 2 example challenges (`squat` 1000 / `pushup` 300) if `ChallengeRepository.count() == 0`. Use the injected `ChallengeRepository`.
- [ ] **Step 3: Verify** — fresh dev run shows 4 plans with non-empty `itemsJson` via `GET /api/plans/official`.
- [ ] **Step 4: Commit** — `git commit -m "feat(seed): real itemsJson for plans + idempotent challenge seed"`


---

## Phase 7 — Tests

### Task T28: Repository slice tests

**Files:**
- Create: `src/test/java/com/fitcoach/session/SessionRepositoryTest.java`
- Create: `src/test/resources/application-test.yml`

- [ ] **Step 1: `application-test.yml`** — H2 in-memory, JPA `create-drop`, Flyway disabled. Used by `@ActiveProfiles("test")`.
- [ ] **Step 2: `@DataJpaTest`** covers `SessionRepository.aggregateSince`, `search` with action+dateRange filters, `countByAction`, `sumRepsByUserId`, `findByUserIdSince`.
- [ ] **Step 3: Run** → green.
- [ ] **Step 4: Commit** — `git commit -m "test(session): repository slice tests with H2"`

### Task T29: Service unit tests fill-in

**Files:** add tests for any service that doesn't already have one from Phase 2-5.

- [ ] **Step 1: PasswordPolicyTest, JwtUtilTest, RateLimiterTest** — pure unit, no Spring.
- [ ] **Step 2: BadgeServiceTest, UserServiceTest, SessionServiceTest** — Mockito-only, cover happy + edge (e.g., `computeStreak` with gaps).
- [ ] **Step 3: AdminServiceTest** — cover the fixed pv7d calc.
- [ ] **Step 4: Commit** — `git commit -m "test(services): unit tests for password/jwt/ratelimit/badge/user/session/admin"`

### Task T30: Controller integration tests

**Files:**
- Create: one `*ControllerIT.java` per controller (or one combined `WebFlowIT.java`) that doesn't already exist from earlier phases.

- [ ] **Step 1: For each controller** write at least: (a) happy path with valid JWT, (b) 401 without JWT, (c) 400 with malformed body (where applicable), (d) 403 when accessing other-user resource.
- [ ] **Step 2: Use `@SpringBootTest` + `MockMvc`** with `@ActiveProfiles("test")`.
- [ ] **Step 3: For endpoints that depend on Redis** (`/api/auth/sms/send`, rate-limited routes), use **Testcontainers Redis** (`@Testcontainers`, `@Container static GenericContainer redis = new GenericContainer("redis:7-alpine").withExposedPorts(6379)`). On Windows hosts without Docker Desktop, annotate the test class with `@DisabledIfEnvironmentVariable(named="DISABLE_REDIS_IT", matches="true")` and document `set DISABLE_REDIS_IT=true` as the local-skip toggle.
- [ ] **Step 4: Commit** — `git commit -m "test(it): controller integration tests covering happy + 401/403/400"`

### Task T31: End-to-end FitnessFlowIT

**Files:**
- Create: `src/test/java/com/fitcoach/flow/FitnessFlowIT.java`

- [ ] **Step 1: One `@SpringBootTest` flow**: register new user → login → create 3 sessions → call coach `/feedback` (mock provider) → call badges `/check` (expects `first_training` unlocked) → call leaderboard `/weekly` (current user appears).
- [ ] **Step 2: Run** → green.
- [ ] **Step 3: Commit** — `git commit -m "test(flow): end-to-end register→login→session→coach→badge→leaderboard"`

---

## Phase 8 — Production hardening

### Task T32: application-prod.yml + secret validation + security headers

**Files:**
- Modify: `src/main/resources/application-prod.yml`
- Modify: `src/main/java/com/fitcoach/security/SecurityConfig.java`
- Modify: `src/main/java/com/fitcoach/security/JwtUtil.java`

- [ ] **Step 1: `application-prod.yml`** — full prod-only block:

```yaml
spring:
  jpa:
    open-in-view: false
    show-sql: false
  flyway:
    enabled: true
    baseline-on-migrate: true
  data:
    redis:
      host: ${SPRING_DATA_REDIS_HOST}
      port: ${SPRING_DATA_REDIS_PORT:6379}
      password: ${SPRING_DATA_REDIS_PASSWORD:}

server:
  error:
    include-message: never
    include-stacktrace: never
    include-binding-errors: never

cors:
  allowed-origins: ${CORS_ALLOWED_ORIGINS}

jwt:
  secret: ${JWT_SECRET}

ai:
  coach:
    provider: ${AI_COACH_PROVIDER:mock}
    mimo:
      api-key: ${MIMO_API_KEY:}

logging:
  level:
    root: INFO
    com.fitcoach: INFO
    org.hibernate: WARN
  file:
    name: logs/fitcoach.log
```

- [ ] **Step 2: `JwtUtil.@PostConstruct`** — if `secret == null || secret.getBytes(UTF_8).length < 32` and the active profile is `prod`, throw `IllegalStateException` so the app fails fast.
- [ ] **Step 3: `SecurityConfig`** — `http.headers(h -> h.httpStrictTransportSecurity(hsts -> hsts.maxAgeInSeconds(31536000)).contentTypeOptions(...).referrerPolicy(...).permissionsPolicy(p -> p.policy("camera=(self), microphone=()")))`. Conditional on prod via `@Profile` or by toggling via env.
- [ ] **Step 4: `logback-spring.xml`** — `<springProfile name="prod">` rolling file appender (100MB × 30); `<springProfile name="!prod">` console.
- [ ] **Step 5: Commit** — `git commit -m "feat(prod): hardened prod yml + JWT secret fail-fast + security headers"`

### Task T33: docker-compose + README

**Files:**
- Modify: `home-fitness-fullstack/docker-compose.yml`
- Modify: `home-fitness-fullstack/README.md`
- Modify: `README.md` (root)

- [ ] **Step 1: docker-compose** — add `redis` service (`redis:7-alpine` with healthcheck), backend `depends_on: redis: condition: service_healthy`, env vars `SPRING_DATA_REDIS_HOST=redis`, `JWT_SECRET`, `MIMO_API_KEY`, `AI_COACH_PROVIDER`, `CORS_ALLOWED_ORIGINS`. Add `redis-data` volume.
- [ ] **Step 2: Backend README** — table of new env vars + a "Production deploy" subsection.
- [ ] **Step 3: Root README** — feature matrix update: every "占位" cell flipped to done (except WeChat which is gone). Add MiMo and Redis to tech stack.
- [ ] **Step 4: Final smoke** — `docker compose up --build` boots backend + redis; `curl localhost:8080/actuator/health` → UP.
- [ ] **Step 5: Commit** — `git commit -m "chore: docker-compose adds redis; README updated for MiMo+Redis"`

---

## Done

After T33, the backend should:
- Serve every endpoint the frontend `api/*.js` calls with real data (no `Map`-based mocks).
- Boot identically in dev (H2 + data.sql + mock providers) and prod (MySQL + Flyway + MiMo + Redis).
- Pass `mvn -q test` end-to-end with Testcontainers Redis.
- Show `{"status":"UP"}` at `/actuator/health` and proper headers/CSRF disabled JWT chain.

## Risks during implementation

1. **MiMo response_format support** — if `mimo-v2-flash` returns plain text instead of JSON, the parser fallback (wrap-in-review) keeps the API non-broken.
2. **Testcontainers on Windows without Docker Desktop** — fallback is to `@DisabledIfEnvironmentVariable(named="CI", matches="windows-no-docker")` on Redis-dependent ITs.
3. **Flyway V1 mismatch with current JPA entities** — `ddl-auto: validate` in prod will fail on mismatch; mitigation: explicit `mvn -q -Dtest=PlanRepositoryIT test` against MySQL via Testcontainers before merging.
4. **DataInitializer ordering** — runs before ChallengeRepository may be ready if classpath weirdness. Mitigation: `@DependsOn` or use `ApplicationRunner` with order.


---

## Phase 9 — User Profile + AI Memory

**Why:** Today every MiMo prompt is built from the latest session + last 7 raw sessions. That misses long-term context (favorite actions, weak dimensions, goal progression, last week's complaints). A user profile is a compact, versioned, persisted *memory* that gets fed into every coach prompt, making suggestions personalized and continuous across sessions.

**Design:**

- One row per user in `t_user_profile`, JSON column for the extracted profile + `updated_at`, `version`, `summary_text` (short Chinese summary for prompt injection).
- v1 extraction is **deterministic** (pure aggregation over `t_session` + `t_coach_feedback`): favorite action, total reps by action, best/avg scores, weakest dimension, streak stats, last 5 user-written notes. No MiMo call needed for v1 — cheap and reliable.
- v2 (opt-in via `ai.profile.summarizer=mimo`) calls MiMo to rewrite the deterministic aggregate into a 1-paragraph Chinese summary; cached 1 day.
- Trigger: `@TransactionalEventListener` on session-create → async refresh of profile. Also a manual `POST /api/users/me/profile/refresh`.
- Read endpoint: `GET /api/users/me/profile` returns the structured profile.
- `CoachService` injects `profile.summaryText` into `CoachContext` before calling the provider.

### Task T34: UserProfile entity + repo + table

**Files:**
- Create: `src/main/java/com/fitcoach/user/UserProfile.java`
- Create: `src/main/java/com/fitcoach/user/UserProfileRepository.java`
- Create: `src/main/resources/db/migration/V6__user_profile.sql`
- Modify: `src/main/resources/application.yml` (add `ai.profile.summarizer` config)

- [ ] **Step 1: V6 migration**

```sql
CREATE TABLE t_user_profile (
  user_id BIGINT NOT NULL PRIMARY KEY,
  profile_json TEXT,
  summary_text VARCHAR(800),
  summarizer VARCHAR(16) DEFAULT 'aggregate',  -- aggregate | mimo
  version INT NOT NULL DEFAULT 1,
  updated_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- [ ] **Step 2: Entity** — `@Entity @Table(name="t_user_profile")`. Lombok. Fields match the SQL.
- [ ] **Step 3: Repo** — `UserProfileRepository extends JpaRepository<UserProfile, Long>` with `findByUserId`.
- [ ] **Step 4: Config**

```yaml
ai:
  profile:
    summarizer: ${AI_PROFILE_SUMMARIZER:aggregate}   # aggregate | mimo
    refresh-on-session: true
```

- [ ] **Step 5: Commit** — `git commit -m "feat(profile): UserProfile entity + V6 migration"`

### Task T35: ProfileExtractionService (v1 aggregate)

**Files:**
- Create: `src/main/java/com/fitcoach/user/ProfileExtractionService.java`
- Create: `src/main/java/com/fitcoach/user/dto/UserProfileDto.java`
- Create: `src/test/java/com/fitcoach/user/ProfileExtractionServiceTest.java`

- [ ] **Step 1: DTO** — `UserProfileDto` fields: `favoriteAction`, `favoriteActionLabel`, `actionCounts: Map<String,Long>`, `bestScore`, `avgScore`, `weakestDimension` (one of rhythm/stability/depth/symmetry/completion), `streakDays`, `totalDays`, `totalReps`, `recentNotes: List<String>`, `summaryText: String`, `updatedAt`.
- [ ] **Step 2: Failing test**

```java
@Test
void aggregate_extracts_favorite_action_and_weakest_dimension() {
    given(sessionRepo.findByUserIdOrderBySessionDateDesc(7L)).willReturn(List.of(
        sess("squat", 80, 90, 60, 85, 80, 95),
        sess("squat", 78, 88, 55, 80, 75, 90),
        sess("pushup", 70, 70, 70, 70, 70, 70)
    ));
    given(feedbackRepo.findByUserIdOrderByCreatedAtDesc(7L)).willReturn(List.of());
    var dto = service.aggregate(7L);
    assertThat(dto.getFavoriteAction()).isEqualTo("squat");
    assertThat(dto.getWeakestDimension()).isEqualTo("depth");
    assertThat(dto.getSummaryText()).contains("深蹲");
}
```

- [ ] **Step 3: Implement** — pure JPA aggregation. Choose favorite by count tiebreak total reps. Weakest dim = lowest avg over last 30 sessions. Build short `summaryText`: e.g. `"<nickname>偏爱深蹲(累计 N 次), 平均分 80, 深度待提升, 连续 X 天"`.
- [ ] **Step 4: Persist** — `service.refresh(uid)` calls `aggregate`, upserts `t_user_profile` (uses `UserProfileRepository.findByUserId` + save with incremented `version`).
- [ ] **Step 5: Test passes**.
- [ ] **Step 6: Commit** — `git commit -m "feat(profile): deterministic v1 aggregate extraction"`

### Task T36: Async refresh on session-create + optional MiMo summarizer

**Files:**
- Modify: `src/main/java/com/fitcoach/session/SessionService.java` (publish event)
- Create: `src/main/java/com/fitcoach/user/SessionCreatedEvent.java`
- Create: `src/main/java/com/fitcoach/user/ProfileRefreshListener.java`
- Modify: `src/main/java/com/fitcoach/FitCoachApplication.java` (add `@EnableAsync`)
- Optional: extend `ProfileExtractionService.refresh` to call MiMo when `summarizer=mimo`

- [ ] **Step 1: Event** — `record SessionCreatedEvent(Long userId, Long sessionId) {}`.
- [ ] **Step 2: `SessionService.create`** — after `sessionRepo.save(s)`, publish event via `ApplicationEventPublisher`.
- [ ] **Step 3: Listener** — `@Component`, `@Async`, `@TransactionalEventListener(phase=AFTER_COMMIT)`; first reads existing profile via `UserProfileRepository.findByUserId(uid)` — if present AND `updated_at` is within the last 60s, return early (re-entrancy guard). Otherwise call `profileExtractionService.refresh(uid)`.
- [ ] **Step 4: MiMo path** — when `ai.profile.summarizer=mimo`, after deterministic aggregate, send the aggregate JSON to MiMo with system prompt "用 1 段中文 ≤120 字总结这位用户". Replace `summaryText`. Cache 1 day to avoid spamming MiMo on every session.
- [ ] **Step 5: `@EnableAsync`** on app class.
- [ ] **Step 6: Test** — create 1 session → after 200ms `t_user_profile.updated_at` is recent.
- [ ] **Step 7: Commit** — `git commit -m "feat(profile): async refresh on session-create + optional MiMo summarizer"`

### Task T37: Wire profile into CoachService

**Files:**
- Modify: `src/main/java/com/fitcoach/coach/CoachService.java`
- Modify: `src/main/java/com/fitcoach/infra/ai/CoachContext.java`
- Modify: `src/main/java/com/fitcoach/infra/ai/CoachPromptTemplates.java`

- [ ] **Step 1: Add field** `String userProfileSummary` to `CoachContext`.
- [ ] **Step 2: `CoachService.feedback`** — also read `UserProfileRepository.findByUserId(uid)`; populate `ctx.userProfileSummary` with `profile.getSummaryText()` (or empty string if profile missing).
- [ ] **Step 3: `CoachPromptTemplates.userMessage`** — prepend `"用户画像: <summary>"` to the JSON dump (becomes "用户画像: ..." + structured context).
- [ ] **Step 4: Test** — `CoachServiceTest` adds a case where profile exists; verify the provider's input `ctx.userProfileSummary` is populated.
- [ ] **Step 5: Commit** — `git commit -m "feat(coach): inject user profile summary into MiMo prompt context"`

### Task T38: Profile endpoint + DTO

**Files:**
- Create: `src/main/java/com/fitcoach/user/ProfileController.java`
- Create: `src/test/java/com/fitcoach/user/ProfileControllerIT.java`

- [ ] **Step 1: Endpoints**

```java
@RestController
@RequestMapping("/api/users/me/profile")
@RequiredArgsConstructor
public class ProfileController {
    private final ProfileExtractionService service;

    @GetMapping
    public ApiResult<UserProfileDto> me() { return ApiResult.ok(service.read(SecurityUtil.currentUserId())); }

    @PostMapping("/refresh")
    public ApiResult<UserProfileDto> refresh() { return ApiResult.ok(service.refresh(SecurityUtil.currentUserId())); }
}
```

- [ ] **Step 2: `service.read`** returns the stored profile or — if missing — runs `aggregate` synchronously, persists, and returns.
- [ ] **Step 3: Frontend contract** — this is a NEW endpoint; tell frontend team to add `userApi.getProfile()` later; not required for backend completion.
- [ ] **Step 4: Test** — call refresh after creating sessions → returns updated DTO.
- [ ] **Step 5: Commit** — `git commit -m "feat(profile): GET/POST /api/users/me/profile endpoints"`

---

## Phase 10 — Vector memory (RAG over training history)

> Full design in spec §13. All paths relative to `home-fitness-fullstack/backend/`.

### Task T39: Switch to Redis Stack image + ai.memory config

**Files:**
- Modify: `home-fitness-fullstack/docker-compose.yml`
- Modify: `src/main/resources/application.yml`

- [ ] **Step 1: Compose** — change Redis service `image: redis:7-alpine` → `image: redis/redis-stack-server:7.4.0-v0`. Keep ports/volumes/healthcheck identical (RediSearch is enabled by default; FT.* commands work over the same 6379).
- [ ] **Step 2: application.yml** — add new section:

```yaml
ai:
  embedding:
    enabled: true
    provider: ${AI_EMBEDDING_PROVIDER:mock}      # mock | mimo
    dimension: 1024
    mimo:
      api-key: ${AI_EMBEDDING_API_KEY:${MIMO_API_KEY:}}
      base-url: ${AI_EMBEDDING_BASE_URL:https://api.xiaomimimo.com/v1}
      model: ${AI_EMBEDDING_MODEL:mimo-embedding-v1}
      timeout-seconds: 10
  memory:
    enabled: true
    top-k: 5
    distance-threshold: 0.45
    rebuild-batch-size: 200
    rebuild-cooldown-seconds: 60
```

- [ ] **Step 3: Smoke test** — `docker compose up redis -d && docker exec fitcoach-redis redis-cli FT._LIST` → returns empty array (no error → RediSearch is loaded).
- [ ] **Step 4: Commit** — `git commit -m "chore(infra): switch redis to redis-stack + add ai.memory config"`

---

### Task T40: EmbeddingProvider interface + Mock + OpenAI-compatible impls

**Files:**
- Create: `src/main/java/com/fitcoach/infra/ai/embedding/EmbeddingProvider.java`
- Create: `src/main/java/com/fitcoach/infra/ai/embedding/MockEmbeddingProvider.java`
- Create: `src/main/java/com/fitcoach/infra/ai/embedding/OpenAiCompatEmbeddingProvider.java`
- Create: `src/main/java/com/fitcoach/infra/ai/embedding/EmbeddingConfig.java`
- Create: `src/test/java/com/fitcoach/infra/ai/embedding/MockEmbeddingProviderTest.java`

- [ ] **Step 1: Interface**

```java
package com.fitcoach.infra.ai.embedding;

import java.util.List;

public interface EmbeddingProvider {
    List<float[]> embed(List<String> texts);
    int dimension();
    String name();
}
```

- [ ] **Step 2: MockEmbeddingProvider** — bean `@ConditionalOnProperty(name="ai.embedding.provider", havingValue="mock", matchIfMissing=true)`. Implementation: SHA-256(text) → seed `Random` → 1024 floats in `[-1,1]` → L2-normalize. Pure Java, zero external deps.
- [ ] **Step 3: OpenAiCompatEmbeddingProvider** — bean `@ConditionalOnProperty(name="ai.embedding.provider", havingValue="mimo")`. Uses Spring 6 `RestClient`. POST `${base-url}/embeddings` with `{"model": cfg.model, "input": texts}`. Parse response → `data[].embedding` → `float[]`. 10s timeout. Wrap exceptions as `BusinessException(503, "Embedding provider unavailable")`. On startup log a warning if `provider=mimo` but key blank, and degrade to mock (mirrors MiMo coach pattern).
- [ ] **Step 4: EmbeddingConfig** — `@ConfigurationProperties(prefix="ai.embedding")` record holding `enabled, provider, dimension, mimo`.
- [ ] **Step 5: Test** — same input twice → same output array; 1024 length; L2 norm in [0.99, 1.01].
- [ ] **Step 6: `mvn -q -Dtest=MockEmbeddingProviderTest test`** passes.
- [ ] **Step 7: Commit** — `git commit -m "feat(embedding): EmbeddingProvider interface + Mock + OpenAI-compat impls"`

---

### Task T41: VectorMemoryService (RediSearch index + KNN search)

**Files:**
- Create: `src/main/java/com/fitcoach/infra/ai/memory/VectorMemoryService.java`
- Create: `src/main/java/com/fitcoach/infra/ai/memory/Memory.java`
- Create: `src/main/java/com/fitcoach/infra/ai/memory/MemoryConfig.java`
- Create: `src/test/java/com/fitcoach/infra/ai/memory/VectorMemoryServiceIT.java`

- [ ] **Step 1: Memory record** — `record Memory(Long sessionId, Long userId, String action, int score, long createdAt, String text, double distance)`.
- [ ] **Step 2: MemoryConfig** — `@ConfigurationProperties(prefix="ai.memory")` record `(boolean enabled, int topK, double distanceThreshold, int rebuildBatchSize, int rebuildCooldownSeconds)`.
- [ ] **Step 3: VectorMemoryService**

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class VectorMemoryService {
    private final StringRedisTemplate template;       // for FT.* via execute()
    private final RedisTemplate<String, byte[]> bytesTemplate; // for HSET embedding
    private final EmbeddingProvider embedding;
    private final MemoryConfig cfg;
    private static final String INDEX = "idx:session:vec";
    private static final String KEY_PREFIX = "sess:vec:";

    @PostConstruct
    public void ensureIndex() {
        if (!cfg.enabled()) return;
        try {
            template.execute((RedisCallback<Object>) c -> c.execute("FT.INFO", INDEX.getBytes()));
            log.info("RediSearch index {} already exists", INDEX);
        } catch (Exception e) {
            createIndex();
        }
    }
    // createIndex(): FT.CREATE idx:session:vec ON HASH PREFIX 1 sess:vec: SCHEMA
    //   user_id TAG SORTABLE  action TAG  score NUMERIC SORTABLE  created NUMERIC SORTABLE
    //   text TEXT NOSTEM  embedding VECTOR HNSW 8 TYPE FLOAT32 DIM 1024 DISTANCE_METRIC COSINE M 16 EF_CONSTRUCTION 200

    public void index(SessionDoc doc) { ... }      // HSET sess:vec:<sessionId>
    public List<Memory> searchSimilar(Long uid, float[] query, int k) { ... }   // FT.SEARCH KNN
    public int reindexUser(Long uid, List<SessionDoc> docs) { ... }
}
```

Use `RedisCallback` to drop down to raw RESP for `FT.SEARCH`. Helper `floatsToLeBytes(float[])` writes IEEE 754 little-endian. Parse FT.SEARCH reply: `[total, key1, [field, val, ...], key2, ...]`.

- [ ] **Step 4: IT** — `@Testcontainers` with `redis/redis-stack-server:7.4.0-v0`. Skip if Docker not available (use `@EnabledIfSystemProperty(named="docker.available", matches="true")` and a static initializer that checks `DockerClientFactory.instance().isDockerAvailable()`). Insert 5 docs for u=1, 5 for u=2 with embeddings; KNN(u=1, K=3) returns only u=1 docs.
- [ ] **Step 5: `mvn -q -Dtest=VectorMemoryServiceIT test`** passes (or is `Skipped` when Docker unavailable — log clearly).
- [ ] **Step 6: Commit** — `git commit -m "feat(memory): VectorMemoryService over RediSearch (FT.CREATE + KNN)"`

---

### Task T42: VectorMemoryListener (async embed on session-create)

**Files:**
- Create: `src/main/java/com/fitcoach/infra/ai/memory/VectorMemoryListener.java`
- Create: `src/main/java/com/fitcoach/infra/ai/memory/SessionDoc.java`
- Modify: `src/main/java/com/fitcoach/session/SessionService.java` (no change if T36 already publishes the event — just verify)

- [ ] **Step 1: SessionDoc** — record holding the fields VectorMemoryService needs (sessionId, userId, action, score, createdAt epoch, text built from action+score+notes).
- [ ] **Step 2: Listener**

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class VectorMemoryListener {
    private final SessionRepository sessionRepo;
    private final VectorMemoryService memory;
    private final EmbeddingProvider embedding;
    private final MemoryConfig cfg;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(SessionCreatedEvent evt) {
        if (!cfg.enabled()) return;
        try {
            var s = sessionRepo.findById(evt.sessionId()).orElse(null);
            if (s == null) return;
            var doc = SessionDoc.from(s);
            float[] vec = embedding.embed(List.of(doc.text())).get(0);
            memory.index(doc, vec);
        } catch (Exception e) {
            log.warn("vector memory index failed sid={} : {}", evt.sessionId(), e.toString());
        }
    }
}
```

- [ ] **Step 3: Verify** — confirm `SessionService.create` already publishes `SessionCreatedEvent` (added in T36). If not, add it now.
- [ ] **Step 4: Quick smoke** — start dev backend with redis-stack, register a user, POST a session, then `redis-cli FT.SEARCH idx:session:vec '*' LIMIT 0 5` shows the document.
- [ ] **Step 5: Commit** — `git commit -m "feat(memory): async embed sessions on commit (RAG write path)"`

---

### Task T43: Inject memory into Coach prompt (RAG read path)

**Files:**
- Modify: `src/main/java/com/fitcoach/infra/ai/CoachContext.java`
- Modify: `src/main/java/com/fitcoach/infra/ai/CoachPromptTemplates.java`
- Modify: `src/main/java/com/fitcoach/coach/CoachService.java`
- Modify: `src/test/java/com/fitcoach/coach/CoachServiceTest.java`

- [ ] **Step 1: Add field** `List<Memory> recentMemories` to `CoachContext` (default empty list).
- [ ] **Step 2: CoachService** — for `feedback`/`suggestion`/`weeklyPlan`, after building the base context:

```java
if (memoryCfg.enabled()) {
    String queryText = (ctx.userProfileSummary() == null ? "" : ctx.userProfileSummary())
                     + "\n请求: " + ctx.requestKind();
    try {
        float[] qv = embedding.embed(List.of(queryText)).get(0);
        var hits = memory.searchSimilar(uid, qv, memoryCfg.topK()).stream()
                         .filter(m -> m.distance() <= memoryCfg.distanceThreshold())
                         .toList();
        ctx = ctx.withMemories(hits);
    } catch (Exception e) {
        log.warn("memory.search failed for uid={} : {}", uid, e.toString());
    }
}
```

- [ ] **Step 3: CoachPromptTemplates** — when `ctx.recentMemories` non-empty, add system line `"参考用户最近的训练记录(recentMemories)调整建议,但不要逐条复述。"` and append `"recentMemories": [{ "action":..,"score":..,"text":..,"daysAgo":..}, ...]` to the user-message JSON.
- [ ] **Step 4: Test** — extend `CoachServiceTest`: with memory.enabled=true and Mock provider returning consistent vectors, mock `VectorMemoryService` to return 2 hits → assert provider's invocation has `ctx.recentMemories.size()==2`. With memory.enabled=false → empty.
- [ ] **Step 5: `mvn -q -Dtest=CoachServiceTest test`** passes.
- [ ] **Step 6: Commit** — `git commit -m "feat(coach): RAG-inject recent training memories into MiMo prompt"`

---

### Task T44: Memory rebuild endpoint + admin variant

**Files:**
- Create: `src/main/java/com/fitcoach/user/MemoryController.java`
- Modify: `src/main/java/com/fitcoach/admin/AdminController.java` (add `POST /api/admin/memory/rebuild`)
- Modify: `src/main/java/com/fitcoach/infra/ai/memory/VectorMemoryService.java` (add `rebuildForUser` with cooldown)
- Create: `src/test/java/com/fitcoach/user/MemoryRebuildIT.java`

- [ ] **Step 1: MemoryController**

```java
@RestController
@RequestMapping("/api/users/me/memory")
@RequiredArgsConstructor
public class MemoryController {
    private final VectorMemoryService memory;

    @PostMapping("/rebuild")
    public ApiResult<Map<String,Object>> rebuild() {
        Long uid = SecurityUtil.currentUserId();
        var r = memory.rebuildForUser(uid);
        return ApiResult.ok(Map.of("reembedded", r.count(), "took", r.tookMs()));
    }
}
```

- [ ] **Step 2: VectorMemoryService.rebuildForUser(uid)**:
  - Cooldown check: read `memory:rebuild:cooldown:<uid>` from Redis; if present and TTL > 0, throw `BusinessException(429, "Memory rebuild cooldown active, retry in N s")`.
  - Page through `sessionRepo.findByUserIdOrderByCreatedAtDesc(uid, PageRequest.of(0, 500))`.
  - Embed in batches of `cfg.rebuildBatchSize()`.
  - Pipeline `HSET` writes (Lettuce `executePipelined`).
  - Set cooldown key with TTL `cfg.rebuildCooldownSeconds()`.
  - Return `{count, tookMs}`.
- [ ] **Step 3: AdminController** — add `POST /api/admin/memory/rebuild?userId=<id>` that calls the same service after `@PreAuthorize("hasRole('ADMIN')")`.
- [ ] **Step 4: IT** — register a user, create 3 sessions, POST `/api/users/me/memory/rebuild` → `count==3`. Second call within 60s → 429.
- [ ] **Step 5: `mvn -q -Dtest=MemoryRebuildIT test`** passes (skip if Docker unavailable).
- [ ] **Step 6: Commit** — `git commit -m "feat(memory): rebuild endpoint + admin variant + cooldown"`

---
