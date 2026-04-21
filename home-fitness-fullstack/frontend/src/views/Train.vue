<script setup>
import { ref, onMounted, onBeforeUnmount, computed } from 'vue';
import { useConfigStore } from '@/stores/config';
import { useAuthStore } from '@/stores/auth';
import { useAppStore } from '@/stores/app';
import { useTrainingStore } from '@/stores/training';
import { poseDetector } from '@/modules/pose';
import { exercise, ACTION_DEFS } from '@/modules/exercise';
import { voice } from '@/modules/voice';
import { storage } from '@/modules/storage';
import { sessionApi } from '@/api/session';

import HUD from '@/components/training/HUD.vue';
import ActionCard from '@/components/training/ActionCard.vue';
import TargetStepper from '@/components/training/TargetStepper.vue';
import CountdownOverlay from '@/components/training/CountdownOverlay.vue';
import ReportModal from '@/components/training/ReportModal.vue';

const config = useConfigStore();
const auth = useAuthStore();
const app = useAppStore();
const train = useTrainingStore();

// DOM refs
const videoEl = ref(null);
const canvasEl = ref(null);
const countdownRef = ref(null);

// 动作列表（图标）
const actionList = [
  { code: 'squat',   label: ACTION_DEFS.squat.label,   desc: ACTION_DEFS.squat.desc,
    icon: 'M24 14v12 M14 22h20 M20 16v10 M28 16v10 M20 38l4-10 4 10 M24 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6' },
  { code: 'stretch', label: ACTION_DEFS.stretch.label, desc: ACTION_DEFS.stretch.desc,
    icon: 'M24 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6 M24 15v8 M16 22l8 2 8-2 M18 38l6-14 6 14' },
  { code: 'pushup',  label: ACTION_DEFS.pushup.label,  desc: ACTION_DEFS.pushup.desc,
    icon: 'M12 24a4 4 0 1 0 0 8 4 4 0 0 0 0-8 M16 28h16 M32 26v4 M36 28a4 4 0 1 1 0 0' },
  { code: 'lunge',   label: ACTION_DEFS.lunge.label,   desc: ACTION_DEFS.lunge.desc,
    icon: 'M24 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6 M24 15v8 M24 23l-6 15 M24 23l6 10 4-4' },
  { code: 'bridge',  label: ACTION_DEFS.bridge.label,  desc: ACTION_DEFS.bridge.desc,
    icon: 'M8 34h32 M12 34l6-8 12 0 6 8 M18 26v-6 M30 26v-6 M24 16a3 3 0 1 0 0 6 3 3 0 0 0 0-6' },
  { code: 'plank',   label: ACTION_DEFS.plank.label,   desc: ACTION_DEFS.plank.desc,
    icon: 'M8 28h32 M10 26l4-6 M34 26l4-6 M14 20h20 M14 18v-3 M34 18v-3 M24 12a2 2 0 1 0 0 4 2 2 0 0 0 0-4' },
  { code: 'jumpingJack', label: ACTION_DEFS.jumpingJack.label, desc: ACTION_DEFS.jumpingJack.desc,
    icon: 'M24 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6 M24 15v10 M12 20l12 5 12-5 M14 38l10-13 10 13' }
];

// 本地状态
const poseInit = ref(false);
const showReport = ref(false);
const lastSession = ref(null);
const stats = ref({ today: 0, week: 0, streak: 0 });
const lostPoseCount = ref(0);

let timerInterval = null;
let startTime = 0;
let lastCorrectionTime = 0;
const CORRECTION_COOLDOWN = 3000;
const ENCOURAGE_INTERVAL = 5;
const AUTO_PAUSE_FRAMES = 150;

const actionRef = () => train.action;

/* ========== 姿态结果回调 ========== */
function onPoseResult(results) {
  if (!train.isTraining || train.isPaused) return;
  const landmarks = results.poseLandmarks;
  const res = exercise.update(landmarks);

  train.reps = res.reps;
  train.currentAngle = res.angle;

  // 自动暂停检测
  if (res.event === 'lost') {
    lostPoseCount.value++;
    if (config.autoPauseEnabled && lostPoseCount.value > AUTO_PAUSE_FRAMES && !train.isPaused) {
      togglePause();
      app.showToast('未检测到人体 · 已自动暂停', 'warning');
    }
    return;
  } else {
    lostPoseCount.value = 0;
  }

  if (res.event === 'count') {
    voice.countVoice(res.reps);
    if (res.reps % ENCOURAGE_INTERVAL === 0 && res.reps > 0) {
      setTimeout(() => voice.encourage(), 600);
    }
    if (res.reps >= res.targetReps) {
      stopTraining();
      return;
    }
  } else if (res.event === 'correction') {
    const now = Date.now();
    if (now - lastCorrectionTime > CORRECTION_COOLDOWN) {
      voice.correct(res.message);
      lastCorrectionTime = now;
    }
  }

  if (res.message) train.statusText = res.message;
}

async function ensurePose() {
  if (poseInit.value) return;
  train.statusText = '正在加载姿态模型...';
  await poseDetector.init(videoEl.value, canvasEl.value, onPoseResult, actionRef);
  poseInit.value = true;
  train.statusText = '模型加载完成';
}

/* ========== 开始训练 ========== */
async function startTraining() {
  voice.setEnabled(config.voiceEnabled);
  voice.setRate(config.ttsRate);

  try {
    await ensurePose();
  } catch (e) {
    app.showToast('姿态模型加载失败，请检查网络', 'error');
    return;
  }

  poseDetector.start();
  await countdownRef.value?.run(3);

  exercise.init(train.action, config.snapshot(), train.targetReps);
  train.reset();
  train.isTraining = true;
  train.isPaused = false;
  startTime = Date.now();
  lostPoseCount.value = 0;

  timerInterval = setInterval(() => {
    if (!train.isPaused) {
      train.elapsedMs = Date.now() - startTime;
    }
  }, 500);

  if (config.metronomeEnabled) voice.startMetronome(config.bpm);
  voice.speak('训练开始', 'high');
}

function togglePause() {
  if (!train.isTraining) return;
  train.isPaused = !train.isPaused;
  if (train.isPaused) {
    poseDetector.pause();
    voice.stopMetronome();
    voice.speak('已暂停');
  } else {
    poseDetector.resume();
    startTime = Date.now() - train.elapsedMs;
    if (config.metronomeEnabled) voice.startMetronome(config.bpm);
    voice.speak('继续训练');
  }
}

async function stopTraining() {
  if (!train.isTraining) return;
  train.isTraining = false;
  train.isPaused = false;
  clearInterval(timerInterval);
  poseDetector.stop();
  voice.stopAll();

  const result = exercise.getResult(config.bpm);
  const duration = Math.round(train.elapsedMs / 1000);
  const actionDef = ACTION_DEFS[train.action];

  voice.setEnabled(config.voiceEnabled);
  voice.finish(result.reps);

  train.score = result.score;
  train.rhythmScore = result.rhythmScore;
  train.stabilityScore = result.stabilityScore;
  train.depthScore = result.depthScore;
  train.symmetryScore = result.symmetryScore;

  const session = {
    date: new Date().toISOString().slice(0, 19).replace('T', ' '),
    action: train.action,
    actionLabel: actionDef?.label || train.action,
    reps: result.reps,
    duration,
    score: result.score,
    rhythmScore: result.rhythmScore,
    stabilityScore: result.stabilityScore,
    depthScore: result.depthScore,
    symmetryScore: result.symmetryScore,
    completionScore: result.completionScore,
    targetReps: train.targetReps
  };

  // 本地保存
  try {
    const localId = await storage.saveSession(session);
    session.localId = localId;
  } catch (_) { /* ignore */ }

  // 上传后端（失败也继续）
  if (auth.isLogin) {
    try {
      const remote = await sessionApi.create(session);
      if (remote?.id) {
        session.remoteId = remote.id;
        storage.markSynced(session.localId, remote.id);
      }
    } catch (_) { /* 离线留存 */ }
  }

  lastSession.value = session;
  showReport.value = true;
  refreshStats();
}

/* ========== 统计概览 ========== */
async function refreshStats() {
  const sessions = await storage.getAllSessions();
  const now = new Date();
  const todayStr = now.toISOString().slice(0, 10);
  stats.value.today = sessions.filter(s => s.date?.startsWith(todayStr))
    .reduce((sum, s) => sum + (s.reps || 0), 0);

  const dow = now.getDay() || 7;
  const weekStart = new Date(now);
  weekStart.setDate(now.getDate() - dow + 1);
  weekStart.setHours(0, 0, 0, 0);
  stats.value.week = sessions.filter(s => new Date(s.date) >= weekStart)
    .reduce((sum, s) => sum + (s.reps || 0), 0);

  const days = [...new Set(sessions.map(s => s.date?.slice(0, 10)))].sort().reverse();
  let streak = 0;
  const check = new Date(now);
  check.setHours(0, 0, 0, 0);
  for (const d of days) {
    const ds = check.toISOString().slice(0, 10);
    if (d === ds) { streak++; check.setDate(check.getDate() - 1); }
    else if (d < ds) break;
  }
  stats.value.streak = streak;
}

const timeStr = computed(() => {
  const s = Math.floor(train.elapsedMs / 1000);
  const m = Math.floor(s / 60);
  return `${String(m).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
});

/* ========== 生命周期 ========== */
onMounted(() => {
  refreshStats();
});
onBeforeUnmount(() => {
  if (train.isTraining) stopTraining();
  voice.stopAll();
});
</script>

<template>
  <div class="page-wrap train-page">
    <!-- 概览 Header -->
    <div class="brand-row">
      <div class="brand-left">
        <div class="brand-logo-ico">
          <svg viewBox="0 0 32 32" fill="none">
            <defs><linearGradient id="trlg" x1="4" y1="4" x2="28" y2="28">
              <stop stop-color="#00F0FF"/><stop offset="1" stop-color="#7C6AFF"/>
            </linearGradient></defs>
            <circle cx="16" cy="10" r="2.5" stroke="url(#trlg)" stroke-width="1.8"/>
            <path d="M12 22l2-7 2 3 2-3 2 7" stroke="url(#trlg)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </div>
        <div>
          <div class="brand-n text-gradient">FitCoach</div>
          <div class="brand-s">AI 居家健身</div>
        </div>
      </div>
    </div>

    <div class="stats-overview">
      <div class="stat-mini">
        <div class="stat-ico"><svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"><path d="M10 3v7l4 4"/><circle cx="10" cy="10" r="7"/></svg></div>
        <div class="stat-v">{{ stats.today }}</div>
        <div class="stat-l">今日次数</div>
      </div>
      <div class="stat-mini">
        <div class="stat-ico"><svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"><rect x="3" y="4" width="14" height="13" rx="2"/><path d="M3 8h14 M7 2v4 M13 2v4"/></svg></div>
        <div class="stat-v">{{ stats.week }}</div>
        <div class="stat-l">本周累计</div>
      </div>
      <div class="stat-mini">
        <div class="stat-ico"><svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"><path d="M10 2l2.5 5 5.5.8-4 3.9.9 5.3-4.9-2.6-4.9 2.6.9-5.3-4-3.9 5.5-.8z"/></svg></div>
        <div class="stat-v">{{ stats.streak }}</div>
        <div class="stat-l">连续天数</div>
      </div>
    </div>

    <!-- 两列布局 -->
    <div class="two-col">
      <!-- 视频区 -->
      <div class="video-wrap">
        <div class="video-container">
          <video ref="videoEl" autoplay playsinline muted></video>
          <canvas ref="canvasEl"></canvas>

          <div v-if="!train.isTraining" class="placeholder">
            <div class="pulse-icon">
              <div class="pulse-ring"></div>
              <div class="pulse-ring delay"></div>
              <svg viewBox="0 0 80 80" fill="none" stroke="currentColor" stroke-width="1.2">
                <circle cx="40" cy="16" r="7"/>
                <path d="M40 24v14"/>
                <path d="M28 32l12 6 12-6"/>
                <path d="M30 60l10-22 10 22"/>
              </svg>
            </div>
            <p>点击下方按钮开始训练</p>
            <span>将自动开启摄像头进行姿态识别</span>
          </div>

          <HUD
            :reps="train.reps"
            :score="train.score || '-'"
            :time="timeStr"
            :angle="train.currentAngle"
          />

          <CountdownOverlay ref="countdownRef" />

          <transition name="fade">
            <div v-if="train.statusText" class="status-msg">{{ train.statusText }}</div>
          </transition>
        </div>
      </div>

      <!-- 控制面板 -->
      <div class="control-panel">
        <div class="sec-label">选择动作</div>
        <ActionCard :list="actionList" :selected="train.action" @select="train.action = $event" />

        <TargetStepper v-model="train.targetReps" />

        <div class="btn-row">
          <button v-if="!train.isTraining" class="btn-start" @click="startTraining">
            <span class="glow"></span>
            <svg viewBox="0 0 24 24" fill="currentColor"><polygon points="5,3 19,12 5,21"/></svg>
            <span>开始训练</span>
          </button>
          <div v-else class="running-btns">
            <button class="btn-ctrl pause" @click="togglePause">
              <svg v-if="!train.isPaused" viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="4" width="4" height="16" rx="1"/><rect x="14" y="4" width="4" height="16" rx="1"/></svg>
              <svg v-else viewBox="0 0 24 24" fill="currentColor"><polygon points="5,3 19,12 5,21"/></svg>
              {{ train.isPaused ? '继续' : '暂停' }}
            </button>
            <button class="btn-ctrl stop" @click="stopTraining">
              <svg viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="6" width="12" height="12" rx="2"/></svg>
              结束
            </button>
          </div>
        </div>

        <div class="toggle-row">
          <label class="toggle">
            <input type="checkbox" v-model="config.voiceEnabled" />
            <div class="track"><div class="thumb"></div></div>
            <span>语音提示</span>
          </label>
          <label class="toggle">
            <input type="checkbox" v-model="config.metronomeEnabled" />
            <div class="track"><div class="thumb"></div></div>
            <span>节拍器</span>
          </label>
        </div>
      </div>
    </div>

    <ReportModal :show="showReport" :session="lastSession" @close="showReport = false" />
  </div>
</template>

<style scoped>
.train-page { padding-top: 12px; padding-bottom: 20px; }

.brand-row { margin-bottom: 8px; }
.brand-left { display: flex; align-items: center; gap: 10px; }
.brand-logo-ico { width: 32px; height: 32px; }
.brand-n { font-size: 18px; font-weight: 800; letter-spacing: -.3px; }
.brand-s { font-size: 11px; color: var(--text-3); letter-spacing: .06em; }

.stats-overview {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-bottom: 14px;
}
.stat-mini {
  padding: 12px 10px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 14px;
  text-align: center;
  position: relative;
  overflow: hidden;
}
.stat-mini::before {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 2px;
  background: var(--grad-primary);
  opacity: .5;
}
.stat-ico {
  width: 28px; height: 28px;
  margin: 0 auto 4px;
  color: var(--cyan);
  display: flex;
  align-items: center;
  justify-content: center;
}
.stat-ico svg { width: 18px; height: 18px; }
.stat-v {
  font-size: 22px;
  font-weight: 800;
  background: var(--grad-primary);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}
.stat-l { font-size: 10px; color: var(--text-3); margin-top: 2px; }

.video-wrap { min-width: 0; display: flex; }
.video-container {
  position: relative;
  width: 100%;
  aspect-ratio: 4 / 3;
  background: #08080d;
  border-radius: 16px;
  overflow: hidden;
  border: 1px solid var(--border);
  box-shadow: var(--shadow);
}
.video-container video,
.video-container canvas {
  position: absolute;
  inset: 0;
  width: 100%; height: 100%;
  object-fit: cover;
}
.placeholder {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  background: radial-gradient(ellipse at center, rgba(0, 240, 255, .04) 0%, transparent 70%);
  z-index: 3;
}
.pulse-icon { position: relative; width: 72px; height: 72px; display: flex; align-items: center; justify-content: center; }
.pulse-icon svg { width: 48px; height: 48px; color: var(--text-3); z-index: 1; position: relative; }
.pulse-ring {
  position: absolute; inset: 0;
  border-radius: 50%;
  border: 1px solid rgba(0, 240, 255, .2);
  animation: pulseRing 3s ease-out infinite;
}
.pulse-ring.delay { animation-delay: 1.5s; }
.placeholder p { font-size: 14px; color: var(--text-2); font-weight: 500; }
.placeholder span { font-size: 11px; color: var(--text-3); }

.status-msg {
  position: absolute;
  bottom: 14px; left: 50%;
  transform: translateX(-50%);
  padding: 6px 14px;
  background: rgba(0, 0, 0, .65);
  backdrop-filter: blur(12px);
  border-radius: 100px;
  color: #fff;
  font-size: 12px;
  font-weight: 500;
  z-index: 7;
  white-space: nowrap;
}

.control-panel {
  padding: 14px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}
.sec-label {
  font-size: 11px;
  color: var(--text-3);
  letter-spacing: .08em;
  text-transform: uppercase;
}

.btn-start {
  width: 100%;
  height: 52px;
  border-radius: 14px;
  background: var(--grad-primary);
  color: #fff;
  font-size: 15px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  box-shadow: 0 8px 24px rgba(0, 240, 255, .3);
  position: relative;
  overflow: hidden;
  transition: transform var(--transition);
}
.btn-start svg { width: 20px; height: 20px; }
.btn-start:active { transform: scale(.98); }
.glow {
  position: absolute; inset: 0;
  background: radial-gradient(circle at center, rgba(255, 255, 255, .2), transparent 60%);
  opacity: 0;
  transition: opacity var(--transition);
}
.btn-start:hover .glow { opacity: 1; }

.running-btns { display: flex; gap: 10px; }
.btn-ctrl {
  flex: 1;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  transition: all var(--transition);
}
.btn-ctrl svg { width: 16px; height: 16px; }
.btn-ctrl.pause { background: var(--orange-dim); color: var(--orange); border: 1px solid rgba(255, 159, 67, .3); }
.btn-ctrl.stop  { background: var(--red-dim); color: var(--red); border: 1px solid rgba(255, 90, 90, .3); }
.btn-ctrl:active { transform: scale(.97); }

.toggle-row {
  padding-top: 12px;
  border-top: 1px solid var(--border);
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text-2);
  cursor: pointer;
}
.toggle input { display: none; }
.track {
  width: 36px; height: 20px;
  background: var(--bg-elevated);
  border-radius: 100px;
  position: relative;
  transition: background var(--transition);
}
.thumb {
  width: 16px; height: 16px;
  background: #fff;
  border-radius: 50%;
  position: absolute;
  top: 2px; left: 2px;
  transition: transform var(--transition), background var(--transition);
}
.toggle input:checked + .track { background: var(--cyan); }
.toggle input:checked + .track .thumb { transform: translateX(16px); }

.fade-enter-active, .fade-leave-active { transition: opacity .3s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

/* Tablet+: stretch video to match right panel height */
@media (min-width: 768px) {
  .video-wrap { align-self: stretch; }
  .video-container {
    aspect-ratio: auto;
    height: 100%;
    min-height: 360px;
  }
}
</style>
