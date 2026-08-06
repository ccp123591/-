# FitCoach — AI 居家健身陪练

> 基于浏览器端姿态识别、AI 教练陪练与 PWA 技术的居家健身训练系统

Vue 3 PWA 前端 + Spring Boot 3.2 后端 + Python 视觉服务，提供实时动作识别、多维评分、语音陪练、跌倒预警与 AI 个性化反馈。

---

## 功能亮点

- **实时姿态识别** — 浏览器端直接运行 MediaPipe Pose，视频不出设备，内置 7 个动作（深蹲 / 前屈伸展 / 俯卧撑 / 弓步蹲 / 臀桥 / 平板支撑 / 开合跳）
- **多维度评分** — 节奏、稳定度、深度、对称性、完成率综合评估
- **语音陪练** — MiMo 云 TTS 为主、浏览器 Web Speech 兜底，配 Web Audio 高精度节拍器
- **AI 智能教练** — 小米 MiMo 驱动的训练点评、周计划、语音畅聊；默认 `mock` provider 可完全离线演示
- **跌倒预警** — 训练中由端侧 MediaPipe 姿态点做启发式检测，触发后端邮件告警
- **情感计算** — 中英词典法分析畅聊文本情绪，7 天汇总回灌进教练上下文
- **视觉语义理解** — 可选接 JoyAI-VL（京东开源 VLM，远程 GPU 部署），经 vision-svc 提供三项 MediaPipe 给不了的语义能力：环境建模（面积 / 障碍 / 安全分 / 动作适配）、动作关键帧点评、畅聊场景摘要；未配置时降级为占位输出
- **社交激励** — 排行榜、动态 Feed、挑战赛、成就徽章
- **离线优先** — IndexedDB 本地存储，恢复网络后自动同步到后端；PWA 可安装
- **响应式布局** — 手机 / 平板 / 桌面自适应

---

## 目录结构

```
.
├── docs/superpowers/       历史设计 spec 与实施 plan（按日期归档）
└── home-fitness-fullstack/
    ├── frontend/           Vue 3 + Vite 5 前端 PWA
    ├── backend/            Spring Boot 3.2 后端（Java 17）
    ├── vision-svc/         FastAPI 视觉推理适配服务（可选接 JoyAI-VL）
    ├── deploy/             裸机 / VPS 部署脚本与 nginx 配置
    ├── docker-compose.yml       dev：backend + redis + vision-svc + frontend
    ├── docker-compose.prod.yml  prod override：MySQL + Flyway + 安全加固
    └── 启动.bat            Windows 一键启动（前后端）
```

---

## 快速开始

### Docker Compose（推荐）

```bash
cd home-fitness-fullstack
docker compose up -d --build     # 前端 :5173 · 后端 :8080 · vision-svc :8081 · redis :6379
```

### 本机直跑

```bash
# 后端（默认 dev profile：H2 内存库 + data.sql 种子）
cd home-fitness-fullstack/backend
mvn spring-boot:run              # 需 JDK 17；仓库未附带 mvnw wrapper 脚本

# 前端
cd home-fitness-fullstack/frontend
npm install
npm run dev                      # http://localhost:5173，代理 /api -> :8080
```

- API：http://localhost:8080
- Swagger：http://localhost:8080/swagger-ui.html
- H2 控制台：http://localhost:8080/h2-console（`jdbc:h2:mem:fitcoach` · `sa` · 空密码）
- 健康检查：http://localhost:8080/actuator/health

种子账号（仅 dev 自动创建）：`admin@fitcoach.com` / `admin123` · `demo@fitcoach.com` / `admin123`

### Windows 一键启动

双击 `home-fitness-fullstack/启动.bat`，自动拉起前后端并打开浏览器（不含 vision-svc）。

### 无后端演示

设置页打开「全站演示模式」后，前端用本地 mock 数据驱动所有页面，不连接后端、AI、情绪分析与语音服务，适合断网答辩。默认关闭。

---

## 技术栈

### 前端

Vue 3.4 · Vite 5 · Vue Router 4 · Pinia 2 · Axios 1 · MediaPipe Pose 0.5（CDN 加载）· Web Speech / Web Audio API · IndexedDB · Service Worker

### 后端

Spring Boot 3.2.5（Java 17）· Spring Security 6 + JJWT 0.12 · Spring Data JPA · Spring Data Redis · Flyway（prod）· H2（dev）/ MySQL 8（prod）· SpringDoc OpenAPI 2 · Apache Tika · Lombok

### 视觉服务

Python + FastAPI · 可选接 [JoyAI-VL](https://github.com/jd-opensource/JoyAI-VL-Interaction)（vLLM OpenAI 兼容端点，需远程 GPU）；未配置端点时返回占位结果供链路诊断

### AI

小米 MiMo（`mimo-v2.5` 对话 / `mimo-v2.5-tts` 语音合成 / `mimo-v2.5-asr` 语音识别），通过 `AI_COACH_PROVIDER` 在 `mock` / `mimo` 间切换，默认 `mock`

---

## 架构速览

```
┌───────────────────────────────────────────────────┐
│               浏览器（Vue 3 PWA）                 │
│  摄像头 → MediaPipe Pose → 评分/跌倒预警          │
│                          → IndexedDB 离线缓存     │
└──────────────────────┬────────────────────────────┘
                       │  HTTPS REST / JSON
┌──────────────────────┴────────────────────────────┐
│                Spring Boot 3.2                    │
│  auth · user · session · coach · plan · badge ·   │
│  leaderboard · social · challenge · exercise ·    │
│  emotion · room · safety · asr · tts · admin      │
└───┬─────────┬──────────┬──────────────┬───────────┘
    │         │          │              │
  MySQL     Redis    MiMo API      vision-svc
 (持久化)  (缓存/限流) (教练/TTS/ASR)  (环境建模)
                                       │
                                  JoyAI-VL（可选，远程 GPU）
```

后端按 **业务域** 切分包，每个域内自带 Controller/Service/Entity/Repository；跨域能力集中在 `common/`、`config/`、`security/`、`exception/`、`infra/`。统一通过 `common/ApiResult<T>` 封装响应，`/api/**` 前缀走 JWT 鉴权。

---

## 开发约定

- 所有 HTTP 接口返回 `ApiResult<T>`（`code` / `message` / `data` / `timestamp`）
- 所有端点统一 `/api/**` 前缀，Vite dev 服务器代理，CORS 白名单由 `CORS_ALLOWED_ORIGINS` 控制（默认覆盖 `:5173`、`:4173`、`:8080`）
- Schema：dev 用 `ddl-auto: update` + `data.sql`；**prod 用 Flyway（`V1`..`V8`）作为唯一来源**，`ddl-auto: validate`
- 前端 `modules/` 目录保持框架无关，核心姿态 / 评分 / 语音 / 跌倒逻辑与 Vue 解耦
- 改前端资源后必须递增 `frontend/public/sw.js` 的 `VERSION`，否则用户端缓存不更新（当前 `fitcoach-v4-0-9`）
- 响应式断点：`<768px` 单列 + 底部 Tab，`768–1024px` 双列训练布局，`>1024px` 侧边栏布局

---

## 验证状态

| 层 | 命令 | 结果 |
| --- | --- | :---: |
| 后端 | `mvn test`（JDK 17） | 136 通过 |
| 视觉服务 | `pytest -q` | 36 通过 |
| 前端 | `npm test` | 22 通过 |

详细架构、API 列表、环境变量与部署说明见 [home-fitness-fullstack/README.md](./home-fitness-fullstack/README.md)。

---

## 许可

大学计算机设计大赛作品 · 仅供学习与研究使用
