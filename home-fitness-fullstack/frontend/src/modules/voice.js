/**
 * voice.js — TTS 语音 + 高精度 Web Audio 节拍器
 * 节拍器使用 lookahead scheduler，精度 < 1ms
 */

class Voice {
  constructor() {
    this.synth = window.speechSynthesis;
    this.enabled = true;
    this.rate = 1;

    // Web Audio 节拍器
    this.audioCtx = null;
    this.nextTickTime = 0;
    this.bpm = 30;
    this.schedulerTimer = null;
    this.metronomeRunning = false;
  }

  setEnabled(v) { this.enabled = v; }
  setRate(r) { this.rate = r; }

  speak(text, priority) {
    if (!this.enabled || !this.synth) return;
    if (priority === 'high') this.synth.cancel();
    const u = new SpeechSynthesisUtterance(text);
    u.lang = 'zh-CN';
    u.rate = this.rate;
    u.pitch = 1;
    u.volume = 1;
    this.synth.speak(u);
  }

  countVoice(n) { this.speak(String(n), 'high'); }

  encourage() {
    const pool = ['很好', '继续保持', '不错', '加油', '真棒', '状态绝佳', '就是这样'];
    this.speak(pool[Math.floor(Math.random() * pool.length)]);
  }

  correct(msg) { this.speak(msg, 'high'); }

  finish(reps) { this.speak(`训练结束，共完成 ${reps} 次，辛苦了`, 'high'); }

  stopSpeak() { if (this.synth) this.synth.cancel(); }

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
