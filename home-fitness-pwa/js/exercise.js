/**
 * exercise.js — 角度计算、状态机计数、节奏/稳定度评分
 */
const Exercise = (() => {
  /* ---------- 通用数学 ---------- */
  function angleDeg(a, b, c) {
    // 计算 a-b-c 三点形成的角度（b 为顶点），返回 0-180 度
    const ab = { x: a.x - b.x, y: a.y - b.y };
    const cb = { x: c.x - b.x, y: c.y - b.y };
    const dot = ab.x * cb.x + ab.y * cb.y;
    const cross = ab.x * cb.y - ab.y * cb.x;
    let angle = Math.atan2(Math.abs(cross), dot) * (180 / Math.PI);
    return angle;
  }

  // 5 帧滑动平均
  const SMOOTH_WINDOW = 5;
  let angleBuffer = [];

  function smoothAngle(raw) {
    angleBuffer.push(raw);
    if (angleBuffer.length > SMOOTH_WINDOW) angleBuffer.shift();
    return angleBuffer.reduce((s, v) => s + v, 0) / angleBuffer.length;
  }

  function resetSmooth() { angleBuffer = []; }

  /* ---------- 状态机 ---------- */
  // 状态: idle | down | up
  let state = 'idle';
  let reps = 0;
  let targetReps = 10;
  let timestamps = [];   // 每次有效计数的时间戳
  let angleHistory = []; // 所有帧角度，用于稳定度

  // 阈值（由 config 设置）
  let thresholdDown = 90;
  let thresholdUp = 160;

  // 当前动作类型
  let actionType = 'squat';

  function init(action, config, target) {
    actionType = action;
    if (action === 'squat') {
      thresholdDown = config.squat.down;
      thresholdUp = config.squat.up;
    } else {
      thresholdDown = config.stretch.down;
      thresholdUp = config.stretch.up;
    }
    state = 'idle';
    reps = 0;
    targetReps = target;
    timestamps = [];
    angleHistory = [];
    resetSmooth();
  }

  /**
   * 从 MediaPipe 关键点提取关键角度
   * 深蹲: 髋(23/24) - 膝(25/26) - 踝(27/28)
   * 前屈: 肩(11/12) - 髋(23/24) - 膝(25/26)
   * 返回左右平均角度
   */
  function extractAngle(landmarks) {
    if (!landmarks || landmarks.length < 33) return null;
    let a1, b1, c1, a2, b2, c2;
    if (actionType === 'squat') {
      // 左侧: hip(23) - knee(25) - ankle(27)
      a1 = landmarks[23]; b1 = landmarks[25]; c1 = landmarks[27];
      // 右侧: hip(24) - knee(26) - ankle(28)
      a2 = landmarks[24]; b2 = landmarks[26]; c2 = landmarks[28];
    } else {
      // 前屈: shoulder(11) - hip(23) - knee(25)
      a1 = landmarks[11]; b1 = landmarks[23]; c1 = landmarks[25];
      a2 = landmarks[12]; b2 = landmarks[24]; c2 = landmarks[26];
    }
    // 检查关键点可见性
    const vis = [a1, b1, c1, a2, b2, c2].every(p => p && p.visibility > 0.5);
    if (!vis) return null;

    const left = angleDeg(a1, b1, c1);
    const right = angleDeg(a2, b2, c2);
    return (left + right) / 2;
  }

  /**
   * 每帧调用，返回事件对象
   * { reps, angle, state, event: null|'count'|'down'|'correction' }
   */
  function update(landmarks) {
    const rawAngle = extractAngle(landmarks);
    if (rawAngle === null) {
      return { reps, angle: null, state, event: null, message: '未检测到关键点' };
    }

    const angle = smoothAngle(rawAngle);
    angleHistory.push(angle);

    let event = null;
    let message = '';

    if (state === 'idle' || state === 'up') {
      if (angle < thresholdDown) {
        state = 'down';
        event = 'down';
        if (actionType === 'squat') {
          message = '下蹲到位';
        } else {
          message = '前屈到位';
        }
      } else if (angle > thresholdUp) {
        state = 'up'; // 保持站立
      } else if (state === 'up') {
        // 中间区域，给出纠正
        if (actionType === 'squat') {
          message = '再蹲低一些';
        } else {
          message = '再弯一些';
        }
        event = 'correction';
      }
    } else if (state === 'down') {
      if (angle > thresholdUp) {
        // 站起来 → 计一次
        state = 'up';
        reps++;
        timestamps.push(Date.now());
        event = 'count';
        message = `第 ${reps} 次`;
      }
    }

    return { reps, angle: Math.round(angle), state, event, message, targetReps };
  }

  /* ---------- 评分 ---------- */
  function calcRhythmScore(bpm) {
    if (timestamps.length < 2) return 100;
    const targetInterval = 60000 / bpm; // ms
    let totalDeviation = 0;
    for (let i = 1; i < timestamps.length; i++) {
      const interval = timestamps[i] - timestamps[i - 1];
      totalDeviation += Math.abs(interval - targetInterval);
    }
    const mae = totalDeviation / (timestamps.length - 1);
    // MAE 为 0 → 100分；MAE ≥ targetInterval → 0分
    const score = Math.max(0, Math.round(100 * (1 - mae / targetInterval)));
    return score;
  }

  function calcStabilityScore() {
    if (angleHistory.length < 10) return 100;
    // 取 thresholdDown 附近的帧角度标准差
    const nearThreshold = angleHistory.filter(a =>
      a >= thresholdDown - 20 && a <= thresholdDown + 20
    );
    if (nearThreshold.length < 3) return 100;
    const mean = nearThreshold.reduce((s, v) => s + v, 0) / nearThreshold.length;
    const variance = nearThreshold.reduce((s, v) => s + (v - mean) ** 2, 0) / nearThreshold.length;
    const std = Math.sqrt(variance);
    // std 为 0 → 100；std ≥ 20 → 0
    return Math.max(0, Math.round(100 * (1 - std / 20)));
  }

  function calcOverallScore(bpm) {
    const r = calcRhythmScore(bpm);
    const s = calcStabilityScore();
    return Math.round(r * 0.5 + s * 0.5);
  }

  function getResult(bpm) {
    return {
      reps,
      rhythmScore: calcRhythmScore(bpm),
      stabilityScore: calcStabilityScore(),
      score: calcOverallScore(bpm),
      action: actionType
    };
  }

  function getReps() { return reps; }
  function getState() { return state; }

  return {
    init, update, getResult, getReps, getState,
    extractAngle, angleDeg, resetSmooth
  };
})();
