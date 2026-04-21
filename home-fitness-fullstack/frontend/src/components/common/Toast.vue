<script setup>
import { useAppStore } from '@/stores/app';
const app = useAppStore();
</script>

<template>
  <div class="toast-container">
    <transition-group name="toast">
      <div
        v-for="t in app.toasts"
        :key="t.id"
        :class="['toast', `toast-${t.type}`]"
      >
        <div class="toast-icon">
          <svg v-if="t.type === 'success'" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="10" cy="10" r="7"/><path d="M7 10l2 2 4-4"/></svg>
          <svg v-else-if="t.type === 'warning'" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M10 3l7.5 13H2.5z"/><line x1="10" y1="9" x2="10" y2="11"/><circle cx="10" cy="13.5" r=".5" fill="currentColor"/></svg>
          <svg v-else viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="10" cy="10" r="7"/><path d="M7.5 7.5l5 5 M12.5 7.5l-5 5"/></svg>
        </div>
        <span>{{ t.message }}</span>
      </div>
    </transition-group>
  </div>
</template>

<style scoped>
.toast-container {
  position: fixed;
  top: 20px; left: 50%;
  transform: translateX(-50%);
  z-index: 9999;
  display: flex;
  flex-direction: column;
  gap: 8px;
  pointer-events: none;
  padding-top: env(safe-area-inset-top, 0);
}
.toast {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 18px;
  background: var(--overlay-bg);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid var(--border);
  border-radius: 14px;
  color: var(--text);
  font-size: 13px;
  font-weight: 500;
  box-shadow: var(--shadow);
  pointer-events: auto;
  min-width: 180px;
  max-width: 90vw;
}
.toast-icon {
  width: 20px; height: 20px;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.toast-icon svg { width: 18px; height: 18px; }
.toast-success .toast-icon { color: var(--green); }
.toast-warning .toast-icon { color: var(--orange); }
.toast-error   .toast-icon { color: var(--red); }
.toast-success { border-color: var(--green-dim); }
.toast-warning { border-color: var(--orange-dim); }
.toast-error   { border-color: var(--red-dim); }

.toast-enter-active,
.toast-leave-active { transition: all .3s cubic-bezier(.2, 1.1, .3, 1); }
.toast-enter-from { opacity: 0; transform: translateY(-16px) scale(.95); }
.toast-leave-to   { opacity: 0; transform: translateY(-16px) scale(.95); }
</style>
