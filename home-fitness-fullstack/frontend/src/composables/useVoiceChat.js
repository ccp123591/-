/**
 * useVoiceChat — 畅聊状态机
 *
 * 封装：
 *   1) 浏览器原生 STT（webkitSpeechRecognition / SpeechRecognition）
 *      - lang=zh-CN, continuous=true, interimResults=true
 *      - onresult 累积 finalTranscript，超过静音阈值（默认 1500ms）后视为一句完整发言
 *   2) 状态机：idle → listening → thinking → speaking → listening（循环）
 *   3) TTS 自动播报后端 ttsApi.speak 拿到的音频；播放期间停 STT 避免回声
 *   4) supported = false 时降级让上层提示「请用 Chrome/Edge」
 *
 * 暴露：
 *   state         ref('idle' | 'listening' | 'thinking' | 'speaking' | 'error')
 *   supported     bool —— 浏览器是否支持原生 STT
 *   interim       ref(string) —— 实时未定稿文字
 *   transcripts   ref(Array<{ role:'user'|'assistant', content, time, recalled? }>)
 *   start()       开始畅聊：申请麦权限 + 开 STT 循环
 *   stop()        结束畅聊：停 STT + 停 TTS
 *   interruptTts() 用户开口时中断当前 TTS（高级，可选）
 *   error         ref(string|null)
 *
 * 上层注入：
 *   chat(text, history, sceneSummary?) → Promise<{ reply, recalled, provider }>
 *   speak(text) → Promise<{ audioBase64, mimeType, fallbackText }>
 *   getSceneContext?() → Promise<string|null> —— 视频畅聊："看一眼"当前画面出场景摘要。
 *     用户一开口就并行预取（藏进说话时间里），句子说完后随 chat 附带；
 *     没就绪最多再等 SCENE_WAIT_MS，超时就不带画面，绝不拖垮对话回合。
 */

import { ref, onBeforeUnmount } from 'vue';

const SILENCE_MS = 1500;    // 静音多久判定一句话说完
const MAX_HISTORY = 8;      // 给后端的历史长度
const SCENE_WAIT_MS = 2500; // 句子说完后最多再等画面摘要多久

function getRecognitionCtor() {
  if (typeof window === 'undefined') return null;
  return window.SpeechRecognition || window.webkitSpeechRecognition || null;
}

export function useVoiceChat({ chat, speak, getSceneContext }) {
  const Recognition = getRecognitionCtor();
  const supported = !!Recognition;

  const state       = ref('idle');                  // idle | listening | thinking | speaking | error
  const interim     = ref('');
  const transcripts = ref([]);
  const error       = ref(null);

  // 内部状态（不响应式）
  let recognition = null;
  let currentAudio = null;
  let finalBuf = '';
  let silenceTimer = null;
  let restartTimer = null;
  let userStopped = true;                            // true=外部主动 stop，循环就别再启
  let processing = false;                            // 防止同一句话重复入 chat
  let scenePromise = null;                           // 本句话的画面摘要预取（开口时启动）

  function clearTimers() {
    if (silenceTimer) { clearTimeout(silenceTimer); silenceTimer = null; }
    if (restartTimer) { clearTimeout(restartTimer); restartTimer = null; }
  }

  function destroyRecognition() {
    if (!recognition) return;
    try {
      recognition.onresult = null;
      recognition.onerror  = null;
      recognition.onend    = null;
      recognition.onstart  = null;
      recognition.abort();
    } catch (_) { /* noop */ }
    recognition = null;
  }

  function makeRecognition() {
    const r = new Recognition();
    r.lang = 'zh-CN';
    r.continuous = true;
    r.interimResults = true;
    r.maxAlternatives = 1;

    r.onresult = (e) => {
      let interimText = '';
      for (let i = e.resultIndex; i < e.results.length; i++) {
        const res = e.results[i];
        if (res.isFinal) {
          finalBuf += res[0].transcript;
        } else {
          interimText += res[0].transcript;
        }
      }
      interim.value = (finalBuf + interimText).trim();

      // 用户一开口就并行"看一眼"画面 —— 抓帧+VLM 藏进说话时间里，不占回合延迟
      if (getSceneContext && !scenePromise && interim.value) {
        scenePromise = Promise.resolve().then(getSceneContext).catch(() => null);
      }

      // 任何时候一来语音 → 重置静音计时
      if (silenceTimer) clearTimeout(silenceTimer);
      silenceTimer = setTimeout(() => {
        flushUtterance().catch(() => {});
      }, SILENCE_MS);
    };

    r.onerror = (e) => {
      // no-speech / aborted 都属于可恢复，不报错给用户
      const code = e?.error || 'unknown';
      if (code === 'aborted' || code === 'no-speech' || code === 'audio-capture') {
        return;
      }
      if (code === 'not-allowed' || code === 'service-not-allowed') {
        error.value = '请允许麦克风权限';
        state.value = 'error';
        userStopped = true;
        return;
      }
      // 其他网络/未知错误也不打断循环
    };

    r.onend = () => {
      // 自动续听：用户没主动停 && 当前不是 thinking/speaking，就重启
      if (userStopped) return;
      if (state.value !== 'listening') return;
      // 避免 Chrome 立即重启冲突
      if (restartTimer) clearTimeout(restartTimer);
      restartTimer = setTimeout(() => {
        try { recognition && recognition.start(); }
        catch (_) { /* already started or stopped */ }
      }, 250);
    };

    return r;
  }

  async function startRecognition() {
    if (!recognition) recognition = makeRecognition();
    try {
      recognition.start();
    } catch (_) {
      // 可能 onend 还没回调就再 start → 忽略
    }
  }

  function stopRecognition() {
    clearTimers();
    if (recognition) {
      try { recognition.stop(); } catch (_) {}
    }
  }

  /** 一句话说完：取出转写文本交给 processUtterance。 */
  async function flushUtterance() {
    const text = (finalBuf + interim.value.slice(finalBuf.length)).trim();
    finalBuf = '';
    interim.value = '';
    await processUtterance(text);
  }

  /** 把一段用户发言（语音转写 或 点选追问）走完 chat → TTS → 续听。 */
  async function processUtterance(text) {
    if (processing || !text) return;
    processing = true;

    transcripts.value.push({ role: 'user', content: text, time: Date.now() });

    state.value = 'thinking';
    stopRecognition();          // thinking/speaking 期间停 STT
    destroyRecognition();       // 彻底关掉避免 onend 续听竞态

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
        .slice(-MAX_HISTORY * 2 - 1, -1)   // 不含本条
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

    // —— Speak ——
    state.value = 'speaking';
    try {
      const tts = await speak(reply);
      await playTts(tts, reply);
    } catch (_) {
      // 退化为浏览器朗读
      browserSpeak(reply);
    }

    processing = false;

    // —— 续听（除非外部 stop） ——
    if (!userStopped) {
      state.value = 'listening';
      startRecognition();
    } else {
      state.value = 'idle';
    }
  }

  /** 点选快捷追问：把这句话当作用户发言直接送入对话（不必开口）。 */
  async function say(text) {
    const t = (text || '').trim();
    if (!t || processing) return;
    clearTimers();
    stopCurrentAudio();
    // 不在畅聊进行中（idle/error）时，处理完不自动开麦
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
      // 仅 fallbackText → 浏览器朗读
      browserSpeak(tts?.fallbackText || fallbackText);
      // 估算朗读时长，给个最低 1.2s
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
    } catch (_) {}
  }

  function stopCurrentAudio() {
    if (currentAudio) {
      try { currentAudio.pause(); } catch (_) {}
      currentAudio = null;
    }
    if ('speechSynthesis' in window) {
      try { window.speechSynthesis.cancel(); } catch (_) {}
    }
  }

  /** 用户主动开口可调，立刻打断当前 TTS 并回到 listening。 */
  function interruptTts() {
    if (state.value !== 'speaking') return;
    stopCurrentAudio();
    state.value = 'listening';
    startRecognition();
  }

  async function start() {
    if (!supported) {
      error.value = '当前浏览器不支持语音识别，请用 Chrome / Edge / Opera。';
      state.value = 'error';
      return;
    }
    // 预热麦权限（更友好的提示）— 不阻塞，失败就让 STT onerror 自己 handle
    try {
      await navigator.mediaDevices?.getUserMedia?.({ audio: true });
    } catch (_) { /* SpeechRecognition 自己也会再请求一次 */ }

    error.value = null;
    userStopped = false;
    state.value = 'listening';
    startRecognition();
  }

  function stop() {
    userStopped = true;
    clearTimers();
    stopCurrentAudio();
    destroyRecognition();
    state.value = 'idle';
    interim.value = '';
    finalBuf = '';
    processing = false;
    scenePromise = null;
  }

  function clearTranscripts() {
    transcripts.value = [];
  }

  onBeforeUnmount(() => { stop(); });

  return {
    state, supported, interim, transcripts, error,
    start, stop, interruptTts, clearTranscripts, say
  };
}
