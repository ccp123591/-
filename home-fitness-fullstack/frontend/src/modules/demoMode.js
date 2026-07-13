export const CONFIG_KEY = 'fitcoach_config';
export const DEFAULT_DEMO_MODE = true;

export function readDemoConfig() {
  if (typeof localStorage === 'undefined') return {};
  try {
    const raw = localStorage.getItem(CONFIG_KEY);
    return raw ? (JSON.parse(raw) || {}) : {};
  } catch (_) {
    return {};
  }
}

/**
 * 全站演示模式默认开启。只有用户在设置中显式关闭后，页面才访问真实后端。
 */
export function isDemoModeEnabled() {
  const value = readDemoConfig().demoMode;
  return typeof value === 'boolean' ? value : DEFAULT_DEMO_MODE;
}

export function persistDemoMode(enabled) {
  if (typeof localStorage === 'undefined') return;
  const next = { ...readDemoConfig(), demoMode: Boolean(enabled) };
  localStorage.setItem(CONFIG_KEY, JSON.stringify(next));
}
