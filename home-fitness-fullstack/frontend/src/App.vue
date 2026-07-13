<script setup>
import { computed, onMounted, onBeforeUnmount, watch } from 'vue';
import { useRoute } from 'vue-router';
import { useConfigStore } from '@/stores/config';
import { useAppStore } from '@/stores/app';
import { useAuthStore } from '@/stores/auth';
import { syncOfflineSessions } from '@/modules/sync';
import AppLayout from '@/components/layout/AppLayout.vue';
import Toast from '@/components/common/Toast.vue';
import ConfirmModal from '@/components/common/ConfirmModal.vue';

const route = useRoute();
const config = useConfigStore();
const app = useAppStore();
const auth = useAuthStore();

// 登录页无外壳
const useLayout = computed(() => route.meta?.layout !== 'none');

function trySync() {
  if (!auth.isRealLogin || auth.isDemo) return;
  syncOfflineSessions().then(n => {
    if (n > 0) app.showToast(`已同步 ${n} 条离线训练记录`, 'success');
  });
}

watch(() => config.demoMode, enabled => {
  auth.setDemoMode(enabled);
}, { immediate: true });

onMounted(() => {
  config.loadFromLocal();
  config.applyTheme(config.theme);
  // 已登录启动时 / 断网恢复时，把离线记录推上云
  trySync();
  window.addEventListener('online', trySync);
});
onBeforeUnmount(() => {
  window.removeEventListener('online', trySync);
});
</script>

<template>
  <AppLayout v-if="useLayout">
    <router-view v-slot="{ Component }">
      <transition name="page" mode="out-in">
        <component :is="Component" />
      </transition>
    </router-view>
  </AppLayout>

  <router-view v-else v-slot="{ Component }">
    <transition name="page" mode="out-in">
      <component :is="Component" />
    </transition>
  </router-view>

  <Toast />
  <ConfirmModal />
</template>

<style>
.page-enter-active,
.page-leave-active {
  transition: opacity .25s ease, transform .25s ease;
}
.page-enter-from { opacity: 0; transform: translateY(10px); }
.page-leave-to   { opacity: 0; transform: translateY(-6px); }
</style>
