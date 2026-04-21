<script setup>
import TabBar from './TabBar.vue';
import SideNav from './SideNav.vue';
import TopBar from './TopBar.vue';
</script>

<template>
  <SideNav />
  <TopBar />
  <main class="app-main">
    <slot />
  </main>
  <TabBar />
</template>
