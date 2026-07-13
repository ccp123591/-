import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { isDemoModeEnabled } from '@/modules/demoMode';

const TOKEN_KEY = 'fitcoach_token';
const USER_KEY = 'fitcoach_user';
const REFRESH_KEY = 'fitcoach_refresh_token';
const DEMO_USER = Object.freeze({
  id: 900001,
  nickname: '演示学员',
  role: 'USER',
  avatar: '',
  demo: true
});

function readStoredUser() {
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw);
  } catch (_) {
    localStorage.removeItem(USER_KEY);
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(REFRESH_KEY);
    return null;
  }
}

export const useAuthStore = defineStore('auth', () => {
  const storedUser = readStoredUser();
  const token = ref(storedUser ? (localStorage.getItem(TOKEN_KEY) || '') : '');
  const refreshToken = ref(storedUser ? (localStorage.getItem(REFRESH_KEY) || '') : '');
  const demoMode = ref(isDemoModeEnabled());
  const user = ref(storedUser || (demoMode.value ? { ...DEMO_USER } : null));
  const guestMode = ref(!storedUser && !demoMode.value);

  const isRealLogin = computed(() => !!token.value && !!user.value && !user.value.demo);
  const isDemo = computed(() => demoMode.value);
  // 演示账户解锁依赖登录态展示的页面，但不会携带真实 token，所有请求由 demo API 层接管。
  const isLogin = computed(() => isRealLogin.value || isDemo.value);
  const isAdmin = computed(() => isDemo.value || (isRealLogin.value && user.value?.role === 'ADMIN'));
  const displayName = computed(() => user.value?.nickname || (demoMode.value ? DEMO_USER.nickname : (guestMode.value ? '游客' : '未登录')));
  const avatar = computed(() => user.value?.avatar || '');

  function setAuth(newToken, newUser, newRefreshToken) {
    token.value = newToken;
    user.value = newUser;
    guestMode.value = false;
    localStorage.setItem(TOKEN_KEY, newToken);
    localStorage.setItem(USER_KEY, JSON.stringify(newUser));
    if (newRefreshToken) {
      refreshToken.value = newRefreshToken;
      localStorage.setItem(REFRESH_KEY, newRefreshToken);
    }
  }

  function setDemoMode(enabled) {
    demoMode.value = Boolean(enabled);
    if (demoMode.value) {
      guestMode.value = false;
      if (!isRealLogin.value) user.value = { ...DEMO_USER };
    } else if (!isRealLogin.value) {
      // 清理旧版演示登录可能遗留的假 token，避免关闭演示后带到真实后端。
      if (user.value?.demo || token.value.startsWith('demo-')) {
        token.value = '';
        refreshToken.value = '';
        localStorage.removeItem(TOKEN_KEY);
        localStorage.removeItem(USER_KEY);
        localStorage.removeItem(REFRESH_KEY);
      }
      if (user.value?.demo) user.value = null;
      guestMode.value = true;
    }
  }

  /** access token 过期后由刷新流程单独更新。 */
  function setAccessToken(newToken) {
    token.value = newToken;
    localStorage.setItem(TOKEN_KEY, newToken);
  }

  function logout() {
    token.value = '';
    refreshToken.value = '';
    user.value = demoMode.value ? { ...DEMO_USER } : null;
    guestMode.value = !demoMode.value;
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    localStorage.removeItem(REFRESH_KEY);
  }

  function enterGuest() {
    if (demoMode.value) return;
    guestMode.value = true;
  }

  function updateProfile(partial) {
    if (!user.value) return;
    user.value = { ...user.value, ...partial };
    localStorage.setItem(USER_KEY, JSON.stringify(user.value));
  }

  return {
    token, refreshToken, user, guestMode, demoMode,
    isLogin, isRealLogin, isDemo, isAdmin, displayName, avatar,
    setAuth, setAccessToken, setDemoMode, logout, enterGuest, updateProfile
  };
});
