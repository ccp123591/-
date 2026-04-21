# FitCoach Frontend

Vue 3 + Vite 前端工程，包含 PWA、响应式布局、MediaPipe 姿态识别、离线同步。

## 技术栈
- Vue 3.4 · Vite 5 · Vue Router 4 · Pinia 2
- Axios · IndexedDB · Service Worker
- MediaPipe Pose · Web Speech / Web Audio API

## 开发命令
```bash
npm install
npm run dev        # http://localhost:5173
npm run build
npm run preview
```

## 目录
```
src/
├── api/           后端接口封装
├── assets/css     全局样式：base / themes / animations / responsive
├── components/
│   ├── layout     侧边栏 / 顶栏 / Tab 栏
│   ├── common     Toast / Confirm / EmptyState
│   ├── training   HUD / ActionCard / 倒计时 / 报告弹窗 / AI 教练
│   └── charts     柱状图 / 热力图
├── composables    组合式逻辑
├── modules        pose / exercise / voice / poster / storage
├── router         路由定义 + 守卫
├── stores         Pinia：auth / config / training / app
└── views          Login / Train / Records / Plans / Leaderboard ...
```

## 自适应说明
- 手机（< 768px）：单列 + 底部 Tab 栏
- 平板（768 ~ 1024px）：训练页左视频右控制的双列布局
- 桌面（> 1024px）：左侧边栏 + 右内容，大屏更好利用

## 数据流
```
训练完成 → 本地 IndexedDB → 后台推送 /api/sessions → AI 反馈 /api/coach/feedback
                ↓
         离线时仅本地保存，登录后批量同步
```
