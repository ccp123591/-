<script setup>
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();

const pageTitle = computed(() => route.meta?.title || 'FitCoach');

function toLogin() { router.push('/login'); }
</script>

<template>
  <header class="top-bar">
    <h3 class="page-title">{{ pageTitle }}</h3>
    <div class="top-actions">
      <button class="action-btn" title="通知">
        <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round">
          <path d="M15 7A5 5 0 0 0 5 7v4l-2 2h14l-2-2V7z"/><path d="M7 15a3 3 0 0 0 6 0"/>
        </svg>
      </button>
      <button v-if="!auth.isLogin" class="login-btn" @click="toLogin">登录</button>
      <div v-else class="top-avatar" :style="{ background: auth.avatar ? `url(${auth.avatar}) center/cover` : 'var(--grad-primary)' }"></div>
    </div>
  </header>
</template>

<style scoped>
.page-title { font-size: 18px; font-weight: 700; letter-spacing: -.2px; }
.top-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 12px;
}
.action-btn {
  width: 36px; height: 36px;
  border-radius: 10px;
  background: var(--bg-card-2);
  border: 1px solid var(--border);
  color: var(--text-2);
  display: flex; align-items: center; justify-content: center;
  transition: all var(--transition);
}
.action-btn:hover { color: var(--text); background: var(--bg-elevated); }
.action-btn svg { width: 18px; height: 18px; }
.login-btn {
  padding: 8px 18px;
  border-radius: 10px;
  background: var(--grad-primary);
  color: #fff;
  font-weight: 600;
  font-size: 13px;
  transition: transform var(--transition);
}
.login-btn:active { transform: scale(.96); }
.top-avatar {
  width: 36px; height: 36px;
  border-radius: 50%;
  background: var(--grad-primary);
  border: 2px solid var(--border);
}
</style>
