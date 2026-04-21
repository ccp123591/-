/**
 * pose.js — MediaPipe Pose 封装（Vue 版）
 * 提供初始化、启动、暂停、停止、骨架绘制、关键点回调
 */

const CONNECTIONS = [
  [11, 13], [13, 15], [12, 14], [14, 16],
  [11, 12], [11, 23], [12, 24], [23, 24],
  [23, 25], [25, 27], [24, 26], [26, 28],
  [27, 29], [29, 31], [28, 30], [30, 32],
  [0, 1], [1, 2], [2, 3], [3, 7],
  [0, 4], [4, 5], [5, 6], [6, 8],
  [9, 10]
];

const HIGHLIGHT_MAP = {
  squat:   new Set([23, 24, 25, 26, 27, 28]),
  stretch: new Set([11, 12, 23, 24, 25, 26]),
  pushup:  new Set([11, 12, 13, 14, 15, 16]),
  lunge:   new Set([23, 24, 25, 26, 27, 28]),
  bridge:  new Set([11, 12, 23, 24, 25, 26]),
  plank:   new Set([11, 12, 23, 24, 25, 26]),
  jumpingJack: new Set([11, 12, 13, 14, 15, 16, 25, 26, 27, 28])
};

export class PoseDetector {
  constructor() {
    this.pose = null;
    this.camera = null;
    this.running = false;
    this.videoEl = null;
    this.canvasEl = null;
    this.onResult = null;
    this.actionRef = () => 'squat';
    this.accentColor = '#00d4ff';
  }

  async init(videoEl, canvasEl, onResult, actionRef) {
    this.videoEl = videoEl;
    this.canvasEl = canvasEl;
    this.onResult = onResult;
    this.actionRef = actionRef || (() => 'squat');

    if (!window.Pose) throw new Error('MediaPipe Pose 未加载');

    this.pose = new window.Pose({
      locateFile: file => `https://cdn.jsdelivr.net/npm/@mediapipe/pose@0.5.1675469404/${file}`
    });

    this.pose.setOptions({
      modelComplexity: 1,
      smoothLandmarks: true,
      enableSegmentation: false,
      minDetectionConfidence: 0.5,
      minTrackingConfidence: 0.5
    });

    this.pose.onResults(results => {
      this._drawSkeleton(results);
      if (this.onResult) this.onResult(results);
    });

    this.camera = new window.Camera(videoEl, {
      onFrame: async () => {
        if (this.running && this.pose) {
          try { await this.pose.send({ image: videoEl }); } catch (_) { /* ignore */ }
        }
      },
      width: 320,
      height: 240
    });
  }

  start() { this.running = true; this.camera && this.camera.start(); }
  stop()  { this.running = false; this.camera && this.camera.stop(); }
  pause() { this.running = false; }
  resume(){ this.running = true; }

  setAccentColor(color) { this.accentColor = color; }

  _drawSkeleton(results) {
    const canvas = this.canvasEl;
    const video = this.videoEl;
    if (!canvas || !video) return;
    const ctx = canvas.getContext('2d');
    canvas.width = video.videoWidth || 320;
    canvas.height = video.videoHeight || 240;
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    if (!results.poseLandmarks) return;
    const lm = results.poseLandmarks;
    const w = canvas.width, h = canvas.height;
    const action = this.actionRef();
    const highlights = HIGHLIGHT_MAP[action] || HIGHLIGHT_MAP.squat;
    const hl = this.accentColor;

    ctx.lineWidth = 2;
    for (const [i, j] of CONNECTIONS) {
      if (!lm[i] || !lm[j]) continue;
      const isHL = highlights.has(i) && highlights.has(j);
      ctx.strokeStyle = isHL ? hl : 'rgba(255, 255, 255, .35)';
      ctx.lineWidth = isHL ? 3 : 1.5;
      ctx.beginPath();
      ctx.moveTo(lm[i].x * w, lm[i].y * h);
      ctx.lineTo(lm[j].x * w, lm[j].y * h);
      ctx.stroke();
    }
    for (let i = 0; i < lm.length; i++) {
      const p = lm[i];
      if (!p || p.visibility < 0.5) continue;
      const isHL = highlights.has(i);
      ctx.fillStyle = isHL ? hl : 'rgba(255, 255, 255, .5)';
      ctx.beginPath();
      ctx.arc(p.x * w, p.y * h, isHL ? 5 : 3, 0, 2 * Math.PI);
      ctx.fill();
    }
  }
}

export const poseDetector = new PoseDetector();
