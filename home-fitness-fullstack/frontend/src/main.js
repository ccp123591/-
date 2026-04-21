import { createApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';
import router from './router';

// 全局样式（顺序敏感）
import './assets/css/base.css';
import './assets/css/themes.css';
import './assets/css/animations.css';
import './assets/css/responsive.css';

const app = createApp(App);
app.use(createPinia());
app.use(router);
app.mount('#app');

// 注册 Service Worker
if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch(() => {});
  });
}
