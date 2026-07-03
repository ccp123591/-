/**
 * fallguard.js — 训练中跌倒风险预警
 *
 * 基于 MediaPipe 姿态点的启发式实时检测，两级输出：
 *   risk — 髋部重心短时间内下坠过快（远超正常下蹲速度）→ 失稳预警
 *   fall — 躯干接近水平且髋部位于画面下部 → 疑似已跌倒
 *
 * 俯卧撑/平板支撑/臀桥本来就是水平姿态，对这些动作只保留速度规则。
 * 全部带冷却时间，避免连续弹警报。
 */

// 水平姿态属于动作本身的动作 code
const HORIZONTAL_ACTIONS = new Set(['pushup', 'plank', 'bridge']);

const WINDOW_MS = 700;        // 髋部轨迹保留窗口
const LOOKBACK_MS = 320;      // 速度计算回看时长
const RISK_VEL = 1.6;         // 归一化画面高度/秒 —— 正常下蹲约 0.3~0.8
const RISK_DROP = 0.22;       // 回看窗口内的最小下坠幅度
const FALL_TORSO_DEG = 65;    // 躯干与竖直方向夹角超过此值视为接近水平
const FALL_HIP_Y = 0.62;      // 髋部低于画面 62% 高度
const RISK_COOLDOWN = 5000;
const FALL_COOLDOWN = 8000;

export class FallGuard {
  constructor() {
    this.reset();
  }

  reset() {
    this.hist = [];
    this.lastRisk = 0;
    this.lastFall = 0;
  }

  /**
   * 每帧喂入姿态点。返回 null 或 { level: 'risk'|'fall', message }。
   * @param {Array} lm MediaPipe poseLandmarks（归一化坐标）
   * @param {string} action 当前动作 code（用于豁免水平动作）
   */
  update(lm, action, now = Date.now()) {
    if (!lm || !lm[11] || !lm[12] || !lm[23] || !lm[24]) return null;
    const vis = p => (p.visibility ?? 1) > 0.4;
    if (![lm[11], lm[12], lm[23], lm[24]].every(vis)) return null;

    const hip = { x: (lm[23].x + lm[24].x) / 2, y: (lm[23].y + lm[24].y) / 2 };
    const sh  = { x: (lm[11].x + lm[12].x) / 2, y: (lm[11].y + lm[12].y) / 2 };

    this.hist.push({ t: now, y: hip.y });
    while (this.hist.length && now - this.hist[0].t > WINDOW_MS) this.hist.shift();

    // 疑似跌倒：躯干接近水平 + 髋部贴近画面底部（水平动作豁免）
    if (!HORIZONTAL_ACTIONS.has(action)) {
      const dx = sh.x - hip.x;
      const dy = sh.y - hip.y;                                  // 屏幕 y 向下为正
      const torsoDeg = Math.abs(Math.atan2(dx, -dy)) * 180 / Math.PI;
      if (torsoDeg > FALL_TORSO_DEG && hip.y > FALL_HIP_Y && now - this.lastFall > FALL_COOLDOWN) {
        this.lastFall = now;
        this.lastRisk = now;
        return { level: 'fall', message: '检测到疑似跌倒，请确认是否安全' };
      }
    }

    // 失稳预警：髋部高速下坠
    const past = this.hist.find(h => now - h.t >= LOOKBACK_MS) || this.hist[0];
    const dt = (now - past.t) / 1000;
    if (dt > 0.1) {
      const drop = hip.y - past.y;
      if (drop > RISK_DROP && drop / dt > RISK_VEL && now - this.lastRisk > RISK_COOLDOWN) {
        this.lastRisk = now;
        return { level: 'risk', message: '重心下坠过快，注意稳住身体' };
      }
    }
    return null;
  }
}

export const fallGuard = new FallGuard();
