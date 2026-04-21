<script setup>
import { ref, watch } from 'vue';
import { coachApi } from '@/api/coach';
import { useConfigStore } from '@/stores/config';

const props = defineProps({ session: Object });
const config = useConfigStore();

const loading = ref(false);
const feedback = ref(null);
const error = ref('');

async function fetchFeedback() {
  if (!props.session || !config.coachEnabled) return;
  loading.value = true;
  error.value = '';
  try {
    feedback.value = await coachApi.feedback(props.session.remoteId || props.session.localId);
  } catch (e) {
    // 后端尚未实现：做一个本地兜底反馈
    feedback.value = localFallback(props.session);
  } finally {
    loading.value = false;
  }
}

function localFallback(s) {
  const reviews = [];
  const tips = [];
  if (s.score >= 85) reviews.push('今天的状态非常好，动作质量稳定！');
  else if (s.score >= 70) reviews.push('整体完成得不错，还有小幅提升空间。');
  else reviews.push('继续努力，动作完成度还可以更上一层楼。');
  if (s.rhythmScore < 70) tips.push('尝试跟着节拍器的节奏，让动作频率更稳定');
  if (s.stabilityScore < 70) tips.push('动作最低点停顿 0.5 秒，有助于提升稳定性');
  if (s.depthScore < 70) tips.push('可以稍微加大幅度，让动作更到位');
  if (s.symmetryScore < 70) tips.push('注意左右两侧发力均匀');
  if (!tips.length) tips.push('继续保持现有节奏，逐步增加次数挑战自己');
  return {
    review: reviews.join(''),
    suggestion: tips.join('；'),
    encouragement: '坚持就是胜利，明天也要继续哦！',
    nextGoal: `建议下一次目标：${s.reps + 2} 次`,
    offline: true
  };
}

watch(() => props.session, (v) => {
  if (v) fetchFeedback();
}, { immediate: true });
</script>

<template>
  <div v-if="config.coachEnabled" class="coach-wrap">
    <div class="coach-head">
      <div class="avatar">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="12" cy="7" r="4"/>
          <path d="M5 21a7 7 0 0 1 14 0"/>
          <circle cx="8.5" cy="6.5" r="1" fill="currentColor"/>
          <circle cx="15.5" cy="6.5" r="1" fill="currentColor"/>
        </svg>
      </div>
      <div class="title-row">
        <span class="name">AI 教练</span>
        <span v-if="feedback?.offline" class="tag">离线点评</span>
        <span v-else class="tag online">Claude 驱动</span>
      </div>
    </div>

    <div v-if="loading" class="skeleton-row">
      <div class="skeleton" style="width: 70%; height: 12px;"></div>
      <div class="skeleton" style="width: 90%; height: 12px;"></div>
      <div class="skeleton" style="width: 60%; height: 12px;"></div>
    </div>

    <div v-else-if="feedback" class="coach-body">
      <div class="block">
        <div class="b-label">点评</div>
        <div class="b-text">{{ feedback.review }}</div>
      </div>
      <div class="block">
        <div class="b-label">建议</div>
        <div class="b-text">{{ feedback.suggestion }}</div>
      </div>
      <div class="block-row">
        <div class="b-text encourage">{{ feedback.encouragement }}</div>
      </div>
      <div v-if="feedback.nextGoal" class="next-goal">🎯 {{ feedback.nextGoal }}</div>
    </div>
  </div>
</template>

<style scoped>
.coach-wrap {
  margin: 16px 0;
  padding: 14px;
  border-radius: 16px;
  background: linear-gradient(135deg, rgba(0, 240, 255, .06), rgba(124, 106, 255, .06));
  border: 1px solid rgba(0, 240, 255, .15);
}
.coach-head { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.avatar {
  width: 32px; height: 32px;
  border-radius: 50%;
  background: var(--grad-primary);
  color: #fff;
  display: flex; align-items: center; justify-content: center;
}
.avatar svg { width: 18px; height: 18px; }
.title-row { display: flex; align-items: center; gap: 8px; }
.name { font-size: 14px; font-weight: 700; color: var(--text); }
.tag {
  font-size: 10px;
  padding: 2px 8px;
  border-radius: 8px;
  background: var(--bg-card-2);
  color: var(--text-3);
}
.tag.online { background: var(--cyan-dim); color: var(--cyan); }

.skeleton-row { display: flex; flex-direction: column; gap: 8px; padding: 8px 0; }

.coach-body { font-size: 13px; color: var(--text-2); line-height: 1.7; }
.block { margin-bottom: 8px; }
.b-label { font-size: 11px; color: var(--text-3); margin-bottom: 2px; letter-spacing: .04em; }
.b-text { color: var(--text); font-weight: 500; }
.encourage {
  font-size: 12px;
  font-style: italic;
  color: var(--text-2);
  padding-left: 8px;
  border-left: 2px solid var(--cyan);
}
.next-goal {
  margin-top: 10px;
  padding: 8px 12px;
  background: var(--bg-card-2);
  border-radius: 10px;
  font-size: 12px;
  color: var(--text);
  font-weight: 600;
}
</style>
