<script setup>
import { ref } from 'vue';

const dashboardData = ref({
  users: 1234,
  sessions: 8567,
  todaySessions: 92,
  dau: 318
});
</script>

<template>
  <div class="page-wrap admin-page">
    <div class="page-head">
      <h2>管理后台</h2>
      <p class="sub">后端接口对接后即可启用</p>
    </div>

    <div class="dashboard">
      <div class="dash-card">
        <div class="dc-v">{{ dashboardData.users }}</div>
        <div class="dc-l">注册用户</div>
      </div>
      <div class="dash-card">
        <div class="dc-v">{{ dashboardData.sessions }}</div>
        <div class="dc-l">训练总次</div>
      </div>
      <div class="dash-card">
        <div class="dc-v">{{ dashboardData.todaySessions }}</div>
        <div class="dc-l">今日训练</div>
      </div>
      <div class="dash-card">
        <div class="dc-v">{{ dashboardData.dau }}</div>
        <div class="dc-l">今日 DAU</div>
      </div>
    </div>

    <div class="quick-grid">
      <div class="qk-card">
        <div class="qk-ico" style="background: var(--cyan-dim); color: var(--cyan);">
          <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.6"><circle cx="10" cy="8" r="3"/><path d="M3 18a7 7 0 0 1 14 0"/></svg>
        </div>
        <h4>用户管理</h4>
        <p>搜索、封禁、权限</p>
      </div>
      <div class="qk-card">
        <div class="qk-ico" style="background: var(--purple-dim); color: var(--purple);">
          <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.6"><rect x="3" y="3" width="14" height="14" rx="2"/><path d="M3 8h14 M8 17V8"/></svg>
        </div>
        <h4>动作库</h4>
        <p>新增、编辑动作配置</p>
      </div>
      <div class="qk-card">
        <div class="qk-ico" style="background: var(--green-dim); color: var(--green);">
          <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.6"><rect x="3" y="4" width="14" height="13" rx="2"/><path d="M3 8h14 M7 2v4 M13 2v4"/></svg>
        </div>
        <h4>训练计划</h4>
        <p>发布官方课程</p>
      </div>
      <div class="qk-card">
        <div class="qk-ico" style="background: var(--orange-dim); color: var(--orange);">
          <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.6"><path d="M3 17V7l6-4 8 4v10 M9 11l3-2 3 2"/></svg>
        </div>
        <h4>数据分析</h4>
        <p>留存、评分分布</p>
      </div>
    </div>

    <div class="info-notice">
      <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"><circle cx="10" cy="10" r="7"/><path d="M10 7v4 M10 13h.01"/></svg>
      当前为 UI 占位页面 · 需后端接口上线后启用实际功能
    </div>
  </div>
</template>

<style scoped>
.page-head { margin-bottom: 16px; }
.page-head h2 { font-size: 26px; font-weight: 800; background: var(--grad-primary); -webkit-background-clip: text; -webkit-text-fill-color: transparent; background-clip: text; }
.page-head .sub { font-size: 12px; color: var(--text-2); margin-top: 4px; }

.dashboard {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
  margin-bottom: 16px;
}
@media (min-width: 640px) { .dashboard { grid-template-columns: repeat(4, 1fr); } }

.dash-card {
  padding: 16px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 14px;
  text-align: center;
}
.dc-v {
  font-size: 28px;
  font-weight: 900;
  background: var(--grad-primary);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}
.dc-l { font-size: 11px; color: var(--text-3); margin-top: 2px; }

.quick-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
  margin-bottom: 16px;
}
@media (min-width: 768px) { .quick-grid { grid-template-columns: repeat(4, 1fr); } }

.qk-card {
  padding: 18px 14px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 14px;
  cursor: pointer;
  transition: all var(--transition);
}
.qk-card:hover { transform: translateY(-2px); border-color: var(--border-hover); }
.qk-ico {
  width: 40px; height: 40px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 10px;
}
.qk-ico svg { width: 22px; height: 22px; }
.qk-card h4 { font-size: 14px; font-weight: 700; margin-bottom: 2px; }
.qk-card p { font-size: 11px; color: var(--text-3); }

.info-notice {
  margin-top: 20px;
  padding: 12px 14px;
  background: var(--orange-dim);
  border: 1px solid rgba(255, 159, 67, .3);
  border-radius: 12px;
  color: var(--orange);
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.info-notice svg { width: 16px; height: 16px; flex-shrink: 0; }
</style>
