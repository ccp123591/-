/**
 * poster.js — 训练成果分享海报生成（Canvas 渲染 PNG）
 */

export function renderPoster(session, user) {
  const W = 1080, H = 1350;
  const canvas = document.createElement('canvas');
  canvas.width = W;
  canvas.height = H;
  const ctx = canvas.getContext('2d');

  // 背景渐变
  const bg = ctx.createLinearGradient(0, 0, W, H);
  bg.addColorStop(0, '#0a0a0f');
  bg.addColorStop(1, '#1a1a22');
  ctx.fillStyle = bg;
  ctx.fillRect(0, 0, W, H);

  // 顶部光晕
  const glow = ctx.createRadialGradient(W / 2, 200, 0, W / 2, 200, 500);
  glow.addColorStop(0, 'rgba(0, 240, 255, .28)');
  glow.addColorStop(1, 'rgba(0, 240, 255, 0)');
  ctx.fillStyle = glow;
  ctx.fillRect(0, 0, W, 600);

  // 品牌
  ctx.fillStyle = 'rgba(255,255,255,.6)';
  ctx.font = '500 28px Inter, sans-serif';
  ctx.textAlign = 'center';
  ctx.fillText('FitCoach · AI 居家健身陪练', W / 2, 90);

  // 用户名
  ctx.fillStyle = '#fff';
  ctx.font = '800 58px Inter, sans-serif';
  ctx.fillText(user?.nickname || '健身达人', W / 2, 190);

  // 动作
  const grad = ctx.createLinearGradient(0, 0, W, 0);
  grad.addColorStop(0, '#00F0FF');
  grad.addColorStop(1, '#7C6AFF');
  ctx.fillStyle = grad;
  ctx.font = '900 120px Inter, sans-serif';
  ctx.fillText(session.actionLabel || session.action || '训练', W / 2, 380);

  // 大数字
  ctx.fillStyle = '#fff';
  ctx.font = '900 220px Inter, sans-serif';
  ctx.fillText(String(session.reps || 0), W / 2, 640);
  ctx.fillStyle = 'rgba(255,255,255,.6)';
  ctx.font = '500 36px Inter, sans-serif';
  ctx.fillText('次 · ' + formatDuration(session.duration), W / 2, 700);

  // 三分数条
  const badges = [
    { label: '综合',   value: session.score ?? '-' },
    { label: '节奏',   value: session.rhythmScore ?? '-' },
    { label: '稳定',   value: session.stabilityScore ?? '-' }
  ];
  const boxW = 260, boxH = 160, totalW = boxW * 3 + 40 * 2;
  let bx = (W - totalW) / 2, by = 820;
  badges.forEach(b => {
    roundRect(ctx, bx, by, boxW, boxH, 22, 'rgba(255,255,255,.05)');
    ctx.strokeStyle = 'rgba(255,255,255,.1)';
    ctx.lineWidth = 2;
    ctx.stroke();
    ctx.fillStyle = grad;
    ctx.font = '800 64px Inter, sans-serif';
    ctx.textAlign = 'center';
    ctx.fillText(String(b.value), bx + boxW / 2, by + 80);
    ctx.fillStyle = 'rgba(255,255,255,.5)';
    ctx.font = '500 26px Inter, sans-serif';
    ctx.fillText(b.label, bx + boxW / 2, by + 130);
    bx += boxW + 40;
  });

  // 日期
  ctx.fillStyle = 'rgba(255,255,255,.35)';
  ctx.font = '500 28px Inter, sans-serif';
  ctx.fillText(session.date || new Date().toLocaleString('zh-CN'), W / 2, 1080);

  // 底部
  ctx.fillStyle = 'rgba(255,255,255,.55)';
  ctx.font = '700 36px Inter, sans-serif';
  ctx.fillText('坚持每一天，成为更好的自己', W / 2, 1200);
  ctx.fillStyle = 'rgba(255,255,255,.3)';
  ctx.font = '500 22px Inter, sans-serif';
  ctx.fillText('扫码下载 · FitCoach', W / 2, 1260);

  return canvas.toDataURL('image/png');
}

function roundRect(ctx, x, y, w, h, r, fill) {
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.arcTo(x + w, y, x + w, y + h, r);
  ctx.arcTo(x + w, y + h, x, y + h, r);
  ctx.arcTo(x, y + h, x, y, r);
  ctx.arcTo(x, y, x + w, y, r);
  ctx.closePath();
  if (fill) { ctx.fillStyle = fill; ctx.fill(); }
}

function formatDuration(s) {
  s = Math.round(s || 0);
  const m = Math.floor(s / 60);
  const rem = s % 60;
  return `${m}:${String(rem).padStart(2, '0')}`;
}

export function downloadPoster(dataUrl, filename = 'fitcoach-poster.png') {
  const a = document.createElement('a');
  a.href = dataUrl;
  a.download = filename;
  a.click();
}
