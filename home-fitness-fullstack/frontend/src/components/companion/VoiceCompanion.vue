<script setup>
/**
 * VoiceCompanion — 畅聊模式：语音 → 语音的免按手对话（可选视频：让 AI 看得见你）
 *
 * - 中央一个 SVG 形象（脸 + 眼 + 嘴），四态光晕：idle / listening / thinking / speaking
 * - 摄像头开关：开了之后你一开口就抓一帧给 JoyAI-VL 出场景摘要，随本轮 chat 注入
 * - 下面实时转写 + 最近对话
 * - 大按钮启停
 */
import { ref, computed, watch, nextTick, onBeforeUnmount } from 'vue';
import { useVoiceChat } from '@/composables/useVoiceChat';
import { coachApi } from '@/api/coach';
import { ttsApi } from '@/api/tts';
import { useConfigStore } from '@/stores/config';
import { followupsFor } from '@/modules/followups';

const config = useConfigStore();

/* ========== 视频（可选）：摄像头 + 抓帧 → 场景摘要 ========== */
const CAM_PREF_KEY = 'vc-cam-on';
const camOn = ref(false);
const camError = ref(null);
const videoEl = ref(null);
let camStream = null;

async function openCamera() {
  camError.value = null;
  try {
    camStream = await navigator.mediaDevices.getUserMedia({
      video: { width: { ideal: 640 }, height: { ideal: 480 }, facingMode: 'user' },
      audio: false
    });
    camOn.value = true;
    localStorage.setItem(CAM_PREF_KEY, '1');
    await nextTick();               // videoEl 由 v-if 渲染，等 DOM 更新后再绑流
    if (videoEl.value && camStream) videoEl.value.srcObject = camStream;
  } catch (_) {
    camError.value = '摄像头打开失败，请检查权限';
    camOn.value = false;
  }
}

function closeCamera() {
  if (camStream) {
    camStream.getTracks().forEach(t => t.stop());
    camStream = null;
  }
  camOn.value = false;
  localStorage.setItem(CAM_PREF_KEY, '0');
}

function toggleCamera() {
  if (camOn.value) closeCamera();
  else openCamera();
}

/** 抓一帧（降到 480 宽 JPEG）；摄像头没开/没就绪返回 null。 */
function captureFrame() {
  const v = videoEl.value;
  if (!camOn.value || !v || !v.videoWidth) return Promise.resolve(null);
  const w = 480;
  const h = Math.round(v.videoHeight * (w / v.videoWidth));
  const canvas = document.createElement('canvas');
  canvas.width = w;
  canvas.height = h;
  canvas.getContext('2d').drawImage(v, 0, 0, w, h);
  return new Promise(res => canvas.toBlob(res, 'image/jpeg', 0.8));
}

/** 给 useVoiceChat 的钩子：看一眼画面 → 场景摘要文本（不可用返回 null）。 */
async function getSceneContext() {
  try {
    const frame = await captureFrame();
    if (!frame) return null;
    const r = await coachApi.scene([frame]);
    return r?.summary ? r.summary : null;   // 空 = 视觉不可用，跳过注入
  } catch (_) {
    return null;
  }
}

onBeforeUnmount(() => closeCamera());

/* ========== 语音回合状态机 ========== */
const chat = (msg, history, sceneSummary) => coachApi.chat(msg, history, sceneSummary);
const speak = (text) => ttsApi.speak(text);

const {
  state, supported, interim, transcripts, error,
  start, stop, clearTranscripts, say
} = useVoiceChat({ chat, speak, getSceneContext });

// 上次开着摄像头的话，开始畅聊时自动恢复
watch(() => state.value, (v, prev) => {
  if (v === 'listening' && prev === 'idle'
      && !camOn.value && localStorage.getItem(CAM_PREF_KEY) === '1') {
    openCamera();
  }
});

const companionName = computed(() => config.companionName || '小柯');

const statusLabel = computed(() => {
  if (error.value)               return error.value;
  if (state.value === 'idle')    return supported ? '点开始，我们就开聊' : '请用 Chrome / Edge 打开';
  if (state.value === 'listening') return interim.value ? '在听你说…' : '听你的，请说';
  if (state.value === 'thinking')  return '想想看…';
  if (state.value === 'speaking')  return '正在回你…';
  return '';
});

const lastAssistant = computed(() => {
  for (let i = transcripts.value.length - 1; i >= 0; i--) {
    if (transcripts.value[i].role === 'assistant') return transcripts.value[i];
  }
  return null;
});

const visibleTurns = computed(() => transcripts.value.slice(-4));

// AI 回复后的情境化快捷追问（忙着想/说时不打扰）
const followups = computed(() => {
  if (state.value !== 'idle' && state.value !== 'listening') return [];
  const la = lastAssistant.value;
  return la ? followupsFor(la.content) : [];
});

function tapChip(p) { say(p); }

function toggle() {
  if (state.value === 'idle' || state.value === 'error') start();
  else stop();
}

function fmtTime(t) {
  if (!t) return '';
  const d = new Date(t);
  return `${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`;
}

// 切走时自动停（父组件 unmount 时也会触发 onBeforeUnmount）
defineExpose({ stop, start });
</script>

<template>
  <div :class="['voice-room', `s-${state}`]">
    <!-- ====== 形象 ====== -->
    <div class="stage">
      <div class="rings">
        <span class="ring r1"></span>
        <span class="ring r2"></span>
        <span class="ring r3"></span>
      </div>

      <svg class="avatar" viewBox="0 0 160 160" aria-hidden="true">
        <defs>
          <radialGradient id="vc-face" cx="50%" cy="40%" r="65%">
            <stop offset="0%"  stop-color="#ffd0bb"/>
            <stop offset="55%" stop-color="#e98461"/>
            <stop offset="100%" stop-color="#b5512e"/>
          </radialGradient>
          <linearGradient id="vc-cheek" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stop-color="#ffb19a" stop-opacity=".0"/>
            <stop offset="100%" stop-color="#ff7e5a" stop-opacity=".55"/>
          </linearGradient>
        </defs>

        <!-- 脸 -->
        <circle cx="80" cy="80" r="62" fill="url(#vc-face)"/>

        <!-- 头顶小天线 / 触角，朋友感 -->
        <g class="antennae" stroke="#b5512e" stroke-width="2.4" stroke-linecap="round" fill="none">
          <path d="M62 22 Q60 12 66 8"/>
          <path d="M98 22 Q100 12 94 8"/>
          <circle cx="66" cy="8"  r="2.4" fill="#ffe2c8" stroke="none"/>
          <circle cx="94" cy="8"  r="2.4" fill="#ffe2c8" stroke="none"/>
        </g>

        <!-- 高光 -->
        <ellipse cx="58" cy="48" rx="22" ry="12" fill="#fff" opacity=".25"/>

        <!-- 腮红 -->
        <circle cx="52" cy="92"  r="9" fill="url(#vc-cheek)"/>
        <circle cx="108" cy="92" r="9" fill="url(#vc-cheek)"/>

        <!-- 眼睛 -->
        <g class="eyes">
          <ellipse class="eye left"  cx="64" cy="76" rx="5.6" ry="7"/>
          <ellipse class="eye right" cx="96" cy="76" rx="5.6" ry="7"/>
          <!-- 眼神高光 -->
          <circle class="glint l" cx="66" cy="73" r="1.6" fill="#fff"/>
          <circle class="glint r" cx="98" cy="73" r="1.6" fill="#fff"/>
          <!-- 思考时眼睛会变成 ◓◓（用 mask 实现太重，这里用一个 path 盖住下半） -->
          <g class="think-eyelid" opacity="0">
            <path d="M55 76 Q64 80 73 76 L73 84 L55 84 Z" fill="#7a2f17"/>
            <path d="M87 76 Q96 80 105 76 L105 84 L87 84 Z" fill="#7a2f17"/>
          </g>
        </g>

        <!-- 嘴 -->
        <g class="mouth">
          <!-- idle/listening 的微笑 -->
          <path class="smile" d="M64 108 Q80 120 96 108" stroke="#7a2f17" stroke-width="3.2"
                stroke-linecap="round" fill="none"/>
          <!-- speaking 时显示的张嘴 O -->
          <ellipse class="oh" cx="80" cy="112" rx="9" ry="6.5" fill="#5a1f0c"/>
          <ellipse class="oh-tongue" cx="80" cy="115" rx="6" ry="2.6" fill="#ff7e7e" opacity=".85"/>
        </g>
      </svg>

      <!-- 麦克风状态点（小） -->
      <div class="mic-dot" :class="state">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
             stroke-linecap="round" stroke-linejoin="round">
          <rect x="9" y="3" width="6" height="12" rx="3"/>
          <path d="M5 11a7 7 0 0 0 14 0 M12 18v3 M8 21h8"/>
        </svg>
      </div>

      <!-- 摄像头画中画（开着 = AI 看得见你） -->
      <div class="cam-pip" v-if="camOn">
        <video ref="videoEl" autoplay muted playsinline></video>
        <span class="cam-eye" :title="`${companionName}能看到画面`">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
               stroke-linecap="round" stroke-linejoin="round">
            <path d="M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6-10-6-10-6z"/>
            <circle cx="12" cy="12" r="2.6"/>
          </svg>
        </span>
      </div>
    </div>

    <!-- ====== 状态行 ====== -->
    <div class="status">
      <span class="dot-pulse" v-if="state === 'listening'"></span>
      <span class="dot-pulse spin" v-else-if="state === 'thinking'"></span>
      <span class="dot-pulse wave" v-else-if="state === 'speaking'"></span>
      <span class="dot-pulse idle" v-else></span>
      <span class="label">{{ statusLabel }}</span>
    </div>

    <!-- ====== 实时转写气泡（你正在说什么） ====== -->
    <div class="interim" v-if="state === 'listening' && interim">
      <span class="quote">"</span>{{ interim }}<span class="quote">"</span>
    </div>

    <!-- ====== 最近两三轮对话（紧凑） ====== -->
    <div class="turns" v-if="visibleTurns.length">
      <div v-for="(t, i) in visibleTurns"
           :key="i"
           :class="['turn', t.role]">
        <div class="bubble">
          <span>{{ t.content }}</span>
          <div class="meta">
            <span>{{ t.role === 'user' ? '你' : companionName }}</span>
            <span>·</span>
            <span>{{ fmtTime(t.time) }}</span>
          </div>
          <ul v-if="t.recalled && t.recalled.length" class="recalled">
            <li v-for="(r, k) in t.recalled" :key="k">想起 · {{ r }}</li>
          </ul>
        </div>
      </div>
    </div>

    <!-- ====== 情境化快捷追问（点一下直接发，不必开口） ====== -->
    <div class="vc-quick" v-if="followups.length">
      <button v-for="p in followups" :key="p" class="vc-chip" @click="tapChip(p)">{{ p }}</button>
    </div>

    <!-- ====== 操作按钮 ====== -->
    <div class="ctrl">
      <button
        class="big"
        :class="{ on: state !== 'idle' && state !== 'error' }"
        :disabled="!supported && state !== 'error'"
        @click="toggle">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"
             stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <template v-if="state === 'idle' || state === 'error'">
            <rect x="9" y="3" width="6" height="12" rx="3"/>
            <path d="M5 11a7 7 0 0 0 14 0 M12 18v3 M8 21h8"/>
          </template>
          <template v-else>
            <rect x="6" y="6" width="12" height="12" rx="2"/>
          </template>
        </svg>
        <span>{{ (state === 'idle' || state === 'error') ? '开始畅聊' : '结束畅聊' }}</span>
      </button>

      <button class="ghost cam-toggle" :class="{ on: camOn }" @click="toggleCamera">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
             stroke-linecap="round" stroke-linejoin="round">
          <path d="M23 7l-7 5 7 5V7z"/>
          <rect x="1" y="5" width="15" height="14" rx="2" ry="2"/>
        </svg>
        {{ camOn ? '关掉画面' : `让${companionName}看到你` }}
      </button>

      <button class="ghost" v-if="visibleTurns.length" @click="clearTranscripts">
        清空对话
      </button>
    </div>

    <!-- 小贴士 -->
    <p class="tip" v-if="state === 'idle' && supported && !visibleTurns.length">
      免按手 · 我说完会自动接着听你说
    </p>
    <p class="tip warn" v-else-if="!supported">
      你当前的浏览器不支持原生语音识别，建议用 Chrome / Edge。
    </p>
    <p class="tip warn" v-if="camError">{{ camError }}</p>
  </div>
</template>

<style scoped>
.voice-room {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 10px;
  padding: 4px 2px 2px;
}

/* ============ 形象 ============ */
.stage {
  position: relative;
  width: 100%;
  display: flex;
  justify-content: center;
  padding: 8px 0 4px;
}
.avatar {
  width: 132px;
  height: 132px;
  display: block;
  filter: drop-shadow(0 10px 18px rgba(217, 119, 87, .35));
  position: relative;
  z-index: 2;
  transition: transform .35s cubic-bezier(.2, 1.1, .3, 1);
}

/* 状态色：通过 voice-room 上的 s-* 切 */
.s-idle .avatar      { animation: vc-breath 4.2s ease-in-out infinite; }
.s-listening .avatar { animation: vc-breath 2.2s ease-in-out infinite; transform: translateY(-2px); }
.s-thinking .avatar  { animation: vc-wob 1.6s ease-in-out infinite; }
.s-speaking .avatar  { animation: vc-bounce .9s ease-in-out infinite; }

@keyframes vc-breath { 0%,100% { transform: scale(1); } 50% { transform: scale(1.04); } }
@keyframes vc-bounce { 0%,100% { transform: translateY(0) scale(1); } 50% { transform: translateY(-3px) scale(1.05); } }
@keyframes vc-wob    { 0%,100% { transform: rotate(-2deg); } 50% { transform: rotate(2deg); } }

/* 眨眼 */
.eyes .eye { fill: #2c1408; }
.s-idle .eyes .eye      { animation: vc-blink 5.4s infinite; transform-origin: center; }
.s-listening .eyes .eye { animation: vc-blink 3.4s infinite; transform-origin: center; }
.s-speaking .eyes .eye  { animation: vc-blink 2.6s infinite; transform-origin: center; }
@keyframes vc-blink { 0%,92%,96%,100% { transform: scaleY(1); } 94% { transform: scaleY(.08); } }

/* 思考的眼皮 */
.s-thinking .think-eyelid { opacity: 1; animation: vc-thinkroll 1.4s ease-in-out infinite; }
@keyframes vc-thinkroll { 0%,100% { transform: translateY(0); } 50% { transform: translateY(2px); } }

/* 嘴：默认露 smile，speaking 时换成 oh */
.mouth .oh, .mouth .oh-tongue { opacity: 0; transform-origin: 80px 112px; }
.s-speaking .mouth .smile     { opacity: 0; }
.s-speaking .mouth .oh        { opacity: 1; animation: vc-talk 0.32s ease-in-out infinite alternate; }
.s-speaking .mouth .oh-tongue { opacity: 1; animation: vc-talk 0.32s ease-in-out infinite alternate; }
@keyframes vc-talk { from { transform: scaleY(.6); } to { transform: scaleY(1.1); } }

/* 思考时嘴变成「·」 */
.s-thinking .mouth .smile { stroke-dasharray: 1 8; }

/* 三圈光晕 */
.rings {
  position: absolute;
  inset: 0;
  display: flex; align-items: center; justify-content: center;
  pointer-events: none;
  z-index: 1;
}
.ring {
  position: absolute;
  width: 132px; height: 132px;
  border-radius: 50%;
  border: 2px solid var(--cyan, #5fa9c0);
  opacity: 0;
}
.s-idle .ring,  .s-error .ring { opacity: 0; }
.s-listening .ring { animation: vc-ring 1.8s ease-out infinite; border-color: #5fa9c0; }
.s-listening .ring.r2 { animation-delay: .55s; }
.s-listening .ring.r3 { animation-delay: 1.1s; }
.s-thinking .ring { animation: vc-spin 1.6s linear infinite; border-style: dashed; border-color: #d4a542; opacity: .8; }
.s-thinking .ring.r2, .s-thinking .ring.r3 { display: none; }
.s-speaking .ring { animation: vc-ring 1.2s ease-out infinite; border-color: #d97757; }
.s-speaking .ring.r2 { animation-delay: .35s; }
.s-speaking .ring.r3 { animation-delay: .7s; }
@keyframes vc-ring { 0% { transform: scale(.78); opacity: .55; } 100% { transform: scale(1.6); opacity: 0; } }
@keyframes vc-spin { from { transform: rotate(0deg) scale(1); } to { transform: rotate(360deg) scale(1); } }

/* 摄像头画中画 */
.cam-pip {
  position: absolute;
  top: 2px; right: 4px;
  width: 96px;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid var(--border);
  box-shadow: 0 6px 14px rgba(0,0,0,.22);
  z-index: 3;
  line-height: 0;
}
.cam-pip video {
  width: 100%;
  aspect-ratio: 4 / 3;
  object-fit: cover;
  transform: scaleX(-1);   /* 自拍镜像 */
  display: block;
}
.cam-eye {
  position: absolute;
  bottom: 3px; right: 3px;
  width: 18px; height: 18px;
  border-radius: 50%;
  background: rgba(0,0,0,.45);
  color: #7fd4a3;
  display: flex; align-items: center; justify-content: center;
}
.cam-eye svg { width: 11px; height: 11px; }

/* 麦克风状态点 */
.mic-dot {
  position: absolute;
  bottom: 4px; right: calc(50% - 76px);
  width: 26px; height: 26px;
  border-radius: 50%;
  background: var(--bg-card);
  border: 1px solid var(--border);
  display: flex; align-items: center; justify-content: center;
  color: var(--text-3);
  z-index: 3;
  transition: all .2s;
}
.mic-dot svg { width: 14px; height: 14px; }
.mic-dot.listening { color: #5fa9c0; border-color: #5fa9c0; background: rgba(95,169,192,.12); }
.mic-dot.thinking  { color: #d4a542; border-color: #d4a542; }
.mic-dot.speaking  { color: #d97757; border-color: #d97757; opacity: .55; }
.mic-dot.error     { color: #d95757; border-color: #d95757; }

/* ============ 状态行 ============ */
.status {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 4px;
  font-size: 13px;
  color: var(--text-2);
  font-weight: 600;
}
.dot-pulse {
  width: 7px; height: 7px; border-radius: 50%;
  background: var(--text-3);
}
.s-listening .dot-pulse { background: #5fa9c0; box-shadow: 0 0 0 0 rgba(95,169,192,.5); animation: pulse 1.4s infinite; }
.s-thinking .dot-pulse  { background: #d4a542; animation: vc-spin 1.4s linear infinite; }
.s-speaking .dot-pulse  { background: #d97757; animation: pulse 1s infinite; }
@keyframes pulse {
  0% { box-shadow: 0 0 0 0 currentColor; }
  70% { box-shadow: 0 0 0 6px transparent; }
  100% { box-shadow: 0 0 0 0 transparent; }
}

/* ============ interim 实时转写 ============ */
.interim {
  align-self: center;
  max-width: 92%;
  padding: 6px 12px;
  font-size: 12.5px;
  color: var(--text);
  background: rgba(95,169,192,.08);
  border: 1px dashed rgba(95,169,192,.45);
  border-radius: 12px;
  line-height: 1.5;
  text-align: center;
}
.interim .quote { color: var(--text-3); }

/* ============ 对话流 ============ */
.turns {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 200px;
  overflow-y: auto;
  padding: 2px 4px;
  margin-top: 4px;
  scrollbar-width: thin;
}
.turns::-webkit-scrollbar { width: 4px; }
.turns::-webkit-scrollbar-thumb { background: var(--border); border-radius: 2px; }

.turn { display: flex; }
.turn.user      { justify-content: flex-end; }
.turn.assistant { justify-content: flex-start; }
.turn .bubble {
  max-width: 86%;
  padding: 6px 11px;
  border-radius: 12px;
  font-size: 12.5px;
  line-height: 1.5;
  word-break: break-word;
}
.turn.assistant .bubble {
  background: var(--bg-card-2);
  color: var(--text);
  border-bottom-left-radius: 4px;
}
.turn.user .bubble {
  background: linear-gradient(135deg, rgba(217,119,87,.18), rgba(201,100,66,.10));
  color: var(--text);
  border: 1px solid rgba(217,119,87,.22);
  border-bottom-right-radius: 4px;
}
.turn .meta {
  margin-top: 3px;
  font-size: 10px;
  color: var(--text-3);
  display: inline-flex;
  gap: 4px;
}
.recalled {
  margin: 4px 0 0; padding: 0; list-style: none;
  font-size: 10.5px; color: var(--cyan);
}
.recalled li {
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}

/* ============ 操作按钮 ============ */
.vc-quick {
  display: flex; flex-wrap: wrap; gap: 6px;
  justify-content: center;
  margin-top: 2px;
}
.vc-chip {
  font-size: 11px;
  padding: 5px 11px;
  border-radius: 999px;
  background: var(--bg-card-2);
  color: var(--text-2);
  border: 1px solid var(--border);
  cursor: pointer;
  transition: all .15s;
}
.vc-chip:hover { color: var(--cyan); border-color: var(--cyan); background: var(--cyan-dim); }
.ctrl {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
}
.big {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 9px 18px;
  border: 0;
  border-radius: 999px;
  background: var(--grad-primary, linear-gradient(135deg, #d97757, #c96442));
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 6px 14px rgba(217,119,87,.35);
  transition: transform .18s, box-shadow .18s, filter .18s;
}
.big:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 8px 18px rgba(217,119,87,.45); }
.big:disabled { opacity: .45; cursor: not-allowed; }
.big.on {
  background: var(--bg-card-2);
  color: var(--text);
  border: 1px solid var(--border);
  box-shadow: none;
}
.big.on:hover { filter: brightness(.96); }
.big svg { width: 14px; height: 14px; }

.ghost {
  font-size: 11px;
  color: var(--text-3);
  background: transparent;
  border: 0;
  cursor: pointer;
  padding: 6px 8px;
}
.ghost:hover { color: var(--text-2); }

.cam-toggle {
  display: inline-flex; align-items: center; gap: 5px;
  border: 1px solid var(--border);
  border-radius: 999px;
  padding: 6px 12px;
}
.cam-toggle svg { width: 13px; height: 13px; }
.cam-toggle.on { color: #7fd4a3; border-color: rgba(127,212,163,.5); background: rgba(127,212,163,.08); }

.tip {
  margin: 4px 0 2px;
  font-size: 11px;
  color: var(--text-3);
  text-align: center;
}
.tip.warn { color: #d95757; }
</style>
