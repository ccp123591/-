/**
 * wavRecorder — 麦克风采集 + 自动断句（VAD）+ WAV 编码
 *
 * 为什么不用 MediaRecorder：
 *   MiMo ASR 网关只接受 wav / mp3（实测 webm / mp4 / opus / pcm 全部 400）。
 *   而 MediaRecorder 的原生输出在 Android Chrome 是 webm/opus、iOS Safari 是 mp4/aac，
 *   没有任何一个平台能直接产出 wav。因此这里走 AudioContext 采原始 PCM 自行编码。
 *   附带好处：VAD 静音检测与采集共用同一条音频链路，不必额外开一路分析。
 *
 * 为什么用 ScriptProcessorNode（已 deprecated）而非 AudioWorklet：
 *   AudioWorklet 需要独立模块文件且 iOS 支持较晚；ScriptProcessorNode 在目标机型上
 *   （含 iOS Safari、国产 WebView）一致可用。若将来只面向新机型可平滑替换。
 *
 * 断句策略：音量高于噪声基线视为说话，静音持续 silenceMs 即认定一句话说完，
 * 回调整段 WAV。短于 minSpeechMs 的片段当作误触噪声丢弃。
 */

/** 上传采样率：16kHz 单声道足够语音识别，且比 48kHz 省 2/3 流量。 */
export const TARGET_SAMPLE_RATE = 16000;

const PROCESSOR_BUFFER_SIZE = 4096;   // ~85ms @48kHz，兼顾延迟与回调频率
const NOISE_CALIBRATION_MS = 300;     // 开头这段用于测量环境底噪
const MIN_RMS_THRESHOLD = 0.008;      // 绝对下限，避免极安静环境下把呼吸当人声
const NOISE_MULTIPLIER = 3;           // 高于底噪这么多倍才算说话

/** 计算一帧的均方根音量。 */
export function rms(samples) {
  if (!samples || !samples.length) return 0;
  let sum = 0;
  for (let i = 0; i < samples.length; i++) sum += samples[i] * samples[i];
  return Math.sqrt(sum / samples.length);
}

/**
 * 线性插值降采样到目标采样率。
 * 输入输出均为 Float32（-1..1）。srcRate <= targetRate 时原样返回。
 */
export function downsample(input, srcRate, targetRate = TARGET_SAMPLE_RATE) {
  if (srcRate <= targetRate) return input;
  const ratio = srcRate / targetRate;
  const out = new Float32Array(Math.floor(input.length / ratio));
  for (let i = 0; i < out.length; i++) {
    const pos = i * ratio;
    const lo = Math.floor(pos);
    const hi = Math.min(lo + 1, input.length - 1);
    out[i] = input[lo] + (input[hi] - input[lo]) * (pos - lo);
  }
  return out;
}

/**
 * Float32 PCM → WAV(16bit 单声道) Blob。
 * 44 字节标准头，后端 MimoAsrProvider 依赖偏移 28 的 ByteRate 估算时长。
 */
export function encodeWav(samples, sampleRate = TARGET_SAMPLE_RATE) {
  const buffer = new ArrayBuffer(44 + samples.length * 2);
  const view = new DataView(buffer);
  const writeStr = (offset, s) => {
    for (let i = 0; i < s.length; i++) view.setUint8(offset + i, s.charCodeAt(i));
  };
  const byteRate = sampleRate * 2;

  writeStr(0, 'RIFF');
  view.setUint32(4, 36 + samples.length * 2, true);
  writeStr(8, 'WAVE');
  writeStr(12, 'fmt ');
  view.setUint32(16, 16, true);          // PCM 子块大小
  view.setUint16(20, 1, true);           // 格式 = PCM
  view.setUint16(22, 1, true);           // 单声道
  view.setUint32(24, sampleRate, true);
  view.setUint32(28, byteRate, true);    // ByteRate — 后端据此算时长
  view.setUint16(32, 2, true);           // BlockAlign
  view.setUint16(34, 16, true);          // 位深
  writeStr(36, 'data');
  view.setUint32(40, samples.length * 2, true);

  for (let i = 0; i < samples.length; i++) {
    const s = Math.max(-1, Math.min(1, samples[i]));
    view.setInt16(44 + i * 2, s < 0 ? s * 0x8000 : s * 0x7fff, true);
  }
  return new Blob([buffer], { type: 'audio/wav' });
}

/**
 * 创建录音器。
 *
 * @param {object}   opts
 * @param {Function} opts.onSpeechEnd  (wavBlob, durationSec) => void  一句话说完
 * @param {Function} [opts.onLevel]    (level:0..1) => void            实时音量，供 UI 做波形
 * @param {Function} [opts.onError]    (code:string, err?) => void     'no-mic'|'denied'|'unsupported'
 * @param {number}   [opts.silenceMs]     静音多久算说完，默认 1500
 * @param {number}   [opts.minSpeechMs]   短于此长度视为噪声丢弃，默认 400
 * @param {number}   [opts.maxSpeechMs]   单句上限，超时强制切分，默认 30000
 */
export function createWavRecorder({
  onSpeechEnd,
  onLevel,
  onError,
  silenceMs = 1500,
  minSpeechMs = 400,
  maxSpeechMs = 30000
} = {}) {
  let audioCtx = null;
  let stream = null;
  let source = null;
  let processor = null;

  let chunks = [];          // 当前句子的 Float32 分片（已降采样）
  let speaking = false;     // 是否处于一句话之中
  let silenceStart = 0;
  let speechStart = 0;
  let noiseFloor = 0;
  let calibrating = true;
  let calibrationEnd = 0;
  let calibrationSum = 0;
  let calibrationCount = 0;
  let running = false;

  function threshold() {
    return Math.max(MIN_RMS_THRESHOLD, noiseFloor * NOISE_MULTIPLIER);
  }

  /** 把当前累积的分片合成一段 WAV 交出去。 */
  function flush() {
    const total = chunks.reduce((n, c) => n + c.length, 0);
    const durationSec = total / TARGET_SAMPLE_RATE;
    const tooShort = Date.now() - speechStart < minSpeechMs;
    const merged = new Float32Array(total);
    let offset = 0;
    for (const c of chunks) { merged.set(c, offset); offset += c.length; }

    chunks = [];
    speaking = false;

    if (total === 0 || tooShort) return;      // 噪声误触，丢弃
    if (onSpeechEnd) onSpeechEnd(encodeWav(merged), Math.round(durationSec * 10) / 10);
  }

  function handleAudio(e) {
    if (!running) return;
    const input = e.inputBuffer.getChannelData(0);
    const level = rms(input);
    if (onLevel) onLevel(Math.min(1, level * 8));

    const now = Date.now();

    // 开头一小段先量环境底噪，避免在嘈杂环境里把噪声当人声
    if (calibrating) {
      calibrationSum += level;
      calibrationCount++;
      if (now >= calibrationEnd) {
        noiseFloor = calibrationCount ? calibrationSum / calibrationCount : 0;
        calibrating = false;
      }
      return;
    }

    const isSpeech = level > threshold();

    if (isSpeech) {
      if (!speaking) {
        speaking = true;
        speechStart = now;
        chunks = [];
      }
      silenceStart = 0;
      chunks.push(downsample(new Float32Array(input), audioCtx.sampleRate));
      // 单句过长（忘了停/持续噪声）强制切分，防止无限累积吃内存
      if (now - speechStart > maxSpeechMs) flush();
      return;
    }

    if (!speaking) return;

    // 静音期仍继续录，保留句尾的自然停顿，避免最后一个字被切掉
    chunks.push(downsample(new Float32Array(input), audioCtx.sampleRate));
    if (!silenceStart) silenceStart = now;
    else if (now - silenceStart >= silenceMs) flush();
  }

  /** 申请麦克风并开始监听。失败时通过 onError 上报，不抛异常。 */
  async function start() {
    if (running) return true;
    if (!navigator.mediaDevices?.getUserMedia) {
      // 非安全上下文（HTTP + 非 localhost）下 mediaDevices 直接是 undefined
      if (onError) onError('unsupported');
      return false;
    }
    try {
      stream = await navigator.mediaDevices.getUserMedia({
        audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true }
      });
    } catch (err) {
      if (onError) onError(err?.name === 'NotAllowedError' ? 'denied' : 'no-mic', err);
      return false;
    }

    const Ctx = window.AudioContext || window.webkitAudioContext;
    audioCtx = new Ctx();
    // iOS 上 AudioContext 初始为 suspended，必须在用户手势链路内 resume
    if (audioCtx.state === 'suspended') await audioCtx.resume();

    source = audioCtx.createMediaStreamSource(stream);
    processor = audioCtx.createScriptProcessor(PROCESSOR_BUFFER_SIZE, 1, 1);
    processor.onaudioprocess = handleAudio;
    source.connect(processor);
    // ScriptProcessor 必须接到 destination 才会被调度；接 0 增益避免自己的声音被外放
    const mute = audioCtx.createGain();
    mute.gain.value = 0;
    processor.connect(mute);
    mute.connect(audioCtx.destination);

    chunks = [];
    speaking = false;
    silenceStart = 0;
    calibrating = true;
    calibrationSum = 0;
    calibrationCount = 0;
    calibrationEnd = Date.now() + NOISE_CALIBRATION_MS;
    running = true;
    return true;
  }

  /** 停止采集并释放麦克风（会熄灭浏览器上的录音指示）。 */
  function stop() {
    running = false;
    speaking = false;
    chunks = [];
    if (processor) {
      try { processor.disconnect(); processor.onaudioprocess = null; } catch (_) { /* noop */ }
      processor = null;
    }
    if (source) {
      try { source.disconnect(); } catch (_) { /* noop */ }
      source = null;
    }
    if (stream) {
      stream.getTracks().forEach(t => { try { t.stop(); } catch (_) { /* noop */ } });
      stream = null;
    }
    if (audioCtx) {
      try { audioCtx.close(); } catch (_) { /* noop */ }
      audioCtx = null;
    }
  }

  /** 暂停送字（TTS 播放期间用，避免把 AI 的声音录进去），但保留麦克风占用。 */
  function pause() { running = false; chunks = []; speaking = false; silenceStart = 0; }

  function resume() {
    if (!audioCtx) return;
    chunks = [];
    speaking = false;
    silenceStart = 0;
    running = true;
  }

  return { start, stop, pause, resume, isRunning: () => running };
}
