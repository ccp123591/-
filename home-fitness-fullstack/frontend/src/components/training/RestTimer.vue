<script setup>
import { ref, onUnmounted } from 'vue';
import { voice } from '@/modules/voice';
import { useAppStore } from '@/stores/app';

const app = useAppStore();
const presets = [30, 60, 90];
const active = ref(null);
const secondsLeft = ref(0);
let timer = null;

function start(sec) {
  stop();
  active.value = sec;
  secondsLeft.value = sec;
  timer = setInterval(() => {
    secondsLeft.value--;
    if (secondsLeft.value <= 0) {
      stop();
      voice.speak('休息结束，可以继续训练了', 'high');
      app.showToast('休息结束！', 'success');
    }
  }, 1000);
}
function stop() {
  if (timer) { clearInterval(timer); timer = null; }
  active.value = null;
  secondsLeft.value = 0;
}

onUnmounted(stop);
defineExpose({ stop });
</script>

<template>
  <div class="rest-section">
    <div class="lbl">休息一下</div>
    <div class="btn-row">
      <button
        v-for="s in presets"
        :key="s"
        :class="['btn', { active: active === s }]"
        @click="start(s)"
      >{{ s }}秒</button>
    </div>
    <transition name="fade">
      <div v-if="active" class="countdown">
        <div class="num">{{ secondsLeft }}</div>
        <div class="sub">休息中...</div>
      </div>
    </transition>
  </div>
</template>

<style scoped>
.rest-section {
  padding: 12px 0;
  border-top: 1px solid var(--border);
  margin-top: 12px;
}
.lbl { font-size: 12px; color: var(--text-3); text-transform: uppercase; letter-spacing: .08em; margin-bottom: 10px; }

.btn-row { display: flex; gap: 8px; }
.btn {
  flex: 1;
  padding: 10px;
  border-radius: 10px;
  background: var(--bg-card-2);
  color: var(--text-2);
  font-size: 13px;
  font-weight: 600;
  transition: all var(--transition);
}
.btn:hover { background: var(--bg-elevated); color: var(--text); }
.btn.active {
  background: var(--cyan-dim);
  color: var(--cyan);
  border: 1px solid var(--cyan);
}

.countdown {
  margin-top: 12px;
  text-align: center;
  padding: 14px;
  border-radius: 14px;
  background: var(--bg-card-2);
}
.num {
  font-size: 44px;
  font-weight: 900;
  background: var(--grad-primary);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}
.sub { font-size: 12px; color: var(--text-3); margin-top: 4px; }

.fade-enter-active, .fade-leave-active { transition: opacity .2s, transform .3s; }
.fade-enter-from, .fade-leave-to { opacity: 0; transform: scale(.9); }
</style>
