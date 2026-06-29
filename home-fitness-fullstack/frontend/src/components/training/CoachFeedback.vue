<script setup>
import { ref, computed, watch, onBeforeUnmount } from 'vue';
import { coachApi } from '@/api/coach';
import { ttsApi, playTtsResult } from '@/api/tts';
import { useConfigStore } from '@/stores/config';

const props = defineProps({
  session: Object,
  formCritique: { type: Object, default: null },
  formCritiqueLoading: { type: Boolean, default: false }
});
const config = useConfigStore();

const FEEDBACK_WAIT_MS = 6000;  // 最多等视觉点评 6s，超时则先出数据版复盘
let feedbackTimer = null;

/** 把视觉点评压成一行摘要注入文字 coach；仅真视觉点评（joyai-vl）才注入。 */
function buildFormReview() {
  const fc = props.formCritique;
  if (!fc || fc.model !== 'joyai-vl') return null;
  const parts = [];
  if (fc.summary) parts.push(fc.summary);
  (fc.issues || []).slice(0, 3).forEach(it => {
    parts.push(`${it.joint ? it.joint + '：' : ''}${it.detail}`);
  });
  if (fc.formScore != null) parts.push(`动作标准度 ${fc.formScore}`);
  const s = parts.join('；');
  return s ? s.slice(0, 500) : null;
}

const TABS = [
  { key: 'feedback',   label: '本次复盘', hint: '针对刚刚那组动作' },
  { key: 'suggestion', label: '近况建议', hint: '基于最近 7 次训练' },
  { key: 'weekly',     label: '本周计划', hint: '帮你安排下一周' }
];

const tab = ref('feedback');
const cache = ref({ feedback: null, suggestion: null, weekly: null });
const loading = ref(false);
const error = ref('');
const speaking = ref(false);
const reveal = ref('');
let revealTimer = null;

const current = computed(() => cache.value[tab.value]);
const companionName = computed(() => config.companionName || '小柯');
const greeting = computed(() => {
  const h = new Date().getHours();
  if (h < 6)  return '夜练辛苦了';
  if (h < 11) return '早上好';
  if (h < 13) return '中午好';
  if (h < 18) return '下午好';
  if (h < 22) return '晚上好';
  return '夜深了';
});
const subtitle = computed(() => {
  if (loading.value) return '正在为你整理思路…';
  if (error.value)   return error.value;
  return TABS.find(t => t.key === tab.value)?.hint || '';
});
const providerTag = computed(() => {
  const c = current.value;
  if (!c) return '';
  if (c.offline) return '离线兜底';
  return c.provider === 'mimo' ? 'MiMo 驱动' : 'AI 驱动';
});

function startTypewriter(text) {
  if (revealTimer) { clearInterval(revealTimer); revealTimer = null; }
  reveal.value = '';
  if (!text) return;
  let i = 0;
  revealTimer = setInterval(() => {
    i += 1;
    reveal.value = text.slice(0, i);
    if (i >= text.length) {
      clearInterval(revealTimer);
      revealTimer = null;
    }
  }, 24);
}

async function load(key, force = false) {
  if (cache.value[key] && !force) return;
  if (key === 'feedback' && !props.session) return;
  loading.value = true;
  error.value = '';
  try {
    let data;
    if (key === 'feedback') {
      data = await coachApi.feedback(props.session.remoteId || props.session.localId, buildFormReview());
    } else if (key === 'suggestion') {
      data = await coachApi.suggestion();
    } else {
      data = await coachApi.weeklyPlan();
    }
    cache.value[key] = data;
  } catch (e) {
    if (key === 'feedback' && props.session) {
      cache.value[key] = localFallback(props.session);
    } else {
      error.value = '暂时连不到 AI 教练，稍后再试一次';
    }
  } finally {
    loading.value = false;
  }
}

function switchTab(k) {
  if (tab.value === k) return;
  tab.value = k;
  load(k);
}

async function speak() {
  const f = current.value;
  if (!f || speaking.value) return;
  speaking.value = true;
  try {
    let r;
    if (f.id && !f.offline) {
      r = await ttsApi.speakFeedback(f.id);
    } else {
      const text = [f.review, f.suggestion, f.encouragement].filter(Boolean).join('。');
      r = { fallbackText: text, provider: 'browser-fallback' };
    }
    await playTtsResult(r);
  } catch (_) {
    /* 静默，TTS 失败不影响主流程 */
  } finally {
    setTimeout(() => speaking.value = false, 600);
  }
}

function refresh() {
  load(tab.value, true);
}

function toggleAutoSpeak() {
  config.companionAutoSpeak = !config.companionAutoSpeak;
  config.save();
}

function localFallback(s) {
  const reviews = [];
  const tips = [];
  const score = s.score ?? 0;
  if (score >= 85) reviews.push('今天的状态非常好，动作质量稳定！');
  else if (score >= 70) reviews.push('整体完成得不错，还有小幅提升空间。');
  else reviews.push('继续努力，动作完成度还可以更上一层楼。');
  if (s.rhythmScore != null && s.rhythmScore < 70) tips.push('跟着节拍器调节奏，让频率更稳');
  if (s.stabilityScore != null && s.stabilityScore < 70) tips.push('最低点停顿 0.5 秒，提升稳定性');
  if (s.depthScore != null && s.depthScore < 70) tips.push('动作幅度可以再大一点');
  if (s.symmetryScore != null && s.symmetryScore < 70) tips.push('注意左右两侧发力均匀');
  if (!tips.length) tips.push('继续保持当前节奏，稳步往上加');
  return {
    review: reviews.join(''),
    suggestion: tips.join('；'),
    encouragement: '坚持就是胜利，明天也要继续哦！',
    nextGoal: `下次目标：${(s.reps ?? 0) + 2} 次`,
    offline: true
  };
}

/**
 * 出"本次复盘"前先等视觉点评就绪（这样反馈能融合"看到的"+"算到的"）。
 * - force=true 或视觉点评已结束（成功/失败/未启用）才真正发起；
 * - 已加载过则不重复；超时兜底见 FEEDBACK_WAIT_MS。
 */
function maybeLoadFeedback(force = false) {
  if (!props.session || cache.value.feedback) return;
  if (!force && props.formCritiqueLoading) return;  // 还在等视觉点评
  if (feedbackTimer) { clearTimeout(feedbackTimer); feedbackTimer = null; }
  load('feedback');
}

watch(() => props.session, (v) => {
  if (!v) return;
  tab.value = 'feedback';
  cache.value = { feedback: null, suggestion: null, weekly: null };
  if (feedbackTimer) clearTimeout(feedbackTimer);
  feedbackTimer = setTimeout(() => maybeLoadFeedback(true), FEEDBACK_WAIT_MS);
  maybeLoadFeedback();
}, { immediate: true });

// 视觉点评结束（loading 翻 false）后立即出复盘，把点评摘要带上
watch(() => props.formCritiqueLoading, (loading) => {
  if (!loading) maybeLoadFeedback();
});

watch(current, (v) => {
  if (!v) { reveal.value = ''; return; }
  startTypewriter(v.review || '');
  if (config.companionAutoSpeak) speak();
});

onBeforeUnmount(() => {
  if (revealTimer) clearInterval(revealTimer);
  if (feedbackTimer) clearTimeout(feedbackTimer);
});
</script>

<template>
  <div v-if="config.coachEnabled && config.companionEnabled" class="companion-wrap">
    <!-- 顶部：头像 + 人格 + 操作 -->
    <header class="ch-head">
      <div class="avatar" :class="{ talking: speaking }">
        <svg viewBox="0 0 32 32" fill="none">
          <defs>
            <linearGradient id="cmpgrad" x1="0" y1="0" x2="32" y2="32">
              <stop stop-color="#d97757"/><stop offset="1" stop-color="#c96442"/>
            </linearGradient>
          </defs>
          <circle cx="16" cy="16" r="14" fill="url(#cmpgrad)"/>
          <circle cx="11" cy="14" r="1.6" fill="#fff" class="eye eye-l"/>
          <circle cx="21" cy="14" r="1.6" fill="#fff" class="eye eye-r"/>
          <path d="M11 20q5 4 10 0" stroke="#fff" stroke-width="1.6" stroke-linecap="round" fill="none"/>
        </svg>
        <span class="pulse" aria-hidden="true"></span>
      </div>

      <div class="meta">
        <div class="name-row">
          <span class="name">{{ companionName }}</span>
          <span class="role">陪伴教练</span>
          <span v-if="providerTag" :class="['tag', current?.offline ? 'off' : 'on']">{{ providerTag }}</span>
        </div>
        <div class="sub">{{ greeting }}，{{ subtitle }}</div>
      </div>

      <div class="actions">
        <button class="ico-btn" :class="{ active: config.companionAutoSpeak }" @click="toggleAutoSpeak"
                :title="config.companionAutoSpeak ? '已开启自动播报' : '开启自动播报'">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <path d="M3 10v4h4l5 4V6L7 10H3z"/>
            <path v-if="config.companionAutoSpeak" d="M16 8a5 5 0 0 1 0 8 M19 5a9 9 0 0 1 0 14"/>
          </svg>
        </button>
        <button class="ico-btn" :disabled="!current || speaking" @click="speak" :title="speaking ? '播报中…' : '听一遍'">
          <svg v-if="!speaking" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <path d="M11 5L6 9H2v6h4l5 4V5z"/>
            <path d="M15.54 8.46a5 5 0 0 1 0 7.07"/>
            <path d="M19.07 4.93a10 10 0 0 1 0 14.14"/>
          </svg>
          <svg v-else class="spin" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="9" stroke-dasharray="40" stroke-dashoffset="20"/>
          </svg>
        </button>
        <button class="ico-btn" :disabled="loading" @click="refresh" title="换一种说法">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" :class="{ spin: loading }">
            <path d="M21 12a9 9 0 1 1-3-6.7"/>
            <path d="M21 4v5h-5"/>
          </svg>
        </button>
      </div>
    </header>

    <!-- Tab 切换 -->
    <nav class="ch-tabs">
      <button
        v-for="t in TABS"
        :key="t.key"
        :class="['tab', { active: tab === t.key }]"
        @click="switchTab(t.key)"
      >{{ t.label }}</button>
    </nav>

    <!-- 内容 -->
    <section class="ch-body">
      <div v-if="loading" class="skeleton">
        <div class="sk-line" style="width: 70%"></div>
        <div class="sk-line" style="width: 90%"></div>
        <div class="sk-line" style="width: 55%"></div>
      </div>

      <div v-else-if="!current && error" class="empty">
        {{ error }}
      </div>

      <div v-else-if="current" class="body-stack">
        <div class="bubble main">
          <div class="b-label">点评</div>
          <div class="b-text">
            <span>{{ reveal || current.review }}</span>
            <span v-if="reveal && reveal.length < (current.review?.length || 0)" class="caret">▍</span>
          </div>
        </div>
        <div v-if="current.suggestion" class="bubble">
          <div class="b-label">建议</div>
          <div class="b-text">{{ current.suggestion }}</div>
        </div>
        <div v-if="current.encouragement" class="encourage">"{{ current.encouragement }}"</div>
        <div v-if="current.nextGoal" class="next-goal">下一目标：{{ current.nextGoal }}</div>
      </div>
    </section>

    <!-- 底部快捷动作 -->
    <footer class="ch-foot">
      <button
        v-for="t in TABS.filter(x => x.key !== tab)"
        :key="t.key"
        class="chip"
        @click="switchTab(t.key)"
      >换到「{{ t.label }}」</button>
    </footer>
  </div>
</template>

<style scoped>
.companion-wrap {
  margin: 16px 0;
  padding: 14px 14px 12px;
  border-radius: 18px;
  background: linear-gradient(135deg, rgba(217, 119, 87, .07), rgba(232, 183, 120, .04));
  border: 1px solid rgba(217, 119, 87, .18);
  position: relative;
}

/* —— 顶部 —— */
.ch-head { display: flex; align-items: flex-start; gap: 12px; margin-bottom: 12px; }
.avatar {
  width: 44px; height: 44px;
  flex-shrink: 0;
  position: relative;
  border-radius: 50%;
  animation: cmp-breath 3.6s ease-in-out infinite;
  filter: drop-shadow(0 4px 10px rgba(217, 119, 87, .25));
}
.avatar svg { width: 100%; height: 100%; display: block; }
.avatar .eye { animation: cmp-blink 5.5s infinite; transform-origin: center; }
.avatar .pulse {
  position: absolute; inset: -3px;
  border-radius: 50%;
  border: 2px solid rgba(217, 119, 87, .35);
  opacity: 0;
  animation: cmp-pulse 2.4s ease-out infinite;
}
.avatar.talking { animation-duration: 1.4s; }
.avatar.talking .pulse { opacity: 1; animation-duration: 1.2s; }

@keyframes cmp-breath {
  0%, 100% { transform: scale(1); }
  50%      { transform: scale(1.06); }
}
@keyframes cmp-blink {
  0%, 92%, 96%, 100% { transform: scaleY(1); }
  94%                { transform: scaleY(.1); }
}
@keyframes cmp-pulse {
  0%   { transform: scale(.85); opacity: .55; }
  100% { transform: scale(1.25); opacity: 0; }
}

.meta { flex: 1; min-width: 0; }
.name-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.name { font-weight: 700; font-size: 15px; color: var(--text); }
.role { font-size: 11px; color: var(--text-3); letter-spacing: .04em; }
.tag {
  font-size: 10px;
  padding: 2px 8px;
  border-radius: 8px;
  background: var(--bg-card-2);
  color: var(--text-3);
}
.tag.on { background: var(--cyan-dim); color: var(--cyan); }
.tag.off { background: rgba(140, 140, 140, .12); color: var(--text-3); }
.sub { font-size: 12px; color: var(--text-2); margin-top: 2px; }

.actions { display: flex; gap: 6px; }
.ico-btn {
  width: 30px; height: 30px;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  border: 1px solid var(--border);
  background: var(--bg-card-2);
  color: var(--text-2);
  cursor: pointer;
  transition: all .15s;
}
.ico-btn:hover:not(:disabled) { color: var(--cyan); background: var(--cyan-dim); }
.ico-btn.active { color: var(--cyan); background: var(--cyan-dim); border-color: var(--cyan); }
.ico-btn:disabled { opacity: .45; cursor: not-allowed; }
.ico-btn svg { width: 15px; height: 15px; }

.spin { animation: cmp-spin 1s linear infinite; transform-origin: center; }
@keyframes cmp-spin { to { transform: rotate(360deg); } }

/* —— Tab —— */
.ch-tabs {
  display: flex;
  gap: 4px;
  padding: 4px;
  background: var(--bg-card-2);
  border-radius: 10px;
  margin-bottom: 12px;
}
.tab {
  flex: 1;
  padding: 7px 8px;
  border-radius: 8px;
  border: 0;
  background: transparent;
  color: var(--text-2);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all .15s;
}
.tab:hover { color: var(--text); }
.tab.active {
  background: var(--bg-card);
  color: var(--text);
  box-shadow: 0 1px 4px rgba(0, 0, 0, .08);
}

/* —— Body —— */
.ch-body { min-height: 80px; }
.body-stack { display: flex; flex-direction: column; gap: 8px; }
.bubble {
  padding: 8px 12px;
  border-radius: 12px;
  background: var(--bg-card);
  border: 1px solid var(--border);
}
.bubble.main { border-left: 3px solid var(--cyan); border-radius: 4px 12px 12px 12px; }
.b-label { font-size: 10px; color: var(--text-3); margin-bottom: 2px; letter-spacing: .06em; text-transform: uppercase; }
.b-text { font-size: 13px; color: var(--text); line-height: 1.65; font-weight: 500; }
.caret {
  margin-left: 2px;
  color: var(--cyan);
  animation: cmp-caret 1s steps(2) infinite;
}
@keyframes cmp-caret { 50% { opacity: 0; } }

.encourage {
  font-size: 12px;
  font-style: italic;
  color: var(--text-2);
  padding: 4px 12px;
  border-left: 2px solid var(--orange, #d97757);
  margin-left: 2px;
}
.next-goal {
  margin-top: 4px;
  padding: 8px 12px;
  background: var(--bg-card-2);
  border-radius: 10px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text);
}

.skeleton { display: flex; flex-direction: column; gap: 8px; padding: 4px 0; }
.sk-line {
  height: 12px;
  border-radius: 6px;
  background: linear-gradient(90deg, var(--bg-card-2), var(--bg-card), var(--bg-card-2));
  background-size: 200% 100%;
  animation: cmp-shimmer 1.4s linear infinite;
}
@keyframes cmp-shimmer {
  0%   { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.empty {
  font-size: 13px;
  color: var(--text-2);
  text-align: center;
  padding: 14px 0;
}
.empty .emo { font-size: 20px; margin-right: 4px; }

/* —— 底部 Chip —— */
.ch-foot {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed var(--border);
}
.chip {
  font-size: 11px;
  padding: 5px 10px;
  border-radius: 14px;
  background: var(--bg-card-2);
  color: var(--text-2);
  border: 1px solid var(--border);
  cursor: pointer;
  transition: all .15s;
}
.chip:hover { color: var(--cyan); border-color: var(--cyan); background: var(--cyan-dim); }
</style>
