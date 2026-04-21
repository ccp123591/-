# FitCoach Backend

Spring Boot 3.2 后端工程，已搭建完整架构，所有 10 个领域的 Controller 全部占位就绪。

## 技术栈
- Spring Boot 3.2 · Spring Security 6 · Spring Data JPA
- JJWT 0.12 · SpringDoc OpenAPI 2.3
- H2（开发）/ MySQL（生产）
- Lombok · Bean Validation

## 启动命令
```bash
./mvnw spring-boot:run
# 或 mvn spring-boot:run
```

启动后：
- **接口文档**：http://localhost:8080/swagger-ui.html
- **H2 控制台**：http://localhost:8080/h2-console（jdbc:h2:mem:fitcoach · sa · 空密码）
- **前端代理**：http://localhost:5173 → `/api`

## 环境切换
```bash
# 开发环境（H2 内存数据库，默认）
mvn spring-boot:run

# 生产环境（MySQL）
mvn spring-boot:run -Dspring-boot.run.profiles=prod
# 需配置 DB_HOST、DB_USER、DB_PASSWORD 环境变量
```

## 接口占位状态

| 域 | Controller | 接口数 | 状态 |
| --- | --- | :---: | :---: |
| 认证 | AuthController | 10 | 🟡 占位 |
| 用户 | UserController | 9  | 🟡 占位 |
| 训练记录 | SessionController | 7 | 🟡 占位 |
| AI 教练 | CoachController | 4 | 🟡 占位 |
| 训练计划 | PlanController | 11 | 🟡 占位 |
| 徽章 | BadgeController | 3 | 🟡 占位 |
| 排行榜 | LeaderboardController | 3 | 🟡 占位 |
| 社交 | SocialController | 11 | 🟡 占位 |
| 动作库 | ExerciseController | 5 | 🟡 占位 |
| 管理后台 | AdminController | 6 | 🟡 占位 |

所有接口均已：
- ✅ 统一 `ApiResult<T>` 包装
- ✅ Swagger `@Operation` 注解到位
- ✅ 路径规则 `/api/**`
- ✅ JWT 鉴权集成

## 安全配置

- JWT 无状态鉴权（access 2h + refresh 30d）
- `/api/auth/**`、Swagger、H2 console 公开
- `/api/admin/**` 需 `ROLE_ADMIN`
- 其他需登录

## 领域模型

已定义 Entity + Repository：
- `User` · `Session` · `Exercise` · `Plan` · `Badge` · `UserBadge` · `Post` · `CoachFeedback`

## 下一步（业务落地）

1. **Auth Service** — 密码加密、Token 签发、验证码发送
2. **Session Service** — 训练记录 CRUD、批量同步、权限隔离
3. **Coach Service** — Claude API 对接、Prompt Caching、成本控制
4. **Leaderboard Service** — Redis ZSET 实时累加
5. **Badge Service** — 训练完成后的解锁判定
6. **Admin Service** — 数据统计聚合

## 默认账号（种子数据）

- 管理员：`admin@fitcoach.com` / `admin123`
- 演示用户：`demo@fitcoach.com` / `admin123`
