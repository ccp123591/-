# FitCoach — AI 居家健身陪练系统（全栈版）

> 基于浏览器端姿态识别 + Spring Boot 后端 + AI 教练的全栈 PWA 应用

![架构](https://img.shields.io/badge/Architecture-Full%20Stack-blue) ![Vue](https://img.shields.io/badge/Vue-3-brightgreen) ![Spring%20Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green) ![PWA](https://img.shields.io/badge/PWA-Ready-purple)

---

## 一、项目简介

FitCoach 是一款面向居家场景的 **AI 健身陪练系统**，通过浏览器端摄像头实时识别用户姿态，结合自研评分算法和 AI 教练，为用户提供：

- 🎯 **实时动作识别**（MediaPipe Pose）
- 📊 **多维度评分**（节奏 / 稳定度 / 深度 / 对称性 / 完成率）
- 🎙️ **语音陪练**（TTS + 节拍器）
- 🤖 **AI 智能教练**（Claude 驱动个性化反馈）
- 🏆 **社交激励**（排行榜 / 好友 / 挑战赛 / 成就徽章）
- 📱 **全端自适应**（手机优先 · 平板 · 桌面）
- 📴 **离线可用**（PWA · 本地优先 · 联网同步）

---

## 二、架构总览

```
┌───────────────────────────────────────────────────────────────┐
│                      客 户 端 (Vue 3 PWA)                     │
│     手机  ·  平板  ·  桌面    —    自适应布局 + 离线缓存      │
└───────────────────────────┬───────────────────────────────────┘
                            │  HTTPS (REST/JSON)
┌───────────────────────────┴───────────────────────────────────┐
│                服 务 端 (Spring Boot 3.x)                     │
│  ┌───────┐┌───────┐┌──────────┐┌─────────┐┌────────┐┌───────┐│
│  │ Auth  ││ User  ││ Training ││AI Coach ││ Social ││ Plan  ││
│  └───────┘└───────┘└──────────┘└─────────┘└────────┘└───────┘│
│  ┌───────┐┌────────────┐┌──────────┐┌────────────┐           │
│  │ Badge ││Leaderboard ││ Exercise ││   Admin    │           │
│  └───────┘└────────────┘└──────────┘└────────────┘           │
└───────────────────────────┬───────────────────────────────────┘
                            │
             ┌──────────────┼──────────────┐
             │              │              │
        ┌────┴────┐    ┌───┴────┐    ┌───┴────┐
        │ MySQL   │    │ Redis  │    │ MinIO  │
        │ (数据) │    │ (缓存) │    │ (对象)  │
        └────────┘    └────────┘    └────────┘
```

---

## 三、目录结构

```
home-fitness-fullstack/
├── frontend/                      # Vue 3 前端 (PWA)
│   ├── public/                    # 静态资源、manifest、sw
│   ├── src/
│   │   ├── api/                   # 后端接口封装
│   │   ├── assets/                # 全局样式、图标
│   │   ├── components/            # 可复用组件
│   │   ├── composables/           # 组合式逻辑
│   │   ├── modules/               # 核心业务（姿态/语音/评分）
│   │   ├── router/                # 路由
│   │   ├── stores/                # Pinia 状态
│   │   ├── views/                 # 页面
│   │   ├── App.vue
│   │   └── main.js
│   ├── index.html
│   ├── vite.config.js
│   └── package.json
│
├── backend/                       # Spring Boot 后端
│   ├── src/main/java/com/fitcoach/
│   │   ├── FitCoachApplication.java
│   │   ├── common/                # 通用 Result / PageResult
│   │   ├── config/                # Cors / OpenAPI
│   │   ├── security/              # JWT / SecurityConfig
│   │   ├── exception/             # 全局异常处理
│   │   ├── auth/                  # 登录注册（多方式预留）
│   │   ├── user/                  # 用户资料
│   │   ├── session/               # 训练记录
│   │   ├── coach/                 # AI 教练（Claude）
│   │   ├── plan/                  # 训练计划
│   │   ├── badge/                 # 成就徽章
│   │   ├── leaderboard/           # 排行榜
│   │   ├── social/                # 社交动态
│   │   ├── exercise/              # 动作库
│   │   └── admin/                 # 管理后台
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── application-dev.yml
│   └── pom.xml
│
└── README.md
```

---

## 四、快速开始

### 方式 A：Docker Compose 一键启动（推荐）

```bash
cd home-fitness-fullstack

# dev 模式：backend (H2 内存) + redis + frontend
docker compose up -d --build

# 查看后端日志
docker compose logs -f backend

# 访问
# - 前端:        http://localhost:5173
# - 后端 API:    http://localhost:8080
# - Swagger UI:  http://localhost:8080/swagger-ui.html
# - 健康检查:    http://localhost:8080/actuator/health

# 停止
docker compose down

# 完整生产模式（MySQL + Flyway + prod 安全配置）
# 先复制 .env.example 为 .env，填写 DB/JWT/CORS 等必需值
docker compose -f docker-compose.yml -f docker-compose.prod.yml --profile prod up -d --build
```

> dev profile 用 H2 内存数据库 + data.sql 自动建表与种子；Redis 用真实容器，启用所有需要 Redis 的功能（限流、锁定、refresh 黑名单、RAG）。

### 方式 B：本机直跑

#### 1. 启动后端

```bash
cd backend
./mvnw spring-boot:run        # 或 mvn spring-boot:run
# 默认 dev profile：H2 + data.sql；Redis 健康检查在 dev 关闭，不强制需要 Redis
```

#### 2. 启动前端

```bash
cd frontend
npm install
npm run dev
# 默认 http://localhost:5173
```

### 测试账号（dev 自动种子）

| 邮箱 | 密码 | 角色 |
| --- | --- | --- |
| admin@fitcoach.com | admin123 | ADMIN |
| demo@fitcoach.com  | admin123 | USER  |

### 环境变量清单（生产部署需配置）

| 变量 | 必需 | 默认 | 说明 |
| --- | :---: | --- | --- |
| `SPRING_PROFILES_ACTIVE` | ✓ | dev | 生产 override 固定为 prod，使用 MySQL + Flyway |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | prod | localhost/3306/fitcoach | MySQL 连接 |
| `DB_USER` / `DB_PASSWORD` | prod | fitcoach/无 | MySQL 凭据，生产密码必填 |
| `SPRING_DATA_REDIS_HOST` / `_PORT` / `_PASSWORD` | ✓ | localhost/6379/空 | Redis 连接 |
| `JWT_SECRET` | prod | — | **prod 必须** ≥ 32 字节，启动校验 |
| `CORS_ALLOWED_ORIGINS` | prod | localhost 系列 | 跨域白名单（逗号分隔）|
| `SMTP_HOST` / `SMTP_PORT` / `SMTP_USERNAME` / `SMTP_PASSWORD` / `SMTP_FROM` | prod | 无/587/无 | 跌倒预警、验证码和重置邮件真实发送 |
| `JOYAI_VL_BASE_URL` / `JOYAI_VL_MODEL` / `JOYAI_VL_API_KEY` | JoyAI | 空/joyai-vl/空 | 指向分开部署的远端 OpenAI 兼容视觉端点，本机 Compose 不启动 JoyAI |
| `FRONTEND_PORT` / `BACKEND_PORT` / `VISION_PORT` / `REDIS_PORT` / `MYSQL_PORT` | — | 5173/8080/8081/6379/3306 | 宿主机端口冲突时可覆盖 |
| `AI_COACH_PROVIDER` | — | mock | mock / mimo |
| `MIMO_API_KEY` / `MIMO_BASE_URL` / `MIMO_MODEL` | mimo | — | 小米 MiMo 接入 |
| `AI_MEMORY_ENABLED` | — | true | RAG 向量记忆开关 |
| `AI_MEMORY_STORE` | — | memory | memory（内存）/ redis（未来）|
| `AI_MEMORY_EMBEDDING` | — | mock | mock（确定性 hash）/ openai |
| `EMOTION_ANALYZER` | — | lexicon | 词典法情感分析（matchIfMissing）|

---

## 五、技术选型

### 前端

| 技术 | 版本 | 作用 |
| --- | --- | --- |
| Vue | 3.4+ | 渐进式框架 |
| Vite | 5.x | 构建工具 |
| Vue Router | 4.x | 路由 |
| Pinia | 2.x | 状态管理 |
| Axios | 1.x | HTTP 请求 |
| MediaPipe Pose | 0.5 | 姿态识别 |
| Web Speech API | — | TTS 语音 |
| Web Audio API | — | 节拍器 |
| IndexedDB | — | 本地缓存 |
| Service Worker | — | PWA 离线 |

### 后端

| 技术 | 版本 | 作用 |
| --- | --- | --- |
| Spring Boot | 3.2.x | 应用框架 |
| Spring Security | 6.x | 鉴权 |
| Spring Data JPA | 3.x | ORM |
| JJWT | 0.12.x | JWT 工具 |
| SpringDoc | 2.x | OpenAPI 文档 |
| MySQL (H2 开发) | 8.x | 数据库 |
| Redis | 7.x | 缓存（可选）|
| Lombok | — | 简化代码 |

---

## 六、功能矩阵

| 模块 | 功能 | 前端 | 后端 |
| --- | --- | :---: | :---: |
| 训练 | 实时姿态识别 + 计数 + 评分 | ✅ | — |
| 训练 | 语音陪练 + 节拍器 | ✅ | — |
| 训练 | 倒计时 + 休息计时器 | ✅ | — |
| 账号 | 邮箱密码登录 + 注册 | ✅ | ✅ |
| 账号 | 手机验证码登录（SMS mock）| ✅ UI | ✅ |
| 账号 | 邮件验证码 / 密码重置 | ✅ UI | ✅ |
| 账号 | refresh token 黑名单 + 登出 | — | ✅ |
| 账号 | 5/15min 登录锁定 + 限流 | — | ✅ |
| 记录 | 云端同步 + 筛选 / 搜索 | ✅ | ✅ |
| 记录 | CSV 导出 | ✅ | ✅ |
| AI 教练 | 训练后智能点评（mock/mimo）| ✅ | ✅ |
| AI 教练 | 综合建议 + 周计划 | ✅ | ✅ |
| AI 教练 | 用户画像注入 prompt | — | ✅ |
| AI 教练 | RAG 历史召回注入 prompt | — | ✅ |
| 情感计算 | 文本情感分析（中英词典）| — | ✅ |
| 情感计算 | 历史 + 7 天汇总 → coach context | — | ✅ |
| 社交 | 排行榜（周/月/好友，60s Redis 缓存）| ✅ | ✅ |
| 社交 | 动态 Feed + 点赞 + 评论 | ✅ | ✅ |
| 挑战赛 | 报名 + 进度自动同步 + 排行榜 | ✅ UI | ✅ |
| 计划 | 官方 + 用户训练计划 | ✅ | ✅ |
| 徽章 | 成就系统 | ✅ | ✅ |
| 上传 | Tika MIME 嗅探 + 扩展名 + 大小校验 | — | ✅ |
| 观测 | X-Request-Id + MDC 日志 + actuator | — | ✅ |
| 安全 | HSTS + CSP-类头 + JWT secret 校验 | — | ✅ |
| 后台 | 用户 / 内容管理 | ✅ UI | ✅ |
| PWA | 离线可用 + 安装 | ✅ | — |
| 适配 | 手机 / 平板 / 桌面 | ✅ | — |

> 当前验证：**后端 120 个单元 / 集成测试，视觉服务 36 个 pytest，前端跌倒规则 4 个 Node 测试，全部通过**

---

## 七、生产部署建议

1. **Profile**：使用 `docker-compose.prod.yml`，会固定启用 `prod` 并执行 Flyway（V1..V8）、`spring.sql.init.mode=never`、`server.error.include-*=never`、`ddl-auto=validate`。
2. **JWT**：`JWT_SECRET` 必须 **≥ 32 字节** 随机串；启动期 `JwtUtil.@PostConstruct` 会校验，不达标直接 `IllegalStateException` 退出。
3. **数据库**：MySQL 8.x，连接串自动带 `useUnicode=true&characterEncoding=utf8`。Flyway 用 `baseline-on-migrate=true`。
4. **Redis**：所有限流 / 黑名单 / 锁定 / 排行榜缓存依赖 Redis，**强烈建议**生产配齐。Redis 不可用时各服务都会 fail-open（业务不中断，但安全控制降级）。
5. **AI Coach**：默认 `mock`；切到 `mimo` 需配 `MIMO_API_KEY`。Mock provider 完全离线可用，UTF-8 中文反馈。
6. **RAG**：默认 `memory` 内存存储（单实例上限 500/用户）。生产多实例需切到 `redis`（RediSearch，待实现）或 Qdrant/Pinecone 等。
7. **安全头**：HSTS 1 年 + includeSubDomains、`X-Content-Type-Options:nosniff`、`Referrer-Policy:strict-origin-when-cross-origin`、`Permissions-Policy:camera=(self),microphone=(),geolocation=()`。HSTS 仅在 HTTPS 下生效。
8. **观测**：`/actuator/health` (公开)、`/actuator/info` (公开)、`/actuator/metrics` (ADMIN)。每请求带 `X-Request-Id`，日志格式 `[rid] METHOD PATH status=X duration=Yms user=Z`。
9. **跌倒预警**：检测与 JoyAI 无关，由浏览器 MediaPipe 完成。生产必须配置 SMTP；开发态 `log-mail` 只记日志，前端不再误报“已发送真实邮件”。

---

## 八、作者 & 许可

大学计算机设计大赛作品 · 仅供学习与研究使用
