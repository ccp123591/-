<script setup>
import { ref, computed } from 'vue';
import { renderPoster, downloadPoster } from '@/modules/poster';
import { useAuthStore } from '@/stores/auth';
import RestTimer from './RestTimer.vue';
import CoachFeedback from './CoachFeedback.vue';

const props = defineProps({
  show: Boolean,
  session: Object
});
const emit = defineEmits(['close', 'retry']);
const auth = useAuthStore();
const rest = ref(null);

function fmtTime(s) {
  const m = Math.floor(s / 60);
  const r = s % 60;
  return `${String(m).padStart(2, '0')}:${String(r).padStart(2, '0')}`;
}

function share() {
  const s = props.session || {};
  const dataUrl = renderPoster(s, auth.user);
  downloadPoster(dataUrl, `FitCoach-${s.date || ''}.png`);
}

const stars = computed(() => {
  const score = props.session?.score ?? 0;
  if (score >= 90) return 5;
  if (score >= 75) return 4;
  if (score >= 60) return 3;
  if (score >= 40) return 2;
  return 1;
});

function close() {
  rest.value?.stop();
  emit('close');
}
</script>

<template>
  <transition name="fade">
    <div v-if="show" class="report-modal">
      <div class="backdrop" @click="close"></div>
      <div class="report-box">
        <div class="report-hero">
          <div class="celebration">
            <svg viewBox="0 0 48 48" fill="none">
              <circle cx="24" cy="24" r="22" stroke="url(#rgl)" stroke-width="2.5"/>
              <path d="M15 24l6 6 12-12" stroke="url(#rgl)" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
              <defs>
                <linearGradient id="rgl" x1="2" y1="2" x2="46" y2="46">
                  <stop stop-color="#00F0FF"/><stop offset="1" stop-color="#00E5A0"/>
                </linearGradient>
              </defs>
            </svg>
          </div>
          <h2>训练完成</h2>
          <p>太棒了，再接再厉！</p>
          <div class="star-row">
            <span v-for="i in 5" :key="i" :class="['star', i <= stars ? 'on' : '']">★</span>
          </div>
        </div>

        <div class="stat-summary">
          <div class="big-num">{{ session?.reps ?? 0 }}</div>
          <div class="big-lbl">次 · 用时 {{ fmtTime(session?.duration ?? 0) }}</div>
        </div>

        <div class="score-grid">
          <div class="score-item">
            <div class="sv">{{ session?.score ?? '-' }}</div>
            <div class="sl">综合</div>
          </div>
          <div class="score-item">
            <div class="sv">{{ session?.rhythmScore ?? '-' }}</div>
            <div class="sl">节奏</div>
          </div>
          <div class="score-item">
            <div class="sv">{{ session?.stabilityScore ?? '-' }}</div>
            <div class="sl">稳定</div>
          </div>
          <div class="score-item">
            <div class="sv">{{ session?.depthScore ?? '-' }}</div>
            <div class="sl">深度</div>
          </div>
          <div class="score-item">
            <div class="sv">{{ session?.symmetryScore ?? '-' }}</div>
            <div class="sl">对称</div>
          </div>
          <div class="score-item">
            <div class="sv">{{ session?.completionScore ?? '-' }}</div>
            <div class="sl">完成</div>
          </div>
        </div>

        <CoachFeedback :session="session" />

        <div class="btn-row">
          <button class="btn primary" @click="close">完成</button>
          <button class="btn secondary" @click="share">
            <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"><path d="M10 3v10m0 0l-3-3m3 3l3-3 M3 14v2a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-2"/></svg>
            分享海报
          </button>
        </div>

        <RestTimer ref="rest" />
      </div>
    </div>
  </transition>
</template>

<style scoped>
.report-modal {
  position: fixed; inset: 0;
  z-index: 9000;
  display: flex; align-items: flex-end; justify-content: center;
  padding: 0;
}
@media (min-width: 768px) {
  .report-modal { align-items: center; padding: 20px; }
}
.backdrop { position: absolute; inset: 0; background: rgba(0, 0, 0, .6); backdrop-filter: blur(8px); }
.report-box {
  position: relative;
  width: 100%;
  max-width: 480px;
  max-height: 90vh;
  overflow-y: auto;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 24px 24px 0 0;
  padding: 24px 20px 32px;
  animation: slideUp .4s cubic-bezier(.2, 1.1, .3, 1);
}
@media (min-width: 768px) {
  .report-box { border-radius: 24px; padding: 28px; }
}
@keyframes slideUp {
  from { transform: translateY(40px); opacity: 0; }
  to   { transform: translateY(0); opacity: 1; }
}

.report-hero { text-align: center; margin-bottom: 20px; }
.celebration { width: 60px; height: 60px; margin: 0 auto 12px; }
.celebration svg { width: 100%; height: 100%; }
.report-hero h2 { font-size: 22px; font-weight: 800; margin-bottom: 4px; }
.report-hero p { font-size: 13px; color: var(--text-2); }
.star-row { margin-top: 10px; display: flex; justify-content: center; gap: 4px; }
.star { font-size: 18px; color: var(--text-3); }
.star.on { color: var(--orange); filter: drop-shadow(0 0 6px var(--orange)); }

.stat-summary { text-align: center; margin: 20px 0; }
.big-num {
  font-size: 56px;
  font-weight: 900;
  line-height: 1;
  background: var(--grad-primary);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}
.big-lbl { font-size: 12px; color: var(--text-3); margin-top: 4px; }

.score-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin-bottom: 16px;
}
.score-item {
  background: var(--bg-card-2);
  border-radius: 12px;
  padding: 10px;
  text-align: center;
}
.sv { font-size: 20px; font-weight: 800; color: var(--cyan); }
.sl { font-size: 10px; color: var(--text-3); margin-top: 2px; }

.btn-row { display: flex; gap: 10px; margin-top: 18px; }
.btn {
  flex: 1;
  padding: 12px;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  transition: transform var(--transition);
}
.btn svg { width: 15px; height: 15px; }
.btn:active { transform: scale(.96); }
.primary { background: var(--grad-primary); color: #fff; }
.secondary { background: var(--bg-card-2); color: var(--text-2); }

.fade-enter-active, .fade-leave-active { transition: opacity .3s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
