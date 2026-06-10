<template>
  <div class="page challenges-page">
    <div class="hero">
      <h2>挑战赛</h2>
      <p class="hint">报名后训练自动同步进度，看看能不能挤进榜单。</p>
    </div>

    <section v-if="!detail" class="grid">
      <article v-for="c in list" :key="c.id" class="card" @click="open(c.id)">
        <div class="cover" :style="{ background: c.cover || 'var(--grad-primary)' }">
          <div class="title">{{ c.title }}</div>
        </div>
        <div class="body">
          <p class="desc">{{ c.description }}</p>
          <div class="meta">
            <span>目标 {{ c.targetReps }} {{ c.action === 'plank' ? '秒' : '次' }}{{ zhAction(c.action) }}</span>
            <span>{{ c.participantCount || 0 }} 人参与</span>
            <span>截止 {{ c.endDate }}</span>
          </div>
          <div v-if="c.joined" class="progress">
            <div class="bar"><div :style="{ width: pct(c.myProgress, c.targetReps) + '%' }"></div></div>
            <div class="prog-text">{{ c.myProgress }} / {{ c.targetReps }} {{ c.myCompleted ? '已完成' : '' }}</div>
          </div>
        </div>
      </article>
      <p v-if="!list.length" class="empty">暂无活跃挑战</p>
    </section>

    <section v-else class="detail">
      <button class="back" @click="detail = null">← 返回</button>
      <h3>{{ detail.title }}</h3>
      <p class="desc">{{ detail.description }}</p>
      <div class="actions">
        <button class="primary" :disabled="joining" @click="join">
          {{ detail.joined ? (detail.myCompleted ? '已完成' : '已报名') : '报名挑战' }}
        </button>
      </div>
      <div v-if="detail.joined" class="my-progress">
        我的进度: <b>{{ detail.myProgress }}</b> / {{ detail.targetReps }}
      </div>

      <h4>排行榜</h4>
      <ol class="rank">
        <li v-for="r in rank" :key="r.userId">
          <span class="num">#{{ r.rank }}</span>
          <span class="name">{{ r.nickname }}</span>
          <span class="reps">{{ r.progressReps }} 次</span>
          <span v-if="r.completed" class="done">已完成</span>
        </li>
        <li v-if="!rank.length" class="empty">暂无参与者</li>
      </ol>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { challengeApi } from '@/api/challenge';

const list = ref([]);
const detail = ref(null);
const rank = ref([]);
const joining = ref(false);

const pct = (a, b) => Math.min(100, Math.round((a || 0) / Math.max(1, b) * 100));

const zhAction = a => ({ squat:'深蹲', pushup:'俯卧撑', plank:'平板支撑', stretch:'前屈伸展',
                         lunge:'弓步蹲', bridge:'臀桥', jumpingJack:'开合跳' }[a] || a || '');

async function loadList() {
  list.value = await challengeApi.list();
}

async function open(id) {
  detail.value = await challengeApi.detail(id);
  rank.value = await challengeApi.rank(id, 20);
}

async function join() {
  joining.value = true;
  try {
    detail.value = await challengeApi.join(detail.value.id);
    rank.value = await challengeApi.rank(detail.value.id, 20);
  } finally { joining.value = false; }
}

onMounted(loadList);
</script>

<style scoped>
.challenges-page { padding: 16px; max-width: 900px; margin: 0 auto; }
.hero h2 { margin: 0; color: var(--text-strong); }
.hint { color: var(--text-soft); font-size: 13px; }
.grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 14px; margin-top: 16px; }
.card { background: var(--bg-card); border-radius: 14px; overflow: hidden; box-shadow: var(--shadow-sm); cursor: pointer; transition: transform .15s; }
.card:hover { transform: translateY(-2px); }
.cover { padding: 18px; min-height: 88px; display: flex; align-items: flex-end; color: #fff; }
.cover .title { font-size: 16px; font-weight: 700; }
.body { padding: 12px 14px; }
.desc { color: var(--text-soft); font-size: 13px; margin: 0 0 10px; }
.meta { display: flex; gap: 10px; flex-wrap: wrap; font-size: 12px; color: var(--text-soft); }
.progress { margin-top: 10px; }
.bar { background: var(--bg-elev); border-radius: 8px; height: 8px; overflow: hidden; }
.bar > div { background: var(--grad-primary); height: 100%; transition: width .3s; }
.prog-text { font-size: 12px; color: var(--text-soft); margin-top: 4px; }
.detail { background: var(--bg-card); border-radius: 14px; padding: 18px; box-shadow: var(--shadow-sm); }
.back { background: transparent; border: 0; color: var(--cyan); cursor: pointer; padding: 0 0 12px; font-size: 14px; }
.detail h3 { margin: 0 0 8px; color: var(--text-strong); }
.actions { margin: 12px 0; }
button.primary { background: var(--grad-primary); color: #fff; padding: 10px 22px; border: 0; border-radius: 10px; font-weight: 600; cursor: pointer; }
button.primary:disabled { opacity: .55; cursor: not-allowed; }
.my-progress { margin: 10px 0; padding: 10px; background: var(--bg-elev); border-radius: 10px; }
.my-progress b { color: var(--cyan); }
.detail h4 { margin: 18px 0 8px; }
.rank { list-style: none; padding: 0; margin: 0; }
.rank li { display: flex; align-items: center; gap: 14px; padding: 8px 10px; border-bottom: 1px solid var(--border); }
.num { width: 36px; color: var(--text-soft); font-weight: 700; }
.name { flex: 1; }
.reps { color: var(--cyan); font-weight: 600; }
.empty { color: var(--text-soft); text-align: center; padding: 16px 0; }
</style>
