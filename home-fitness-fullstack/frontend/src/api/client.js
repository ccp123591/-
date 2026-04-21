/**
 * client.js — Axios 实例封装
 * 统一拦截：token 注入、错误提示、401 跳登录
 */
import axios from 'axios';
import { useAuthStore } from '@/stores/auth';
import { useAppStore } from '@/stores/app';

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
  err => {
    const status = err.response?.status;
    const app = useAppStore();
    if (status === 401) {
      const auth = useAuthStore();
      auth.logout();
      app.showToast('登录已过期', 'warning');
    } else if (!err.response) {
      app.showToast('网络异常，请稍后重试', 'error');
    } else {
      app.showToast(err.response?.data?.message || '服务器开小差了', 'error');
    }
    return Promise.reject(err);
  }
);

export default client;
