<script setup>
import { computed } from 'vue';

const props = defineProps({
  sessions: { type: Array, default: () => [] },
  weeks: { type: Number, default: 12 }
});

// 生成 12 周 × 7 天的热力图
const grid = computed(() => {
  const map = {};
  props.sessions.forEach(s => {
    const d = (s.date || '').slice(0, 10);
    if (!d) return;
    map[d] = (map[d] || 0) + (s.reps || 0);
  });

  const today = new Date();
  today.setHours(0, 0, 0, 0);
  // 回到今日起的第几周，让最后一周是本周
  const endWeekMonday = new Date(today);
  const dow = today.getDay() || 7;
  endWeekMonday.setDate(today.getDate() - dow + 1);

  const cols = [];
  for (let w = props.weeks - 1; w >= 0; w--) {
    const col = [];
    for (let d = 0; d < 7; d++) {
      const dt = new Date(endWeekMonday);
      dt.setDate(endWeekMonday.getDate() - w * 7 + d);
      const key = dt.toISOString().slice(0, 10);
      const v = map[key] || 0;
      col.push({ date: key, value: v, future: dt > today });
    }
    cols.unshift(col);
  }
  return cols;
});

const max = computed(() => {
  let m = 0;
  grid.value.forEach(col => col.forEach(cell => { if (cell.value > m) m = cell.value; }));
  return m || 1;
});

function level(v) {
  if (!v) return 0;
  const ratio = v / max.value;
  if (ratio > 0.75) return 4;
  if (ratio > 0.5)  return 3;
  if (ratio > 0.25) return 2;
  return 1;
}
</script>

<template>
  <div class="heatmap">
    <div class="labels">
      <span>一</span><span>二</span><span>三</span><span>四</span><span>五</span><span>六</span><span>日</span>
    </div>
    <div class="grid">
      <div v-for="(col, i) in grid" :key="i" class="col">
        <div
          v-for="cell in col"
          :key="cell.date"
          :class="['cell', `lv-${level(cell.value)}`, { future: cell.future }]"
          :title="`${cell.date}: ${cell.value} 次`"
        ></div>
      </div>
    </div>
    <div class="legend">
      <span class="lbl">少</span>
      <div class="cell lv-0"></div>
      <div class="cell lv-1"></div>
      <div class="cell lv-2"></div>
      <div class="cell lv-3"></div>
      <div class="cell lv-4"></div>
      <span class="lbl">多</span>
    </div>
  </div>
</template>

<style scoped>
.heatmap {
  padding: 16px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 16px;
}
.labels {
  display: grid;
  grid-template-columns: 1fr;
  grid-template-rows: repeat(7, 1fr);
  gap: 3px;
  font-size: 9px;
  color: var(--text-3);
  float: left;
  width: 16px;
  height: 116px;
  padding-right: 4px;
  line-height: 14px;
}
.grid {
  display: flex;
  gap: 3px;
  margin-left: 20px;
  overflow-x: auto;
  padding-bottom: 4px;
}
.col {
  display: flex;
  flex-direction: column;
  gap: 3px;
  flex-shrink: 0;
}
.cell {
  width: 14px; height: 14px;
  border-radius: 3px;
  background: var(--bg-card-2);
  transition: transform var(--transition);
}
.cell:hover { transform: scale(1.3); }
.cell.future { opacity: .3; }
.cell.lv-1 { background: rgba(0, 240, 255, .25); }
.cell.lv-2 { background: rgba(0, 240, 255, .5);  }
.cell.lv-3 { background: rgba(0, 240, 255, .75); }
.cell.lv-4 { background: var(--cyan); box-shadow: 0 0 8px var(--cyan); }

.legend {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 12px;
  font-size: 10px;
  color: var(--text-3);
  justify-content: flex-end;
}
.legend .cell { width: 10px; height: 10px; }
.lbl { margin: 0 6px; }
</style>
