/**
 * exercise.js — 角度计算、状态机计数、多维评分
 * 支持：深蹲 / 前屈 / 俯卧撑 / 弓步 / 臀桥 / 平板 / 开合跳
 */

// 动作配置表（扩展性强，新增动作只需在此追加）
export const ACTION_DEFS = {
  squat:       { label: '深蹲',     desc: '锻炼下肢力量', landmarks: { L: [23, 25, 27], R: [24, 26, 28] }, kind: 'rep' },
  stretch:     { label: '前屈伸展', desc: '提升柔韧性',   landmarks: { L: [11, 23, 25], R: [12, 24, 26] }, kind: 'rep' },
  pushup:      { label: '俯卧撑',   desc: '强化胸臂',     landmarks: { L: [11, 13, 15], R: [12, 14, 16] }, kind: 'rep' },
  lunge:       { label: '弓步蹲',   desc: '下肢稳定性',   landmarks: { L: [23, 25, 27], R: [24, 26, 28] }, kind: 'rep' },
  bridge:      { label: '臀桥',     desc: '臀部激活',     landmarks: { L: [11, 23, 25], R: [12, 24, 26] }, kind: 'rep' },
  plank:       { label: '平板支撑', desc: '核心力量',     landmarks: { L: [11, 23, 27], R: [12, 24, 28] }, kind: 'timed' },
  jumpingJack: { label: '开合跳',   desc: '有氧燃脂',     landmarks: { L: [12, 14, 16], R: [11, 13, 15] }, kind: 'rep' }
};

function angleDeg(a, b, c) {
  const ab = { x: a.x - b.x, y: a.y - b.y };
  const cb = { x: c.x - b.x, y: c.y - b.y };
  const dot = ab.x * cb.x + ab.y * cb.y;
  const cross = ab.x * cb.y - ab.y * cb.x;
  return Math.atan2(Math.abs(cross), dot) * (180 / Math.PI);
}

export class Exercise {
  constructor() {
    this.angleBuffer = [];
    this.state = 'idle';
    this.reps = 0;
    this.targetReps = 10;
    this.timestamps = [];
    this.angleHistory = [];
    this.leftAngleHistory = [];
    this.rightAngleHistory = [];
    this.minAngleInRep = 180;
    this.depthSamples = [];
    this.action = 'squat';
    this.thresholdDown = 90;
    this.thresholdUp = 160;
    this.idealDepth = 85;
  }

  init(action, config, target) {
    this.action = action;
    const cfg = config[action] || { down: 90, up: 160 };
    this.thresholdDown = cfg.down;
    this.thresholdUp = cfg.up;
    this.idealDepth = Math.max(45, cfg.down - 10);
    this.state = 'idle';
    this.reps = 0;
    this.targetReps = target;
    this.timestamps = [];
    this.angleHistory = [];
    this.leftAngleHistory = [];
    this.rightAngleHistory = [];
    this.depthSamples = [];
    this.minAngleInRep = 180;
    this.angleBuffer = [];
  }

  _smooth(raw) {
    this.angleBuffer.push(raw);
    if (this.angleBuffer.length > 5) this.angleBuffer.shift();
    return this.angleBuffer.reduce((s, v) => s + v, 0) / this.angleBuffer.length;
  }

  extractAngles(landmarks) {
    if (!landmarks || landmarks.length < 33) return null;
    const def = ACTION_DEFS[this.action];
    if (!def) return null;
    const [l1, l2, l3] = def.landmarks.L;
    const [r1, r2, r3] = def.landmarks.R;
    const pL = [landmarks[l1], landmarks[l2], landmarks[l3]];
    const pR = [landmarks[r1], landmarks[r2], landmarks[r3]];
    const visOk = [...pL, ...pR].every(p => p && p.visibility > 0.5);
    if (!visOk) return null;
    const left = angleDeg(pL[0], pL[1], pL[2]);
    const right = angleDeg(pR[0], pR[1], pR[2]);
    return { left, right, mean: (left + right) / 2 };
  }

  update(landmarks) {
    const angles = this.extractAngles(landmarks);
    if (!angles) {
      return { reps: this.reps, angle: null, state: this.state, event: 'lost', message: '未检测到关键点', targetReps: this.targetReps };
    }
    const raw = angles.mean;
    const angle = this._smooth(raw);

    this.angleHistory.push(angle);
    this.leftAngleHistory.push(angles.left);
    this.rightAngleHistory.push(angles.right);

    let event = null;
    let message = '';

    if (this.state === 'idle' || this.state === 'up') {
      if (angle < this.thresholdDown) {
        this.state = 'down';
        event = 'down';
        this.minAngleInRep = angle;
        message = this._downMessage();
      } else if (angle > this.thresholdUp) {
        this.state = 'up';
      } else if (this.state === 'up') {
        event = 'correction';
        message = this._correctionMessage();
      }
    } else if (this.state === 'down') {
      if (angle < this.minAngleInRep) this.minAngleInRep = angle;
      if (angle > this.thresholdUp) {
        this.state = 'up';
        this.reps++;
        this.timestamps.push(Date.now());
        this.depthSamples.push(this.minAngleInRep);
        this.minAngleInRep = 180;
        event = 'count';
        message = `第 ${this.reps} 次`;
      }
    }

    return {
      reps: this.reps,
      angle: Math.round(angle),
      angles: { left: Math.round(angles.left), right: Math.round(angles.right) },
      state: this.state,
      event,
      message,
      targetReps: this.targetReps
    };
  }

  _downMessage() {
    switch (this.action) {
      case 'squat':  return '下蹲到位';
      case 'stretch':return '前屈到位';
      case 'pushup': return '很好，撑起';
      case 'lunge':  return '弓步到位';
      case 'bridge': return '臀部抬起';
      case 'plank':  return '姿势稳定';
      case 'jumpingJack': return '打开完成';
      default: return '动作到位';
    }
  }
  _correctionMessage() {
    switch (this.action) {
      case 'squat':  return '再蹲低一些';
      case 'stretch':return '再弯一些';
      case 'pushup': return '再下压一点';
      case 'lunge':  return '蹲深一点';
      case 'bridge': return '臀部再抬高';
      case 'plank':  return '保持稳定';
      case 'jumpingJack': return '动作再大';
      default: return '动作再到位一些';
    }
  }

  /* ========== 多维评分 ========== */
  _rhythmScore(bpm) {
    if (this.timestamps.length < 2) return 100;
    const target = 60000 / bpm;
    let total = 0;
    for (let i = 1; i < this.timestamps.length; i++) {
      total += Math.abs(this.timestamps[i] - this.timestamps[i - 1] - target);
    }
    const mae = total / (this.timestamps.length - 1);
    return Math.max(0, Math.round(100 * (1 - mae / target)));
  }

  _stabilityScore() {
    if (this.angleHistory.length < 10) return 100;
    const near = this.angleHistory.filter(a => a >= this.thresholdDown - 20 && a <= this.thresholdDown + 20);
    if (near.length < 3) return 100;
    const mean = near.reduce((s, v) => s + v, 0) / near.length;
    const variance = near.reduce((s, v) => s + (v - mean) ** 2, 0) / near.length;
    const std = Math.sqrt(variance);
    return Math.max(0, Math.round(100 * (1 - std / 20)));
  }

  _depthScore() {
    if (this.depthSamples.length === 0) return 100;
    const mean = this.depthSamples.reduce((s, v) => s + v, 0) / this.depthSamples.length;
    const dev = Math.abs(mean - this.idealDepth);
    return Math.max(0, Math.round(100 - dev * 2.5));
  }

  _symmetryScore() {
    const n = Math.min(this.leftAngleHistory.length, this.rightAngleHistory.length);
    if (n < 10) return 100;
    let total = 0;
    for (let i = 0; i < n; i++) {
      total += Math.abs(this.leftAngleHistory[i] - this.rightAngleHistory[i]);
    }
    const mae = total / n;
    return Math.max(0, Math.round(100 - mae * 2));
  }

  _completionScore() {
    if (!this.targetReps) return 100;
    return Math.min(100, Math.round(this.reps / this.targetReps * 100));
  }

  getResult(bpm) {
    const rhythm = this._rhythmScore(bpm);
    const stability = this._stabilityScore();
    const depth = this._depthScore();
    const symmetry = this._symmetryScore();
    const completion = this._completionScore();
    // 综合评分：节奏 25% · 稳定 25% · 深度 20% · 对称 15% · 完成率 15%
    const overall = Math.round(rhythm * 0.25 + stability * 0.25 + depth * 0.20 + symmetry * 0.15 + completion * 0.15);
    return {
      reps: this.reps,
      action: this.action,
      rhythmScore: rhythm,
      stabilityScore: stability,
      depthScore: depth,
      symmetryScore: symmetry,
      completionScore: completion,
      score: overall
    };
  }
}

export const exercise = new Exercise();
