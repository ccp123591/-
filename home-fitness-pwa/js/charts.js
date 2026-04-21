/**
 * charts.js — Canvas 折线图/柱状图 可视化（配合新 UI 配色）
 */
const Charts = (() => {
  function draw(canvasEl, sessions) {
    const ctx = canvasEl.getContext('2d');
    const dpr = window.devicePixelRatio || 1;
    const rect = canvasEl.getBoundingClientRect();
    canvasEl.width = rect.width * dpr;
    canvasEl.height = rect.height * dpr;
    ctx.scale(dpr, dpr);

    const W = rect.width;
    const H = rect.height;

    ctx.clearRect(0, 0, W, H);

    if (!sessions || sessions.length === 0) {
      ctx.fillStyle = 'rgba(255,255,255,.2)';
      ctx.font = '500 13px Inter, sans-serif';
      ctx.textAlign = 'center';
      ctx.fillText('暂无数据', W / 2, H / 2);
      return;
    }

    const data = sessions.slice(0, 14).reverse();
    const padding = { top: 32, right: 20, bottom: 40, left: 42 };
    const chartW = W - padding.left - padding.right;
    const chartH = H - padding.top - padding.bottom;

    const maxReps = Math.max(...data.map(d => d.reps), 1);
    const maxScore = 100;
    const barWidth = Math.min(18, chartW / data.length * 0.4);
    const gap = chartW / data.length;

    // Grid lines
    ctx.lineWidth = 1;
    for (let i = 0; i <= 4; i++) {
      const y = padding.top + chartH * (1 - i / 4);
      ctx.strokeStyle = 'rgba(255,255,255,.04)';
      ctx.beginPath();
      ctx.moveTo(padding.left, y);
      ctx.lineTo(W - padding.right, y);
      ctx.stroke();

      ctx.fillStyle = 'rgba(255,255,255,.2)';
      ctx.font = '500 10px Inter, sans-serif';
      ctx.textAlign = 'right';
      ctx.fillText(Math.round(maxReps * i / 4), padding.left - 8, y + 4);
    }

    // Bars (reps) - cyan gradient
    data.forEach((d, i) => {
      const x = padding.left + gap * i + gap / 2;
      const h = (d.reps / maxReps) * chartH;
      const y = padding.top + chartH - h;

      const grad = ctx.createLinearGradient(x, y, x, padding.top + chartH);
      grad.addColorStop(0, 'rgba(0,240,255,.7)');
      grad.addColorStop(1, 'rgba(0,240,255,.1)');
      ctx.fillStyle = grad;

      // Rounded top bar
      const r = Math.min(barWidth / 2, 4);
      ctx.beginPath();
      ctx.moveTo(x - barWidth / 2, padding.top + chartH);
      ctx.lineTo(x - barWidth / 2, y + r);
      ctx.quadraticCurveTo(x - barWidth / 2, y, x - barWidth / 2 + r, y);
      ctx.lineTo(x + barWidth / 2 - r, y);
      ctx.quadraticCurveTo(x + barWidth / 2, y, x + barWidth / 2, y + r);
      ctx.lineTo(x + barWidth / 2, padding.top + chartH);
      ctx.closePath();
      ctx.fill();

      // X-axis labels
      ctx.fillStyle = 'rgba(255,255,255,.2)';
      ctx.font = '500 9px Inter, sans-serif';
      ctx.textAlign = 'center';
      const label = (d.date || '').slice(5, 10);
      ctx.fillText(label, x, padding.top + chartH + 16);
    });

    // Line (score) - purple
    ctx.strokeStyle = '#7C6AFF';
    ctx.lineWidth = 2;
    ctx.lineJoin = 'round';
    ctx.lineCap = 'round';
    ctx.beginPath();
    data.forEach((d, i) => {
      const x = padding.left + gap * i + gap / 2;
      const y = padding.top + chartH * (1 - (d.score || 0) / maxScore);
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    });
    ctx.stroke();

    // Line fill
    const lastX = padding.left + gap * (data.length - 1) + gap / 2;
    const firstX = padding.left + gap / 2;
    ctx.lineTo(lastX, padding.top + chartH);
    ctx.lineTo(firstX, padding.top + chartH);
    ctx.closePath();
    const areaGrad = ctx.createLinearGradient(0, padding.top, 0, padding.top + chartH);
    areaGrad.addColorStop(0, 'rgba(124,106,255,.12)');
    areaGrad.addColorStop(1, 'rgba(124,106,255,0)');
    ctx.fillStyle = areaGrad;
    ctx.fill();

    // Line dots
    data.forEach((d, i) => {
      const x = padding.left + gap * i + gap / 2;
      const y = padding.top + chartH * (1 - (d.score || 0) / maxScore);
      ctx.fillStyle = '#7C6AFF';
      ctx.beginPath();
      ctx.arc(x, y, 3.5, 0, 2 * Math.PI);
      ctx.fill();
      // White center
      ctx.fillStyle = '#141419';
      ctx.beginPath();
      ctx.arc(x, y, 1.5, 0, 2 * Math.PI);
      ctx.fill();
    });

    // Legend
    ctx.font = '500 11px Inter, sans-serif';
    ctx.textAlign = 'left';

    // Cyan dot for reps
    ctx.fillStyle = 'rgba(0,240,255,.7)';
    ctx.beginPath();
    ctx.arc(padding.left + 6, 14, 4, 0, 2 * Math.PI);
    ctx.fill();
    ctx.fillStyle = 'rgba(255,255,255,.35)';
    ctx.fillText('次数', padding.left + 16, 18);

    // Purple dot for score
    ctx.fillStyle = '#7C6AFF';
    ctx.beginPath();
    ctx.arc(padding.left + 60, 14, 4, 0, 2 * Math.PI);
    ctx.fill();
    ctx.fillStyle = 'rgba(255,255,255,.35)';
    ctx.fillText('评分', padding.left + 70, 18);
  }

  return { draw };
})();
