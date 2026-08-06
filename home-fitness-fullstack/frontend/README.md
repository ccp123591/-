# FitCoach Frontend

Vue 3 + Vite 前端工程（`fitcoach-frontend` 3.0.0），包含 PWA、响应式布局、MediaPipe 姿态识别、跌倒预警、语音陪练与离线同步。

## 技术栈

- Vue 3.4 · Vite 5 · Vue Router 4 · Pinia 2 · Axios 1
- MediaPipe Pose 0.5 + camera_utils（`index.html` 由 jsdelivr CDN 加载，非 npm 依赖）
- Web Speech API（TTS 兜底 / 桌面端 STT）· Web Audio API（节拍器 + WAV 采集）
- IndexedDB（离线记录）· Service Worker（PWA）

## 开发命令

```bash
npm install
npm run dev              # http://localhost:5173，代理 /api -> :8080
npm run build
npm run preview          # http://localhost:4173
npm test                 # node --test，22 个单测
npm run test:e2e:demo    # Playwright 演示模式冒烟（需先起 dev server）
```

## 目录

```
src/
├── api/           后端接口封装：auth / user / profile / session / coach / plan /
│                  exercise / social / challenge / emotion / room / safety /
│                  asr / tts + client(axios 实例) + demoMock(演示模式数据源)
├── assets/css     全局样式：base / themes / animations / responsive
├── components/
│   ├── layout     AppLayout / SideNav / TopBar / TabBar
│   ├── common     Toast / ConfirmModal / EmptyState
│   ├── training   HUD / ActionCard / CountdownOverlay / RestTimer /
│   │              TargetStepper / ReportModal / CoachFeedback
│   ├── companion  CompanionWidget（AI 陪练助手面板）/ VoiceCompanion（语音畅聊）
│   └── charts     ProgressChart / HeatmapCalendar
├── composables/   useVoiceChat（语音畅聊状态机）
├── modules/       框架无关的核心逻辑，见下表
├── router/        路由定义 + 登录守卫
├── stores/        Pinia：auth / config / training / app
└── views/         Login / Train / Records / RecordDetail / Plans / Leaderboard /
                   Feed / Challenges / Emotion / Room / Profile / Settings / Admin
```

### modules/

| 文件 | 职责 |
| --- | --- |
| `pose.js` | MediaPipe Pose 封装：摄像头接入、关键点回调 |
| `exercise.js` | 7 个动作定义（深蹲 / 前屈伸展 / 俯卧撑 / 弓步蹲 / 臀桥 / 平板支撑 / 开合跳）+ 计数状态机 + 多维评分 |
| `fallguard.js` | 跌倒预警：髋部下坠速度（risk）+ 躯干接近水平（fall）两级启发式，水平类动作豁免 |
| `voice.js` | MiMo 云 TTS 主路径 + 浏览器合成兜底 + CacheStorage 预热报数，Web Audio lookahead 节拍器 |
| `wavRecorder.js` | `AudioContext` 采 PCM 并编码 16kHz 单声道 WAV（上游网关只收 wav/mp3，`MediaRecorder` 原生输出不被接受） |
| `sttEngines.js` | 语音识别引擎选择：服务端 MiMo ASR 优先，浏览器 SpeechRecognition 次之，都不可用则降级文字输入 |
| `followups.js` | 畅聊追问建议 |
| `storage.js` | IndexedDB 本地训练记录 |
| `sync.js` | 离线记录批量回传后端 |
| `poster.js` | 训练成果海报生成 |
| `demoMode.js` | 全站演示模式：纯前端 mock 数据，不连后端 |

## 自适应说明

- 手机（< 768px）：单列 + 底部 Tab 栏
- 平板（768 ~ 1024px）：训练页左视频右控制的双列布局
- 桌面（> 1024px）：左侧边栏 + 右内容

## 数据流

```
训练完成 → 本地 IndexedDB → 后台推送 /api/sessions → AI 反馈 /api/coach/feedback
                ↓
         离线时仅本地保存，登录后由 sync.js 批量同步
```

跌倒预警走另一条链路：`fallguard.js` 端侧判定 → `POST /api/safety/fall-alert` → 后端 SMTP 邮件告警（dev 只写日志）。

视觉语义链路（后端背后接可选的 JoyAI-VL，未部署时返回占位结果）：

| 前端入口 | 调用 | 用途 |
| --- | --- | --- |
| `Train.vue` 训练结束抓帧 | `coachApi.formCritique()` | 动作关键帧点评，结果传给 `ReportModal` / `CoachFeedback`（`model === 'joyai-vl'` 才当作真实点评展示）|
| `VoiceCompanion.vue` 畅聊抓帧 | `coachApi.scene()` | 场景摘要随 `chat()` 一起发，让 AI "看得见"画面 |
| `Room.vue` 环境扫描 | `roomApi` | 拍 1-3 帧空间 → 面积 / 障碍 / 安全分 / 动作适配 |

## 演示模式

设置页的「全站演示模式」开关（`config.demoMode`，默认**关闭**）打开后，所有页面改用 `api/demoMock.js` 的本地数据，不连接后端、AI、情绪分析与语音服务，适合断网答辩。开关持久化在 `localStorage` 的 `fitcoach_config`。

## 缓存与发版

改动前端资源后**必须递增** `public/sw.js` 的 `VERSION`（当前 `fitcoach-v4-0-9`），否则 Service Worker 会继续吐旧缓存，用户端看不到更新。

## 语音输入的平台限制

浏览器的 `SpeechRecognition` 只是外壳，识别引擎来自平台：桌面 Chrome 走 Google 云端、Edge 走微软 Azure；但 Android Chrome 依赖 GMS/Google App（国行手机通常没有）、iOS 仅 Safari 本体有、微信等 WKWebView/X5 完全不提供。引擎缺失时 `start()` 后既无 `onresult` 也无 `onerror`。

因此手机端语音输入需后端配 `AI_ASR_PROVIDER=mimo` 走服务端识别；保持 `none` 时前端自动降级为文字输入，对话仍可闭环（AI 回复照常语音播报）。`getUserMedia` 要求安全上下文，部署必须是 HTTPS（或 localhost）。
