<script setup>
import { ref, computed } from 'vue';

const tab = ref('weekly');

// 占位数据
const mockData = {
  weekly: [
    { rank: 1, name: '健身达人小王', reps: 1234, score: 95, avatar: '', you: false },
    { rank: 2, name: '晨跑小李', reps: 1100, score: 92, avatar: '', you: false },
    { rank: 3, name: '肌肉男张', reps: 980, score: 89, avatar: '', you: false },
    { rank: 4, name: '你', reps: 820, score: 88, avatar: '', you: true },
    { rank: 5, name: 'FitGirl', reps: 750, score: 87, avatar: '', you: false },
    { rank: 6, name: '卷王刘', reps: 720, score: 85, avatar: '', you: false },
    { rank: 7, name: '瑜伽妹妹', reps: 680, score: 90, avatar: '', you: false },
    { rank: 8, name: '力量派', reps: 650, score: 82, avatar: '', you: false }
  ],
  monthly: [],
  friends: []
};

const data = computed(() => mockData[tab.value] || []);

const top3 = computed(() => data.value.slice(0, 3));
const rest = computed(() => data.value.slice(3));

function medal(r) {
  if (r === 1) return '🥇';
  if (r === 2) return '🥈';
  if (r === 3) return '🥉';
  return `#${r}`;
}
</script>

<template>
  <div class="page-wrap lb-page">
    <div class="page-head">
      <h2>排行榜</h2>
      <p class="sub">看看其他人有多努力</p>
    </div>

    <div class="tabs">
      <button :class="['tab', tab === 'weekly' ? 'active' : '']" @click="tab = 'weekly'">本周</button>
      <button :class="['tab', tab === 'monthly' ? 'active' : '']" @click="tab = 'monthly'">本月</button>
      <button :class="['tab', tab === 'friends' ? 'active' : '']" @click="tab = 'friends'">好友</button>
    </div>

    <!-- Top3 Podium -->
    <div v-if="top3.length" class="podium">
      <div class="podium-card rank-2">
        <div class="rank-medal">🥈</div>
        <div class="pd-avatar"></div>
        <div class="pd-name">{{ top3[1]?.name }}</div>
        <div class="pd-score">{{ top3[1]?.reps }} 次</div>
      </div>
      <div class="podium-card rank-1">
        <div class="rank-crown">👑</div>
        <div class="rank-medal">🥇</div>
        <div class="pd-avatar"></div>
        <div class="pd-name">{{ top3[0]?.name }}</div>
        <div class="pd-score">{{ top3[0]?.reps }} 次</div>
      </div>
      <div class="podium-card rank-3">
        <div class="rank-medal">🥉</div>
        <div class="pd-avatar"></div>
        <div class="pd-name">{{ top3[2]?.name }}</div>
        <div class="pd-score">{{ top3[2]?.reps }} 次</div>
      </div>
    </div>

    <!-- Rest List -->
    <ul v-if="rest.length" class="rank-list">
      <li
        v-for="u in rest"
        :key="u.rank"
        :class="['rank-item', u.you ? 'you' : '']"
      >
        <div class="rk">{{ medal(u.rank) }}</div>
        <div class="avatar" :style="{ background: 'var(--grad-primary)' }">{{ u.name[0] }}</div>
        <div class="info">
          <div class="n-row">
            <span class="n">{{ u.name }}</span>
            <span v-if="u.you" class="you-tag">你</span>
          </div>
          <div class="s">综合评分 {{ u.score }}</div>
        </div>
        <div class="reps">{{ u.reps }} 次</div>
      </li>
    </ul>

    <div v-else-if="tab === 'friends'" class="placeholder">
      <div>暂无好友数据</div>
      <small>绑定账号并关注好友后可见</small>
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

.podium {
  display: grid;
  grid-template-columns: 1fr 1.1fr 1fr;
  gap: 8px;
  align-items: end;
  margin: 20px 0;
}
.podium-card {
  padding: 14px 10px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 14px;
  text-align: center;
  position: relative;
}
.rank-1 {
  padding: 22px 10px 16px;
  border-color: rgba(255, 215, 0, .4);
  background: linear-gradient(180deg, rgba(255, 215, 0, .08), var(--bg-card));
  transform: translateY(-8px);
}
.rank-2 { padding-top: 16px; }
.rank-3 { padding-top: 12px; }

.rank-crown { position: absolute; top: -20px; left: 50%; transform: translateX(-50%); font-size: 24px; }
.rank-medal { font-size: 24px; margin-bottom: 4px; }

.pd-avatar {
  width: 48px; height: 48px;
  margin: 0 auto 6px;
  border-radius: 50%;
  background: var(--grad-primary);
}
.pd-name { font-size: 12px; font-weight: 700; margin-bottom: 2px; }
.pd-score {
  font-size: 14px;
  font-weight: 800;
  color: var(--cyan);
}

.rank-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  list-style: none;
}
.rank-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 12px;
  transition: all var(--transition);
}
.rank-item.you {
  border-color: var(--cyan);
  background: var(--cyan-dim);
}
.rk {
  width: 30px;
  font-size: 13px;
  font-weight: 700;
  color: var(--text-3);
  text-align: center;
}
.rank-item .avatar {
  width: 36px; height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 700;
  font-size: 14px;
}
.info { flex: 1; min-width: 0; }
.n-row { display: flex; align-items: center; gap: 6px; }
.n { font-size: 13px; font-weight: 600; }
.you-tag {
  font-size: 9px;
  padding: 1px 6px;
  border-radius: 6px;
  background: var(--cyan);
  color: var(--bg);
  font-weight: 700;
}
.s { font-size: 10px; color: var(--text-3); margin-top: 2px; }
.reps {
  font-size: 14px;
  font-weight: 800;
  color: var(--cyan);
}

.placeholder {
  text-align: center;
  padding: 60px 20px;
  color: var(--text-3);
}
.placeholder div { font-size: 14px; margin-bottom: 4px; }
.placeholder small { font-size: 11px; }
</style>
