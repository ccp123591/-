/**
 * app.js — 主控逻辑：Tab 切换、训练流程、记录展示、设置管理、PWA 安装
 *           + 主题系统、Toast 通知、倒计时、统计概览、休息计时器、周目标、确认弹窗
 */
(async () => {
  'use strict';

  /* ========== PWA 注册 ========== */
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('./sw.js').catch(() => {});
  }

  let deferredPrompt = null;
  window.addEventListener('beforeinstallprompt', e => {
    e.preventDefault();
    deferredPrompt = e;
    document.getElementById('pwa-install-hint').classList.remove('hidden');
  });
  document.getElementById('btn-install')?.addEventListener('click', () => {
    if (deferredPrompt) {
      deferredPrompt.prompt();
      deferredPrompt = null;
      document.getElementById('pwa-install-hint').classList.add('hidden');
    }
  });

  /* ========== Toast 通知系统 ========== */
  const toastContainer = document.getElementById('toast-container');

  const TOAST_ICONS = {
    success: '<svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="10" cy="10" r="7"/><path d="M7 10l2 2 4-4"/></svg>',
    warning: '<svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M10 3l7.5 13H2.5z"/><line x1="10" y1="9" x2="10" y2="11"/><circle cx="10" cy="13.5" r=".5" fill="currentColor"/></svg>',
    error:   '<svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="10" cy="10" r="7"/><path d="M7.5 7.5l5 5M12.5 7.5l-5 5"/></svg>'
  };

  function showToast(message, type = 'success', duration = 2500) {
    const el = document.createElement('div');
    el.className = `toast toast-${type}`;
    el.innerHTML = `<div class="toast-icon">${TOAST_ICONS[type] || TOAST_ICONS.success}</div><span>${message}</span>`;
    toastContainer.appendChild(el);
    setTimeout(() => {
      el.classList.add('toast-out');
      el.addEventListener('animationend', () => el.remove());
    }, duration);
  }

  /* ========== 自定义确认弹窗 ========== */
  const confirmModal = document.getElementById('confirm-modal');
  const confirmTitle = document.getElementById('confirm-title');
  const confirmMsg = document.getElementById('confirm-msg');
  const btnConfirmOk = document.getElementById('btn-confirm-ok');
  const btnConfirmCancel = document.getElementById('btn-confirm-cancel');
  let confirmResolve = null;

  function showConfirm(title, message) {
    return new Promise(resolve => {
      confirmTitle.textContent = title;
      confirmMsg.textContent = message;
      confirmModal.classList.remove('hidden');
      confirmResolve = resolve;
    });
  }

  btnConfirmOk.addEventListener('click', () => {
    confirmModal.classList.add('hidden');
    if (confirmResolve) confirmResolve(true);
    confirmResolve = null;
  });
  btnConfirmCancel.addEventListener('click', () => {
    confirmModal.classList.add('hidden');
    if (confirmResolve) confirmResolve(false);
    confirmResolve = null;
  });
  confirmModal.querySelector('.modal-backdrop').addEventListener('click', () => {
    confirmModal.classList.add('hidden');
    if (confirmResolve) confirmResolve(false);
    confirmResolve = null;
  });

  /* ========== 主题系统 ========== */
  const themeGrid = document.getElementById('theme-grid');

  function applyTheme(themeName, animate = true) {
    if (animate) {
      document.documentElement.classList.add('theme-transitioning');
      setTimeout(() => document.documentElement.classList.remove('theme-transitioning'), 400);
    }
    if (themeName === 'dark') {
      document.documentElement.removeAttribute('data-theme');
    } else {
      document.documentElement.setAttribute('data-theme', themeName);
    }
    // Update meta theme-color
    const meta = document.querySelector('meta[name="theme-color"]');
    const bgColors = { dark: '#0a0a0f', light: '#F5F5F7', ocean: '#0B1628', forest: '#0A1A0F', sunset: '#1A0E0A', 'purple-night': '#0E0A1A' };
    if (meta) meta.content = bgColors[themeName] || '#0a0a0f';
    // Update active state
    themeGrid.querySelectorAll('.theme-option').forEach(opt => {
      opt.classList.toggle('active', opt.dataset.theme === themeName);
    });
  }

  themeGrid.addEventListener('click', e => {
    const option = e.target.closest('.theme-option');
    if (!option) return;
    const theme = option.dataset.theme;
    applyTheme(theme);
    // Save immediately
    const cfg = Storage.getConfig();
    cfg.theme = theme;
    Storage.saveConfig(cfg);
  });

  /* ========== Tab 切换 ========== */
  const tabs = document.querySelectorAll('.tab-btn');
  const pages = document.querySelectorAll('.page');

  tabs.forEach(btn => {
    btn.addEventListener('click', () => {
      const target = btn.dataset.tab;
      tabs.forEach(t => t.classList.toggle('active', t === btn));
      pages.forEach(p => {
        const active = p.id === `page-${target}`;
        p.classList.toggle('active', active);
        p.classList.toggle('hidden', !active);
      });
      if (target === 'records') refreshRecords();
      if (target === 'train') refreshStats();
    });
  });

  /* ========== 动作选择卡片 ========== */
  const actionCards = document.querySelectorAll('.action-card');
  actionCards.forEach(card => {
    card.addEventListener('click', () => {
      actionCards.forEach(c => c.classList.remove('selected'));
      card.classList.add('selected');
      card.querySelector('input').checked = true;
    });
  });

  function getSelectedAction() {
    const radio = document.querySelector('input[name="action"]:checked');
    return radio ? radio.value : 'squat';
  }

  /* ========== 目标次数步进器 ========== */
  const inpTarget = document.getElementById('inp-target');
  document.getElementById('btn-target-minus').addEventListener('click', () => {
    inpTarget.value = Math.max(1, (parseInt(inpTarget.value) || 10) - 1);
  });
  document.getElementById('btn-target-plus').addEventListener('click', () => {
    inpTarget.value = Math.min(200, (parseInt(inpTarget.value) || 10) + 1);
  });

  /* ========== 训练页逻辑 ========== */
  const videoEl = document.getElementById('camera');
  const canvasEl = document.getElementById('skeleton');
  const cameraPlaceholder = document.getElementById('camera-placeholder');
  const hudReps = document.getElementById('hud-reps');
  const hudScore = document.getElementById('hud-score');
  const hudTime = document.getElementById('hud-time');
  const hudAngle = document.getElementById('hud-angle');
  const statusMsg = document.getElementById('status-msg');
  const btnStart = document.getElementById('btn-start');
  const btnPause = document.getElementById('btn-pause');
  const btnStop = document.getElementById('btn-stop');
  const trainingBtns = document.getElementById('training-btns');
  const mainBtnRow = btnStart.parentElement;
  const chkVoice = document.getElementById('chk-voice');
  const chkMetronome = document.getElementById('chk-metronome');

  let training = false;
  let paused = false;
  let startTime = 0;
  let elapsed = 0;
  let timerInterval = null;
  let poseInitialized = false;

  let lastCorrectionTime = 0;
  const CORRECTION_COOLDOWN = 3000;
  const ENCOURAGE_INTERVAL = 5;

  const PAUSE_SVG = '<svg viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="4" width="4" height="16"/><rect x="14" y="4" width="4" height="16"/></svg>';
  const RESUME_SVG = '<svg viewBox="0 0 24 24" fill="currentColor"><polygon points="5,3 19,12 5,21"/></svg>';

  function showStatus(text) {
    statusMsg.textContent = text;
    statusMsg.classList.add('show');
    clearTimeout(statusMsg._timer);
    statusMsg._timer = setTimeout(() => statusMsg.classList.remove('show'), 2000);
  }

  function formatTime(ms) {
    const s = Math.floor(ms / 1000);
    const m = Math.floor(s / 60);
    return `${String(m).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
  }

  function setTrainingUI(active) {
    if (active) {
      mainBtnRow.classList.add('hidden');
      trainingBtns.classList.remove('hidden');
      cameraPlaceholder.classList.add('hidden');
      actionCards.forEach(c => c.style.pointerEvents = 'none');
      inpTarget.disabled = true;
    } else {
      mainBtnRow.classList.remove('hidden');
      trainingBtns.classList.add('hidden');
      btnStart.disabled = false;
      actionCards.forEach(c => c.style.pointerEvents = '');
      inpTarget.disabled = false;
      btnPause.innerHTML = PAUSE_SVG + ' 暂停';
    }
  }

  async function ensurePoseInit() {
    if (poseInitialized) return;
    showStatus('正在加载姿态模型...');
    btnStart.disabled = true;
    await PoseDetector.init(videoEl, canvasEl, onPoseResult);
    poseInitialized = true;
    showStatus('模型加载完成');
  }

  function onPoseResult(results) {
    if (!training || paused) return;
    const landmarks = results.poseLandmarks;
    const res = Exercise.update(landmarks);

    hudReps.textContent = res.reps;
    hudAngle.textContent = res.angle !== null ? `${res.angle}°` : '-°';

    if (res.event === 'count') {
      Voice.countVoice(res.reps);
      if (res.reps % ENCOURAGE_INTERVAL === 0 && res.reps > 0) {
        setTimeout(() => Voice.encourageVoice(), 600);
      }
      if (res.reps >= res.targetReps) {
        stopTraining();
        return;
      }
    } else if (res.event === 'correction') {
      const now = Date.now();
      if (now - lastCorrectionTime > CORRECTION_COOLDOWN) {
        Voice.correctVoice(res.message);
        lastCorrectionTime = now;
      }
    }

    if (res.message) showStatus(res.message);
  }

  /* ========== 训练倒计时 ========== */
  const countdownOverlay = document.getElementById('countdown-overlay');
  const countdownNum = document.getElementById('countdown-num');

  function runCountdown() {
    return new Promise(resolve => {
      countdownOverlay.classList.remove('hidden');
      let count = 3;
      countdownNum.textContent = count;
      // re-trigger animation
      countdownNum.style.animation = 'none';
      void countdownNum.offsetWidth;
      countdownNum.style.animation = '';

      const tick = () => {
        count--;
        if (count > 0) {
          countdownNum.textContent = count;
          countdownNum.style.animation = 'none';
          void countdownNum.offsetWidth;
          countdownNum.style.animation = '';
        } else if (count === 0) {
          countdownNum.textContent = 'GO';
          countdownNum.style.animation = 'none';
          void countdownNum.offsetWidth;
          countdownNum.style.animation = '';
        } else {
          countdownOverlay.classList.add('hidden');
          resolve();
          return;
        }
        setTimeout(tick, 800);
      };
      setTimeout(tick, 800);
    });
  }

  // 开始训练
  btnStart.addEventListener('click', async () => {
    const config = Storage.getConfig();
    Voice.setEnabled(chkVoice.checked);
    Voice.setRate(config.ttsRate);

    await ensurePoseInit();

    // 倒计时
    cameraPlaceholder.classList.add('hidden');
    PoseDetector.start();
    await runCountdown();

    const action = getSelectedAction();
    Exercise.init(action, config, parseInt(inpTarget.value) || 10);

    training = true;
    paused = false;
    startTime = Date.now();
    elapsed = 0;
    lastCorrectionTime = 0;

    hudReps.textContent = '0';
    hudScore.textContent = '-';
    hudTime.textContent = '00:00';
    hudAngle.textContent = '-°';

    timerInterval = setInterval(() => {
      if (!paused) {
        elapsed = Date.now() - startTime;
        hudTime.textContent = formatTime(elapsed);
      }
    }, 500);

    if (chkMetronome.checked) Voice.startMetronome(config.bpm);

    Voice.speak('训练开始', 'high');
    setTrainingUI(true);
  });

  // 暂停/继续
  btnPause.addEventListener('click', () => {
    if (!training) return;
    paused = !paused;
    if (paused) {
      btnPause.innerHTML = RESUME_SVG + ' 继续';
      PoseDetector.pause();
      Voice.stopMetronome();
      Voice.speak('已暂停');
    } else {
      btnPause.innerHTML = PAUSE_SVG + ' 暂停';
      PoseDetector.resume();
      startTime = Date.now() - elapsed;
      const config = Storage.getConfig();
      if (chkMetronome.checked) Voice.startMetronome(config.bpm);
      Voice.speak('继续训练');
    }
  });

  // 结束训练
  btnStop.addEventListener('click', () => {
    if (training) stopTraining();
  });

  async function stopTraining() {
    training = false;
    paused = false;
    clearInterval(timerInterval);
    PoseDetector.stop();
    Voice.stop();

    const config = Storage.getConfig();
    const result = Exercise.getResult(config.bpm);
    const duration = Math.round(elapsed / 1000);

    Voice.setEnabled(chkVoice.checked);
    Voice.finishVoice(result.reps);

    hudScore.textContent = result.score;

    const session = {
      date: new Date().toISOString().slice(0, 19).replace('T', ' '),
      action: result.action === 'squat' ? '深蹲' : '前屈伸展',
      reps: result.reps,
      score: result.score,
      rhythmScore: result.rhythmScore,
      stabilityScore: result.stabilityScore,
      duration,
      notes: ''
    };
    await Storage.saveSession(session);

    showReport(session);
    setTrainingUI(false);
    refreshStats();
  }

  /* ========== 训练报告弹窗 ========== */
  const reportModal = document.getElementById('report-modal');
  const reportBody = document.getElementById('report-body');
  const btnReportClose = document.getElementById('btn-report-close');
  const btnReportCSV = document.getElementById('btn-report-csv');

  let lastSession = null;

  function showReport(session) {
    lastSession = session;
    reportBody.innerHTML = `
      <div class="report-item"><span>动作</span><span class="report-val">${session.action}</span></div>
      <div class="report-item"><span>完成次数</span><span class="report-val">${session.reps}</span></div>
      <div class="report-item"><span>用时</span><span class="report-val">${formatTime(session.duration * 1000)}</span></div>
      <div class="report-item"><span>综合评分</span><span class="report-val">${session.score}</span></div>
      <div class="report-item"><span>节奏评分</span><span class="report-val">${session.rhythmScore}</span></div>
      <div class="report-item"><span>稳定度评分</span><span class="report-val">${session.stabilityScore}</span></div>
    `;
    // Reset rest timer
    clearRestTimer();
    reportModal.classList.remove('hidden');
  }

  btnReportClose.addEventListener('click', () => {
    clearRestTimer();
    reportModal.classList.add('hidden');
  });
  reportModal.querySelector('.modal-backdrop').addEventListener('click', () => {
    clearRestTimer();
    reportModal.classList.add('hidden');
  });

  btnReportCSV.addEventListener('click', () => {
    if (lastSession) {
      const csv = Storage.sessionsToCSV([lastSession]);
      Storage.downloadCSV(csv, `训练报告_${lastSession.date.replace(/[: ]/g, '_')}.csv`);
    }
  });

  /* ========== 休息计时器 ========== */
  let restTimerInterval = null;
  const restCountdownEl = document.getElementById('rest-countdown');
  const restCountdownNum = document.getElementById('rest-countdown-num');
  const restBtns = document.querySelectorAll('.btn-rest');

  function clearRestTimer() {
    if (restTimerInterval) {
      clearInterval(restTimerInterval);
      restTimerInterval = null;
    }
    restCountdownEl.classList.remove('show');
    restBtns.forEach(b => b.classList.remove('active'));
  }

  restBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      clearRestTimer();
      btn.classList.add('active');
      let seconds = parseInt(btn.dataset.rest);
      restCountdownNum.textContent = seconds;
      restCountdownEl.classList.add('show');

      restTimerInterval = setInterval(() => {
        seconds--;
        restCountdownNum.textContent = seconds;
        if (seconds <= 0) {
          clearRestTimer();
          Voice.speak('休息结束，可以继续训练了', 'high');
          showToast('休息结束！', 'success');
        }
      }, 1000);
    });
  });

  /* ========== 统计概览 ========== */
  const statToday = document.getElementById('stat-today');
  const statWeek = document.getElementById('stat-week');
  const statStreak = document.getElementById('stat-streak');

  async function refreshStats() {
    const sessions = await Storage.getAllSessions();
    const now = new Date();
    const todayStr = now.toISOString().slice(0, 10);

    // 今日次数
    const todayReps = sessions
      .filter(s => s.date.startsWith(todayStr))
      .reduce((sum, s) => sum + (s.reps || 0), 0);
    statToday.textContent = todayReps;

    // 本周累计 (周一开始)
    const dayOfWeek = now.getDay() || 7; // 周日=7
    const weekStart = new Date(now);
    weekStart.setDate(now.getDate() - dayOfWeek + 1);
    weekStart.setHours(0, 0, 0, 0);
    const weekReps = sessions
      .filter(s => new Date(s.date) >= weekStart)
      .reduce((sum, s) => sum + (s.reps || 0), 0);
    statWeek.textContent = weekReps;

    // 连续天数
    const uniqueDays = [...new Set(sessions.map(s => s.date.slice(0, 10)))].sort().reverse();
    let streak = 0;
    const checkDate = new Date(now);
    checkDate.setHours(0, 0, 0, 0);
    for (const day of uniqueDays) {
      const d = checkDate.toISOString().slice(0, 10);
      if (day === d) {
        streak++;
        checkDate.setDate(checkDate.getDate() - 1);
      } else if (day < d) {
        break;
      }
    }
    statStreak.textContent = streak;
  }

  /* ========== 记录页 ========== */
  const recordsList = document.getElementById('records-list');
  const noRecords = document.getElementById('no-records');
  const chartCanvas = document.getElementById('chart-canvas');
  const btnExportAll = document.getElementById('btn-export-all');
  const btnClearRecords = document.getElementById('btn-clear-records');

  // 周目标
  const weeklyGoalNums = document.getElementById('weekly-goal-nums');
  const weeklyProgressFill = document.getElementById('weekly-progress-fill');
  const weeklyGoalDone = document.getElementById('weekly-goal-done');

  async function refreshRecords() {
    const sessions = await Storage.getAllSessions();
    const config = Storage.getConfig();
    recordsList.innerHTML = '';

    // Update weekly goal
    const now = new Date();
    const dayOfWeek = now.getDay() || 7;
    const weekStart = new Date(now);
    weekStart.setDate(now.getDate() - dayOfWeek + 1);
    weekStart.setHours(0, 0, 0, 0);
    const weekReps = sessions
      .filter(s => new Date(s.date) >= weekStart)
      .reduce((sum, s) => sum + (s.reps || 0), 0);
    const goal = config.weeklyGoal || 50;
    const pct = Math.min(100, Math.round(weekReps / goal * 100));
    weeklyGoalNums.textContent = `${weekReps} / ${goal} 次`;
    weeklyProgressFill.style.width = pct + '%';
    weeklyGoalDone.classList.toggle('show', pct >= 100);

    if (sessions.length === 0) {
      noRecords.classList.remove('hidden');
      Charts.draw(chartCanvas, []);
      return;
    }
    noRecords.classList.add('hidden');
    Charts.draw(chartCanvas, sessions);

    sessions.forEach(s => {
      const li = document.createElement('li');
      li.innerHTML = `
        <div class="rec-main">
          <div class="rec-action">${s.action}</div>
          <div class="rec-date">${s.date}</div>
        </div>
        <div class="rec-stats">
          <div class="rec-stat"><span>${s.reps}</span><small>次</small></div>
          <div class="rec-stat"><span>${s.score}</span><small>分</small></div>
          <div class="rec-stat"><span>${formatTime(s.duration * 1000)}</span><small>用时</small></div>
        </div>
      `;
      recordsList.appendChild(li);
    });
  }

  btnExportAll.addEventListener('click', async () => {
    const sessions = await Storage.getAllSessions();
    if (sessions.length === 0) {
      showToast('暂无记录可导出', 'warning');
      return;
    }
    const csv = Storage.sessionsToCSV(sessions);
    Storage.downloadCSV(csv, `训练记录_全部.csv`);
    showToast('导出成功', 'success');
  });

  btnClearRecords.addEventListener('click', async () => {
    const ok = await showConfirm('清空记录', '确定清空所有训练记录？此操作不可撤销。');
    if (ok) {
      await Storage.clearSessions();
      refreshRecords();
      showToast('记录已清空', 'success');
    }
  });

  /* ========== 设置页 ========== */
  const setSquatDown = document.getElementById('set-squat-down');
  const setSquatUp = document.getElementById('set-squat-up');
  const setStretchDown = document.getElementById('set-stretch-down');
  const setStretchUp = document.getElementById('set-stretch-up');
  const setBpm = document.getElementById('set-bpm');
  const setTtsRate = document.getElementById('set-tts-rate');
  const setWeeklyGoal = document.getElementById('set-weekly-goal');

  const valSquatDown = document.getElementById('val-squat-down');
  const valSquatUp = document.getElementById('val-squat-up');
  const valStretchDown = document.getElementById('val-stretch-down');
  const valStretchUp = document.getElementById('val-stretch-up');
  const valBpm = document.getElementById('val-bpm');
  const valTtsRate = document.getElementById('val-tts-rate');
  const valWeeklyGoal = document.getElementById('val-weekly-goal');

  function loadSettings() {
    const cfg = Storage.getConfig();
    setSquatDown.value = cfg.squat.down;  valSquatDown.textContent = cfg.squat.down + '°';
    setSquatUp.value = cfg.squat.up;      valSquatUp.textContent = cfg.squat.up + '°';
    setStretchDown.value = cfg.stretch.down; valStretchDown.textContent = cfg.stretch.down + '°';
    setStretchUp.value = cfg.stretch.up;  valStretchUp.textContent = cfg.stretch.up + '°';
    setBpm.value = cfg.bpm;               valBpm.textContent = cfg.bpm;
    setTtsRate.value = cfg.ttsRate;        valTtsRate.textContent = cfg.ttsRate.toFixed(1);
    setWeeklyGoal.value = cfg.weeklyGoal || 50; valWeeklyGoal.textContent = cfg.weeklyGoal || 50;

    // Apply theme
    applyTheme(cfg.theme || 'dark', false);
  }

  // 滑块实时更新
  const angleSuffix = [setSquatDown, setSquatUp, setStretchDown, setStretchUp];
  const sliders = [
    [setSquatDown, valSquatDown], [setSquatUp, valSquatUp],
    [setStretchDown, valStretchDown], [setStretchUp, valStretchUp],
    [setBpm, valBpm], [setTtsRate, valTtsRate], [setWeeklyGoal, valWeeklyGoal]
  ];
  sliders.forEach(([slider, label]) => {
    slider.addEventListener('input', () => {
      const isAngle = angleSuffix.includes(slider);
      const isTts = slider === setTtsRate;
      label.textContent = isTts ? parseFloat(slider.value).toFixed(1) : slider.value + (isAngle ? '°' : '');
    });
  });

  document.getElementById('btn-save-settings').addEventListener('click', () => {
    const cfg = {
      squat: { down: parseInt(setSquatDown.value), up: parseInt(setSquatUp.value) },
      stretch: { down: parseInt(setStretchDown.value), up: parseInt(setStretchUp.value) },
      bpm: parseInt(setBpm.value),
      ttsRate: parseFloat(setTtsRate.value),
      theme: Storage.getConfig().theme || 'dark',
      weeklyGoal: parseInt(setWeeklyGoal.value)
    };
    Storage.saveConfig(cfg);
    showToast('设置已保存', 'success');
    Voice.speak('设置已保存');
  });

  document.getElementById('btn-reset-settings').addEventListener('click', async () => {
    const ok = await showConfirm('恢复默认', '确定恢复所有设置为默认值？');
    if (ok) {
      Storage.resetConfig();
      loadSettings();
      showToast('已恢复默认设置', 'success');
    }
  });

  /* ========== 初始化 ========== */
  loadSettings();
  await Storage.open();
  refreshStats();
})();
