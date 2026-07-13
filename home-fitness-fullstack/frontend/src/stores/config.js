import { defineStore } from 'pinia';
import { ref, watch } from 'vue';

const CONFIG_KEY = 'fitcoach_config';
const COMPANION_PANEL_VERSION = 2;

const DEFAULT_CONFIG = {
  squat: { down: 90, up: 160 },
  stretch: { down: 60, up: 160 },
  pushup: { down: 80, up: 160 },
  lunge: { down: 100, up: 170 },
  bridge: { down: 150, up: 175 },
  plank: { down: 150, up: 170 },        // 时间型：down = 身体直线角下限（>= 即有效支撑）
  jumpingJack: { down: 40, up: 140 },   // 手臂外展角：合拢 < down，张开 > up
  bpm: 30,
  ttsRate: 1,
  theme: 'light',
  weeklyGoal: 50,
  voiceEnabled: true,
  metronomeEnabled: false,
  autoPauseEnabled: true,
  coachEnabled: true,
  demoMode: true,
  companionEnabled: true,
  companionAutoSpeak: false,
  companionName: '小柯'
};

/** 旧版本把时间型动作阈值存成 0/0（状态机会失效），迁移为新默认值。 */
function migrate(saved, key) {
  const v = saved?.[key];
  if (!v || !v.down || !v.up) return { ...DEFAULT_CONFIG[key] };
  return { ...DEFAULT_CONFIG[key], ...v };
}

export const useConfigStore = defineStore('config', () => {
  const squat = ref({ ...DEFAULT_CONFIG.squat });
  const stretch = ref({ ...DEFAULT_CONFIG.stretch });
  const pushup = ref({ ...DEFAULT_CONFIG.pushup });
  const lunge = ref({ ...DEFAULT_CONFIG.lunge });
  const bridge = ref({ ...DEFAULT_CONFIG.bridge });
  const plank = ref({ ...DEFAULT_CONFIG.plank });
  const jumpingJack = ref({ ...DEFAULT_CONFIG.jumpingJack });
  const bpm = ref(DEFAULT_CONFIG.bpm);
  const ttsRate = ref(DEFAULT_CONFIG.ttsRate);
  const theme = ref(DEFAULT_CONFIG.theme);
  const weeklyGoal = ref(DEFAULT_CONFIG.weeklyGoal);
  const voiceEnabled = ref(DEFAULT_CONFIG.voiceEnabled);
  const metronomeEnabled = ref(DEFAULT_CONFIG.metronomeEnabled);
  const autoPauseEnabled = ref(DEFAULT_CONFIG.autoPauseEnabled);
  const coachEnabled = ref(DEFAULT_CONFIG.coachEnabled);
  const demoMode = ref(DEFAULT_CONFIG.demoMode);
  const companionEnabled = ref(DEFAULT_CONFIG.companionEnabled);
  const companionAutoSpeak = ref(DEFAULT_CONFIG.companionAutoSpeak);
  const companionName = ref(DEFAULT_CONFIG.companionName);

  function snapshot() {
    return {
      squat: squat.value, stretch: stretch.value, pushup: pushup.value,
      lunge: lunge.value, bridge: bridge.value,
      plank: plank.value, jumpingJack: jumpingJack.value,
      bpm: bpm.value, ttsRate: ttsRate.value, theme: theme.value,
      weeklyGoal: weeklyGoal.value,
      voiceEnabled: voiceEnabled.value,
      metronomeEnabled: metronomeEnabled.value,
      autoPauseEnabled: autoPauseEnabled.value,
      coachEnabled: coachEnabled.value,
      demoMode: demoMode.value,
      companionEnabled: companionEnabled.value,
      companionAutoSpeak: companionAutoSpeak.value,
      companionName: companionName.value,
      companionPanelVersion: COMPANION_PANEL_VERSION
    };
  }

  function loadFromLocal() {
    try {
      const raw = localStorage.getItem(CONFIG_KEY);
      if (raw) {
        const saved = JSON.parse(raw);
        const data = { ...DEFAULT_CONFIG, ...saved };
        squat.value = { ...DEFAULT_CONFIG.squat, ...data.squat };
        stretch.value = { ...DEFAULT_CONFIG.stretch, ...data.stretch };
        pushup.value = { ...DEFAULT_CONFIG.pushup, ...data.pushup };
        lunge.value = { ...DEFAULT_CONFIG.lunge, ...data.lunge };
        bridge.value = { ...DEFAULT_CONFIG.bridge, ...data.bridge };
        plank.value = migrate(saved, 'plank');
        jumpingJack.value = migrate(saved, 'jumpingJack');
        bpm.value = data.bpm;
        ttsRate.value = data.ttsRate;
        theme.value = data.theme;
        weeklyGoal.value = data.weeklyGoal;
        voiceEnabled.value = data.voiceEnabled;
        metronomeEnabled.value = data.metronomeEnabled;
        autoPauseEnabled.value = data.autoPauseEnabled;
        coachEnabled.value = data.coachEnabled;
        demoMode.value = data.demoMode;
        // v2 恢复训练页右侧助手卡片：旧配置曾可能被冒烟测试关闭，升级时重新开启一次。
        companionEnabled.value = saved.companionPanelVersion === COMPANION_PANEL_VERSION
          ? data.companionEnabled
          : true;
        companionAutoSpeak.value = data.companionAutoSpeak;
        companionName.value = data.companionName || DEFAULT_CONFIG.companionName;
      }
    } catch (_) { /* ignore */ }
  }

  function save() {
    localStorage.setItem(CONFIG_KEY, JSON.stringify(snapshot()));
  }

  // 开关类设置（训练页直接 v-model 绑 store）变更即持久化，避免离开页面丢配置
  watch([voiceEnabled, metronomeEnabled, autoPauseEnabled, coachEnabled, demoMode,
         companionEnabled, companionAutoSpeak],
        () => { try { save(); } catch (_) {} });

  function reset() {
    Object.assign(squat.value, DEFAULT_CONFIG.squat);
    Object.assign(stretch.value, DEFAULT_CONFIG.stretch);
    Object.assign(pushup.value, DEFAULT_CONFIG.pushup);
    Object.assign(lunge.value, DEFAULT_CONFIG.lunge);
    Object.assign(bridge.value, DEFAULT_CONFIG.bridge);
    Object.assign(plank.value, DEFAULT_CONFIG.plank);
    Object.assign(jumpingJack.value, DEFAULT_CONFIG.jumpingJack);
    bpm.value = DEFAULT_CONFIG.bpm;
    ttsRate.value = DEFAULT_CONFIG.ttsRate;
    theme.value = DEFAULT_CONFIG.theme;
    weeklyGoal.value = DEFAULT_CONFIG.weeklyGoal;
    voiceEnabled.value = DEFAULT_CONFIG.voiceEnabled;
    metronomeEnabled.value = DEFAULT_CONFIG.metronomeEnabled;
    autoPauseEnabled.value = DEFAULT_CONFIG.autoPauseEnabled;
    coachEnabled.value = DEFAULT_CONFIG.coachEnabled;
    demoMode.value = DEFAULT_CONFIG.demoMode;
    companionEnabled.value = DEFAULT_CONFIG.companionEnabled;
    companionAutoSpeak.value = DEFAULT_CONFIG.companionAutoSpeak;
    companionName.value = DEFAULT_CONFIG.companionName;
    save();
    applyTheme(theme.value);
  }

  function applyTheme(name) {
    theme.value = name;
    if (name === 'dark') {
      document.documentElement.removeAttribute('data-theme');
    } else {
      document.documentElement.setAttribute('data-theme', name);
    }
    const meta = document.querySelector('meta[name="theme-color"]');
    const colors = {
      dark: '#141413', light: '#faf9f5', ocean: '#f7f5ef',
      forest: '#f6f4ee', sunset: '#fbf7f1', 'purple-night': '#141413'
    };
    if (meta) meta.content = colors[name] || '#141413';
  }

  return {
    squat, stretch, pushup, lunge, bridge, plank, jumpingJack,
    bpm, ttsRate, theme, weeklyGoal,
    voiceEnabled, metronomeEnabled, autoPauseEnabled, coachEnabled, demoMode,
    companionEnabled, companionAutoSpeak, companionName,
    snapshot, loadFromLocal, save, reset, applyTheme
  };
});
