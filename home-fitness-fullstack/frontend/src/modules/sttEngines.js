/**
 * sttEngines — 两套语音转文字引擎，接口统一，供 useVoiceChat 按环境选用。
 *
 * native：浏览器 Web Speech API。低延迟、免流量、有实时中间结果，但**只在桌面可靠**。
 *   它本身不含识别引擎，只是平台能力的外壳：
 *     桌面 Chrome → Google 云端 ✅        桌面 Edge → 微软 Azure ✅
 *     Android Chrome → 设备端 GMS/Google App（国行手机通常没有）❌
 *     iOS Safari → Apple 听写（仅 Safari 本体）⚠️
 *     微信 / WKWebView / X5 → 完全不提供 ❌
 *   最坑的是引擎缺失时 start() 后既无 onresult 也无 onerror，界面停在"监听中"永远没反应。
 *   为此这里加了启动健康探测：start() 后若迟迟收不到 onstart，判定引擎已死并上报 engine-dead，
 *   由上层切到 server 引擎 —— 而不是假装在听。
 *
 * server：wavRecorder 采 PCM 编 WAV → 上传后端 → MiMo ASR。识别能力不依赖设备平台，
 *   任何支持 getUserMedia 的浏览器都能用（需 HTTPS）。代价是没有实时中间结果，
 *   且每句话有一次上传往返。
 *
 * 统一接口：{ start, stop, pause, resume, destroy, kind }
 * 统一回调：onFinal(text) / onInterim(text) / onLevel(0..1) / onError(code, detail)
 * 错误码：unsupported | denied | no-mic | engine-dead | network | server
 */
import { asrApi } from '@/api/asr';
import { createWavRecorder } from '@/modules/wavRecorder';

/** start() 后多久没等到 onstart 就判定引擎不可用。真实启动通常在数百毫秒内。 */
const STARTUP_PROBE_MS = 2500;

/** 判定是否移动端 —— 移动端原生引擎不可靠，优先用服务端识别。 */
export function isMobileBrowser() {
  if (typeof navigator === 'undefined') return false;
  return /Android|iPhone|iPad|iPod|HarmonyOS|Windows Phone/i.test(navigator.userAgent);
}

/** 浏览器是否暴露了 SpeechRecognition 构造函数（注意：存在 ≠ 能用）。 */
export function hasNativeStt() {
  if (typeof window === 'undefined') return false;
  return !!(window.SpeechRecognition || window.webkitSpeechRecognition);
}

/**
 * 原生 Web Speech 引擎。
 */
export function createNativeStt({ onFinal, onInterim, onError, silenceMs = 1500 }) {
  const Ctor = typeof window !== 'undefined'
    && (window.SpeechRecognition || window.webkitSpeechRecognition);

  let recognition = null;
  let finalBuf = '';
  let silenceTimer = null;
  let restartTimer = null;
  let probeTimer = null;
  let started = false;      // 是否真的收到过 onstart
  let running = false;      // 外部期望的运行状态
  let paused = false;

  function clearTimers() {
    [silenceTimer, restartTimer, probeTimer].forEach(t => t && clearTimeout(t));
    silenceTimer = restartTimer = probeTimer = null;
  }

  function flush() {
    const text = finalBuf.trim();
    finalBuf = '';
    if (onInterim) onInterim('');
    if (text && onFinal) onFinal(text);
  }

  function build() {
    const r = new Ctor();
    r.lang = 'zh-CN';
    r.continuous = true;
    r.interimResults = true;
    r.maxAlternatives = 1;

    r.onstart = () => {
      started = true;
      if (probeTimer) { clearTimeout(probeTimer); probeTimer = null; }
    };

    r.onresult = (e) => {
      let interim = '';
      for (let i = e.resultIndex; i < e.results.length; i++) {
        const res = e.results[i];
        if (res.isFinal) finalBuf += res[0].transcript;
        else interim += res[0].transcript;
      }
      if (onInterim) onInterim((finalBuf + interim).trim());
      if (silenceTimer) clearTimeout(silenceTimer);
      silenceTimer = setTimeout(flush, silenceMs);
    };

    r.onerror = (e) => {
      const code = e?.error || 'unknown';
      // no-speech / aborted 是正常的循环噪声，静默无妨
      if (code === 'no-speech' || code === 'aborted') return;
      // 以下都必须上报 —— 旧实现把 audio-capture 和未知错误一起吞掉，
      // 导致任何失败都表现为"点了开始毫无反应"，这正是本次排查困难的根源。
      if (code === 'not-allowed' || code === 'service-not-allowed') {
        running = false;
        if (onError) onError('denied', code);
        return;
      }
      if (code === 'audio-capture') {
        running = false;
        if (onError) onError('no-mic', code);
        return;
      }
      if (code === 'network') {
        if (onError) onError('network', code);
        return;
      }
      if (onError) onError('engine-dead', code);
    };

    r.onend = () => {
      if (!running || paused) return;
      if (restartTimer) clearTimeout(restartTimer);
      restartTimer = setTimeout(() => {
        try { recognition && recognition.start(); } catch (_) { /* 已在运行，忽略 */ }
      }, 250);
    };

    return r;
  }

  function launch() {
    if (!recognition) recognition = build();
    try {
      recognition.start();
    } catch (err) {
      // 旧实现在这里 catch(_) {} 完全吞掉。同步异常意味着引擎压根没起来，
      // 若继续把状态显示为"监听中"，用户就会对着一个死掉的麦克风说话。
      if (String(err?.name) !== 'InvalidStateError') {
        running = false;
        if (onError) onError('engine-dead', err);
        return false;
      }
    }
    return true;
  }

  return {
    kind: 'native',

    async start() {
      if (!Ctor) {
        if (onError) onError('unsupported');
        return false;
      }
      running = true;
      paused = false;
      started = false;
      finalBuf = '';
      if (!launch()) return false;

      // 健康探测：引擎缺失时 start() 不报错也不回调，这里兜住那种"静默死亡"
      probeTimer = setTimeout(() => {
        if (running && !started && onError) {
          running = false;
          onError('engine-dead', 'no onstart within ' + STARTUP_PROBE_MS + 'ms');
        }
      }, STARTUP_PROBE_MS);
      return true;
    },

    stop() {
      running = false;
      paused = false;
      clearTimers();
      finalBuf = '';
      if (recognition) {
        try { recognition.stop(); } catch (_) { /* noop */ }
      }
    },

    pause() {
      paused = true;
      clearTimers();
      finalBuf = '';
      if (recognition) {
        try { recognition.abort(); } catch (_) { /* noop */ }
      }
    },

    resume() {
      if (!running) return;
      paused = false;
      launch();
    },

    destroy() {
      running = false;
      clearTimers();
      if (recognition) {
        recognition.onresult = recognition.onerror = recognition.onend = recognition.onstart = null;
        try { recognition.abort(); } catch (_) { /* noop */ }
        recognition = null;
      }
    }
  };
}

/**
 * 服务端识别引擎：录音 → 上传 → 转写。
 */
export function createServerStt({ onFinal, onInterim, onLevel, onError, silenceMs = 1500 }) {
  let uploading = false;

  const recorder = createWavRecorder({
    silenceMs,
    onLevel,
    onError: (code, err) => onError && onError(code, err),
    onSpeechEnd: async (wavBlob) => {
      if (uploading) return;          // 上一句还在识别，丢弃避免乱序
      uploading = true;
      if (onInterim) onInterim('识别中…');
      try {
        const res = await asrApi.transcribe(wavBlob);
        const text = (res?.text || '').trim();
        if (onInterim) onInterim('');
        // 空文本 = 这段是静音或噪声，属正常结果，静默丢弃继续听
        if (text && onFinal) onFinal(text);
      } catch (err) {
        if (onInterim) onInterim('');
        if (onError) onError(err?.response?.status === 503 ? 'server' : 'network', err);
      } finally {
        uploading = false;
      }
    }
  });

  return {
    kind: 'server',
    start: () => recorder.start(),
    stop: () => recorder.stop(),
    pause: () => recorder.pause(),
    resume: () => recorder.resume(),
    destroy: () => recorder.stop()
  };
}
