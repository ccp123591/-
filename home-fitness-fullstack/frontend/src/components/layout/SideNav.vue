<script setup>
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();

const navs = [
  { key: 'train',       label: '开始训练',   path: '/train',       icon: 'M5 3l14 9L5 21V3z' },
  { key: 'room',        label: '环境扫描',   path: '/room',        icon: 'M3 12l9-9 9 9 M5 10v10h14V10' },
  { key: 'records',     label: '训练记录',   path: '/records',     icon: 'M3 3v18h18 M7 16l4-4 4 2 5-6' },
  { key: 'plans',       label: '训练计划',   path: '/plans',       icon: 'M8 2v3 M16 2v3 M3 9h18 M5 5h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z' },
  { key: 'leaderboard', label: '排行榜',     path: '/leaderboard', icon: 'M12 2l3 7h7l-5.5 4.5 2 7L12 16l-6.5 4.5 2-7L2 9h7z' },
  { key: 'challenges',  label: '挑战赛',     path: '/challenges',  icon: 'M6 9V4h12v5a6 6 0 0 1-12 0 M9 22h6 M12 15v7' },
  { key: 'feed',        label: '训练动态',   path: '/feed',        icon: 'M2 12a10 10 0 1 1 20 0 10 10 0 0 1-20 0 M6 12h12 M12 6v12' },
  { key: 'emotion',     label: '情感记录',   path: '/emotion',     icon: 'M9 10h.01 M15 10h.01 M8 15c1 1.5 2.5 2 4 2s3-.5 4-2 M3 12a9 9 0 1 0 18 0 9 9 0 0 0-18 0z' },
  { key: 'profile',     label: '个人中心',   path: '/profile',     icon: 'M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2 M12 3a4 4 0 1 0 0 8 4 4 0 0 0 0-8' },
  { key: 'settings',    label: '设置',       path: '/settings',    icon: 'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6 M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4 1.65 1.65 0 0 0 7.18 19.73l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9 1.65 1.65 0 0 0 4.27 7.18l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z' }
];

const adminNav = { key: 'admin', label: '管理后台', path: '/admin', icon: 'M3 3h18v18H3z M3 9h18 M9 21V9' };

const activeKey = computed(() => {
  const path = route.path;
  if (path.startsWith('/settings')) return 'settings';
  if (path.startsWith('/admin')) return 'admin';
  if (path.startsWith('/profile')) return 'profile';
  if (path.startsWith('/emotion')) return 'emotion';
  if (path.startsWith('/challenges')) return 'challenges';
  if (path.startsWith('/leaderboard')) return 'leaderboard';
  if (path.startsWith('/feed')) return 'feed';
  if (path.startsWith('/plans')) return 'plans';
  if (path.startsWith('/records')) return 'records';
  if (path.startsWith('/room')) return 'room';
  return 'train';
});

function go(n) {
  if (activeKey.value === n.key) return;
  router.push(n.path);
}
</script>

<template>
  <aside class="side-nav">
    <div class="brand">
      <div class="logo-box">
        <svg viewBox="0 0 32 32" fill="none">
          <defs><linearGradient id="sidelg" x1="4" y1="4" x2="28" y2="28">
            <stop stop-color="#d97757"/><stop offset="1" stop-color="#c96442"/>
          </linearGradient></defs>
          <circle cx="16" cy="10" r="2.5" stroke="url(#sidelg)" stroke-width="1.8"/>
          <path d="M12 22l2-7 2 3 2-3 2 7" stroke="url(#sidelg)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </div>
      <div>
        <div class="brand-title text-gradient">FitCoach</div>
        <div class="brand-sub">AI 居家健身</div>
      </div>
    </div>

    <nav class="side-list">
      <button
        v-for="n in navs"
        :key="n.key"
        :class="['side-item', { active: activeKey === n.key }]"
        @click="go(n)"
      >
        <svg class="ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <path :d="n.icon"/>
        </svg>
        <span>{{ n.label }}</span>
      </button>

      <button
        v-if="auth.isAdmin"
        :class="['side-item', { active: activeKey === 'admin' }]"
        @click="go(adminNav)"
      >
        <svg class="ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <path :d="adminNav.icon"/>
        </svg>
        <span>{{ adminNav.label }}</span>
      </button>
    </nav>

    <div class="side-foot">
      <div class="user-mini">
        <div
          v-if="auth.avatar"
          class="avatar-dot"
          :style="{ background: `url(${auth.avatar}) center/cover` }"
        ></div>
        <div v-else-if="!auth.isLogin" class="avatar-dot guest">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="9" r="3.2"/>
            <path d="M5 20v-.5a5 5 0 0 1 5-5h4a5 5 0 0 1 5 5v.5"/>
          </svg>
        </div>
        <div v-else class="avatar-dot initial">{{ (auth.displayName || '?')[0] }}</div>
        <div class="user-meta">
          <div class="name">{{ auth.displayName }}</div>
          <div class="role">{{ auth.isDemo ? '演示模式' : (auth.isAdmin ? '管理员' : (auth.isRealLogin ? '已登录' : '游客')) }}</div>
        </div>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.side-nav { gap: var(--sp-2); }
.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: var(--sp-3) var(--sp-3) var(--sp-6);
}
.logo-box {
  width: 40px; height: 40px;
  background: var(--bg-card-2);
  border: 1px solid var(--border);
  border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
}
.logo-box svg { width: 24px; height: 24px; }
.brand-title { font-family: var(--font-heading); font-size: 18px; font-weight: 700; letter-spacing: -.03em; }
.brand-sub { font-family: var(--font-ui); font-size: 11px; color: var(--text-3); letter-spacing: .08em; text-transform: uppercase; }

.side-list { display: flex; flex-direction: column; gap: 2px; flex: 1; }
.side-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 14px;
  border-radius: 12px;
  color: var(--text-2);
  font-family: var(--font-ui);
  font-size: 13px;
  font-weight: 600;
  transition: all var(--transition);
  text-align: left;
}
.side-item { position: relative; }
.side-item:hover { background: var(--bg-card-2); color: var(--text); }
.side-item.active {
  background: var(--bg-card-2);
  color: var(--text);
}
.side-item.active::before {
  content: '';
  position: absolute;
  left: 0; top: 50%;
  transform: translateY(-50%);
  width: 3px; height: 16px;
  border-radius: 3px;
  background: var(--cyan);
}
.side-item.active .ico { color: var(--cyan); }
.ico { width: 20px; height: 20px; flex-shrink: 0; }

.side-foot {
  border-top: 1px solid var(--border);
  padding-top: var(--sp-4);
}
.user-mini {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 12px;
  background: var(--bg-card-2);
}
.avatar-dot {
  width: 36px; height: 36px;
  border-radius: 50%;
  flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  background: var(--bg-elevated);
  border: 1px solid var(--border);
  color: var(--text-2);
  position: relative;
  overflow: hidden;
}
.avatar-dot::after {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(circle at 30% 25%, rgba(217, 119, 87, .18), transparent 60%);
  pointer-events: none;
}
.avatar-dot.guest svg { width: 20px; height: 20px; position: relative; z-index: 1; }
.avatar-dot.initial {
  color: var(--text);
  font-size: 14px;
  font-weight: 700;
  letter-spacing: -.02em;
}
.user-meta .name { font-family: var(--font-ui); font-size: 13px; font-weight: 600; }
.user-meta .role { font-family: var(--font-ui); font-size: 11px; color: var(--text-3); letter-spacing: .04em; text-transform: uppercase; }
</style>
