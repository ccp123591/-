/**
 * voice.js — TTS 语音提示 + 节拍器
 */
const Voice = (() => {
  const synth = window.speechSynthesis;
  let enabled = true;
  let rate = 1;
  let metronomeTimer = null;
  let audioCtx = null;

  function setEnabled(v) { enabled = v; }
  function setRate(r) { rate = r; }

  function speak(text, priority) {
    if (!enabled || !synth) return;
    // 如果是高优先级，清除队列
    if (priority === 'high') synth.cancel();
    const u = new SpeechSynthesisUtterance(text);
    u.lang = 'zh-CN';
    u.rate = rate;
    u.pitch = 1;
    u.volume = 1;
    synth.speak(u);
  }

  /* ---------- 预设语音短语 ---------- */
  function countVoice(n) { speak(`${n}`, 'high'); }

  function encourageVoice() {
    const phrases = ['很好', '继续保持', '不错', '加油', '真棒'];
    speak(phrases[Math.floor(Math.random() * phrases.length)]);
  }

  function correctVoice(msg) { speak(msg, 'high'); }

  function finishVoice(reps) { speak(`训练结束，共完成 ${reps} 次，辛苦了`, 'high'); }

  /* ---------- 节拍器（Web Audio） ---------- */
  function getAudioCtx() {
    if (!audioCtx) audioCtx = new (window.AudioContext || window.webkitAudioContext)();
    return audioCtx;
  }

  function playTick() {
    const ctx = getAudioCtx();
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.connect(gain);
    gain.connect(ctx.destination);
    osc.frequency.value = 880;
    osc.type = 'sine';
    gain.gain.setValueAtTime(0.3, ctx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.1);
    osc.start(ctx.currentTime);
    osc.stop(ctx.currentTime + 0.1);
  }

  function startMetronome(bpm) {
    stopMetronome();
    const interval = 60000 / bpm;
    playTick();
    metronomeTimer = setInterval(playTick, interval);
  }

  function stopMetronome() {
    if (metronomeTimer) {
      clearInterval(metronomeTimer);
      metronomeTimer = null;
    }
  }

  function stop() {
    stopMetronome();
    if (synth) synth.cancel();
  }

  return {
    setEnabled, setRate,
    speak, countVoice, encourageVoice, correctVoice, finishVoice,
    startMetronome, stopMetronome, stop
  };
})();
