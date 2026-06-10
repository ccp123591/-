import { defineStore } from 'pinia';
import { ref, computed } from 'vue';

const TOKEN_KEY = 'fitcoach_token';
const USER_KEY = 'fitcoach_user';
const REFRESH_KEY = 'fitcoach_refresh_token';

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) || '');
  const refreshToken = ref(localStorage.getItem(REFRESH_KEY) || '');
  const user = ref(JSON.parse(localStorage.getItem(USER_KEY) || 'null'));
  const guestMode = ref(false);

  const isLogin = computed(() => !!token.value && !!user.value);
  const isAdmin = computed(() => user.value?.role === 'ADMIN');
  const displayName = computed(() => user.value?.nickname || (guestMode.value ? '游客' : '未登录'));
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

  /** access token 过期后由刷新流程单独更新。 */
  function setAccessToken(newToken) {
    token.value = newToken;
    localStorage.setItem(TOKEN_KEY, newToken);
  }

  function logout() {
    token.value = '';
    refreshToken.value = '';
    user.value = null;
    guestMode.value = false;
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    localStorage.removeItem(REFRESH_KEY);
  }

  function enterGuest() {
    guestMode.value = true;
  }

  function updateProfile(partial) {
    if (!user.value) return;
    user.value = { ...user.value, ...partial };
    localStorage.setItem(USER_KEY, JSON.stringify(user.value));
  }

  return {
    token, refreshToken, user, guestMode,
    isLogin, isAdmin, displayName, avatar,
    setAuth, setAccessToken, logout, enterGuest, updateProfile
  };
});
