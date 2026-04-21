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

### 1. 启动后端

```bash
cd backend
./mvnw spring-boot:run
# 或 mvn spring-boot:run
# 默认 http://localhost:8080
# Swagger UI: http://localhost:8080/swagger-ui.html
```

### 2. 启动前端

```bash
cd frontend
npm install
npm run dev
# 默认 http://localhost:5173
```

浏览器访问 `http://localhost:5173` 即可。

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
| 账号 | 多方式登录（邮箱/手机/微信/游客）| ✅ UI | 占位 |
| 记录 | 云端同步 | ✅ | 占位 |
| 记录 | 筛选 / 搜索 / 详情 | ✅ | 占位 |
| AI | 训练后智能点评 | ✅ UI | 占位 |
| AI | 个性化训练建议 | ✅ UI | 占位 |
| 社交 | 排行榜（周/月/好友） | ✅ | 占位 |
| 社交 | 动态 Feed + 点赞 | ✅ | 占位 |
| 计划 | 官方 + 用户训练计划 | ✅ | 占位 |
| 徽章 | 成就系统 | ✅ | 占位 |
| 后台 | 用户 / 内容管理 | ✅ UI | 占位 |
| PWA | 离线可用 + 安装 | ✅ | — |
| 适配 | 手机 / 平板 / 桌面 | ✅ | — |

> 🟢 UI 完整可用 · 🟡 后端接口已占位，待补业务逻辑

---

## 七、适配说明

- **手机端（< 768px）**：单列布局 + 底部 Tab 栏
- **平板端（768 ~ 1024px）**：双列布局，训练页左视频右控制
- **桌面端（> 1024px）**：侧边导航 + 三列内容

---

## 八、作者 & 许可

大学计算机设计大赛作品 · 仅供学习与研究使用
