<script setup>
import { ref, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import { useConfigStore } from '@/stores/config';
import { useAppStore } from '@/stores/app';
import { useAuthStore } from '@/stores/auth';
import { storage } from '@/modules/storage';
import { sessionApi } from '@/api/session';
import { ACTION_DEFS } from '@/modules/exercise';
import ProgressChart from '@/components/charts/ProgressChart.vue';
import HeatmapCalendar from '@/components/charts/HeatmapCalendar.vue';
import EmptyState from '@/components/common/EmptyState.vue';

const router = useRouter();
const config = useConfigStore();
const app = useAppStore();
const auth = useAuthStore();

const tab = ref('chart'); // chart | heatmap
const filter = ref('all');
const sessions = ref([]);

async function load() {
  if (auth.isLogin) {
    try {
      const res = await sessionApi.list({ page: 1, size: 500 });
      sessions.value = (res?.items || []).map(s => ({ ...s, date: s.sessionDate, synced: 1 }));
      return;
    } catch (_) { /* 回退本地 */ }
  }
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
  const msg = auth.isLogin
    ? '仅清空本机离线缓存，云端已同步的记录会保留并继续显示。确定继续？'
    : '确定清空所有本地训练记录？此操作不可撤销';
  const ok = await app.showConfirm('清空本地记录', msg);
  if (ok) {
    await storage.clearSessions();
    await load();
    app.showToast(auth.isLogin ? '本地缓存已清空（云端记录保留）' : '记录已清空', 'success');
  }
}

function openDetail(s) {
  const id = s.remoteId || s.id || s.localId;
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
      <div v-if="weeklyGoal.pct >= 100" class="weekly-done">目标达成！继续保持！</div>
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
      <li v-for="s in filtered" :key="s.localId ?? s.id" class="rec-item" @click="openDetail(s)">
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
.page-head h2 { font-family: var(--font-heading); font-size: 28px; font-weight: 700; color: var(--text); letter-spacing: -.04em; }
.page-head .sub { font-size: 12px; color: var(--text-2); margin-top: 4px; }

.weekly-card {
  position: relative;
  overflow: hidden;
  padding: 18px 20px;
  background:
    radial-gradient(90% 120% at 100% 0%, rgba(224, 144, 106, .14), transparent 55%),
    var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  margin-bottom: 14px;
  animation: fadeInUp .45s ease both;
}
.weekly-top { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 12px; }
.weekly-label { font-size: 13px; color: var(--text-2); font-weight: 600; }
.weekly-num {
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
  font-size: 18px;
  font-weight: 600;
  color: var(--text);
}
.progress-bar {
  height: 10px;
  background: var(--bg-card-2);
  border-radius: 100px;
  overflow: hidden;
}
.progress-fill {
  height: 100%;
  background: var(--grad-primary);
  border-radius: 100px;
  transition: width .6s cubic-bezier(.4, 0, .2, 1);
}
.weekly-done {
  margin-top: 10px;
  font-size: 12px;
  color: var(--green);
  text-align: center;
  font-weight: 700;
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
  padding: 14px 16px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  transition: all var(--transition);
  animation: fadeInUp .4s ease both;
}
/* 错峰入场：前 8 条依次延迟 */
.rec-item:nth-child(1) { animation-delay: .03s; }
.rec-item:nth-child(2) { animation-delay: .07s; }
.rec-item:nth-child(3) { animation-delay: .11s; }
.rec-item:nth-child(4) { animation-delay: .15s; }
.rec-item:nth-child(5) { animation-delay: .19s; }
.rec-item:nth-child(6) { animation-delay: .23s; }
.rec-item:nth-child(7) { animation-delay: .27s; }
.rec-item:nth-child(8) { animation-delay: .31s; }
.rec-item:hover { border-color: var(--border-hover); transform: translateY(-2px); box-shadow: var(--shadow-sm); }
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
.rec-stat span {
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
  font-size: 16px;
  font-weight: 600;
  color: var(--text);
  display: block;
}
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
