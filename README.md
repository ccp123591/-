# FitCoach — AI 居家健身陪练

> 基于浏览器端姿态识别、AI 教练反馈与 PWA 技术的居家健身训练系统

Vue 3 PWA 前端 + Spring Boot 3.2 后端,提供实时动作识别、多维评分、语音陪练与 AI 个性化反馈。

---

## 功能亮点

- **实时姿态识别** — 浏览器端直接运行 MediaPipe Pose,无需上传视频
- **多维度评分** — 节奏、稳定度、深度、对称性、完成率综合评估
- **语音陪练** — Web Speech API TTS + Web Audio 节拍器
- **AI 智能点评** — 训练后由 Claude 生成个性化反馈与建议
- **离线优先** — IndexedDB 本地存储,恢复网络后自动同步到后端
- **响应式布局** — 手机 / 平板 / 桌面自适应,支持 PWA 安装

---

## 目录结构

```
.
└── home-fitness-fullstack/
    ├── frontend/       Vue 3 + Vite 5 前端 PWA
    ├── backend/        Spring Boot 3.2 后端(Java 17)
    ├── docker-compose.yml
    └── 启动.bat        Windows 一键启动
```

---

## 快速开始

### 后端 · Spring Boot

```bash
cd home-fitness-fullstack/backend
mvn spring-boot:run          # 默认 dev profile,使用 H2 内存数据库
```

- API:  http://localhost:8080
- Swagger:  http://localhost:8080/swagger-ui.html
- H2 控制台:  http://localhost:8080/h2-console

种子账号: `admin@fitcoach.com` / `admin123` · `demo@fitcoach.com` / `admin123`

### 前端 · Vue 3 + Vite

```bash
cd home-fitness-fullstack/frontend
npm install
npm run dev                  # http://localhost:5173,代理 /api -> :8080
```

### Windows 一键启动

双击 `home-fitness-fullstack/启动.bat`,自动拉起前后端并打开浏览器。

---

## 技术栈

### 前端

Vue 3.4 · Vite 5 · Vue Router 4 · Pinia 2 · Axios · MediaPipe Pose · Web Speech / Web Audio API · IndexedDB · Service Worker

### 后端

Spring Boot 3.2 (Java 17) · Spring Security + JJWT 0.12 · Spring Data JPA · H2(dev) / MySQL 8(prod) · SpringDoc OpenAPI 2 · Lombok

### AI

Claude API(可通过 `application.yml` 的 `ai.coach.provider` 切换 `mock` / `claude` / `openai`)

---

## 架构速览

```
┌──────────────────────────────────────────────┐
│          浏览器(Vue 3 PWA)               │
│  摄像头 → MediaPipe Pose → 评分 → IndexedDB  │
└──────────────────┬───────────────────────────┘
                   │  HTTPS REST / JSON
┌──────────────────┴───────────────────────────┐
│             Spring Boot 3.2                  │
│  auth · user · session · coach · plan ·      │
│  badge · leaderboard · social · exercise     │
└──────┬──────────┬────────────┬───────────────┘
       │          │            │
     MySQL      Redis       Claude API
    (持久化)   (缓存)      (AI 教练)
```

后端按 **业务域** 切分包,每个域内自带 Controller/Service/Entity/Repository;统一通过 `common/ApiResult<T>` 封装响应,`/api/**` 前缀走 JWT 鉴权。

---

## 开发约定

- 所有 HTTP 接口返回 `ApiResult<T>`(`code` / `message` / `data` / `timestamp`)
- 所有端点统一 `/api/**` 前缀,Vite dev 服务器代理,CORS 白名单覆盖 `:5173`、`:4173`、`:8080`
- JPA 使用 `ddl-auto: update`,Schema 从 `@Entity` 演进,无迁移工具
- 前端 `modules/` 目录保持框架无关,核心姿态/评分逻辑与 Vue 解耦
- 响应式断点:`<768px` 单列 + 底部 Tab,`768–1024px` 双列训练布局,`>1024px` 侧边栏布局

详细架构、API 列表、功能矩阵见 [home-fitness-fullstack/README.md](./home-fitness-fullstack/README.md)。

---

## 许可

大学计算机设计大赛作品 · 仅供学习与研究使用
