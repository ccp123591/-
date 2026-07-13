<script setup>
import TabBar from './TabBar.vue';
import SideNav from './SideNav.vue';
import TopBar from './TopBar.vue';
import CompanionWidget from '@/components/companion/CompanionWidget.vue';
import { useConfigStore } from '@/stores/config';

const config = useConfigStore();
</script>

<template>
  <SideNav />
  <TopBar />
  <main class="app-main">
    <div v-if="config.demoMode" class="demo-mode-banner" role="status">
      <span class="demo-mode-dot"></span>
      <strong>全站演示模式</strong>
      <span>数据仅用于展示，不会请求真实 AI、邮件或业务服务</span>
      <router-link to="/settings">管理</router-link>
    </div>
    <slot />
  </main>
  <TabBar />
  <CompanionWidget />
</template>

<style scoped>
.demo-mode-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 auto 14px;
  padding: 9px 12px;
  max-width: 1180px;
  border: 1px solid rgba(217, 119, 87, .28);
  border-radius: 12px;
  background: rgba(217, 119, 87, .08);
  color: var(--text-2);
  font-size: 12px;
  line-height: 1.4;
}
.demo-mode-banner strong { color: var(--text); white-space: nowrap; }
.demo-mode-banner a { margin-left: auto; color: var(--cyan); font-weight: 700; white-space: nowrap; }
.demo-mode-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--cyan);
  box-shadow: 0 0 0 4px rgba(217, 119, 87, .12);
  flex: 0 0 auto;
}
@media (max-width: 560px) {
  .demo-mode-banner span:not(.demo-mode-dot) { display: none; }
}
</style>
