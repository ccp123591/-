<script setup>
import { ref } from 'vue';

const tab = ref('official');

const officialPlans = [
  { id: 1, title: '新手入门 7 天', desc: '零基础友好，每天 15 分钟', cover: '#00F0FF', level: '新手', days: 7 },
  { id: 2, title: '核心强化 14 天', desc: '针对核心力量的系统训练', cover: '#7C6AFF', level: '进阶', days: 14 },
  { id: 3, title: '30 天俯卧撑挑战', desc: '从 10 个到 100 个的进阶', cover: '#FF9F43', level: '进阶', days: 30 },
  { id: 4, title: '柔韧性提升', desc: '每天 20 分钟拉伸', cover: '#00E58A', level: '新手', days: 10 }
];

const myPlans = [
  { id: 5, title: '新手入门 7 天', progress: 3, total: 7, today: true }
];
</script>

<template>
  <div class="page-wrap plans-page">
    <div class="page-head">
      <h2>训练计划</h2>
      <p class="sub">选择适合你的训练方案</p>
    </div>

    <div class="tabs">
      <button :class="['tab', tab === 'official' ? 'active' : '']" @click="tab = 'official'">官方推荐</button>
      <button :class="['tab', tab === 'mine' ? 'active' : '']" @click="tab = 'mine'">我的计划</button>
      <button :class="['tab', tab === 'market' ? 'active' : '']" @click="tab = 'market'">社区</button>
    </div>

    <div v-if="tab === 'official'" class="plans-grid">
      <div v-for="p in officialPlans" :key="p.id" class="plan-card">
        <div class="plan-cover" :style="{ background: `linear-gradient(135deg, ${p.cover}, var(--bg-card-2))` }">
          <span class="level-tag">{{ p.level }}</span>
          <div class="cover-deco"></div>
        </div>
        <div class="plan-body">
          <div class="plan-title">{{ p.title }}</div>
          <div class="plan-desc">{{ p.desc }}</div>
          <div class="plan-foot">
            <span class="days">🗓 {{ p.days }} 天</span>
            <button class="adopt">采用</button>
          </div>
        </div>
      </div>
    </div>

    <div v-else-if="tab === 'mine'" class="mine-list">
      <div v-if="myPlans.length" v-for="p in myPlans" :key="p.id" class="mine-card">
        <div class="mine-head">
          <div class="mine-title">{{ p.title }}</div>
          <span v-if="p.today" class="today-tag">今日可练</span>
        </div>
        <div class="mine-prog-bar">
          <div class="mine-prog-fill" :style="{ width: `${p.progress / p.total * 100}%` }"></div>
        </div>
        <div class="mine-prog-text">{{ p.progress }} / {{ p.total }} 天</div>
      </div>
      <div v-if="!myPlans.length" class="empty-p">
        <p>还未采用任何计划</p>
        <button class="btn-small" @click="tab = 'official'">浏览官方计划</button>
      </div>
    </div>

    <div v-else class="placeholder">
      <div>社区计划市场即将上线</div>
      <small>敬请期待</small>
    </div>
  </div>
</template>

<style scoped>
.page-head { margin-bottom: 16px; }
.page-head h2 { font-size: 26px; font-weight: 800; background: var(--grad-primary); -webkit-background-clip: text; -webkit-text-fill-color: transparent; background-clip: text; }
.page-head .sub { font-size: 12px; color: var(--text-2); margin-top: 4px; }

.tabs {
  display: flex;
  gap: 6px;
  margin-bottom: 16px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 4px;
}
.tab {
  flex: 1;
  padding: 10px;
  border-radius: 8px;
  color: var(--text-3);
  font-size: 13px;
  font-weight: 600;
  transition: all var(--transition);
}
.tab.active { background: var(--cyan-dim); color: var(--cyan); }

.plans-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px;
}
@media (min-width: 640px) { .plans-grid { grid-template-columns: 1fr 1fr; } }
@media (min-width: 1024px) { .plans-grid { grid-template-columns: 1fr 1fr 1fr; } }

.plan-card {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 16px;
  overflow: hidden;
  transition: all var(--transition);
}
.plan-card:hover {
  transform: translateY(-2px);
  border-color: var(--border-hover);
}
.plan-cover {
  height: 100px;
  position: relative;
  overflow: hidden;
}
.cover-deco {
  position: absolute;
  top: -40px; right: -40px;
  width: 120px; height: 120px;
  border-radius: 50%;
  background: rgba(255, 255, 255, .1);
}
.level-tag {
  position: absolute;
  top: 10px; left: 10px;
  padding: 3px 8px;
  border-radius: 100px;
  background: rgba(0, 0, 0, .4);
  backdrop-filter: blur(8px);
  color: #fff;
  font-size: 10px;
  font-weight: 600;
}
.plan-body { padding: 14px; }
.plan-title { font-size: 14px; font-weight: 700; margin-bottom: 4px; }
.plan-desc { font-size: 11px; color: var(--text-3); margin-bottom: 12px; line-height: 1.5; }
.plan-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.days { font-size: 11px; color: var(--text-3); }
.adopt {
  padding: 6px 14px;
  border-radius: 100px;
  background: var(--grad-primary);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  transition: transform var(--transition);
}
.adopt:active { transform: scale(.96); }

.mine-list { display: flex; flex-direction: column; gap: 10px; }
.mine-card {
  padding: 14px 16px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 14px;
}
.mine-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}
.mine-title { font-size: 14px; font-weight: 700; }
.today-tag {
  padding: 3px 10px;
  border-radius: 100px;
  background: var(--cyan-dim);
  color: var(--cyan);
  font-size: 10px;
  font-weight: 600;
}
.mine-prog-bar {
  height: 6px;
  background: var(--bg-card-2);
  border-radius: 100px;
  overflow: hidden;
  margin-bottom: 4px;
}
.mine-prog-fill {
  height: 100%;
  background: var(--grad-primary);
  border-radius: 100px;
}
.mine-prog-text { font-size: 11px; color: var(--text-3); }

.empty-p {
  text-align: center;
  padding: 40px 20px;
  color: var(--text-3);
}
.empty-p p { margin-bottom: 12px; font-size: 13px; }
.btn-small {
  padding: 8px 18px;
  border-radius: 100px;
  background: var(--cyan-dim);
  color: var(--cyan);
  font-size: 12px;
  font-weight: 600;
}

.placeholder {
  text-align: center;
  padding: 60px 20px;
  color: var(--text-3);
}
.placeholder div { font-size: 14px; margin-bottom: 4px; }
.placeholder small { font-size: 11px; }
</style>
