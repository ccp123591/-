/**
 * client.js — Axios 实例封装
 * 统一拦截：token 注入、错误提示、401 自动刷新（刷新失败才登出跳登录）
 */
import axios from 'axios';
import { useAuthStore } from '@/stores/auth';
import { useAppStore } from '@/stores/app';
import router from '@/router';

const client = axios.create({
  baseURL: '/api',
  timeout: 15000
});

client.interceptors.request.use(config => {
  const auth = useAuthStore();
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`;
  }
  return config;
});

// 多请求并发 401 时只发一次 refresh
let refreshing = null;

function refreshAccessToken(auth) {
  if (!refreshing) {
    // 用裸 axios，避免再次走本拦截器造成递归
    refreshing = axios.post('/api/auth/refresh', { refreshToken: auth.refreshToken })
      .then(resp => {
        const body = resp.data;
        const accessToken = body?.data?.accessToken;
        if (!(body && body.code === 0 && accessToken)) {
          throw new Error('refresh rejected');
        }
        auth.setAccessToken(accessToken);
        return accessToken;
      })
      .finally(() => { refreshing = null; });
  }
  return refreshing;
}

function forceLogout() {
  const auth = useAuthStore();
  const app = useAppStore();
  auth.logout();
  app.showToast('登录已过期，请重新登录', 'warning');
  if (router.currentRoute.value.path !== '/login') {
    router.push('/login');
  }
}

client.interceptors.response.use(
  resp => {
    const data = resp.data;
    // 后端统一 Result 结构：{ code, message, data }
    if (data && typeof data === 'object' && 'code' in data) {
      if (data.code === 0 || data.code === 200) return data.data;
      const app = useAppStore();
      app.showToast(data.message || '请求失败', 'error');
      return Promise.reject(data);
    }
    return data;
  },
  async err => {
    const status = err.response?.status;
    const app = useAppStore();
    const cfg = err.config || {};

    if (status === 401) {
      const auth = useAuthStore();
      const isAuthPath = (cfg.url || '').includes('/auth/');
      // access token 过期 → 用 refresh token 换新后重放原请求（只重试一次）
      if (auth.refreshToken && !cfg._retried && !isAuthPath) {
        try {
          await refreshAccessToken(auth);
          cfg._retried = true;
          return client(cfg);
        } catch (_) {
          forceLogout();
          return Promise.reject(err);
        }
      }
      if (auth.isLogin || auth.refreshToken) forceLogout();
      return Promise.reject(err);
    }

    if (!err.response) {
      app.showToast('网络异常，请稍后重试', 'error');
    } else {
      app.showToast(err.response?.data?.message || '服务器开小差了', 'error');
    }
    return Promise.reject(err);
  }
);

export default client;
