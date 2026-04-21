<script setup>
import { computed, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { useConfigStore } from '@/stores/config';
import { useAppStore } from '@/stores/app';
import AppLayout from '@/components/layout/AppLayout.vue';
import Toast from '@/components/common/Toast.vue';
import ConfirmModal from '@/components/common/ConfirmModal.vue';

const route = useRoute();
const config = useConfigStore();
const app = useAppStore();

// 登录页无外壳
const useLayout = computed(() => route.meta?.layout !== 'none');

onMounted(() => {
  config.loadFromLocal();
  config.applyTheme(config.theme);
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
