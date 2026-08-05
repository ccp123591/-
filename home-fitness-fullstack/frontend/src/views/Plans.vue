<script setup>
import { ref, computed, watch, onMounted } from 'vue';
import { planApi } from '@/api/plan';
import { useAuthStore } from '@/stores/auth';
import { useAppStore } from '@/stores/app';

const auth = useAuthStore();
const app = useAppStore();
const tab = ref('official');

const officialPlans = ref([]);
const marketPlans = ref([]);
const myPlans = ref([]);
const loading = ref(false);
const adopting = ref(0);

const LEVEL = { NEWBIE: '新手', INTERMEDIATE: '进阶', ADVANCED: '高级' };
const levelLabel = (l) => LEVEL[l] || l || '新手';

/* 计划封面插画 — 按标题/描述关键词匹配运动小人（头部圆用 arc 画进 path） */
const COVER_PATHS = {
  squat:   'M52 26 m-5 0 a5 5 0 1 0 10 0 a5 5 0 1 0 -10 0 M52 31 L50 44 L42 54 L45 68 M50 44 L60 52 L58 68 M51 36 L68 40',
  plank:   'M28 50 m-5 0 a5 5 0 1 0 10 0 a5 5 0 1 0 -10 0 M33 52 L76 46 L94 52 M48 51 L50 66 M76 46 L82 64',
  stretch: 'M68 34 m-5 0 a5 5 0 1 0 10 0 a5 5 0 1 0 -10 0 M65 38 L52 48 L52 68 M52 48 L46 68 M64 42 L50 60',
  jump:    'M60 20 m-5 0 a5 5 0 1 0 10 0 a5 5 0 1 0 -10 0 M60 25 L60 45 M60 30 L44 18 M60 30 L76 18 M60 45 L46 67 M60 45 L74 67',
  pushup:  'M30 52 m-5 0 a5 5 0 1 0 10 0 a5 5 0 1 0 -10 0 M35 54 L78 48 L94 54 M46 54 L46 67 M74 50 L78 66'
};
function coverArt(p) {
  const t = `${p.title || ''}${p.description || ''}`;
  if (/蹲|腿|下肢|臀/.test(t)) return COVER_PATHS.squat;
  if (/核心|平板|支撑|腹/.test(t)) return COVER_PATHS.plank;
  if (/拉伸|柔韧|舒展|放松/.test(t)) return COVER_PATHS.stretch;
  if (/胸|臂|俯卧撑|上肢|推/.test(t)) return COVER_PATHS.pushup;
  return COVER_PATHS.jump;   // 燃脂/有氧/全身默认开合跳
}
const gridList = computed(() => (tab.value === 'market' ? marketPlans.value : officialPlans.value));

async function loadOfficial() {
  officialPlans.value = (await planApi.official()) || [];
}
async function loadMine() {
  if (!auth.isLogin) { myPlans.value = []; return; }
  myPlans.value = (await planApi.myPlans()) || [];
}
async function loadMarket() {
  if (!auth.isLogin) { marketPlans.value = []; return; }
  const res = await planApi.market({ page: 1, size: 20 });
  marketPlans.value = res?.items || [];
}

async function adopt(p) {
  if (!auth.isLogin) { app.showToast('请先登录', 'warning'); return; }
  adopting.value = p.id;
  try {
    await planApi.adopt(p.id);
    app.showToast('已采用，开始训练吧', 'success');
    await loadMine();
    tab.value = 'mine';
  } catch (_) { /* 拦截器已提示 */ } finally { adopting.value = 0; }
}

watch(tab, (t) => {
  if (t === 'market' && !marketPlans.value.length) loadMarket();
  if (t === 'mine') loadMine();
});

onMounted(async () => {
  loading.value = true;
  try { await loadOfficial(); await loadMine(); } finally { loading.value = false; }
});
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

    <div v-if="tab === 'official' || tab === 'market'">
      <div v-if="gridList.length" class="plans-grid">
        <div v-for="p in gridList" :key="p.id" class="plan-card">
          <div class="plan-cover" :style="{ background: `linear-gradient(135deg, ${p.cover || '#e0906a'}, var(--bg-card-2))` }">
            <span class="level-tag">{{ levelLabel(p.level) }}</span>
            <div class="cover-deco"></div>
            <!-- 按计划主题匹配的运动小场景插画 -->
            <svg class="cover-art" viewBox="0 0 120 80" fill="none"
                 stroke="rgba(255,255,255,.85)" stroke-width="3"
                 stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <circle cx="102" cy="14" r="7" fill="rgba(255,255,255,.28)" stroke="none"/>
              <path d="M14 70 H106" stroke="rgba(255,255,255,.35)" stroke-width="2"/>
              <path :d="coverArt(p)"/>
            </svg>
          </div>
          <div class="plan-body">
            <div class="plan-title">{{ p.title }}</div>
            <div class="plan-desc">{{ p.description }}</div>
            <div class="plan-foot">
              <span class="days">{{ p.days }} 天 · {{ p.adoptCount || 0 }} 人在练</span>
              <button class="adopt" :disabled="adopting === p.id" @click="adopt(p)">采用</button>
            </div>
          </div>
        </div>
      </div>
      <div v-else class="placeholder">
        <div>{{ tab === 'market' && !auth.isLogin ? '登录后查看社区计划' : '暂无计划' }}</div>
        <small>{{ tab === 'market' ? '看看大家分享的训练方案' : '官方计划即将上线' }}</small>
      </div>
    </div>

    <div v-else-if="tab === 'mine'" class="mine-list">
      <div v-for="p in myPlans" :key="p.planId" class="mine-card">
        <div class="mine-head">
          <div class="mine-title">{{ p.title }}</div>
          <span class="today-tag">{{ levelLabel(p.level) }}</span>
        </div>
        <div class="mine-prog-bar">
          <div class="mine-prog-fill" :style="{ width: `${(p.progressDay || 0) / Math.max(1, p.days) * 100}%` }"></div>
        </div>
        <div class="mine-prog-text">{{ p.progressDay || 0 }} / {{ p.days }} 天</div>
      </div>
      <div v-if="!myPlans.length" class="empty-p">
        <p>{{ auth.isLogin ? '还未采用任何计划' : '登录后可采用并跟练计划' }}</p>
        <button class="btn-small" @click="tab = 'official'">浏览官方计划</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-head { margin-bottom: 16px; }
.page-head h2 { font-family: var(--font-heading); font-size: 28px; font-weight: 700; color: var(--text); letter-spacing: -.04em; }
.page-head .sub { font-size: 12px; color: var(--text-2); margin-top: 4px; }

.tabs {
  display: flex;
  gap: 6px;
  margin-bottom: 16px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 4px;
  box-shadow: var(--shadow-sm);
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
  box-shadow: var(--shadow-sm);
  transition: all var(--transition);
}
.plan-card:hover {
  transform: translateY(-2px);
  border-color: var(--border-hover);
  box-shadow: var(--shadow);
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
.cover-art {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  filter: drop-shadow(0 2px 4px rgba(0, 0, 0, .12));
}
.plan-card:hover .cover-art { transform: translateY(-2px); transition: transform .25s ease; }
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
  box-shadow: var(--shadow-sm);
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
