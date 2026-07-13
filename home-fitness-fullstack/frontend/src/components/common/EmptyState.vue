<script setup>
defineProps({
  icon: { type: String, default: 'box' },
  title: { type: String, default: '暂无数据' },
  desc: { type: String, default: '' }
});
</script>

<template>
  <div class="empty">
    <div class="empty-icon">
      <svg viewBox="0 0 64 64" fill="none" stroke="currentColor" stroke-width="1.2" stroke-linecap="round">
        <rect x="12" y="8" width="40" height="48" rx="6"/>
        <line x1="22" y1="22" x2="42" y2="22"/>
        <line x1="22" y1="32" x2="36" y2="32"/>
        <line x1="22" y1="42" x2="38" y2="42"/>
      </svg>
    </div>
    <p>{{ title }}</p>
    <span v-if="desc">{{ desc }}</span>
  </div>
</template>

<style scoped>
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 60px 20px;
  color: var(--text-3);
  text-align: center;
}
.empty-icon {
  width: 88px; height: 88px;
  display: flex; align-items: center; justify-content: center;
  color: var(--cyan);
  margin-bottom: 14px;
  opacity: .55;
  border-radius: 50%;
  background: radial-gradient(circle at 50% 42%, var(--cyan-dim), transparent 72%);
  animation: emptyBreath 4.5s ease-in-out infinite;
}
.empty-icon svg { width: 56px; height: 56px; }
@keyframes emptyBreath {
  0%, 100% { transform: translateY(0) scale(1); }
  50%      { transform: translateY(-4px) scale(1.03); }
}
.empty p { font-size: 14px; color: var(--text-2); font-weight: 500; margin-bottom: 4px; }
.empty span { font-size: 12px; color: var(--text-3); }
</style>
