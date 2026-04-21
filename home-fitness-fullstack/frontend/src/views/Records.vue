<script setup>
import { ref, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import { useConfigStore } from '@/stores/config';
import { useAppStore } from '@/stores/app';
import { storage } from '@/modules/storage';
import { ACTION_DEFS } from '@/modules/exercise';
import ProgressChart from '@/components/charts/ProgressChart.vue';
import HeatmapCalendar from '@/components/charts/HeatmapCalendar.vue';
import EmptyState from '@/components/common/EmptyState.vue';

const router = useRouter();
const config = useConfigStore();
const app = useAppStore();

const tab = ref('chart'); // chart | heatmap
const filter = ref('all');
const sessions = ref([]);

async function load() {
  sessions.value = await storage.getAllSessions();
}

const filtered = computed(() => {
  if (filter.value === 'all') return sessions.value;
  return sessions.value.filter(s => s.action === filter.value);
});

const weeklyGoal = computed(() => {
  const goal = config.weeklyGoal;
  const now = new Date();
  const dow = now.getDay() || 7;
  const weekStart = new Date(now);
  weekStart.setDate(now.getDate() - dow + 1);
  weekStart.setHours(0, 0, 0, 0);
  const reps = sessions.value
    .filter(s => new Date(s.date) >= weekStart)
    .reduce((sum, s) => sum + (s.reps || 0), 0);
  return { reps, goal, pct: Math.min(100, Math.round(reps / goal * 100)) };
});

function fmtDuration(s) {
  const m = Math.floor(s / 60);
  return `${String(m).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
}

function actionLabel(code) {
  return ACTION_DEFS[code]?.label || code;
}

async function exportCsv() {
  if (!sessions.value.length) {
    app.showToast('暂无记录可导出', 'warning');
    return;
  }
  const csv = storage.sessionsToCSV(sessions.value);
  storage.download(csv, `训练记录_全部.csv`);
  app.showToast('导出成功', 'success');
}

async function clearAll() {
  const ok = await app.showConfirm('清空记录', '确定清空所有本地训练记录？此操作不可撤销');
  if (ok) {
    await storage.clearSessions();
    await load();
    app.showToast('记录已清空', 'success');
  }
}

function openDetail(s) {
  const id = s.remoteId || s.localId;
  if (id) router.push(`/records/${id}`);
}

onMounted(load);
</script>

<template>
  <div class="page-wrap records-page">
    <div class="page-head">
      <h2>训练记录</h2>
      <p class="sub">查看你的健身历程</p>
    </div>

    <!-- 周目标 -->
    <div class="weekly-card">
      <div class="weekly-top">
        <span class="weekly-label">本周目标</span>
        <span class="weekly-num">{{ weeklyGoal.reps }} / {{ weeklyGoal.goal }} 次</span>
      </div>
      <div class="progress-bar">
        <div class="progress-fill" :style="{ width: weeklyGoal.pct + '%' }"></div>
      </div>
      <div v-if="weeklyGoal.pct >= 100" class="weekly-done">🎉 目标达成！继续保持！</div>
    </div>

    <!-- 视图切换 -->
    <div class="view-tabs">
      <button :class="['view-tab', tab === 'chart' ? 'active' : '']" @click="tab = 'chart'">趋势</button>
      <button :class="['view-tab', tab === 'heatmap' ? 'active' : '']" @click="tab = 'heatmap'">热力图</button>
    </div>

    <div class="chart-wrap">
      <ProgressChart v-if="tab === 'chart'" :sessions="filtered" />
      <HeatmapCalendar v-else :sessions="filtered" />
    </div>

    <!-- 筛选 -->
    <div class="filter-bar">
      <select v-model="filter">
        <option value="all">全部动作</option>
        <option v-for="[code, def] in Object.entries(ACTION_DEFS)" :key="code" :value="code">
          {{ def.label }}
        </option>
      </select>
      <div class="toolbar-btns">
        <button class="outline" @click="exportCsv">
          <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"><path d="M10 3v10 M7 10l3 3 3-3 M3 14v2a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-2"/></svg>
          导出
        </button>
        <button class="outline danger" @click="clearAll">
          <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"><path d="M4 5h12 M7 5V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v1 M16 5l-.7 10.5a2 2 0 0 1-2 1.5H7.7a2 2 0 0 1-2-1.5L5 5z"/></svg>
          清空
        </button>
      </div>
    </div>

    <ul v-if="filtered.length" class="records-list">
      <li v-for="s in filtered" :key="s.localId" class="rec-item" @click="openDetail(s)">
        <div class="rec-left">
          <div class="rec-icon">
            <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"><path d="M10 2v16 M4 8h12 M4 14h12"/></svg>
          </div>
          <div class="rec-main">
            <div class="rec-action">
              <span>{{ actionLabel(s.action) }}</span>
              <span v-if="!s.synced" class="pending-tag" title="离线暂存，登录后可同步">离线</span>
            </div>
            <div class="rec-date">{{ s.date }}</div>
          </div>
        </div>
        <div class="rec-stats">
          <div class="rec-stat"><span>{{ s.reps }}</span><small>次</small></div>
          <div class="rec-stat"><span>{{ s.score }}</span><small>分</small></div>
          <div class="rec-stat"><span>{{ fmtDuration(s.duration) }}</span><small>时长</small></div>
        </div>
      </li>
    </ul>

    <EmptyState v-else title="暂无训练记录" desc="完成一次训练后这里会显示数据" />
  </div>
</template>

<style scoped>
.page-head { margin-bottom: 16px; }
.page-head h2 { font-size: 26px; font-weight: 800; background: var(--grad-primary); -webkit-background-clip: text; -webkit-text-fill-color: transparent; background-clip: text; }
.page-head .sub { font-size: 12px; color: var(--text-2); margin-top: 4px; }

.weekly-card {
  padding: 14px 16px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 16px;
  margin-bottom: 14px;
}
.weekly-top { display: flex; justify-content: space-between; margin-bottom: 10px; }
.weekly-label { font-size: 13px; color: var(--text-2); font-weight: 500; }
.weekly-num { font-size: 14px; font-weight: 700; color: var(--cyan); }
.progress-bar {
  height: 8px;
  background: var(--bg-card-2);
  border-radius: 100px;
  overflow: hidden;
}
.progress-fill {
  height: 100%;
  background: var(--grad-primary);
  border-radius: 100px;
  transition: width .6s cubic-bezier(.4, 0, .2, 1);
  box-shadow: 0 0 10px var(--cyan);
}
.weekly-done {
  margin-top: 8px;
  font-size: 12px;
  color: var(--green);
  text-align: center;
  font-weight: 600;
}

.view-tabs {
  display: flex;
  gap: 6px;
  margin-bottom: 10px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 4px;
  width: fit-content;
}
.view-tab {
  padding: 8px 18px;
  border-radius: 8px;
  color: var(--text-3);
  font-size: 13px;
  font-weight: 500;
  transition: all var(--transition);
}
.view-tab.active {
  background: var(--cyan-dim);
  color: var(--cyan);
}

.chart-wrap { margin-bottom: 16px; }

.filter-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.filter-bar select {
  width: auto;
  flex-shrink: 0;
  padding: 8px 14px;
  font-size: 13px;
  cursor: pointer;
}
.toolbar-btns { display: flex; gap: 8px; margin-left: auto; }
.outline {
  padding: 8px 14px;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--bg-card);
  color: var(--text-2);
  font-size: 12px;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 4px;
  transition: all var(--transition);
}
.outline svg { width: 14px; height: 14px; }
.outline:hover { color: var(--text); border-color: var(--border-hover); }
.outline.danger:hover { color: var(--red); border-color: var(--red); }

.records-list { list-style: none; display: flex; flex-direction: column; gap: 10px; }
.rec-item {
  padding: 14px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 14px;
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  transition: all var(--transition);
}
.rec-item:hover { border-color: var(--border-hover); transform: translateY(-1px); }
.rec-left { display: flex; align-items: center; gap: 10px; min-width: 0; flex-shrink: 1; }
.rec-main { min-width: 0; }
.rec-icon {
  width: 36px; height: 36px;
  border-radius: 10px;
  background: var(--cyan-dim);
  color: var(--cyan);
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.rec-icon svg { width: 18px; height: 18px; }
.rec-action {
  font-size: 14px;
  font-weight: 700;
  color: var(--text);
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.rec-date { font-size: 11px; color: var(--text-3); }

.rec-stats { margin-left: auto; display: flex; gap: 14px; flex-shrink: 0; }
.rec-stat { text-align: center; }
.rec-stat span { font-size: 15px; font-weight: 700; color: var(--cyan); display: block; }
.rec-stat small { font-size: 9px; color: var(--text-3); }

.pending-tag {
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 6px;
  background: var(--orange-dim);
  color: var(--orange);
  font-weight: 500;
}

@media (max-width: 480px) {
  .rec-stats { display: none; }
  .rec-item { padding: 12px; }
}
</style>
