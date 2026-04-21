import { defineStore } from 'pinia';
import { ref } from 'vue';

const CONFIG_KEY = 'fitcoach_config';

const DEFAULT_CONFIG = {
  squat: { down: 90, up: 160 },
  stretch: { down: 60, up: 160 },
  pushup: { down: 80, up: 160 },
  lunge: { down: 100, up: 170 },
  bridge: { down: 150, up: 175 },
  plank: { down: 0, up: 0 },          // 时间型
  jumpingJack: { down: 0, up: 0 },    // 时间型
  bpm: 30,
  ttsRate: 1,
  theme: 'light',
  weeklyGoal: 50,
  voiceEnabled: true,
  metronomeEnabled: false,
  autoPauseEnabled: true,
  coachEnabled: true
};

export const useConfigStore = defineStore('config', () => {
  const squat = ref({ ...DEFAULT_CONFIG.squat });
  const stretch = ref({ ...DEFAULT_CONFIG.stretch });
  const pushup = ref({ ...DEFAULT_CONFIG.pushup });
  const lunge = ref({ ...DEFAULT_CONFIG.lunge });
  const bridge = ref({ ...DEFAULT_CONFIG.bridge });
  const bpm = ref(DEFAULT_CONFIG.bpm);
  const ttsRate = ref(DEFAULT_CONFIG.ttsRate);
  const theme = ref(DEFAULT_CONFIG.theme);
  const weeklyGoal = ref(DEFAULT_CONFIG.weeklyGoal);
  const voiceEnabled = ref(DEFAULT_CONFIG.voiceEnabled);
  const metronomeEnabled = ref(DEFAULT_CONFIG.metronomeEnabled);
  const autoPauseEnabled = ref(DEFAULT_CONFIG.autoPauseEnabled);
  const coachEnabled = ref(DEFAULT_CONFIG.coachEnabled);

  function snapshot() {
    return {
      squat: squat.value, stretch: stretch.value, pushup: pushup.value,
      lunge: lunge.value, bridge: bridge.value,
      bpm: bpm.value, ttsRate: ttsRate.value, theme: theme.value,
      weeklyGoal: weeklyGoal.value,
      voiceEnabled: voiceEnabled.value,
      metronomeEnabled: metronomeEnabled.value,
      autoPauseEnabled: autoPauseEnabled.value,
      coachEnabled: coachEnabled.value
    };
  }

  function loadFromLocal() {
    try {
      const raw = localStorage.getItem(CONFIG_KEY);
      if (raw) {
        const data = { ...DEFAULT_CONFIG, ...JSON.parse(raw) };
        squat.value = { ...DEFAULT_CONFIG.squat, ...data.squat };
        stretch.value = { ...DEFAULT_CONFIG.stretch, ...data.stretch };
        pushup.value = { ...DEFAULT_CONFIG.pushup, ...data.pushup };
        lunge.value = { ...DEFAULT_CONFIG.lunge, ...data.lunge };
        bridge.value = { ...DEFAULT_CONFIG.bridge, ...data.bridge };
        bpm.value = data.bpm;
        ttsRate.value = data.ttsRate;
        theme.value = data.theme;
        weeklyGoal.value = data.weeklyGoal;
        voiceEnabled.value = data.voiceEnabled;
        metronomeEnabled.value = data.metronomeEnabled;
        autoPauseEnabled.value = data.autoPauseEnabled;
        coachEnabled.value = data.coachEnabled;
      }
    } catch (_) { /* ignore */ }
  }

  function save() {
    localStorage.setItem(CONFIG_KEY, JSON.stringify(snapshot()));
  }

  function reset() {
    Object.assign(squat.value, DEFAULT_CONFIG.squat);
    Object.assign(stretch.value, DEFAULT_CONFIG.stretch);
    Object.assign(pushup.value, DEFAULT_CONFIG.pushup);
    Object.assign(lunge.value, DEFAULT_CONFIG.lunge);
    Object.assign(bridge.value, DEFAULT_CONFIG.bridge);
    bpm.value = DEFAULT_CONFIG.bpm;
    ttsRate.value = DEFAULT_CONFIG.ttsRate;
    theme.value = DEFAULT_CONFIG.theme;
    weeklyGoal.value = DEFAULT_CONFIG.weeklyGoal;
    voiceEnabled.value = DEFAULT_CONFIG.voiceEnabled;
    metronomeEnabled.value = DEFAULT_CONFIG.metronomeEnabled;
    autoPauseEnabled.value = DEFAULT_CONFIG.autoPauseEnabled;
    coachEnabled.value = DEFAULT_CONFIG.coachEnabled;
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
      dark: '#0a0a0f', light: '#F5F5F7', ocean: '#0B1628',
      forest: '#0A1A0F', sunset: '#1A0E0A', 'purple-night': '#0E0A1A'
    };
    if (meta) meta.content = colors[name] || '#0a0a0f';
  }

  return {
    squat, stretch, pushup, lunge, bridge,
    bpm, ttsRate, theme, weeklyGoal,
    voiceEnabled, metronomeEnabled, autoPauseEnabled, coachEnabled,
    snapshot, loadFromLocal, save, reset, applyTheme
  };
});
