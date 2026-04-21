<script setup>
import { ref, onMounted } from 'vue';
import { useConfigStore } from '@/stores/config';
import { useAppStore } from '@/stores/app';

const config = useConfigStore();
const app = useAppStore();

const themes = [
  { key: 'dark',         name: '暗黑' },
  { key: 'light',        name: '明亮' },
  { key: 'ocean',        name: '深海' },
  { key: 'forest',       name: '森林' },
  { key: 'sunset',       name: '日落' },
  { key: 'purple-night', name: '紫夜' }
];

function pickTheme(k) {
  config.applyTheme(k);
  config.save();
}

function saveAll() {
  config.save();
  app.showToast('设置已保存', 'success');
}

async function resetAll() {
  const ok = await app.showConfirm('恢复默认', '确定恢复所有设置为默认值？');
  if (ok) {
    config.reset();
    app.showToast('已恢复默认', 'success');
  }
}

onMounted(() => { /* config 已在 App.vue 加载 */ });
</script>

<template>
  <div class="page-wrap settings-page">
    <div class="page-head">
      <h2>训练设置</h2>
      <p class="sub">自定义你的训练参数</p>
    </div>

    <!-- 主题 -->
    <div class="card">
      <div class="card-head">
        <div class="icon purple"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><circle cx="12" cy="12" r="10"/><path d="M12 2a7 7 0 0 0 0 20 4 4 0 0 1 0-8 4 4 0 0 0 0-8"/></svg></div>
        <h3>外观主题</h3>
      </div>
      <div class="theme-grid">
        <div
          v-for="t in themes"
          :key="t.key"
          :class="['theme-option', config.theme === t.key ? 'active' : '']"
          @click="pickTheme(t.key)"
        >
          <div :class="['swatch', `swatch-${t.key}`]"></div>
          <span>{{ t.name }}</span>
        </div>
      </div>
    </div>

    <!-- 深蹲 -->
    <div class="card">
      <div class="card-head">
        <div class="icon blue"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><circle cx="12" cy="5" r="3"/><path d="M8 22l2-10 2 2 2-2 2 10"/><path d="M6 14h12"/></svg></div>
        <h3>深蹲参数</h3>
      </div>
      <div class="item">
        <div class="lbl-row"><span>下蹲角度阈值</span><span class="v">{{ config.squat.down }}°</span></div>
        <input type="range" v-model.number="config.squat.down" min="60" max="120" />
      </div>
      <div class="item">
        <div class="lbl-row"><span>站立角度阈值</span><span class="v">{{ config.squat.up }}°</span></div>
        <input type="range" v-model.number="config.squat.up" min="140" max="180" />
      </div>
    </div>

    <!-- 前屈 -->
    <div class="card">
      <div class="card-head">
        <div class="icon green"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><circle cx="12" cy="5" r="3"/><path d="M12 8v8 M8 12l4 4 4-4 M8 22h8"/></svg></div>
        <h3>前屈伸展</h3>
      </div>
      <div class="item">
        <div class="lbl-row"><span>前屈角度阈值</span><span class="v">{{ config.stretch.down }}°</span></div>
        <input type="range" v-model.number="config.stretch.down" min="30" max="90" />
      </div>
      <div class="item">
        <div class="lbl-row"><span>站直角度阈值</span><span class="v">{{ config.stretch.up }}°</span></div>
        <input type="range" v-model.number="config.stretch.up" min="140" max="180" />
      </div>
    </div>

    <!-- 俯卧撑 -->
    <div class="card">
      <div class="card-head">
        <div class="icon blue"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M3 15h18 M6 15V9a3 3 0 0 1 6 0 M12 15V9a3 3 0 0 1 6 0"/></svg></div>
        <h3>俯卧撑参数</h3>
      </div>
      <div class="item">
        <div class="lbl-row"><span>下压角度阈值</span><span class="v">{{ config.pushup.down }}°</span></div>
        <input type="range" v-model.number="config.pushup.down" min="50" max="110" />
      </div>
      <div class="item">
        <div class="lbl-row"><span>撑起角度阈值</span><span class="v">{{ config.pushup.up }}°</span></div>
        <input type="range" v-model.number="config.pushup.up" min="140" max="180" />
      </div>
    </div>

    <!-- 节奏与语音 -->
    <div class="card">
      <div class="card-head">
        <div class="icon purple"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/></svg></div>
        <h3>节奏与语音</h3>
      </div>
      <div class="item">
        <div class="lbl-row"><span>节拍 BPM</span><span class="v">{{ config.bpm }}</span></div>
        <input type="range" v-model.number="config.bpm" min="15" max="60" />
      </div>
      <div class="item">
        <div class="lbl-row"><span>TTS 语速</span><span class="v">{{ config.ttsRate.toFixed(1) }}</span></div>
        <input type="range" v-model.number="config.ttsRate" min="0.5" max="2" step="0.1" />
      </div>
    </div>

    <!-- 智能选项 -->
    <div class="card">
      <div class="card-head">
        <div class="icon green"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><circle cx="12" cy="12" r="3"/><path d="M12 3v2 M12 19v2 M5 12H3 M21 12h-2 M18 6l-1.5 1.5 M7.5 16.5L6 18 M18 18l-1.5-1.5 M7.5 7.5L6 6"/></svg></div>
        <h3>智能选项</h3>
      </div>
      <label class="toggle-item">
        <span>自动暂停（未检测到人体）</span>
        <input type="checkbox" v-model="config.autoPauseEnabled" />
      </label>
      <label class="toggle-item">
        <span>AI 教练点评</span>
        <input type="checkbox" v-model="config.coachEnabled" />
      </label>
    </div>

    <!-- 周目标 -->
    <div class="card">
      <div class="card-head">
        <div class="icon orange"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M12 2l3 7h7l-5.5 4.5 2 7L12 16l-6.5 4.5 2-7L2 9h7z"/></svg></div>
        <h3>每周目标</h3>
      </div>
      <div class="item">
        <div class="lbl-row"><span>目标次数</span><span class="v">{{ config.weeklyGoal }}</span></div>
        <input type="range" v-model.number="config.weeklyGoal" min="10" max="300" step="10" />
      </div>
    </div>

    <div class="actions">
      <button class="btn primary" @click="saveAll">
        <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M16.7 5.3a1 1 0 0 1 0 1.4l-8 8a1 1 0 0 1-1.4 0l-4-4a1 1 0 1 1 1.4-1.4L8 12.6l7.3-7.3a1 1 0 0 1 1.4 0z"/></svg>
        保存设置
      </button>
      <button class="btn ghost" @click="resetAll">恢复默认</button>
    </div>
  </div>
</template>

<style scoped>
.page-head { margin-bottom: 16px; }
.page-head h2 { font-size: 26px; font-weight: 800; background: var(--grad-primary); -webkit-background-clip: text; -webkit-text-fill-color: transparent; background-clip: text; }
.page-head .sub { font-size: 12px; color: var(--text-2); margin-top: 4px; }

.card {
  padding: 16px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 16px;
  margin-bottom: 12px;
}
.card-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}
.icon {
  width: 32px; height: 32px;
  border-radius: 8px;
  display: flex; align-items: center; justify-content: center;
}
.icon svg { width: 18px; height: 18px; }
.icon.blue   { background: var(--cyan-dim);   color: var(--cyan); }
.icon.green  { background: var(--green-dim);  color: var(--green); }
.icon.purple { background: var(--purple-dim); color: var(--purple); }
.icon.orange { background: var(--orange-dim); color: var(--orange); }
.card-head h3 { font-size: 14px; font-weight: 700; }

.theme-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}
@media (min-width: 480px) { .theme-grid { grid-template-columns: repeat(6, 1fr); } }
.theme-option {
  text-align: center;
  cursor: pointer;
  padding: 8px;
  border-radius: 10px;
  border: 1.5px solid transparent;
  transition: all var(--transition);
}
.theme-option:hover { background: var(--bg-card-2); }
.theme-option.active { border-color: var(--cyan); background: var(--cyan-dim); }
.swatch {
  width: 100%;
  aspect-ratio: 1;
  border-radius: 10px;
  margin-bottom: 6px;
  border: 1px solid var(--border);
}
.theme-option span { font-size: 11px; color: var(--text-2); font-weight: 500; }
.theme-option.active span { color: var(--cyan); }

.item { margin-bottom: 12px; }
.item:last-child { margin-bottom: 0; }
.lbl-row {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  margin-bottom: 6px;
  color: var(--text-2);
}
.v { font-weight: 700; color: var(--cyan); }

.toggle-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  font-size: 13px;
  color: var(--text-2);
  cursor: pointer;
  border-bottom: 1px solid var(--border);
}
.toggle-item:last-child { border-bottom: none; }
.toggle-item input { accent-color: var(--cyan); transform: scale(1.2); }

.actions {
  display: flex;
  gap: 10px;
  margin-top: 20px;
}
.btn {
  flex: 1;
  padding: 14px;
  border-radius: 14px;
  font-size: 14px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  transition: all var(--transition);
}
.btn svg { width: 16px; height: 16px; }
.btn.primary { background: var(--grad-primary); color: #fff; }
.btn.ghost { background: var(--bg-card-2); color: var(--text-2); }
.btn:active { transform: scale(.98); }
</style>
