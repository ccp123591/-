<script setup>
import { ref, computed, onBeforeUnmount } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import { useAppStore } from '@/stores/app';
import { authApi } from '@/api/auth';
import { syncOfflineSessions } from '@/modules/sync';

const router = useRouter();
const auth = useAuthStore();
const app = useAppStore();

const mode = ref('email');    // email | phone | wechat | guest
const tab = ref('login');     // login | register

const form = ref({
  email: '',
  password: '',
  phone: '',
  smsCode: '',
  nickname: '',
  agree: false
});

const loading = ref(false);

const primaryDisabled = computed(() => {
  if (!form.value.agree) return true;
  if (mode.value === 'email') {
    if (!form.value.email || !form.value.password) return true;
    if (tab.value === 'register' && form.value.password.length < 8) return true;
    if (tab.value === 'login'    && form.value.password.length < 6) return true;
    return false;
  }
  if (mode.value === 'phone')  return !form.value.phone || !form.value.smsCode;
  return false;
});

function goBack() {
  if (window.history.length > 1) router.back();
  else router.push('/train');
}

function applyAuth(data) {
  // 后端返回 { accessToken, refreshToken, user } — refreshToken 交给 store 统一管理
  auth.setAuth(data.accessToken, data.user, data.refreshToken);
  // 把离线期间攒下的训练记录推上云（后台静默，不阻塞跳转）
  syncOfflineSessions().then(n => {
    if (n > 0) app.showToast(`已同步 ${n} 条离线训练记录`, 'success');
  });
}

async function submit() {
  if (primaryDisabled.value || loading.value) return;
  if (auth.isDemo) {
    app.showToast('当前为演示模式，无需登录；可在设置中关闭后连接真实账号', 'info');
    router.push('/train');
    return;
  }
  loading.value = true;
  try {
    let data;
    if (tab.value === 'register') {
      if (mode.value !== 'email') {
        app.showToast('暂仅支持邮箱注册', 'warning');
        return;
      }
      data = await authApi.register({
        email: form.value.email,
        password: form.value.password,
        nickname: form.value.nickname || undefined
      });
    } else if (mode.value === 'email') {
      data = await authApi.loginByEmail(form.value.email, form.value.password);
    } else if (mode.value === 'phone') {
      data = await authApi.loginByPhone(form.value.phone, form.value.smsCode);
    }
    if (!data || !data.accessToken) {
      app.showToast('登录响应异常', 'error');
      return;
    }
    applyAuth(data);
    app.showToast(tab.value === 'register' ? '注册成功' : '登录成功', 'success');
    router.push('/train');
  } catch (_) {
    /* 拦截器已 toast */
  } finally {
    loading.value = false;
  }
}

async function loginGuest() {
  if (loading.value) return;
  if (auth.isDemo) {
    app.showToast('已进入全站演示模式', 'info');
    router.push('/train');
    return;
  }
  loading.value = true;
  try {
    let deviceId = localStorage.getItem('fitcoach_device_id');
    if (!deviceId) {
      deviceId = 'd-' + Math.random().toString(36).slice(2, 12) + Date.now().toString(36);
      localStorage.setItem('fitcoach_device_id', deviceId);
    }
    const data = await authApi.loginAsGuest(deviceId);
    if (data?.accessToken) {
      applyAuth(data);
      app.showToast('已以游客身份进入', 'success');
      router.push('/train');
    }
  } catch (_) {
    // 后端没开放游客时，退回前端纯游客模式（无 token，仅可浏览本地数据）
    auth.enterGuest();
    app.showToast('以本地游客身份进入', 'info');
    router.push('/train');
  } finally {
    loading.value = false;
  }
}

function loginWechat() {
  app.showToast('微信登录暂未开放', 'warning');
}

let smsCountdown = ref(0);
let smsTimer = null;

async function sendSms() {
  if (!form.value.phone || smsCountdown.value > 0) return;
  if (auth.isDemo) {
    app.showToast('演示模式不会发送真实验证码', 'info');
    return;
  }
  try {
    await authApi.sendSmsCode(form.value.phone, 'login');
    app.showToast('验证码已发送', 'success');
    smsCountdown.value = 60;
    smsTimer = setInterval(() => {
      smsCountdown.value--;
      if (smsCountdown.value <= 0) { clearInterval(smsTimer); smsTimer = null; }
    }, 1000);
  } catch (_) { /* 拦截器已 toast */ }
}

onBeforeUnmount(() => {
  if (smsTimer) clearInterval(smsTimer);
});
</script>

<template>
  <div class="login-page">
    <!-- 返回按钮 -->
    <button class="back-btn" type="button" @click="goBack" aria-label="返回">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M15 18l-6-6 6-6"/>
      </svg>
      <span>返回</span>
    </button>

    <!-- 背景装饰 -->
    <div class="bg-glow glow-1"></div>
    <div class="bg-glow glow-2"></div>

    <div class="login-wrap">
      <!-- Logo -->
      <div class="brand-area">
        <div class="logo-big">
          <svg viewBox="0 0 32 32" fill="none">
            <defs><linearGradient id="llg" x1="4" y1="4" x2="28" y2="28">
              <stop stop-color="#d97757"/><stop offset="1" stop-color="#c96442"/>
            </linearGradient></defs>
            <circle cx="16" cy="10" r="2.5" stroke="url(#llg)" stroke-width="1.8"/>
            <path d="M12 22l2-7 2 3 2-3 2 7" stroke="url(#llg)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </div>
        <h1 class="title text-gradient">FitCoach</h1>
        <p class="subtitle">AI 居家健身陪练 · 让训练更智能</p>
      </div>

      <!-- 切换 Tab -->
      <div class="auth-tabs">
        <button :class="['auth-tab', tab === 'login' ? 'active' : '']" @click="tab = 'login'">登录</button>
        <button :class="['auth-tab', tab === 'register' ? 'active' : '']" @click="tab = 'register'">注册</button>
      </div>

      <!-- 登录方式 -->
      <div class="mode-switch">
        <button :class="['mode-btn', mode === 'email' ? 'active' : '']" @click="mode = 'email'">
          <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"><rect x="2" y="4" width="16" height="12" rx="2"/><path d="M2 7l8 5 8-5"/></svg>
          邮箱
        </button>
        <button :class="['mode-btn', mode === 'phone' ? 'active' : '']" @click="mode = 'phone'">
          <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"><rect x="5" y="2" width="10" height="16" rx="2"/><circle cx="10" cy="15" r=".5" fill="currentColor"/></svg>
          手机号
        </button>
      </div>

      <!-- 表单 -->
      <form class="form" @submit.prevent="submit">
        <template v-if="mode === 'email'">
          <div class="input-field">
            <label>邮箱</label>
            <input v-model="form.email" type="email" placeholder="请输入邮箱" autocomplete="username" />
          </div>
          <div class="input-field">
            <label>密码</label>
            <input v-model="form.password" type="password" placeholder="至少 6 位" autocomplete="current-password" />
          </div>
          <div v-if="tab === 'register'" class="input-field">
            <label>昵称</label>
            <input v-model="form.nickname" type="text" placeholder="你想让别人怎么叫你" />
          </div>
        </template>

        <template v-else-if="mode === 'phone'">
          <div class="input-field">
            <label>手机号</label>
            <input v-model="form.phone" type="tel" placeholder="请输入 11 位手机号" maxlength="11" />
          </div>
          <div class="input-field sms-field">
            <label>验证码</label>
            <div class="sms-row">
              <input v-model="form.smsCode" type="text" placeholder="6 位验证码" maxlength="6" />
              <button type="button" class="sms-btn" :disabled="smsCountdown > 0 || !form.phone" @click="sendSms">
                {{ smsCountdown > 0 ? `${smsCountdown}s` : '获取验证码' }}
              </button>
            </div>
          </div>
        </template>

        <label class="agree">
          <input type="checkbox" v-model="form.agree" />
          <span>我已阅读并同意《用户协议》与《隐私政策》</span>
        </label>

        <button type="submit" class="submit-btn" :disabled="primaryDisabled || loading">
          <span v-if="loading" class="spin"></span>
          <span>{{ tab === 'login' ? '登录' : '注册' }}</span>
        </button>
      </form>

      <div class="or-line"><span>其他方式</span></div>

      <div class="third-row">
        <button class="third-btn" @click="loginWechat">
          <svg viewBox="0 0 24 24" fill="currentColor"><path d="M8.5 4C4.9 4 2 6.6 2 9.8c0 1.9 1 3.6 2.7 4.7l-.7 2.1 2.3-1.3c.7.2 1.4.3 2.2.3h.8c-.1-.5-.2-1-.2-1.5 0-3.3 3.1-6 7-6h.6C15.7 5.7 12.4 4 8.5 4zm-3 3c.6 0 1 .4 1 1s-.4 1-1 1-1-.4-1-1 .4-1 1-1zm5 0c.6 0 1 .4 1 1s-.4 1-1 1-1-.4-1-1 .4-1 1-1zm6 2c-3.3 0-6 2.2-6 4.9 0 2.8 2.7 5 6 5 .7 0 1.4-.1 2-.3l1.9 1.1-.5-1.7c1.5-.9 2.5-2.3 2.5-4.1 0-2.7-2.7-4.9-6-4.9h.1zm-2 1.8c.5 0 .8.3.8.8s-.3.8-.8.8-.8-.3-.8-.8.3-.8.8-.8zm4 0c.5 0 .8.3.8.8s-.3.8-.8.8-.8-.3-.8-.8.3-.8.8-.8z"/></svg>
          微信
        </button>
        <button class="third-btn" @click="loginGuest">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><circle cx="12" cy="8" r="4"/><path d="M4 20a8 8 0 0 1 16 0"/></svg>
          游客
        </button>
      </div>

      <p class="tip">微信登录暂未开放 · 演示账号 demo@fitcoach.com / admin123</p>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px 20px;
  position: relative;
  overflow: hidden;
}

.back-btn {
  position: fixed;
  top: calc(env(safe-area-inset-top, 0) + 16px);
  left: 16px;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px 8px 10px;
  border-radius: 100px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  color: var(--text-2);
  font-size: 13px;
  font-weight: 500;
  box-shadow: var(--shadow-sm);
  transition: all var(--transition);
}
.back-btn:hover { color: var(--text); border-color: var(--border-hover); }
.back-btn:active { transform: scale(.96); }
.back-btn svg { width: 16px; height: 16px; }
.bg-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(100px);
  opacity: .16;
  pointer-events: none;
  z-index: 0;
}
.glow-1 { top: -120px; left: -120px; width: 340px; height: 340px; background: var(--cyan); }
.glow-2 { bottom: -120px; right: -120px; width: 340px; height: 340px; background: var(--purple); }

.login-wrap {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 380px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 24px;
  padding: 32px 24px;
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  box-shadow: var(--shadow-lg);
}

.brand-area { text-align: center; margin-bottom: 24px; }
.logo-big { width: 56px; height: 56px; margin: 0 auto 12px; animation: floatY 3s ease-in-out infinite; }
.logo-big svg { width: 100%; height: 100%; }
.title { font-family: var(--font-heading); font-size: 30px; font-weight: 700; letter-spacing: -.05em; margin-bottom: 6px; }
.subtitle { font-family: var(--font-ui); font-size: 12px; color: var(--text-3); }

.auth-tabs {
  display: flex;
  background: var(--bg-card-2);
  border-radius: 12px;
  padding: 4px;
  margin-bottom: 20px;
}
.auth-tab {
  flex: 1;
  padding: 10px;
  border-radius: 8px;
  color: var(--text-3);
  font-size: 14px;
  font-weight: 600;
  transition: all var(--transition);
}
.auth-tab.active {
  background: var(--bg-elevated);
  color: var(--text);
  box-shadow: var(--shadow-sm);
}

.mode-switch { display: flex; gap: 8px; margin-bottom: 18px; }
.mode-btn {
  flex: 1;
  padding: 10px;
  border-radius: 10px;
  background: var(--bg-card-2);
  color: var(--text-3);
  font-size: 12px;
  font-weight: 500;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: 1px solid transparent;
  transition: all var(--transition);
}
.mode-btn svg { width: 14px; height: 14px; }
.mode-btn.active {
  background: var(--cyan-dim);
  color: var(--cyan);
  border-color: var(--cyan);
}

.form { display: flex; flex-direction: column; gap: 14px; }
.input-field label {
  display: block;
  font-size: 11px;
  color: var(--text-3);
  margin-bottom: 6px;
  letter-spacing: .04em;
}
.sms-row { display: flex; gap: 8px; }
.sms-row input { flex: 1; }
.sms-btn {
  padding: 10px 14px;
  border-radius: var(--radius-sm);
  background: var(--cyan-dim);
  color: var(--cyan);
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
  transition: all var(--transition);
}
.sms-btn:disabled { opacity: .5; cursor: not-allowed; }

.agree {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--text-3);
  cursor: pointer;
  margin: 4px 0;
}
.agree input { accent-color: var(--cyan); }

.submit-btn {
  padding: 14px;
  border-radius: 14px;
  background: var(--grad-primary);
  color: #fff;
  font-size: 15px;
  font-weight: 700;
  transition: all var(--transition);
  box-shadow: 0 6px 20px rgba(217, 119, 87, .3);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}
.submit-btn:disabled {
  opacity: .5;
  cursor: not-allowed;
  box-shadow: none;
}
.submit-btn:not(:disabled):active { transform: scale(.98); }
.spin {
  width: 16px; height: 16px;
  border: 2px solid rgba(255, 255, 255, .3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

.or-line {
  margin: 24px 0 16px;
  font-size: 11px;
  color: var(--text-3);
  text-align: center;
  position: relative;
}
.or-line::before, .or-line::after {
  content: '';
  position: absolute;
  top: 50%;
  width: calc(50% - 36px);
  height: 1px;
  background: var(--border);
}
.or-line::before { left: 0; }
.or-line::after { right: 0; }

.third-row { display: flex; gap: 12px; }
.third-btn {
  flex: 1;
  padding: 12px;
  border-radius: 12px;
  background: var(--bg-card-2);
  color: var(--text-2);
  font-size: 13px;
  font-weight: 500;
  border: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  transition: all var(--transition);
}
.third-btn:hover { border-color: var(--border-hover); color: var(--text); }
.third-btn svg { width: 16px; height: 16px; }

.tip {
  text-align: center;
  font-size: 10px;
  color: var(--text-3);
  margin-top: 16px;
}

@media (max-width: 380px) {
  .login-wrap { padding: 24px 18px; }
  .title { font-size: 24px; }
}
</style>
