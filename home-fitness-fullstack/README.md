# FitCoach — AI 居家健身陪练系统（全栈版）

> 基于浏览器端姿态识别 + Spring Boot 后端 + Python 视觉服务 + AI 教练的全栈 PWA 应用

![架构](https://img.shields.io/badge/Architecture-Full%20Stack-blue) ![Vue](https://img.shields.io/badge/Vue-3-brightgreen) ![Spring%20Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green) ![PWA](https://img.shields.io/badge/PWA-Ready-purple)

---

## 一、项目简介

FitCoach 是一款面向居家场景的 **AI 健身陪练系统**，通过浏览器端摄像头实时识别用户姿态，结合自研评分算法和 AI 教练，为用户提供：

- **实时动作识别**（MediaPipe Pose，端侧 7 个动作，视频不出设备）
- **多维度评分**（节奏 / 稳定度 / 深度 / 对称性 / 完成率）
- **语音陪练**（MiMo 云 TTS + 浏览器合成兜底 + Web Audio 节拍器）
- **AI 智能教练**（小米 MiMo 驱动的训练点评 / 综合建议 / 周计划 / 语音畅聊）
- **跌倒预警**（端侧姿态启发式检测 + 后端邮件告警）
- **情感计算**（中英词典法分析畅聊文本，7 天汇总回灌教练上下文）
- **视觉语义理解**（JoyAI-VL 驱动，见下）
- **社交激励**（排行榜 / 好友 / 挑战赛 / 成就徽章）
- **全端自适应**（手机优先 · 平板 · 桌面）
- **离线可用**（PWA · 本地优先 · 联网同步）

### 视觉语义理解（JoyAI-VL）

端侧 MediaPipe Pose 只给关键点角度与计数，给不了"这个动作做得像不像样""房间里有什么"这类语义判断。这一层交给 [JoyAI-VL](https://github.com/jd-opensource/JoyAI-VL-Interaction)（京东开源视觉语言模型，Apache-2.0，基座 Qwen3-8B + Qwen3-VL ViT），由 vLLM 以 OpenAI 兼容协议提供服务，经 `vision-svc` 适配后供后端调用，共三个能力：

| 能力 | 链路 | 用途 |
| --- | --- | --- |
| 环境建模 | `POST /api/room/scan` → vision-svc `/infer` | 拍 1-3 帧训练空间 → 面积 / 房型 / 障碍 / 安全分 → 推导可行动作，并作为上下文注入 AI 教练 |
| 动作视觉点评 | `POST /api/coach/form-critique` → vision-svc `/critique` | 训练后拿关键帧看画面点评动作质量，产出 issues / tips / formScore，补 MediaPipe 给不了的语义层 |
| 畅聊场景摘要 | `POST /api/coach/scene` → vision-svc `/scene` | 语音畅聊时描述摄像头画面，让 AI "看得见"再回答 |

> **JoyAI-VL 是可选项**：模型只部署在远程 NVIDIA GPU 机器（8B bf16 权重约 16GB），本机 `docker compose` 不启动它。未配置 `JOYAI_VL_BASE_URL` 时 vision-svc 降级为占位输出（`model=placeholder-v0`）供链路诊断；其中**环境安全评估**会被后端按 `vision.allow-placeholder-results=false` 拒绝入库，避免把 hash 生成的演示结果当成真实识别。部署方式见 [vision-svc/deploy/README.md](./vision-svc/deploy/README.md)。

---

## 二、架构总览

```
┌───────────────────────────────────────────────────────────────┐
│                      客 户 端 (Vue 3 PWA)                     │
│     手机  ·  平板  ·  桌面    —    自适应布局 + 离线缓存      │
│     MediaPipe Pose 端侧推理：计数 / 评分 / 跌倒预警           │
└───────────────────────────┬───────────────────────────────────┘
                            │  HTTPS (REST/JSON)
┌───────────────────────────┴───────────────────────────────────┐
│                服 务 端 (Spring Boot 3.2)                     │
│  ┌───────┐┌───────┐┌──────────┐┌─────────┐┌────────┐┌───────┐│
│  │ Auth  ││ User  ││ Session  ││AI Coach ││ Social ││ Plan  ││
│  └───────┘└───────┘└──────────┘└─────────┘└────────┘└───────┘│
│  ┌───────┐┌────────────┐┌──────────┐┌───────────┐┌──────────┐│
│  │ Badge ││Leaderboard ││ Exercise ││ Challenge ││  Admin   ││
│  └───────┘└────────────┘└──────────┘└───────────┘└──────────┘│
│  ┌─────────┐┌────────┐┌─────────┐┌───────┐┌───────┐          │
│  │ Emotion ││  Room  ││ Safety  ││  ASR  ││  TTS  │          │
│  └─────────┘└────────┘└─────────┘└───────┘└───────┘          │
└──────┬──────────────┬──────────────┬───────────────┬─────────┘
       │              │              │               │
  ┌────┴────┐   ┌─────┴────┐   ┌─────┴─────┐   ┌────┴──────┐
  │  MySQL  │   │  Redis   │   │ MiMo API  │   │vision-svc │
  │ (数据)  │   │(缓存/限流)│  │教练/TTS/ASR│  │ (FastAPI) │
  └─────────┘   └──────────┘   └───────────┘   └────┬──────┘
                                                    │
                                          JoyAI-VL（可选，远程 GPU）
```

文件上传落本地磁盘（`upload.dir`，默认 `./uploads`），由 `LocalStorageService` 提供，未引入对象存储。

---

## 三、目录结构

```
home-fitness-fullstack/
├── frontend/                      # Vue 3 前端 (PWA)
│   ├── public/                    # favicon、manifest.json、sw.js
│   ├── src/
│   │   ├── api/                   # 后端接口封装 + 演示模式 mock
│   │   ├── assets/css             # base / themes / animations / responsive
│   │   ├── components/            # layout / common / training / companion / charts
│   │   ├── composables/           # useVoiceChat
│   │   ├── modules/               # 核心业务（姿态/动作/评分/语音/跌倒/存储/同步）
│   │   ├── router/                # 路由 + 登录守卫
│   │   ├── stores/                # Pinia：auth / config / training / app
│   │   ├── views/                 # 13 个页面
│   │   ├── App.vue
│   │   └── main.js
│   ├── test/ tests/               # node --test 单测
│   ├── demo-smoke.spec.js         # Playwright 演示模式冒烟
│   ├── Dockerfile · nginx.conf
│   ├── index.html · vite.config.js · package.json
│
├── backend/                       # Spring Boot 后端
│   ├── src/main/java/com/fitcoach/
│   │   ├── FitCoachApplication.java
│   │   ├── common/                # ApiResult / PageResult
│   │   ├── config/                # Cors / OpenAPI / Redis / Jpa / WebMvc / DataInitializer
│   │   ├── security/              # JWT / SecurityConfig / 限流 / 登录锁定 / 请求日志
│   │   ├── exception/             # 全局异常处理
│   │   ├── infra/                 # ai / asr / tts / memory / notify / vision / storage / ratelimit
│   │   ├── auth/                  # 邮箱+手机+游客登录、验证码、密码重置、refresh 黑名单
│   │   ├── user/                  # 用户资料 + 画像 + 关注
│   │   ├── session/               # 训练记录（含 CSV 导出）
│   │   ├── coach/                 # AI 教练（MiMo / mock）
│   │   ├── plan/                  # 训练计划
│   │   ├── badge/                 # 成就徽章
│   │   ├── leaderboard/           # 排行榜
│   │   ├── social/                # 动态 / 点赞 / 评论
│   │   ├── challenge/             # 挑战赛
│   │   ├── exercise/              # 动作库
│   │   ├── emotion/               # 情感计算
│   │   ├── room/                  # 环境建模
│   │   ├── safety/                # 跌倒预警
│   │   ├── asr/                   # 服务端语音识别
│   │   ├── tts/                   # 语音合成
│   │   └── admin/                 # 管理后台
│   ├── src/main/resources/
│   │   ├── application.yml · application-dev.yml · application-prod.yml
│   │   ├── data.sql               # dev 种子
│   │   └── db/migration/          # Flyway V1..V8（prod）
│   ├── Dockerfile · pom.xml
│
├── vision-svc/                    # FastAPI 视觉推理适配服务
│   ├── app/                       # main / inference / critique / scene / joyai_vl / rules
│   ├── deploy/                    # 远程 GPU 自托管 JoyAI-VL 脚本
│   ├── tests/ · Dockerfile · requirements.txt
│
├── deploy/                        # 裸机 / VPS 部署
│   ├── start-fitcoach.sh          # vision-svc(8081) + backend(8082)
│   └── nginx-site.conf            # :8090 前端 + API 反代
│
├── docker-compose.yml             # dev：backend + redis + vision-svc + frontend
├── docker-compose.prod.yml        # prod override：MySQL + Flyway + 安全加固
├── .env.example
├── 启动.bat                       # Windows 一键启动（仅前后端）
└── README.md
```

---

## 四、快速开始

### 方式 A：Docker Compose 一键启动（推荐）

```bash
cd home-fitness-fullstack

# dev 模式：backend (H2 内存) + redis + vision-svc + frontend
docker compose up -d --build

# 查看后端日志
docker compose logs -f backend

# 访问
# - 前端:        http://localhost:5173
# - 后端 API:    http://localhost:8080
# - vision-svc:  http://localhost:8081/healthz
# - Swagger UI:  http://localhost:8080/swagger-ui.html
# - 健康检查:    http://localhost:8080/actuator/health

# 停止
docker compose down

# 完整生产模式（MySQL + Flyway + prod 安全配置）
# 先复制 .env.example 为 .env，填写 DB/JWT/CORS/SMTP 等必需值
docker compose -f docker-compose.yml -f docker-compose.prod.yml --profile prod up -d --build
```

> dev profile 用 H2 内存数据库 + data.sql 自动建表与种子；Redis 用真实容器，启用所有需要 Redis 的功能（限流、锁定、refresh 黑名单、排行榜缓存）。

### 方式 B：本机直跑

#### 1. 启动后端

```bash
cd backend
mvn spring-boot:run           # 需 JDK 17；仓库未附带 mvnw wrapper 脚本
# 默认 dev profile：H2 + data.sql；Redis 健康检查在 dev 关闭，不强制需要 Redis
```

#### 2. 启动前端

```bash
cd frontend
npm install
npm run dev
# 默认 http://localhost:5173
```

#### 3. 启动 vision-svc（可选，仅环境建模 / 视觉点评需要）

```bash
cd vision-svc
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8081
```

### 测试账号（dev 自动种子）

| 邮箱 | 密码 | 角色 |
| --- | --- | --- |
| admin@fitcoach.com | admin123 | ADMIN |
| demo@fitcoach.com  | admin123 | USER  |

> prod 只有显式配置 `ADMIN_INIT_PASSWORD` 才会创建管理员，且不创建演示用户；未配置则跳过种子账号（防默认弱口令）。

### 无后端演示

前端设置页的「全站演示模式」（默认关闭）打开后，所有页面改用本地 mock 数据，不连接后端、AI、情绪分析与语音服务，适合断网答辩。

### 环境变量清单（生产部署需配置）

| 变量 | 必需 | 默认 | 说明 |
| --- | :---: | --- | --- |
| `SPRING_PROFILES_ACTIVE` | 是 | dev | 生产 override 固定为 prod，使用 MySQL + Flyway |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | prod | localhost/3306/fitcoach | MySQL 连接 |
| `DB_USER` / `DB_PASSWORD` | prod | fitcoach/无 | MySQL 凭据，生产密码必填 |
| `SPRING_DATA_REDIS_HOST` / `_PORT` / `_PASSWORD` | 是 | localhost/6379/空 | Redis 连接 |
| `JWT_SECRET` | prod | — | **prod 必须** ≥ 32 字节，启动校验 |
| `CORS_ALLOWED_ORIGINS` | prod | localhost 系列 | 跨域白名单（逗号分隔）|
| `ADMIN_INIT_PASSWORD` | prod | 无 | 不配则 prod 不创建种子管理员 |
| `SECURITY_TRUST_PROXY_HEADERS` | 反代后 | false | 置 true 才从 `X-Forwarded-For` 取真实 IP（影响限流准确性）|
| `SMTP_HOST` / `SMTP_PORT` / `SMTP_USERNAME` / `SMTP_PASSWORD` / `SMTP_FROM` | prod | 无/587/无 | 跌倒预警、验证码和重置邮件真实发送 |
| `VISION_BASE_URL` / `VISION_TIMEOUT_SECONDS` | — | http://localhost:8081 / 30 | vision-svc 地址与超时 |
| `VISION_ALLOW_PLACEHOLDER_RESULTS` | — | false | 是否允许把占位识别结果当真实环境安全评估入库 |
| `JOYAI_VL_BASE_URL` / `JOYAI_VL_MODEL` / `JOYAI_VL_API_KEY` | JoyAI | 空/joyai-vl/空 | 指向分开部署的远端 OpenAI 兼容视觉端点，本机 Compose 不启动 JoyAI |
| `FRONTEND_PORT` / `BACKEND_PORT` / `VISION_PORT` / `REDIS_PORT` / `MYSQL_PORT` | — | 5173/8080/8081/6379/3306 | 宿主机端口冲突时可覆盖 |
| `AI_COACH_PROVIDER` | — | mock | mock / mimo |
| `MIMO_API_KEY` / `MIMO_BASE_URL` / `MIMO_MODEL` | mimo | —/token-plan-cn 网关/mimo-v2.5 | 小米 MiMo 接入 |
| `AI_TTS_PROVIDER` | — | browser | mimo / browser。云 TTS 不可用时 service 自动降级 browser |
| `MIMO_TTS_MODEL` / `MIMO_TTS_VOICE` / `MIMO_TTS_FORMAT` | mimo TTS | mimo-v2.5-tts / mimo_default / wav | 音色可选 mimo_default、冰糖、茉莉、苏打、白桦、Mia、Chloe、Milo、Dean |
| `AI_ASR_PROVIDER` | 手机端语音 | none | none / mimo。**手机端要能语音输入必须设为 mimo**，见下方说明 |
| `MIMO_ASR_MODEL` | — | mimo-v2.5-asr | 复用 `MIMO_API_KEY` 与网关，无需另行部署模型 |
| `AI_MEMORY_ENABLED` | — | true | RAG 向量记忆开关 |
| `AI_MEMORY_STORE` | — | memory | memory（内存）/ redis（未来）|
| `AI_MEMORY_EMBEDDING` | — | mock | mock（确定性 hash）/ openai |
| `EMOTION_ANALYZER` | — | lexicon | 词典法情感分析（matchIfMissing）|

---

## 五、技术选型

### 前端

| 技术 | 版本 | 作用 |
| --- | --- | --- |
| Vue | 3.4 | 渐进式框架 |
| Vite | 5.x | 构建工具 |
| Vue Router | 4.x | 路由 |
| Pinia | 2.x | 状态管理 |
| Axios | 1.x | HTTP 请求 |
| MediaPipe Pose | 0.5 | 姿态识别（`index.html` 由 jsdelivr CDN 加载，非 npm 依赖）|
| Web Speech API | — | TTS 兜底 / 桌面端 STT |
| Web Audio API | — | 节拍器 + 16kHz WAV 采集 |
| IndexedDB | — | 本地缓存 |
| Service Worker | — | PWA 离线 |
| Playwright | 1.x | 演示模式端到端冒烟（devDependency）|

### 后端

| 技术 | 版本 | 作用 |
| --- | --- | --- |
| Spring Boot | 3.2.5 | 应用框架 |
| Spring Security | 6.x | 鉴权 |
| Spring Data JPA | 3.x | ORM |
| Spring Data Redis | 3.x | 缓存 / 限流 / 黑名单 |
| Flyway | — | prod schema 迁移（V1..V8）|
| JJWT | 0.12.x | JWT 工具 |
| SpringDoc | 2.x | OpenAPI 文档 |
| Apache Tika | — | 上传 MIME 嗅探 |
| MySQL（H2 开发） | 8.x | 数据库 |
| Redis | 7.x | 缓存 |
| Lombok | — | 简化代码 |

### 视觉服务

Python + FastAPI，提供 `/infer`（环境识别）、`/critique`（动作点评）、`/scene`（场景摘要）；可选接 JoyAI-VL（vLLM OpenAI 兼容端点，需远程 GPU），未配置时返回占位结果供链路诊断。

---

## 六、功能矩阵

| 模块 | 功能 | 前端 | 后端 |
| --- | --- | :---: | :---: |
| 训练 | 实时姿态识别 + 计数 + 评分（7 个动作）| 是 | — |
| 训练 | 语音陪练 + 节拍器 | 是 | 是 |
| 训练 | 倒计时 + 休息计时器 | 是 | — |
| 训练 | 成果海报生成 | 是 | — |
| 账号 | 邮箱密码登录 + 注册 | 是 | 是 |
| 账号 | 手机验证码登录（SMS mock）| 仅 UI | 是 |
| 账号 | 游客登录 | 是 | 是 |
| 账号 | 邮件验证码 / 密码重置 | 仅 UI | 是 |
| 账号 | refresh token 黑名单 + 登出 | — | 是 |
| 账号 | 5 次 / 15min 登录锁定 + 限流 | — | 是 |
| 记录 | 云端同步 + 筛选 / 搜索 | 是 | 是 |
| 记录 | CSV 导出 | 是 | 是 |
| AI 教练 | 训练后智能点评（mock/mimo）| 是 | 是 |
| AI 教练 | 综合建议 + 周计划 | 是 | 是 |
| AI 教练 | 语音畅聊 + 追问建议 | 是 | 是 |
| AI 教练 | 用户画像注入 prompt | — | 是 |
| AI 教练 | RAG 历史召回注入 prompt | — | 是 |
| 语音 | 云 TTS（MiMo）+ 浏览器兜底 | 是 | 是 |
| 语音 | 服务端 ASR（MiMo）+ 文字降级 | 是 | 是 |
| 情感计算 | 文本情感分析（中英词典）| 是 | 是 |
| 情感计算 | 历史 + 7 天汇总 → coach context | 是 | 是 |
| 安全 | 训练中跌倒预警 + 邮件告警 | 是 | 是 |
| 环境建模 | 空间拍摄 → 面积/障碍/安全分/动作适配 | 是 | 是 |
| 视觉 | 动作关键帧点评 / 畅聊场景摘要 | 是 | 是 |
| 社交 | 排行榜（周/月/好友，60s Redis 缓存）| 是 | 是 |
| 社交 | 动态 Feed + 点赞 + 评论 | 是 | 是 |
| 社交 | 关注 / 粉丝 | 是 | 是 |
| 挑战赛 | 报名 + 进度自动同步 + 排行榜 | 仅 UI | 是 |
| 计划 | 官方 + 用户训练计划 + 计划市场 | 是 | 是 |
| 徽章 | 成就系统 | 是 | 是 |
| 上传 | Tika MIME 嗅探 + 扩展名 + 大小校验 | — | 是 |
| 观测 | X-Request-Id + MDC 日志 + actuator | — | 是 |
| 安全 | HSTS + 安全响应头 + JWT secret 校验 | — | 是 |
| 后台 | 用户 / 内容管理 | 仅 UI | 是 |
| 演示 | 全站演示模式（纯前端 mock）| 是 | — |
| PWA | 离线可用 + 安装 | 是 | — |
| 适配 | 手机 / 平板 / 桌面 | 是 | — |

> 当前验证：**后端 `mvn test` 136 个单元 / 集成测试，视觉服务 `pytest -q` 36 个，前端 `npm test` 22 个，全部通过**

---

## 七、生产部署建议

1. **Profile**：使用 `docker-compose.prod.yml`，会固定启用 `prod` 并执行 Flyway（V1..V8）、`spring.sql.init.mode=never`、`server.error.include-*=never`、`ddl-auto=validate`。
2. **JWT**：`JWT_SECRET` 必须 **≥ 32 字节** 随机串；启动期 `JwtUtil.@PostConstruct` 会校验，不达标直接 `IllegalStateException` 退出。
3. **数据库**：MySQL 8.x，连接串自动带 `useUnicode=true&characterEncoding=utf8`。Flyway 用 `baseline-on-migrate=true`。
4. **Redis**：所有限流 / 黑名单 / 锁定 / 排行榜缓存依赖 Redis，**强烈建议**生产配齐。Redis 不可用时各服务都会 fail-open（业务不中断，但安全控制降级）。
5. **AI Coach**：默认 `mock`；切到 `mimo` 需配 `MIMO_API_KEY`。Mock provider 完全离线可用，UTF-8 中文反馈。
6. **RAG**：默认 `memory` 内存存储（单实例上限 500/用户）。生产多实例需切到 `redis`（RediSearch，待实现）或 Qdrant/Pinecone 等。
7. **安全头**：HSTS 1 年 + includeSubDomains、`X-Content-Type-Options:nosniff`、`Referrer-Policy:strict-origin-when-cross-origin`、`Permissions-Policy:camera=(self), microphone=(self), geolocation=()`。HSTS 仅在 HTTPS 下生效。
   > `microphone` 必须是 `(self)` 而非空 `()`。空 allowlist 等于彻底禁用麦克风，语音畅聊会静默失效且极难排查。
8. **反向代理**：nginx 等反代后须设 `SECURITY_TRUST_PROXY_HEADERS=true`，否则限流按代理 IP 聚合，所有用户共用一个额度。
9. **观测**：`/actuator/health`（公开）、`/actuator/info`（公开）、`/actuator/metrics`（ADMIN）。每请求带 `X-Request-Id`，日志格式 `[rid] METHOD PATH status=X duration=Yms user=Z`。
10. **跌倒预警**：检测由浏览器 MediaPipe 端侧完成，与 JoyAI 无关。生产必须配置 SMTP；开发态 `LogMailSender` 只记日志，前端不会误报"已发送真实邮件"。
11. **手机端语音输入**：浏览器的 `SpeechRecognition` 只是一层外壳，真正的识别引擎来自平台 —— 桌面 Chrome 走 Google 云端、Edge 走微软 Azure，都可用；但 **Android Chrome 依赖设备端 GMS/Google App（国行手机通常没有）、iOS 仅 Safari 本体有、微信等 WKWebView/X5 完全不提供**。引擎缺失时 `start()` 后既无 `onresult` 也无 `onerror`，界面会停在"监听中"毫无反应。
    - 手机端要能语音输入，须设 `AI_ASR_PROVIDER=mimo` 走服务端识别（`mimo-v2.5-asr`，与 chat/TTS 同一网关同一 key，**无需自部署模型**，区别于需要自建 GPU 服务的 JoyAI-VL）。
    - 保持 `none` 时前端自动降级为文字输入，对话仍可闭环（AI 回复照常语音播报）。
    - 采集固定 16kHz 单声道 WAV：上游网关只接受 `wav` / `mp3`，而 `MediaRecorder` 的原生输出（Android webm/opus、iOS mp4/aac）均不被接受，因此前端用 `AudioContext` 采 PCM 自行编码，见 `frontend/src/modules/wavRecorder.js`。
    - 前置条件：`getUserMedia` 要求安全上下文，部署必须是 HTTPS（或 localhost）。
12. **前端发版**：改动前端资源后必须递增 `frontend/public/sw.js` 的 `VERSION`（当前 `fitcoach-v4-0-9`），否则 Service Worker 会继续吐旧缓存。

---

## 八、相关文档

- [前端说明](./frontend/README.md)
- [后端说明](./backend/README.md)
- [视觉服务说明](./vision-svc/README.md)
- [JoyAI-VL 远程 GPU 自托管](./vision-svc/deploy/README.md)
- [裸机 / VPS 部署指南](./deploy/README.md)

---

## 九、作者 & 许可

大学计算机设计大赛作品 · 仅供学习与研究使用
