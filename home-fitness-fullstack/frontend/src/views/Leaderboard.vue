<script setup>
import { ref, computed, watch, onMounted } from 'vue';
import { socialApi } from '@/api/social';
import { userApi } from '@/api/user';
import { useAuthStore } from '@/stores/auth';

const auth = useAuthStore();
const tab = ref('weekly');
const loading = ref(false);
const following = ref(new Set());
const cache = ref({ weekly: null, monthly: null, friends: null });

const data = computed(() =>
  (cache.value[tab.value] || []).map(u => ({
    ...u,
    you: auth.user?.id != null && u.userId === auth.user.id
  }))
);

async function load(t) {
  if (cache.value[t]) return;
  loading.value = true;
  try {
    const fn = t === 'monthly' ? socialApi.leaderboardMonthly
             : t === 'friends' ? socialApi.leaderboardFriends
             : socialApi.leaderboardWeekly;
    cache.value[t] = (await fn()) || [];
  } catch (_) {
    cache.value[t] = [];
  } finally {
    loading.value = false;
  }
}

async function loadFollowing() {
  if (!auth.isLogin || auth.user?.id == null) return;
  try {
    const list = await userApi.followings(auth.user.id);
    following.value = new Set((list || []).map(u => u.id));
  } catch (_) { /* ignore */ }
}

async function toggleFollow(u) {
  const id = u.userId;
  if (id == null) return;
  try {
    if (following.value.has(id)) { await userApi.unfollow(id); following.value.delete(id); }
    else { await userApi.follow(id); following.value.add(id); }
    following.value = new Set(following.value);
    cache.value.friends = null; // 关注变化后刷新好友榜
  } catch (_) { /* 拦截器已提示 */ }
}

watch(tab, load);
onMounted(() => { load('weekly'); loadFollowing(); });

const top3 = computed(() => data.value.slice(0, 3));
const rest = computed(() => data.value.slice(3));

function medal(r) {
  return `#${r}`;
}

// 根据姓名生成暖色系 HSL 渐变（色相 15–55 的暖色区间）
function avatarStyle(name) {
  let h = 0;
  for (const ch of name) h = (h * 31 + ch.charCodeAt(0)) | 0;
  const hue = 15 + (Math.abs(h) % 40);
  const c1 = `hsl(${hue}, 52%, 62%)`;
  const c2 = `hsl(${(hue + 340) % 360}, 42%, 44%)`;
  return { background: `linear-gradient(135deg, ${c1}, ${c2})` };
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

    <!-- Loading -->
    <div v-if="loading" class="placeholder">
      <div>加载中…</div>
    </div>

    <!-- Top3 Podium -->
    <div v-else-if="top3.length" class="podium">
      <div class="podium-card rank-2">
        <div class="rank-medal">2</div>
        <div class="pd-avatar" :style="avatarStyle(top3[1]?.name || '')">{{ (top3[1]?.name || '?')[0] }}</div>
        <div class="pd-name">{{ top3[1]?.name }}</div>
        <div class="pd-score">{{ top3[1]?.reps }} 次</div>
      </div>
      <div class="podium-card rank-1">
        <div class="rank-medal">1</div>
        <div class="pd-avatar" :style="avatarStyle(top3[0]?.name || '')">{{ (top3[0]?.name || '?')[0] }}</div>
        <div class="pd-name">{{ top3[0]?.name }}</div>
        <div class="pd-score">{{ top3[0]?.reps }} 次</div>
      </div>
      <div class="podium-card rank-3">
        <div class="rank-medal">3</div>
        <div class="pd-avatar" :style="avatarStyle(top3[2]?.name || '')">{{ (top3[2]?.name || '?')[0] }}</div>
        <div class="pd-name">{{ top3[2]?.name }}</div>
        <div class="pd-score">{{ top3[2]?.reps }} 次</div>
      </div>
    </div>

    <!-- Rest List -->
    <ul v-if="!loading && rest.length" class="rank-list">
      <li
        v-for="u in rest"
        :key="u.rank"
        :class="['rank-item', u.you ? 'you' : '']"
      >
        <div class="rk">{{ medal(u.rank) }}</div>
        <div class="avatar" :style="avatarStyle(u.name)">{{ u.name[0] }}</div>
        <div class="info">
          <div class="n-row">
            <span class="n">{{ u.name }}</span>
            <span v-if="u.you" class="you-tag">你</span>
          </div>
          <div class="s">综合评分 {{ u.score }}</div>
        </div>
        <div class="reps">{{ u.reps }} 次</div>
        <button
          v-if="auth.isLogin && !u.you"
          :class="['follow-btn', { on: following.has(u.userId) }]"
          @click="toggleFollow(u)"
        >{{ following.has(u.userId) ? '已关注' : '关注' }}</button>
      </li>
    </ul>

    <div v-if="!loading && !data.length" class="placeholder">
      <div>{{ tab === 'friends' ? '暂无好友数据' : '暂无排行数据' }}</div>
      <small>{{ tab === 'friends' ? '关注好友后可见 TA 们的排名' : '完成训练后即可上榜' }}</small>
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
  border-color: rgba(224, 160, 80, .45);
  background:
    radial-gradient(80% 90% at 50% 0%, rgba(232, 183, 120, .18), transparent 60%),
    var(--bg-card);
  transform: translateY(-8px);
  box-shadow: var(--shadow-sm);
}
.rank-2 { padding-top: 16px; }
.rank-3 { padding-top: 12px; }

.rank-crown { position: absolute; top: -20px; left: 50%; transform: translateX(-50%); font-size: 24px; }
.rank-medal {
  width: 30px; height: 30px;
  margin: 0 auto 6px;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-family: var(--font-mono);
  font-size: 15px;
  font-weight: 600;
  color: #fff;
  background: var(--grad-primary);
  box-shadow: var(--shadow-sm);
}
.rank-2 .rank-medal, .rank-3 .rank-medal {
  background: var(--bg-elevated);
  color: var(--text-2);
  border: 1px solid var(--border);
}

.pd-avatar {
  width: 48px; height: 48px;
  margin: 0 auto 6px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 700;
  font-size: 18px;
  letter-spacing: -.02em;
  position: relative;
  overflow: hidden;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, .28), 0 4px 12px rgba(60, 44, 30, .22);
}
.pd-avatar::after {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(circle at 30% 22%, rgba(255, 255, 255, .35), transparent 55%);
  pointer-events: none;
}
.pd-name { font-size: 12px; font-weight: 700; margin-bottom: 2px; }
.pd-score {
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
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
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
  font-size: 13px;
  font-weight: 600;
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
  letter-spacing: -.02em;
  position: relative;
  overflow: hidden;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, .25), 0 2px 8px rgba(60, 44, 30, .18);
}
.rank-item .avatar::after {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(circle at 30% 22%, rgba(255, 255, 255, .32), transparent 55%);
  pointer-events: none;
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
.follow-btn {
  flex-shrink: 0;
  padding: 5px 12px;
  border-radius: 100px;
  background: var(--cyan-dim);
  color: var(--cyan);
  font-size: 11px;
  font-weight: 600;
  transition: all var(--transition);
}
.follow-btn.on { background: var(--bg-card-2); color: var(--text-3); }

.placeholder {
  text-align: center;
  padding: 60px 20px;
  color: var(--text-3);
}
.placeholder div { font-size: 14px; margin-bottom: 4px; }
.placeholder small { font-size: 11px; }
</style>
