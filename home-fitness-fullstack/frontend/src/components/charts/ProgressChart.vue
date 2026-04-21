<script setup>
import { onMounted, ref, watch, nextTick } from 'vue';

const props = defineProps({
  sessions: { type: Array, default: () => [] }
});

const canvasEl = ref(null);

function draw() {
  const canvas = canvasEl.value;
  if (!canvas) return;
  const ctx = canvas.getContext('2d');
  const dpr = window.devicePixelRatio || 1;
  const rect = canvas.getBoundingClientRect();
  canvas.width = rect.width * dpr;
  canvas.height = rect.height * dpr;
  ctx.scale(dpr, dpr);
  const W = rect.width, H = rect.height;
  ctx.clearRect(0, 0, W, H);

  if (!props.sessions.length) {
    ctx.fillStyle = 'rgba(255, 255, 255, .22)';
    ctx.font = '500 13px Inter, sans-serif';
    ctx.textAlign = 'center';
    ctx.fillText('暂无数据', W / 2, H / 2);
    return;
  }

  const data = props.sessions.slice(0, 14).reverse();
  const padding = { top: 28, right: 20, bottom: 40, left: 40 };
  const chartW = W - padding.left - padding.right;
  const chartH = H - padding.top - padding.bottom;

  const maxReps = Math.max(...data.map(d => d.reps || 0), 1);
  const barWidth = Math.min(22, chartW / data.length * 0.44);
  const gap = chartW / data.length;

  // Grid
  ctx.lineWidth = 1;
  for (let i = 0; i <= 4; i++) {
    const y = padding.top + chartH * (1 - i / 4);
    ctx.strokeStyle = 'rgba(255,255,255,.04)';
    ctx.beginPath(); ctx.moveTo(padding.left, y); ctx.lineTo(W - padding.right, y); ctx.stroke();
    ctx.fillStyle = 'rgba(255,255,255,.22)';
    ctx.font = '500 10px Inter, sans-serif';
    ctx.textAlign = 'right';
    ctx.fillText(Math.round(maxReps * i / 4), padding.left - 8, y + 4);
  }

  // Bars
  data.forEach((d, i) => {
    const x = padding.left + gap * i + gap / 2;
    const h = ((d.reps || 0) / maxReps) * chartH;
    const y = padding.top + chartH - h;
    const g = ctx.createLinearGradient(x, y, x, padding.top + chartH);
    g.addColorStop(0, 'rgba(0, 240, 255, .75)');
    g.addColorStop(1, 'rgba(0, 240, 255, .1)');
    ctx.fillStyle = g;
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
    ctx.fillStyle = 'rgba(255,255,255,.22)';
    ctx.font = '500 9px Inter, sans-serif';
    ctx.textAlign = 'center';
    ctx.fillText((d.date || '').slice(5, 10), x, padding.top + chartH + 16);
  });

  // Score line
  ctx.strokeStyle = '#7C6AFF';
  ctx.lineWidth = 2;
  ctx.lineJoin = 'round';
  ctx.lineCap = 'round';
  ctx.beginPath();
  data.forEach((d, i) => {
    const x = padding.left + gap * i + gap / 2;
    const y = padding.top + chartH * (1 - (d.score || 0) / 100);
    if (i === 0) ctx.moveTo(x, y);
    else ctx.lineTo(x, y);
  });
  ctx.stroke();
  data.forEach((d, i) => {
    const x = padding.left + gap * i + gap / 2;
    const y = padding.top + chartH * (1 - (d.score || 0) / 100);
    ctx.fillStyle = '#7C6AFF';
    ctx.beginPath(); ctx.arc(x, y, 3.5, 0, 2 * Math.PI); ctx.fill();
    ctx.fillStyle = 'var(--bg-card)';
    ctx.fillStyle = getComputedStyle(canvas).getPropertyValue('background-color') || '#141419';
    ctx.beginPath(); ctx.arc(x, y, 1.6, 0, 2 * Math.PI); ctx.fill();
  });

  // Legend
  ctx.textAlign = 'left';
  ctx.fillStyle = 'rgba(0, 240, 255, .8)';
  ctx.beginPath(); ctx.arc(padding.left + 6, 14, 4, 0, 2 * Math.PI); ctx.fill();
  ctx.fillStyle = 'rgba(255, 255, 255, .38)';
  ctx.font = '500 11px Inter, sans-serif';
  ctx.fillText('次数', padding.left + 16, 18);
  ctx.fillStyle = '#7C6AFF';
  ctx.beginPath(); ctx.arc(padding.left + 60, 14, 4, 0, 2 * Math.PI); ctx.fill();
  ctx.fillStyle = 'rgba(255, 255, 255, .38)';
  ctx.fillText('评分', padding.left + 70, 18);
}

onMounted(async () => {
  await nextTick();
  draw();
  window.addEventListener('resize', draw);
});
watch(() => props.sessions, draw, { deep: true });
</script>

<template>
  <div class="chart-area">
    <canvas ref="canvasEl"></canvas>
  </div>
</template>

<style scoped>
.chart-area {
  width: 100%;
  height: 220px;
  padding: 10px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 16px;
}
canvas { width: 100%; height: 100%; display: block; }
@media (min-width: 768px) {
  .chart-area { height: 280px; }
}
</style>
