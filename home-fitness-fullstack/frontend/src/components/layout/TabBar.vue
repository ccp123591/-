<script setup>
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';

const route = useRoute();
const router = useRouter();

const tabs = [
  { key: 'train',   label: '训练', path: '/train',
    icon: 'M6.5 6.5a3.5 3.5 0 1 0 7 0 3.5 3.5 0 1 0-7 0 M3 20c0-5 3.5-9 7-9 M14 20c0-5-3.5-9-7-9 M17.5 11l2 3.5 M15 14.5L17.5 11l2.5 0' },
  { key: 'records', label: '记录', path: '/records',
    icon: 'M3 3v18h18 M7 16l4-4 4 2 5-6' },
  { key: 'plans',   label: '计划', path: '/plans',
    icon: 'M8 2v3 M16 2v3 M3 9h18 M5 5h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z' },
  { key: 'social',  label: '社区', path: '/leaderboard',
    icon: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M12 3a4 4 0 1 0 0 8 4 4 0 0 0 0-8 M22 21v-2a4 4 0 0 0-3-3.87 M16 3.13a4 4 0 0 1 0 7.75' },
  { key: 'profile', label: '我的', path: '/profile',
    icon: 'M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2 M12 3a4 4 0 1 0 0 8 4 4 0 0 0 0-8' }
];

const currentTab = computed(() => route.meta?.tab || 'train');

function go(tab) {
  if (currentTab.value === tab.key) return;
  router.push(tab.path);
}
</script>

<template>
  <nav class="tab-bar">
    <button
      v-for="t in tabs"
      :key="t.key"
      :class="['tab-btn', { active: currentTab === t.key }]"
      @click="go(t)"
    >
      <div class="tab-icon">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <path :d="t.icon" />
        </svg>
      </div>
      <span>{{ t.label }}</span>
    </button>
  </nav>
</template>

<style scoped>
.tab-btn {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  color: var(--text-3);
  font-size: 10px;
  font-weight: 500;
  transition: color var(--transition);
  position: relative;
}
.tab-icon {
  width: 24px; height: 24px;
  display: flex; align-items: center; justify-content: center;
}
.tab-icon svg { width: 22px; height: 22px; }
.tab-btn.active { color: var(--cyan); }
.tab-btn.active::before {
  content: '';
  position: absolute;
  top: 0; left: 50%;
  transform: translateX(-50%);
  width: 24px; height: 2px;
  border-radius: 0 0 2px 2px;
  background: var(--cyan);
  box-shadow: 0 0 8px var(--cyan);
}
.tab-btn:active { transform: scale(.92); }
</style>
