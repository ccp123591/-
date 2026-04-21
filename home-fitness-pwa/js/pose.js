/**
 * pose.js — MediaPipe Pose 加载与关键点提取 + 骨架绘制
 */
const PoseDetector = (() => {
  let pose = null;
  let camera = null;
  let running = false;
  let onResultCallback = null;

  // 骨架连接定义
  const CONNECTIONS = [
    [11,13],[13,15],[12,14],[14,16], // 手臂
    [11,12],[11,23],[12,24],[23,24], // 躯干
    [23,25],[25,27],[24,26],[26,28], // 腿
    [27,29],[29,31],[28,30],[30,32], // 脚
    [0,1],[1,2],[2,3],[3,7],[0,4],[4,5],[5,6],[6,8], // 面部
    [9,10],[11,23],[12,24] // 嘴/躯干
  ];

  const HIGHLIGHT_SQUAT = new Set([23,24,25,26,27,28]); // 髋膝踝
  const HIGHLIGHT_STRETCH = new Set([11,12,23,24,25,26]); // 肩髋膝

  async function init(videoEl, canvasEl, callback) {
    onResultCallback = callback;

    pose = new Pose({
      locateFile: file => `https://cdn.jsdelivr.net/npm/@mediapipe/pose@0.5.1675469404/${file}`
    });

    pose.setOptions({
      modelComplexity: 1,
      smoothLandmarks: true,
      enableSegmentation: false,
      minDetectionConfidence: 0.5,
      minTrackingConfidence: 0.5
    });

    pose.onResults(results => {
      drawSkeleton(canvasEl, results, videoEl);
      if (onResultCallback) onResultCallback(results);
    });

    camera = new Camera(videoEl, {
      onFrame: async () => {
        if (running) await pose.send({ image: videoEl });
      },
      width: 320,
      height: 240
    });
  }

  function start() {
    running = true;
    camera.start();
  }

  function stop() {
    running = false;
    if (camera) camera.stop();
  }

  function pause() { running = false; }
  function resume() { running = true; }

  /* ---------- 骨架绘制 ---------- */
  function drawSkeleton(canvas, results, video) {
    const ctx = canvas.getContext('2d');
    canvas.width = video.videoWidth || 320;
    canvas.height = video.videoHeight || 240;
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    if (!results.poseLandmarks) return;

    const lm = results.poseLandmarks;
    const w = canvas.width;
    const h = canvas.height;

    // 判断当前高亮集合
    const actionRadio = document.querySelector('input[name="action"]:checked');
    const highlights = actionRadio && actionRadio.value === 'stretch' ? HIGHLIGHT_STRETCH : HIGHLIGHT_SQUAT;

    // 画连接线
    ctx.lineWidth = 2;
    for (const [i, j] of CONNECTIONS) {
      if (!lm[i] || !lm[j]) continue;
      const isHL = highlights.has(i) && highlights.has(j);
      ctx.strokeStyle = isHL ? '#00d4ff' : 'rgba(255,255,255,0.4)';
      ctx.lineWidth = isHL ? 3 : 1.5;
      ctx.beginPath();
      ctx.moveTo(lm[i].x * w, lm[i].y * h);
      ctx.lineTo(lm[j].x * w, lm[j].y * h);
      ctx.stroke();
    }

    // 画关键点
    for (let i = 0; i < lm.length; i++) {
      const p = lm[i];
      if (p.visibility < 0.5) continue;
      const isHL = highlights.has(i);
      ctx.fillStyle = isHL ? '#00d4ff' : 'rgba(255,255,255,0.5)';
      ctx.beginPath();
      ctx.arc(p.x * w, p.y * h, isHL ? 5 : 3, 0, 2 * Math.PI);
      ctx.fill();
    }
  }

  return { init, start, stop, pause, resume };
})();
