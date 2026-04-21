<script setup>
import { useAppStore } from '@/stores/app';
const app = useAppStore();
</script>

<template>
  <transition name="fade">
    <div v-if="app.confirm.show" class="confirm-overlay">
      <div class="backdrop" @click="app.resolveConfirm(false)"></div>
      <div class="confirm-box">
        <div class="icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="10"/>
            <line x1="12" y1="8" x2="12" y2="12"/>
            <circle cx="12" cy="16" r=".5" fill="currentColor"/>
          </svg>
        </div>
        <div class="title">{{ app.confirm.title }}</div>
        <div class="msg">{{ app.confirm.message }}</div>
        <div class="actions">
          <button class="btn cancel" @click="app.resolveConfirm(false)">{{ app.confirm.cancelText }}</button>
          <button class="btn ok" @click="app.resolveConfirm(true)">{{ app.confirm.okText }}</button>
        </div>
      </div>
    </div>
  </transition>
</template>

<style scoped>
.confirm-overlay {
  position: fixed; inset: 0;
  z-index: 9500;
  display: flex; align-items: center; justify-content: center;
  padding: 20px;
}
.backdrop {
  position: absolute; inset: 0;
  background: rgba(0, 0, 0, .55);
  backdrop-filter: blur(8px);
}
.confirm-box {
  position: relative;
  width: min(360px, 100%);
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 20px;
  padding: 28px 24px 20px;
  text-align: center;
  box-shadow: var(--shadow-lg);
  animation: scaleIn .3s cubic-bezier(.2, 1.1, .3, 1);
}
.icon {
  width: 52px; height: 52px;
  margin: 0 auto 16px;
  border-radius: 50%;
  background: var(--orange-dim);
  color: var(--orange);
  display: flex; align-items: center; justify-content: center;
}
.icon svg { width: 28px; height: 28px; }
.title { font-size: 17px; font-weight: 700; margin-bottom: 6px; }
.msg { font-size: 13px; color: var(--text-2); line-height: 1.6; margin-bottom: 20px; }
.actions { display: flex; gap: 10px; }
.btn {
  flex: 1;
  padding: 11px;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 600;
  transition: transform var(--transition);
}
.btn:active { transform: scale(.96); }
.cancel { background: var(--bg-card-2); color: var(--text-2); }
.cancel:hover { background: var(--bg-elevated); }
.ok {
  background: var(--grad-primary);
  color: #fff;
}

.fade-enter-active, .fade-leave-active { transition: opacity .22s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
