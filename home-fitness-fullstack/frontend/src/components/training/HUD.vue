<script setup>
defineProps({
  reps: [Number, String],
  score: [Number, String],
  time: String,
  angle: [Number, String]
});
</script>

<template>
  <div class="hud">
    <div class="hud-item">
      <div class="ring ring-cyan">
        <span>{{ reps ?? 0 }}</span>
      </div>
      <small>次数</small>
    </div>
    <div class="hud-item">
      <div class="ring ring-purple">
        <span>{{ score ?? '-' }}</span>
      </div>
      <small>评分</small>
    </div>
    <div class="hud-item">
      <div class="ring ring-green">
        <span>{{ time ?? '00:00' }}</span>
      </div>
      <small>用时</small>
    </div>
    <div class="hud-item">
      <div class="ring ring-orange">
        <span>{{ angle != null ? `${angle}°` : '-°' }}</span>
      </div>
      <small>角度</small>
    </div>
  </div>
</template>

<style scoped>
.hud {
  position: absolute;
  top: 12px; left: 10px; right: 10px;
  display: flex;
  justify-content: space-around;
  pointer-events: none;
  gap: 4px;
  z-index: 5;
}
.hud-item { text-align: center; }
.ring {
  width: 52px; height: 52px;
  border-radius: 50%;
  background: rgba(0, 0, 0, .55);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto;
  border: 2px solid rgba(255, 255, 255, .08);
  transition: border-color .3s;
  font-size: 14px;
  font-weight: 700;
  color: #fff;
}
.ring-cyan   { border-color: rgba(0, 240, 255, .45); }
.ring-purple { border-color: rgba(124, 106, 255, .45); }
.ring-green  { border-color: rgba(0, 229, 138, .45); }
.ring-orange { border-color: rgba(255, 159, 67, .45); }

.hud-item small {
  display: block;
  margin-top: 4px;
  font-size: 10px;
  color: rgba(255, 255, 255, .7);
  font-weight: 500;
}

@media (min-width: 768px) {
  .ring { width: 60px; height: 60px; font-size: 16px; }
  .hud-item small { font-size: 11px; }
}
</style>
