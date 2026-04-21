<script setup>
import { useRoute, useRouter } from 'vue-router';
import { ref, onMounted } from 'vue';
import { storage } from '@/modules/storage';
import { ACTION_DEFS } from '@/modules/exercise';
import { useAppStore } from '@/stores/app';

const route = useRoute();
const router = useRouter();
const app = useAppStore();
const session = ref(null);

onMounted(async () => {
  const all = await storage.getAllSessions();
  const id = route.params.id;
  session.value = all.find(s => String(s.remoteId ?? s.localId) === String(id)) || all[0];
});

function actionLabel(c) { return ACTION_DEFS[c]?.label || c; }
function fmt(s) {
  const m = Math.floor(s / 60);
  return `${String(m).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
}
</script>

<template>
  <div class="page-wrap detail-page">
    <button class="back-btn" @click="router.back()">
      <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M12 16l-6-6 6-6"/></svg>
      返回
    </button>

    <div v-if="session" class="detail-wrap">
      <div class="detail-hero">
        <div class="action-badge">{{ actionLabel(session.action) }}</div>
        <div class="big-num">{{ session.reps }}</div>
        <div class="big-sub">次 · 用时 {{ fmt(session.duration) }}</div>
        <div class="date">{{ session.date }}</div>
      </div>

      <div class="grid-scores">
        <div class="score-card">
          <div class="v">{{ session.score }}</div>
          <div class="l">综合评分</div>
        </div>
        <div class="score-card">
          <div class="v">{{ session.rhythmScore ?? '-' }}</div>
          <div class="l">节奏</div>
        </div>
        <div class="score-card">
          <div class="v">{{ session.stabilityScore ?? '-' }}</div>
          <div class="l">稳定度</div>
        </div>
        <div class="score-card">
          <div class="v">{{ session.depthScore ?? '-' }}</div>
          <div class="l">深度</div>
        </div>
        <div class="score-card">
          <div class="v">{{ session.symmetryScore ?? '-' }}</div>
          <div class="l">对称</div>
        </div>
        <div class="score-card">
          <div class="v">{{ session.completionScore ?? '-' }}</div>
          <div class="l">完成率</div>
        </div>
      </div>

      <div class="info-block">
        <div class="label">训练信息</div>
        <div class="kv"><span>目标次数</span><b>{{ session.targetReps ?? '-' }}</b></div>
        <div class="kv"><span>实际次数</span><b>{{ session.reps }}</b></div>
        <div class="kv"><span>训练时长</span><b>{{ fmt(session.duration) }}</b></div>
        <div class="kv"><span>同步状态</span><b :class="session.synced ? 'ok' : 'warn'">{{ session.synced ? '已同步' : '待同步' }}</b></div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.back-btn {
  display: flex; align-items: center; gap: 4px;
  font-size: 13px;
  color: var(--text-2);
  margin-bottom: 16px;
  padding: 6px 10px;
  border-radius: 8px;
  transition: all var(--transition);
}
.back-btn:hover { color: var(--text); background: var(--bg-card-2); }
.back-btn svg { width: 16px; height: 16px; }

.detail-hero {
  padding: 28px 20px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 20px;
  text-align: center;
  margin-bottom: 16px;
  position: relative;
  overflow: hidden;
}
.detail-hero::before {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(ellipse at top, rgba(0, 240, 255, .08), transparent 70%);
}
.action-badge {
  display: inline-block;
  padding: 4px 12px;
  border-radius: 100px;
  background: var(--cyan-dim);
  color: var(--cyan);
  font-size: 12px;
  font-weight: 600;
  margin-bottom: 16px;
}
.big-num {
  font-size: 64px;
  font-weight: 900;
  line-height: 1;
  background: var(--grad-primary);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}
.big-sub { font-size: 13px; color: var(--text-2); margin-top: 6px; }
.date { font-size: 12px; color: var(--text-3); margin-top: 8px; }

.grid-scores {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-bottom: 16px;
}
.score-card {
  padding: 14px 10px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 14px;
  text-align: center;
}
.score-card .v {
  font-size: 24px;
  font-weight: 800;
  color: var(--cyan);
}
.score-card .l { font-size: 10px; color: var(--text-3); margin-top: 2px; }

.info-block {
  padding: 16px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 14px;
}
.info-block .label { font-size: 11px; color: var(--text-3); margin-bottom: 10px; letter-spacing: .08em; }
.kv {
  display: flex;
  justify-content: space-between;
  padding: 10px 0;
  font-size: 13px;
  border-bottom: 1px solid var(--border);
}
.kv:last-child { border-bottom: none; }
.kv span { color: var(--text-2); }
.kv b { color: var(--text); font-weight: 600; }
.kv b.ok { color: var(--green); }
.kv b.warn { color: var(--orange); }
</style>
