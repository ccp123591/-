# FitCoach Backend

Spring Boot 3.2.5 后端（`fitcoach-backend` 3.0.0，Java 17）。16 个业务域全部落地为 Controller → Service → Repository 三层，无占位接口。

## 技术栈

- Spring Boot 3.2.5 · Spring Security 6 · Spring Data JPA · Spring Data Redis
- JJWT 0.12 · SpringDoc OpenAPI 2 · Apache Tika（上传 MIME 嗅探）
- H2（dev，内存）/ MySQL 8（prod）+ Flyway（prod）
- Lombok · Bean Validation · Spring Boot Mail

## 启动命令

```bash
mvn spring-boot:run          # 需 JDK 17；仓库未附带 mvnw wrapper 脚本
```

启动后：

- **接口文档**：http://localhost:8080/swagger-ui.html
- **H2 控制台**：http://localhost:8080/h2-console（`jdbc:h2:mem:fitcoach` · `sa` · 空密码）
- **健康检查**：http://localhost:8080/actuator/health
- **前端代理**：http://localhost:5173 → `/api`

## 环境切换

```bash
# dev（默认）：H2 内存库 + data.sql 种子，Flyway 关闭，Redis 健康检查关闭（无 Redis 也能起）
mvn spring-boot:run

# prod：MySQL + Flyway（唯一 schema 来源）+ ddl-auto=validate + 错误详情隐藏
mvn spring-boot:run -Dspring-boot.run.profiles=prod
# 必需环境变量：DB_HOST/DB_USER/DB_PASSWORD、JWT_SECRET(≥32 字节)、CORS_ALLOWED_ORIGINS、SMTP_*
```

## 接口总览

| 域 | Controller | 基础路径 | 接口数 |
| --- | --- | --- | :---: |
| 认证 | `AuthController` | `/api/auth` | 11 |
| 用户 | `UserController` | `/api/users` | 9 |
| 用户画像 | `ProfileController` | `/api/users/me/profile` | 2 |
| 训练记录 | `SessionController` | `/api/sessions` | 7 |
| AI 教练 | `CoachController` | `/api/coach` | 8 |
| 训练计划 | `PlanController` | `/api/plans` | 11 |
| 徽章 | `BadgeController` | `/api/badges` | 3 |
| 排行榜 | `LeaderboardController` | `/api/leaderboard` | 3 |
| 社交 | `SocialController` | `/api/posts/**` | 8 |
| 挑战赛 | `ChallengeController` | `/api/challenges` | 4 |
| 动作库 | `ExerciseController` | `/api/exercises` | 5 |
| 情感计算 | `EmotionController` | `/api/emotion` | 3 |
| 环境建模 | `RoomController` | `/api/room` | 4 |
| 跌倒预警 | `SafetyController` | `/api/safety` | 1 |
| 语音识别 | `AsrController` | `/api/asr` | 2 |
| 语音合成 | `TtsController` | `/api/tts` | 2 |
| 管理后台 | `AdminController` | `/api/admin` | 6 |

所有接口均：

- 统一 `ApiResult<T>` 包装（`code` / `message` / `data` / `timestamp`）
- 带 Swagger `@Operation` 注解
- 走 `/api/**` 路径规则与 JWT 鉴权
- 请求体用 `*Request` DTO + Bean Validation（`PUT /users/me`、`PUT /sessions/{id}` 有意保留 `Map`，因前端发部分字段）

`/api/coach` 下除文本类接口外，还有两个 `multipart/form-data` 视觉端点：`POST /form-critique`（动作关键帧点评）与 `POST /scene`（畅聊场景摘要）；加上 `POST /api/room/scan`（环境建模），三者都经 `VisionClient` 转发到 vision-svc，由可选的 JoyAI-VL 提供视觉语义理解。JoyAI-VL 未部署时 vision-svc 返回占位结果，其中环境建模会被 `vision.allow-placeholder-results=false` 拒绝入库。

## 安全配置

- JWT 无状态鉴权（access 2h + refresh 30d），refresh jti 黑名单存 Redis
- 公开：`/api/auth/**`、Swagger、H2 console、`/uploads/**`、`/actuator/health`、`/actuator/info`
- 公开只读：`/api/exercises/**`、`/api/plans/official`、`/api/leaderboard/**`
- `ROLE_ADMIN`：`/api/admin/**`、`/actuator/**`（含 metrics）
- 其余需登录
- 登录失败锁定（默认 5 次 / 15 分钟窗口）+ `RateLimitFilter` 限流，均依赖 Redis，Redis 不可用时 fail-open
- 安全响应头：HSTS 1 年 + includeSubDomains、`X-Content-Type-Options: nosniff`、`Referrer-Policy: strict-origin-when-cross-origin`、`Permissions-Policy: camera=(self), microphone=(self), geolocation=()`
  > `microphone` 必须是 `(self)`。空 allowlist 等于彻底禁用麦克风，语音畅聊与服务端 STT 会静默失效。
- `JwtUtil.@PostConstruct` 校验密钥 ≥ 32 字节，prod 不达标直接启动失败

## 领域模型

17 个 `@Entity`：

`User` · `UserProfile` · `UserFollow` · `Session` · `Exercise` · `Plan` · `UserPlan` · `Badge` · `UserBadge` · `Post` · `PostComment` · `PostLike` · `Challenge` · `ChallengeParticipant` · `CoachFeedback` · `EmotionRecord` · `RoomLayoutSnapshot`

prod schema 由 Flyway 迁移维护：`V1__init_schema` → `V2__seed_exercises` → `V3__seed_plans` → `V4__seed_badges` → `V5__seed_challenges` → `V6__user_profile` → `V7__room_layout` → `V8__emotion_record`。

## 跨域能力（`infra/`）

| 包 | 内容 |
| --- | --- |
| `infra/ai` | `AiCoachProvider` 接口 + `MimoCoachProvider`（OpenAI 兼容）/ `MockCoachProvider`，`CoachPromptTemplates` |
| `infra/asr` | `AsrProvider` + `MimoAsrProvider`（服务端语音识别，绕开移动端缺失的浏览器语音引擎） |
| `infra/tts` | `TtsProvider` + `MimoTtsProvider` / `BrowserFallbackTtsProvider` |
| `infra/memory` | RAG 向量记忆：`VectorMemoryService` + `InMemoryVectorMemoryStore` + `MockEmbeddingProvider` |
| `infra/notify` | `MailSender`（`SmtpMailSender` / `LogMailSender`）、`SmsSender`（`LogSmsSender`） |
| `infra/vision` | `VisionClient` 调 vision-svc（背后可选 JoyAI-VL），返回 `RoomFeatures` / `FormCritique` / `SceneSummary` |
| `infra/storage` | `LocalStorageService` + `UploadValidator`（Tika MIME 嗅探 + 扩展名 + 大小） |
| `infra/ratelimit` | `RateLimiter`（Redis 计数窗口） |

## 主要配置项

完整环境变量清单见 [../README.md](../README.md#环境变量清单生产部署需配置)，后端侧关键项：

| 配置 | 默认 | 说明 |
| --- | --- | --- |
| `ai.coach.provider` | `mock` | `mock` / `mimo` |
| `ai.tts.provider` | `browser` | `mimo` / `browser`；云不可用时 service 自动降级 browser |
| `ai.asr.provider` | `none` | `mimo` / `none`；`none` 时前端降级为文字输入 |
| `ai.memory.enabled` / `.store` / `.embedding` | `true` / `memory` / `mock` | RAG 向量记忆 |
| `emotion.analyzer` | `lexicon` | 中英词典法情感分析（`matchIfMissing`） |
| `vision.base-url` | `http://localhost:8081` | vision-svc 地址 |
| `vision.allow-placeholder-results` | `false` | 拒绝把占位识别结果当真实环境安全评估入库 |
| `security.login.max-attempts` / `.lock-window-minutes` | `5` / `15` | 登录锁定 |
| `security.trust-proxy-headers` | `false` | 反代后取 `X-Forwarded-For` 真实 IP |
| `upload.max-image-size-bytes` / `.max-audio-size-bytes` | 5 MB / 4 MB | 上传大小上限 |

## 观测

- 每请求带 `X-Request-Id` 并写入 MDC，日志格式 `[rid] METHOD PATH status=X duration=Yms user=Z`
- `/actuator/health`、`/actuator/info` 公开；`/actuator/metrics` 需 ADMIN

## 测试

```bash
mvn test        # 136 个单元 / 集成测试（JUnit 5 + MockMvc + Mockito）
```

覆盖：auth（注册 / 手机登录 / refresh / 锁定 / 验证码 / 密码重置）、coach（mock & mimo provider、controller 集成）、asr、tts、emotion、room、safety、session 校验、social 可见性、challenge、security（JWT filter / 限流 / 登录尝试）、infra（向量记忆 / 上传校验 / SMTP）、全局异常处理。

## 默认账号（种子数据）

dev profile 由 `DataInitializer` 自动创建：

- 管理员：`admin@fitcoach.com` / `admin123`
- 演示用户：`demo@fitcoach.com` / `admin123`

prod 必须显式配置 `ADMIN_INIT_PASSWORD` 才会创建管理员，且不创建演示用户；未配置则跳过种子账号（防默认弱口令）。
