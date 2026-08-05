/**
 * useVoiceChat — 畅聊状态机
 *
 * 封装：
 *   1) STT 引擎选择与自动降级（详见 @/modules/sttEngines）
 *      - 桌面优先 native（Web Speech，低延迟、免流量、有实时中间结果）
 *      - 移动端直接用 server（MiMo ASR）—— 移动端浏览器普遍没有可用的原生识别引擎
 *      - native 运行中判定失效（engine-dead）时自动切 server，不让用户对着死麦克风说话
 *      - 两者都不可用 → canListen=false，上层降级为文字输入
 *   2) 状态机：idle → listening → thinking → speaking → listening（循环）
 *   3) TTS 自动播报后端 ttsApi.speak 拿到的音频；播放期间暂停采音避免回声/自录
 *
 * 暴露：
 *   state         ref('idle' | 'listening' | 'thinking' | 'speaking' | 'error')
 *   canListen     ref(bool) —— 当前环境能否语音输入；false 时请用 say() 走文字
 *   engineKind    ref('native' | 'server' | null)
 *   level         ref(0..1) —— 实时音量（仅 server 引擎），供 UI 做波形
 *   interim       ref(string) —— 实时未定稿文字（native 有；server 显示"识别中…"）
 *   transcripts   ref(Array<{ role:'user'|'assistant', content, time, recalled? }>)
 *   error         ref(string|null)
 *   start()/stop()/interruptTts()/clearTranscripts()
 *   say(text)     —— 把一段文字当作用户发言送入对话（文字输入 / 快捷追问共用）
 *
 * 上层注入：
 *   chat(text, history, sceneSummary?) → Promise<{ reply, recalled, provider }>
 *   speak(text) → Promise<{ audioBase64, mimeType, fallbackText }>
 *   getSceneContext?() → Promise<string|null> —— 视频畅聊："看一眼"当前画面出场景摘要。
 *     用户一开口就并行预取（藏进说话时间里），句子说完后随 chat 附带；
 *     没就绪最多再等 SCENE_WAIT_MS，超时就不带画面，绝不拖垮对话回合。
 */

import { ref, onBeforeUnmount } from 'vue';
import { asrApi } from '@/api/asr';
import { createNativeStt, createServerStt, hasNativeStt, isMobileBrowser } from '@/modules/sttEngines';

const SILENCE_MS = 1500;    // 静音多久判定一句话说完
const MAX_HISTORY = 8;      // 给后端的历史长度
const SCENE_WAIT_MS = 2500; // 句子说完后最多再等画面摘要多久

const ERROR_TEXT = {
  denied: '麦克风权限被拒绝，请在浏览器设置里允许后重试。',
  'no-mic': '没有检测到可用的麦克风。',
  network: '网络不稳定，语音识别失败了。',
  server: '语音识别服务未启用，请改用下方输入框打字。',
  unsupported: '当前浏览器不支持语音输入，请改用下方输入框打字。',
  'engine-dead': '当前设备的语音识别不可用，请改用下方输入框打字。'
};

export function useVoiceChat({ chat, speak, getSceneContext }) {
  const state       = ref('idle');
  const interim     = ref('');
  const transcripts = ref([]);
  const error       = ref(null);
  // 乐观初值：原生构造函数存在与否并不能说明引擎可用（移动端正是"存在但不工作"），
  // 服务端 ASR 也可能补位，所以先允许尝试，由 start() 据实修正。
  const canListen   = ref(true);
  const engineKind  = ref(null);
  const level       = ref(0);

  let engine = null;
  let currentAudio = null;
  let userStopped = true;
  let processing = false;
  let scenePromise = null;
  let serverAvailable = null;    // null=未探测 true/false=已知

  /** 查询后端是否具备服务端识别能力（结果缓存，避免每次开麦都问）。 */
  async function checkServerAsr() {
    if (serverAvailable !== null) return serverAvailable;
    try {
      const res = await asrApi.status();
      serverAvailable = !!res?.enabled;
    } catch (_) {
      serverAvailable = false;
    }
    return serverAvailable;
  }

  function buildEngine(kind) {
    const shared = {
      silenceMs: SILENCE_MS,
      onInterim: (t) => {
        interim.value = t;
        // 用户一开口就并行"看一眼"画面 —— 抓帧+VLM 藏进说话/上传时间里，不占回合延迟
        if (getSceneContext && !scenePromise && t) {
          scenePromise = Promise.resolve().then(getSceneContext).catch(() => null);
        }
      },
      onLevel: (v) => { level.value = v; },
      onFinal: (text) => { processUtterance(text).catch(() => {}); },
      onError: handleEngineError
    };
    return kind === 'server' ? createServerStt(shared) : createNativeStt(shared);
  }

  /**
   * 引擎报错统一处理。native 判定失效时尝试切 server —— 这是移动端最常见的路径：
   * 构造函数存在（所以看起来"支持"）但底层引擎缺失，start() 后毫无动静。
   */
  async function handleEngineError(code, detail) {
    if ((code === 'engine-dead' || code === 'unsupported')
        && engine?.kind === 'native' && await checkServerAsr()) {
      engine.destroy();
      engine = buildEngine('server');
      engineKind.value = 'server';
      const ok = await engine.start();
      if (ok) {
        error.value = null;
        state.value = 'listening';
        return;
      }
    }
    // 到这里说明没有可用的语音通路了，明确告诉用户去打字，而不是停在"监听中"
    if (code === 'engine-dead' || code === 'unsupported' || code === 'server') {
      canListen.value = false;
    }
    error.value = ERROR_TEXT[code] || '语音输入出了点问题，请改用下方输入框打字。';
    state.value = 'error';
    userStopped = true;
    level.value = 0;
    if (detail) console.warn('[voice-chat] engine error:', code, detail);
  }

  /** 把一段用户发言走完 chat → TTS → 续听。 */
  async function processUtterance(text) {
    if (processing || !text) return;
    processing = true;

    transcripts.value.push({ role: 'user', content: text, time: Date.now() });

    state.value = 'thinking';
    interim.value = '';
    if (engine) engine.pause();      // thinking/speaking 期间停止采音，避免把 AI 的声音录进去
    level.value = 0;

    // 画面上下文：优先用开口时预取的；没有（如点选追问）就现取；超时不带画面
    let scene = null;
    if (getSceneContext) {
      const p = scenePromise || Promise.resolve().then(getSceneContext).catch(() => null);
      scenePromise = null;
      scene = await Promise.race([
        p,
        new Promise(res => setTimeout(() => res(null), SCENE_WAIT_MS))
      ]).catch(() => null);
    }

    let reply = '', recalled = [], provider = null;
    try {
      const history = transcripts.value
        .slice(-MAX_HISTORY * 2 - 1, -1)
        .map(t => ({ role: t.role, content: t.content }));
      const res = await chat(text, history, scene);
      reply    = res?.reply || '我这边没听清，再说一遍好吗？';
      recalled = res?.recalled || [];
      provider = res?.provider;
    } catch (_) {
      reply = '我这边连接出了点问题，等会儿再聊吧。';
    }

    transcripts.value.push({
      role: 'assistant', content: reply, recalled, provider, time: Date.now()
    });

    state.value = 'speaking';
    try {
      const tts = await speak(reply);
      await playTts(tts, reply);
    } catch (_) {
      browserSpeak(reply);
    }

    processing = false;

    if (!userStopped && engine) {
      state.value = 'listening';
      engine.resume();
    } else {
      state.value = 'idle';
    }
  }

  /** 点选快捷追问 / 文字输入：把这句话当作用户发言直接送入对话。 */
  async function say(text) {
    const t = (text || '').trim();
    if (!t || processing) return;
    stopCurrentAudio();
    if (state.value === 'idle' || state.value === 'error') userStopped = true;
    error.value = null;
    await processUtterance(t);
  }

  function playTts(tts, fallbackText) {
    return new Promise((resolve) => {
      stopCurrentAudio();
      if (tts?.audioBase64 && tts?.mimeType) {
        const audio = new Audio(`data:${tts.mimeType};base64,${tts.audioBase64}`);
        currentAudio = audio;
        audio.onended = () => { currentAudio = null; resolve(); };
        audio.onerror = () => { currentAudio = null; browserSpeak(tts.fallbackText || fallbackText); resolve(); };
        audio.play().catch(() => { currentAudio = null; browserSpeak(tts.fallbackText || fallbackText); resolve(); });
        return;
      }
      browserSpeak(tts?.fallbackText || fallbackText);
      const dur = Math.max(1200, (fallbackText?.length || 10) * 110);
      setTimeout(resolve, dur);
    });
  }

  function browserSpeak(text) {
    if (!text || !('speechSynthesis' in window)) return;
    try {
      const u = new SpeechSynthesisUtterance(text);
      u.lang = 'zh-CN';
      u.rate = 0.96;
      window.speechSynthesis.cancel();
      window.speechSynthesis.speak(u);
    } catch (_) { /* noop */ }
  }

  function stopCurrentAudio() {
    if (currentAudio) {
      try { currentAudio.pause(); } catch (_) { /* noop */ }
      currentAudio = null;
    }
    if ('speechSynthesis' in window) {
      try { window.speechSynthesis.cancel(); } catch (_) { /* noop */ }
    }
  }

  /** 用户主动开口可调，立刻打断当前 TTS 并回到 listening。 */
  function interruptTts() {
    if (state.value !== 'speaking') return;
    stopCurrentAudio();
    state.value = 'listening';
    if (engine) engine.resume();
  }

  /**
   * 开始畅聊。移动端跳过原生引擎直接用服务端 —— 原生在手机上"看起来支持但不工作"，
   * 先试再降级会白白让用户等一轮探测超时。
   */
  async function start() {
    error.value = null;
    const server = await checkServerAsr();
    const kind = (isMobileBrowser() && server) ? 'server'
      : hasNativeStt() ? 'native'
        : server ? 'server' : null;

    if (!kind) {
      canListen.value = false;
      error.value = ERROR_TEXT.unsupported;
      state.value = 'error';
      return;
    }

    if (engine) engine.destroy();
    engine = buildEngine(kind);
    engineKind.value = kind;

    userStopped = false;
    state.value = 'listening';
    const ok = await engine.start();
    if (ok) {
      canListen.value = true;
    } else if (state.value !== 'error') {
      // start 返回 false 时 onError 通常已经报过；这里兜住没报的情况
      state.value = 'error';
    }
  }

  function stop() {
    userStopped = true;
    stopCurrentAudio();
    if (engine) { engine.destroy(); engine = null; }
    engineKind.value = null;
    state.value = 'idle';
    interim.value = '';
    level.value = 0;
    processing = false;
    scenePromise = null;
  }

  function clearTranscripts() {
    transcripts.value = [];
  }

  onBeforeUnmount(() => { stop(); });

  return {
    state, canListen, engineKind, level, interim, transcripts, error,
    start, stop, interruptTts, clearTranscripts, say
  };
}
