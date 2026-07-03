/**
 * voice.js — 训练语音（MiMo 云 TTS 主路径 + 浏览器合成兜底） + 高精度 Web Audio 节拍器
 *
 * 云语音策略：
 *   - prewarm() 在训练开始时后台预热报数 1..30 与固定短语，存入 CacheStorage（跨会话持久）
 *   - 报数走"缓存命中才用云音频，否则浏览器即时兜底 + 后台补缓存"，保证零延迟
 *   - 其余提示语即取即播（~1s 延迟可接受），MiMo 不可用自动整段降级浏览器合成
 * 节拍器使用 lookahead scheduler，精度 < 1ms
 */
import { ttsApi } from '@/api/tts';

const TTS_CACHE = 'fitcoach-tts-v1';
const PREWARM_COUNT = 30;
const ENCOURAGE_POOL = ['很好', '继续保持', '不错', '加油', '真棒', '状态绝佳', '就是这样'];
const FIXED_PHRASES = ['训练开始', '已暂停', '继续训练', '休息结束，可以继续训练了'];

class Voice {
  constructor() {
    this.synth = window.speechSynthesis;
    this.enabled = true;
    this.rate = 1;

    // 云 TTS 状态
    this.cloud = true;            // 后端/MiMo 失败时本会话降级
    this.mem = new Map();         // text -> object URL（内存缓存，命中即秒播）
    this.pending = new Map();     // text -> Promise（去重 in-flight 请求）
    this.currentAudio = null;
    this.queue = [];              // 低优先级待播队列（上限 2）
    this._prewarmed = false;

    // Web Audio 节拍器
    this.audioCtx = null;
    this.nextTickTime = 0;
    this.bpm = 30;
    this.schedulerTimer = null;
    this.metronomeRunning = false;
  }

  setEnabled(v) { this.enabled = v; }
  setRate(r) { this.rate = r; }

  /* ========== 云音频获取与缓存 ========== */

  async _cacheStorage() {
    if (!('caches' in window)) return null;
    try { return await caches.open(TTS_CACHE); } catch (_) { return null; }
  }

  // mem → CacheStorage → 后端 MiMo，返回可播放的 object URL；失败返回 null
  _resolve(text) {
    if (this.mem.has(text)) return Promise.resolve(this.mem.get(text));
    if (this.pending.has(text)) return this.pending.get(text);

    const p = (async () => {
      const key = `/__tts__/${encodeURIComponent(text)}`;
      const cache = await this._cacheStorage();
      if (cache) {
        const hit = await cache.match(key);
        if (hit) {
          const url = URL.createObjectURL(await hit.blob());
          this.mem.set(text, url);
          return url;
        }
      }
      if (!this.cloud) return null;
      const r = await ttsApi.speak(text);
      if (!r || !r.audioBase64 || !r.mimeType) {
        // 后端已降级（fallbackText）→ MiMo 不可用，本会话不再尝试
        this.cloud = false;
        return null;
      }
      const bytes = Uint8Array.from(atob(r.audioBase64), c => c.charCodeAt(0));
      const blob = new Blob([bytes], { type: r.mimeType });
      if (cache) {
        try { await cache.put(key, new Response(blob, { headers: { 'Content-Type': r.mimeType } })); } catch (_) {}
      }
      const url = URL.createObjectURL(blob);
      this.mem.set(text, url);
      return url;
    })().catch(() => { this.cloud = false; return null; })
      .finally(() => this.pending.delete(text));

    this.pending.set(text, p);
    return p;
  }

  /** 训练开始时调用：后台预热报数与固定短语（已缓存的瞬间跳过，串行防打爆后端）。 */
  async prewarm() {
    if (this._prewarmed || !this.cloud) return;
    this._prewarmed = true;
    const texts = [
      ...Array.from({ length: PREWARM_COUNT }, (_, i) => String(i + 1)),
      ...ENCOURAGE_POOL,
      ...FIXED_PHRASES
    ];
    for (const t of texts) {
      if (!this.cloud) break;
      await this._resolve(t);
    }
  }

  /* ========== 播放 ========== */

  _stopPlayback() {
    this.queue = [];
    if (this.currentAudio) {
      try { this.currentAudio.pause(); this.currentAudio.src = ''; } catch (_) {}
      this.currentAudio = null;
    }
  }

  _playUrl(url) {
    const audio = new Audio(url);
    audio.playbackRate = this.rate;
    this.currentAudio = audio;
    const next = () => {
      if (this.currentAudio === audio) this.currentAudio = null;
      const q = this.queue.shift();
      if (q) this._playUrl(q);
    };
    audio.onended = next;
    audio.onerror = next;
    audio.play().catch(next);
  }

  _enqueue(url, priority) {
    if (priority === 'high') {
      this._stopPlayback();
      if (this.synth) this.synth.cancel();
      this._playUrl(url);
      return;
    }
    if (this.currentAudio) {
      if (this.queue.length < 2) this.queue.push(url);
      return;
    }
    this._playUrl(url);
  }

  _browserSpeak(text, priority) {
    if (!this.synth) return;
    if (priority === 'high') this.synth.cancel();
    const u = new SpeechSynthesisUtterance(text);
    u.lang = 'zh-CN';
    u.rate = this.rate;
    u.pitch = 1;
    u.volume = 1;
    this.synth.speak(u);
  }

  /** 通用播报：缓存命中秒播 MiMo，未命中即取即播，云不可用走浏览器。 */
  speak(text, priority) {
    if (!this.enabled || !text) return;
    const cached = this.mem.get(text);
    if (cached) { this._enqueue(cached, priority); return; }
    if (!this.cloud) { this._browserSpeak(text, priority); return; }
    this._resolve(text).then(url => {
      if (!this.enabled) return;
      if (url) this._enqueue(url, priority);
      else this._browserSpeak(text, priority);
    });
  }

  /** 零延迟播报（报数用）：未缓存立刻浏览器兜底，同时后台补缓存下次用。 */
  _speakInstant(text) {
    if (!this.enabled || !text) return;
    const cached = this.mem.get(text);
    if (cached) { this._enqueue(cached, 'high'); return; }
    this._browserSpeak(text, 'high');
    if (this.cloud) this._resolve(text);
  }

  countVoice(n) { this._speakInstant(String(n)); }

  encourage() {
    this.speak(ENCOURAGE_POOL[Math.floor(Math.random() * ENCOURAGE_POOL.length)]);
  }

  correct(msg) { this.speak(msg, 'high'); }

  finish(reps, unit = '次') {
    if (unit === '秒') this.speak(`训练结束，坚持了 ${reps} 秒，辛苦了`, 'high');
    else this.speak(`训练结束，共完成 ${reps} 次，辛苦了`, 'high');
  }

  stopSpeak() {
    this._stopPlayback();
    if (this.synth) this.synth.cancel();
  }

  /* ========== 节拍器（Web Audio 高精度） ========== */
  _ctx() {
    if (!this.audioCtx) this.audioCtx = new (window.AudioContext || window.webkitAudioContext)();
    return this.audioCtx;
  }

  _playTickAt(time) {
    const ctx = this._ctx();
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.connect(gain);
    gain.connect(ctx.destination);
    osc.frequency.setValueAtTime(880, time);
    osc.type = 'sine';
    gain.gain.setValueAtTime(0.25, time);
    gain.gain.exponentialRampToValueAtTime(0.001, time + 0.1);
    osc.start(time);
    osc.stop(time + 0.1);
  }

  _scheduler() {
    const LOOKAHEAD = 0.1; // 100ms
    const ctx = this._ctx();
    while (this.nextTickTime < ctx.currentTime + LOOKAHEAD) {
      this._playTickAt(this.nextTickTime);
      this.nextTickTime += 60 / this.bpm;
    }
  }

  startMetronome(bpm) {
    this.stopMetronome();
    this.bpm = bpm;
    this.metronomeRunning = true;
    const ctx = this._ctx();
    if (ctx.state === 'suspended') ctx.resume();
    this.nextTickTime = ctx.currentTime + 0.05;
    this.schedulerTimer = setInterval(() => this._scheduler(), 25);
  }

  stopMetronome() {
    this.metronomeRunning = false;
    if (this.schedulerTimer) {
      clearInterval(this.schedulerTimer);
      this.schedulerTimer = null;
    }
  }

  stopAll() { this.stopSpeak(); this.stopMetronome(); }
}

export const voice = new Voice();
