import { defineStore } from 'pinia';
import { ref } from 'vue';

let toastId = 0;
let confirmResolver = null;

export const useAppStore = defineStore('app', () => {
  const toasts = ref([]);
  const confirm = ref({ show: false, title: '', message: '', okText: '确定', cancelText: '取消' });

  function showToast(message, type = 'success', duration = 2500) {
    const id = ++toastId;
    toasts.value.push({ id, message, type });
    setTimeout(() => {
      toasts.value = toasts.value.filter(t => t.id !== id);
    }, duration);
  }

  function showConfirm(title, message, okText = '确定', cancelText = '取消') {
    confirm.value = { show: true, title, message, okText, cancelText };
    return new Promise(resolve => { confirmResolver = resolve; });
  }

  function resolveConfirm(result) {
    confirm.value.show = false;
    if (confirmResolver) {
      confirmResolver(result);
      confirmResolver = null;
    }
  }

  return { toasts, confirm, showToast, showConfirm, resolveConfirm };
});
