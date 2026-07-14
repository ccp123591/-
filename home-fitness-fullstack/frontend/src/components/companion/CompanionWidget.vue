<script setup>
import { ref, computed, nextTick, onMounted, onBeforeUnmount, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { coachApi } from '@/api/coach';
import { emotionApi } from '@/api/emotion';
import { ttsApi, playTtsResult, stopTts } from '@/api/tts';
import { useConfigStore } from '@/stores/config';
import { useAuthStore } from '@/stores/auth';
import VoiceCompanion from './VoiceCompanion.vue';
import { followupsFor } from '@/modules/followups';

const CACHE_KEY    = 'fitcoach_companion_v1';
const CHAT_KEY     = 'fitcoach_companion_chat_v1';   // 每用户隔离的最近聊天（sessionStorage）
const CACHE_TTL    = 30 * 60 * 1000;
const HIDDEN_ROUTES = ['/login'];
const QUICK_PROMPTS = [
  '我今天心情有点低落',
  '上次咱们聊到哪了',
  '今天该练什么动作',
  '帮我安排本周计划'
];

const route = useRoute();
const router = useRouter();
const config = useConfigStore();
const auth = useAuthStore();

const open = ref(false);
const tab  = ref('chat');     // chat | voice | snap
const voiceRef = ref(null);

// —— 闲聊态 ——
const messages = ref([]);       // [{role, content, recalled?, provider?, time?, error?}]
const input    = ref('');
const sending  = ref(false);
const listEl   = ref(null);
const followups = ref([]);      // AI 回复后的情境化快捷追问

// —— 建议态 ——
const loading    = ref(false);
const speaking   = ref(false);
const suggestion = ref(null);
const emotionMood = ref(null);
const widgetEl   = ref(null);

const visible = computed(() => {
  if (!config.companionEnabled) return false;
  if (route.meta?.layout === 'none') return false;
  if (HIDDEN_ROUTES.includes(route.path)) return false;
  return true;
});
const canChat = computed(() => auth.isLogin || config.demoMode);

const companionName = computed(() => config.companionName || '小柯');
const greeting = computed(() => {
  const h = new Date().getHours();
  const who = (auth.isLogin && auth.user?.nickname) ? `，${auth.user.nickname}` : '';
  if (h < 6)  return `夜练辛苦了${who}`;
  if (h < 11) return `早上好${who}`;
  if (h < 13) return `中午好${who}`;
  if (h < 18) return `下午好${who}`;
  if (h < 22) return `晚上好${who}`;
  return `夜深了${who}，注意休息`;
});
const moodTag = computed(() => {
  const m = emotionMood.value;
  if (!m || !m.dominantEmotion) return null;
  const map = {
    positive: { label: '近期心情不错', tone: 'pos' },
    neutral:  { label: '近期偏平稳',   tone: 'neu' },
    negative: { label: '近期偏低落',   tone: 'neg' }
  };
  return map[m.dominantEmotion] || null;
});

// ====== Cache ======
const uid = computed(() => auth.user?.id ?? 'guest');
const chatKey = computed(() => `${CHAT_KEY}_${uid.value}`);

function readSnapCache() {
  try {
    const raw = sessionStorage.getItem(CACHE_KEY);
    if (!raw) return null;
    const obj = JSON.parse(raw);
    if (!obj?.ts || Date.now() - obj.ts > CACHE_TTL) return null;
    return obj;
  } catch (_) { return null; }
}
function writeSnapCache(payload) {
  try { sessionStorage.setItem(CACHE_KEY, JSON.stringify({ ts: Date.now(), ...payload })); } catch (_) {}
}
function loadMessages() {
  try {
    const raw = sessionStorage.getItem(chatKey.value);
    if (raw) {
      const list = JSON.parse(raw);
      // 旧消息直接全显，不再触发打字机；audioCache 保留下来可命中
      messages.value = list.map(m => ({
        ...m,
        revealed: m.role === 'assistant' && typeof m.revealed === 'number'
          ? (m.content?.length || 0)
          : m.revealed
      }));
    }
  } catch (_) {}
}
function persistMessages() {
  try {
    const clipped = messages.value.slice(-30);
    sessionStorage.setItem(chatKey.value, JSON.stringify(clipped));
  } catch (_) {}
}

// ====== 建议态 ======
async function fetchSuggestion(force = false) {
  if (config.demoMode) {
    loading.value = true;
    await sleep(180);
    suggestion.value = {
      review: '演示数据：本周已完成 3 次训练，共 42 分钟。',
      suggestion: force
        ? '今天可以做 3 组深蹲和 2 组拉伸，动作之间休息 45 秒。'
        : '保持轻中强度，先热身 5 分钟，再完成深蹲、平板支撑和拉伸。',
      encouragement: '稳定完成，比一次练得太猛更重要。',
      provider: 'frontend-demo'
    };
    emotionMood.value = { dominantEmotion: 'positive', avgScore: 0.82 };
    loading.value = false;
    return;
  }
  if (!force) {
    const c = readSnapCache();
    if (c?.suggestion) {
      suggestion.value = c.suggestion;
      emotionMood.value = c.emotionMood || null;
      return;
    }
  }
  loading.value = true;
  try {
    const [sug, mood] = await Promise.allSettled([
      coachApi.suggestion(),
      emotionApi.summary(7)
    ]);
    if (sug.status === 'fulfilled') suggestion.value = sug.value;
    if (mood.status === 'fulfilled' && mood.value?.total > 0) {
      emotionMood.value = {
        dominantEmotion: mood.value.dominantEmotion,
        avgScore: mood.value.avgScore
      };
    }
    writeSnapCache({ suggestion: suggestion.value, emotionMood: emotionMood.value });
  } catch (_) { /* 静默 */ } finally {
    loading.value = false;
  }
}

async function speak() {
  if (config.demoMode) return;
  if (!suggestion.value || speaking.value) return;
  speaking.value = true;
  try {
    const s = suggestion.value;
    let r;
    if (s.id) r = await ttsApi.speakFeedback(s.id);
    else r = { fallbackText: [s.review, s.suggestion, s.encouragement].filter(Boolean).join('。') };
    await playTtsResult(r);
  } catch (_) {} finally {
    setTimeout(() => speaking.value = false, 600);
  }
}

// ====== 闲聊态 ======
async function scrollToBottom() {
  await nextTick();
  if (listEl.value) listEl.value.scrollTop = listEl.value.scrollHeight;
}

async function sendMessage(text) {
  const txt = (text ?? input.value).trim();
  if (!txt || sending.value) return;
  followups.value = [];
  messages.value.push({ role: 'user', content: txt, time: Date.now() });
  if (!text) input.value = '';
  sending.value = true;
  await scrollToBottom();

  if (config.demoMode) {
    try {
      await sleep(260);
      const mock = createDemoReply(txt);
      emotionMood.value = mock.mood
        ? { dominantEmotion: mock.mood, avgScore: 0.8 }
        : emotionMood.value;
      await addAssistantReply(mock.reply, mock.recalled, 'frontend-demo');
    } finally {
      sending.value = false;
      await scrollToBottom();
    }
    return;
  }

  // 情感静默捕捉 — 用户每条消息都尝试落到情感库，不阻塞主流程
  emotionApi.analyze(txt, 'chat', null)
    .then(r => {
      if (r?.emotion) {
        // 立刻把这条情感反映到 mood 标签（淡淡的）
        emotionMood.value = {
          dominantEmotion: r.emotion,
          avgScore: r.score
        };
      }
    })
    .catch(() => { /* 静默 */ });

  // 取最近 8 轮（不含本条）作为 history
  const history = messages.value.slice(0, -1).slice(-8).map(m => ({
    role: m.role, content: m.content
  }));
  try {
    const res = await coachApi.chat(txt, history);
    await addAssistantReply(res.reply, res.recalled || [], res.provider);
  } catch (_) {
    messages.value.push({
      role: 'assistant',
      content: '我这边连接有点问题，待会儿再聊好吗？',
      revealed: undefined,
      error: true,
      time: Date.now()
    });
  } finally {
    sending.value = false;
    await scrollToBottom();
    // 每聊一阵子顺手刷新一下情感汇总（拿后端的 7 天聚合）
    refreshEmotionSummary();
  }
}

function recentUserMessages() {
  return messages.value
    .filter(m => m.role === 'user')
    .slice(-3)
    .map(m => m.content);
}

function createDemoReply(text) {
  const recalled = recentUserMessages().slice(0, -1);
  if (/低落|难过|焦虑|累|压力|不开心/.test(text)) {
    return {
      reply: '听起来你今天有点辛苦。先不用逼自己完成高强度目标，我们做 3 分钟呼吸和肩颈拉伸，状态允许再继续。\n\n演示模式里我会陪你把目标拆小：今天完成一点点，也算认真照顾自己。',
      mood: 'negative', recalled
    };
  }
  if (/本周|计划|安排|一周/.test(text)) {
    return {
      reply: '给你一份演示周计划：周一深蹲与臀桥，周三俯卧撑与平板支撑，周五全身循环，周末安排一次舒缓拉伸。每次 20 分钟，强度循序渐进。',
      mood: 'positive', recalled
    };
  }
  if (/练什么|训练|动作|健身/.test(text)) {
    return {
      reply: '今天推荐轻量全身训练：热身 5 分钟，深蹲 3×12、俯卧撑 3×8、平板支撑 3×30 秒，最后拉伸 5 分钟。动作质量优先。',
      mood: 'positive', recalled
    };
  }
  if (/上次|聊到|记得|叙旧/.test(text)) {
    return {
      reply: recalled.length
        ? `这次演示会话里，你刚才提到过“${recalled[recalled.length - 1]}”。我们可以从这里接着聊。`
        : '这是一次新的演示会话，目前还没有更早的聊天记录。先告诉我一个训练目标，我会在本次会话里记住它。',
      mood: 'neutral', recalled
    };
  }
  return {
    reply: '收到。在演示模式下，我可以陪你聊训练、心情和每周计划。你可以试试问：“今天练什么？”',
    mood: 'neutral', recalled
  };
}

/** 拉一次最新的 7 天情感聚合（节流：最多 30s/次） */
let _lastEmoRefresh = 0;
function refreshEmotionSummary() {
  const now = Date.now();
  if (now - _lastEmoRefresh < 30000) return;
  _lastEmoRefresh = now;
  emotionApi.summary(7).then(s => {
    if (s?.total > 0) {
      emotionMood.value = {
        dominantEmotion: s.dominantEmotion,
        avgScore: s.avgScore
      };
    }
  }).catch(() => {});
}

/**
 * 把 AI 回复按 \n\n 拆成多个气泡按序送达，每段走打字机；自动播报命中缓存。
 */
async function addAssistantReply(reply, recalled, provider) {
  if (!reply) return;
  const parts = reply.split(/\n{2,}/).map(s => s.trim()).filter(Boolean);
  if (parts.length === 0) parts.push(reply);

  for (let i = 0; i < parts.length; i++) {
    const isLast = (i === parts.length - 1);
    const msg = {
      role: 'assistant',
      content: parts[i],
      revealed: 0,
      recalled: isLast ? (recalled || []) : [],
      provider,
      time: Date.now()
    };
    messages.value.push(msg);
    await scrollToBottom();

    // 聊天就是会说话 —— 先并行启动 TTS，再走打字机；两者不互相阻塞
    const speakP = config.demoMode ? Promise.resolve() : tryAutoSpeak(msg).catch(() => {});
    await runTypewriter(msg);
    // 不强等 speak 播完，下一段也能开始排队
    speakP;

    if (!isLast) await sleep(420);   // 段间停顿，更像朋友连发两条
  }
  followups.value = followupsFor(reply, emotionMood.value?.dominantEmotion);
  persistMessages();
}

function sleep(ms) { return new Promise(res => setTimeout(res, ms)); }

// 打字机 / 语音播报的可中断句柄
let typeTimer = null;
let typingMsg = null;

/** 关闭/离开时停掉正在进行的打字机与语音播报，避免后台继续跑或叠音。 */
function stopPlayback() {
  stopTts();
  if (typeTimer) { clearInterval(typeTimer); typeTimer = null; }
  if (typingMsg) { typingMsg.revealed = typingMsg.content?.length || 0; typingMsg = null; }
}

/** 把 msg.revealed 从 0 推到 content.length，期间 Vue 自动重渲染。 */
function runTypewriter(msg, tickMs = 28) {
  return new Promise(resolve => {
    const total = msg.content?.length || 0;
    if (total === 0) { msg.revealed = 0; resolve(); return; }
    msg.revealed = 0;
    typingMsg = msg;
    let pending = false;
    typeTimer = setInterval(() => {
      const step = msg.content[msg.revealed] && msg.content.charCodeAt(msg.revealed) < 128 ? 2 : 1;
      msg.revealed = Math.min(msg.revealed + step, total);
      // 跟随滚动 — 节流，避免每 tick scroll
      if (!pending) {
        pending = true;
        nextTick(() => {
          if (listEl.value) listEl.value.scrollTop = listEl.value.scrollHeight;
          pending = false;
        });
      }
      if (msg.revealed >= total) {
        clearInterval(typeTimer); typeTimer = null; typingMsg = null;
        resolve();
      }
    }, tickMs);
  });
}

/** 叙旧：拉最近聊天，让 AI 老朋友式回顾。 */
async function reminisce() {
  if (sending.value) return;
  sending.value = true;
  // 给用户一个"系统提示"占位（不是真用户消息，仅可视提示）
  messages.value.push({
    role: 'assistant',
    content: '让我想想，最近咱们聊过这些…',
    revealed: undefined,
    system: true,
    time: Date.now()
  });
  await scrollToBottom();
  if (config.demoMode) {
    const recalled = recentUserMessages();
    const reply = recalled.length
      ? `本次演示会话里，我们聊过：${recalled.map(x => `“${x}”`).join('、')}。你想从哪一段继续？`
      : '这是一次新的演示会话，还没有可回顾的内容。聊几句后我就能帮你回顾。';
    await addAssistantReply(reply, recalled, 'frontend-demo');
    sending.value = false;
    await scrollToBottom();
    return;
  }
  try {
    const res = await coachApi.reminisce();
    await addAssistantReply(res.reply, res.recalled || [], res.provider);
  } catch (_) {
    messages.value.push({
      role: 'assistant',
      content: '叙旧没拉起来，再等等吧。',
      error: true,
      time: Date.now()
    });
  } finally {
    sending.value = false;
    await scrollToBottom();
  }
}

function applyPrompt(p) {
  input.value = p;
}

function clearChat() {
  stopPlayback();
  messages.value = [];
  followups.value = [];
  sessionStorage.removeItem(chatKey.value);
}

async function speakReply(msg) {
  if (config.demoMode) return;
  if (!msg?.content) return;
  // 命中本条音频缓存 → 直接播，不打 MiMo
  if (msg.audioCache?.audioBase64) {
    try { await playTtsResult(msg.audioCache); } catch (_) {}
    return;
  }
  try {
    const tts = await ttsApi.speak(msg.content);
    if (tts?.audioBase64 && tts?.mimeType) {
      msg.audioCache = { audioBase64: tts.audioBase64, mimeType: tts.mimeType };
    }
    await playTtsResult(tts);
  } catch (_) {
    try { await playTtsResult({ fallbackText: msg.content }); } catch (_) {}
  }
}

/** 自动播报内部用：跟 speakReply 一样会写入 msg.audioCache。 */
async function tryAutoSpeak(msg) {
  if (config.demoMode) return;
  try {
    const tts = await ttsApi.speak(msg.content);
    if (tts?.audioBase64 && tts?.mimeType) {
      msg.audioCache = { audioBase64: tts.audioBase64, mimeType: tts.mimeType };
    }
    await playTtsResult(tts);
  } catch (_) {
    try { await playTtsResult({ fallbackText: msg.content }); } catch (_) {}
  }
}

// ====== 开/关 ======
async function expand() {
  if (open.value) return;
  open.value = true;
  if (!canChat.value) return;
  // 进 chat tab 时如果消息为空，留个开场白（直接全显，无需打字机）
  if (tab.value === 'chat' && messages.value.length === 0) {
    const greet = `${greeting.value}。今天想随便聊聊，还是聊点训练？`;
    messages.value.push({
      role: 'assistant',
      content: greet,
      revealed: greet.length,
      time: Date.now()
    });
  }
  // 建议态需要懒加载
  if (tab.value === 'snap' && !suggestion.value) await fetchSuggestion();
}
function collapse() {
  // 关闭浮窗：停掉聊天 TTS/打字机 + 畅聊语音（不然窗口关了还在播/还在录）
  stopPlayback();
  if (tab.value === 'voice') {
    try { voiceRef.value?.stop?.(); } catch (_) {}
  }
  open.value = false;
}

function onDocClick(e) {
  if (!open.value) return;
  if (widgetEl.value && !widgetEl.value.contains(e.target)) collapse();
}
function onKey(e) {
  if (e.key === 'Escape' && open.value) collapse();
}
function onEnter(e) {
  // Enter 发送，Shift+Enter 换行
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    sendMessage();
  }
}

watch(tab, async (v, old) => {
  // 切走畅聊：自动停 STT/TTS，避免离开后继续录音
  if (old === 'voice' && v !== 'voice') {
    try { voiceRef.value?.stop?.(); } catch (_) {}
  }
  // 切走闲聊：停掉打字机与 TTS
  if (old === 'chat' && v !== 'chat') stopPlayback();
  if (v === 'snap' && !suggestion.value) await fetchSuggestion();
  if (v === 'chat') await scrollToBottom();
});

watch(() => config.demoMode, () => {
  suggestion.value = null;
  emotionMood.value = null;
});

function shouldAutoOpen() {
  return route.path === '/train'
    && typeof window !== 'undefined'
    && window.matchMedia('(min-width: 1024px)').matches;
}

watch(() => route.path, async () => {
  if (open.value) collapse();
  if (shouldAutoOpen()) {
    await nextTick();
    await expand();
  }
});

onMounted(() => {
  const c = readSnapCache();
  if (c?.suggestion) {
    suggestion.value = c.suggestion;
    emotionMood.value = c.emotionMood || null;
  }
  loadMessages();
  document.addEventListener('click', onDocClick);
  document.addEventListener('keydown', onKey);
  if (shouldAutoOpen()) expand();
});
onBeforeUnmount(() => {
  stopPlayback();
  document.removeEventListener('click', onDocClick);
  document.removeEventListener('keydown', onKey);
});

function fmtTime(t) {
  if (!t) return '';
  const d = new Date(t);
  return `${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`;
}

/** 打字机中：revealed 在 [0, content.length) 之间 */
function isTyping(m) {
  return m && m.role === 'assistant' && typeof m.revealed === 'number'
      && m.revealed < (m.content?.length || 0);
}

/** 渲染文本：打字机模式只显示 revealed 前的字符；其它情况直接全显示 */
function displayText(m) {
  if (!m) return '';
  if (m.role === 'assistant' && typeof m.revealed === 'number') {
    return m.content.slice(0, m.revealed);
  }
  return m.content;
}
</script>

<template>
  <div v-if="visible" ref="widgetEl"
       :class="['companion-fab', { open, 'mode-chat': tab === 'chat', 'mode-voice': tab === 'voice' }]">
    <transition name="cmp-pop">
      <div v-if="open" class="panel" role="dialog" aria-label="陪伴教练">
        <!-- 顶部：身份 + 关闭 -->
        <header class="p-head">
          <div class="who">
            <span class="dot"></span>
            <span class="nm">{{ companionName }}</span>
            <span class="rl">陪伴教练</span>
            <span v-if="config.demoMode" class="demo-badge">演示模式</span>
          </div>
          <button class="close" @click="collapse" aria-label="收起">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M6 6l12 12 M18 6L6 18"/></svg>
          </button>
        </header>

        <section v-if="!canChat" class="guest-gate">
          <div class="guest-orb">
            <svg viewBox="0 0 32 32" fill="none">
              <circle cx="16" cy="16" r="15" fill="#d97757"/>
              <circle cx="11" cy="14" r="1.6" fill="#fff"/>
              <circle cx="21" cy="14" r="1.6" fill="#fff"/>
              <path d="M11 20q5 4 10 0" stroke="#fff" stroke-width="1.6" stroke-linecap="round"/>
            </svg>
          </div>
          <h3>{{ companionName }} 在这里</h3>
          <p>登录后可恢复历史对话、训练建议、情绪陪伴和语音畅聊。</p>
          <button class="guest-login" @click="router.push('/login')">登录后开始对话</button>
        </section>

        <!-- Tab 切换 -->
        <nav v-if="canChat" class="p-tabs">
          <button :class="['tab', { active: tab === 'chat' }]"  @click="tab = 'chat'">闲聊</button>
          <button v-if="auth.isLogin" :class="['tab', { active: tab === 'voice' }]" @click="tab = 'voice'">
            畅聊<span class="tab-new">语音</span>
          </button>
          <button :class="['tab', { active: tab === 'snap' }]"  @click="tab = 'snap'">一句话</button>
        </nav>

        <!-- ============= 闲聊 ============= -->
        <section v-if="canChat && tab === 'chat'" class="chat-area">
          <div class="chat-head-row">
            <div v-if="moodTag" :class="['mood', moodTag.tone]">
              <span>{{ moodTag.label }}</span>
            </div>
            <button class="reminisce-btn"
                    :disabled="sending"
                    :title="messages.length < 2 ? '聊几句再来叙旧吧' : '让我回顾我们最近聊过的事'"
                    @click="reminisce">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="M12 2a10 10 0 1 0 10 10h-2a8 8 0 1 1-8-8z"/>
                <path d="M12 6v6l4 2"/>
              </svg>
              <span>叙旧一下</span>
            </button>
          </div>

          <div ref="listEl" class="msg-list">
            <div v-for="(m, i) in messages" :key="i"
                 :class="['msg', m.role, { error: m.error, system: m.system }]">
              <div class="bubble">
                <div class="text">
                  <span>{{ displayText(m) }}</span><span v-if="isTyping(m)" class="caret">▍</span>
                </div>
                <div v-if="m.recalled && m.recalled.length" class="recalled" title="本次唤起的记忆">
                  <div class="recalled-head">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M12 2a10 10 0 1 0 10 10h-2a8 8 0 1 1-8-8z M12 6v6l4 2"/>
                    </svg>
                    <span>想起 {{ m.recalled.length }} 段记忆</span>
                  </div>
                  <ul>
                    <li v-for="(r, k) in m.recalled" :key="k">{{ r }}</li>
                  </ul>
                </div>
                <div class="meta-row" v-if="!m.system">
                  <span class="t">{{ fmtTime(m.time) }}</span>
                  <button
                    v-if="!config.demoMode && m.role === 'assistant' && !m.error && !isTyping(m) && m.audioCache?.audioBase64"
                    class="link replay"
                    title="重听这条"
                    @click="speakReply(m)"
                  >
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M3 12a9 9 0 1 0 3-6.7"/><path d="M3 4v5h5"/>
                    </svg>
                  </button>
                </div>
              </div>
            </div>
            <div v-if="sending" class="msg assistant typing">
              <div class="bubble"><span class="dot-anim"></span><span class="dot-anim"></span><span class="dot-anim"></span></div>
            </div>
          </div>

          <div class="quick-row">
            <template v-if="followups.length">
              <button v-for="p in followups" :key="p" class="chip" :disabled="sending" @click="sendMessage(p)">{{ p }}</button>
            </template>
            <template v-else>
              <button v-for="p in QUICK_PROMPTS" :key="p" class="chip" @click="applyPrompt(p)">{{ p }}</button>
            </template>
          </div>

          <div class="composer">
            <textarea
              v-model="input"
              rows="1"
              maxlength="500"
              placeholder="跟我说说今天怎么样…"
              @keydown="onEnter"
              :disabled="sending"
            ></textarea>
            <button class="send" :disabled="sending || !input.trim()" @click="sendMessage()" aria-label="发送">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="M22 2L11 13 M22 2l-7 20-4-9-9-4 20-7z"/>
              </svg>
            </button>
          </div>

          <div class="foot-tools">
            <button class="mini ghost" @click="clearChat">清空这次对话</button>
          </div>
        </section>

        <!-- ============= 畅聊（语音 hands-free） ============= -->
        <section v-else-if="auth.isLogin && tab === 'voice'" class="voice-area">
          <VoiceCompanion ref="voiceRef" />
        </section>

        <!-- ============= 一句话 ============= -->
        <section v-else-if="canChat" class="snap-area">
          <div class="greet">{{ greeting }}</div>
          <div v-if="moodTag" :class="['mood', moodTag.tone]">
            <span>{{ moodTag.label }}</span>
          </div>

          <div v-if="loading" class="loading">正在听你的训练数据…</div>
          <div v-else-if="suggestion" class="snap-body">
            <p v-if="suggestion.review" class="line">{{ suggestion.review }}</p>
            <p v-if="suggestion.suggestion" class="line muted">{{ suggestion.suggestion }}</p>
            <p v-if="suggestion.encouragement" class="line italic">"{{ suggestion.encouragement }}"</p>
          </div>
          <div v-else class="loading">还没什么数据可说，先去练一次吧。</div>

          <footer class="snap-foot">
            <button v-if="!config.demoMode" class="mini" :disabled="!suggestion || speaking" @click="speak">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M11 5L6 9H2v6h4l5 4V5z"/><path d="M15.54 8.46a5 5 0 0 1 0 7.07"/></svg>
              <span>{{ speaking ? '播报中' : '听一下' }}</span>
            </button>
            <button class="mini" :disabled="loading" @click="fetchSuggestion(true)">换一种</button>
            <button class="mini" @click="router.push('/emotion')">心情</button>
            <button class="mini" @click="router.push('/plans')">计划</button>
          </footer>
        </section>
      </div>
    </transition>

    <button class="fab-btn" :class="{ talking: sending || speaking }" @click.stop="open ? collapse() : expand()" :aria-expanded="open" aria-label="陪伴教练">
      <svg viewBox="0 0 32 32" fill="none">
        <defs>
          <linearGradient id="fabgrad" x1="0" y1="0" x2="32" y2="32">
            <stop stop-color="#d97757"/><stop offset="1" stop-color="#c96442"/>
          </linearGradient>
        </defs>
        <circle cx="16" cy="16" r="15" fill="url(#fabgrad)"/>
        <circle cx="11" cy="14" r="1.6" fill="#fff" class="eye"/>
        <circle cx="21" cy="14" r="1.6" fill="#fff" class="eye"/>
        <path d="M11 20q5 4 10 0" stroke="#fff" stroke-width="1.6" stroke-linecap="round" fill="none"/>
      </svg>
      <span class="ring"></span>
    </button>
  </div>
</template>

<style scoped>
.companion-fab {
  position: fixed;
  right: 18px;
  bottom: 84px;
  z-index: 8500;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 12px;
  pointer-events: none;
}
.companion-fab > * { pointer-events: auto; }
@media (min-width: 768px) {
  .companion-fab { bottom: 24px; right: 24px; }
}

/* ==== FAB ==== */
.fab-btn {
  width: 56px; height: 56px;
  border-radius: 50%;
  border: 0; padding: 0;
  background: transparent;
  cursor: pointer;
  position: relative;
  filter: drop-shadow(0 6px 14px rgba(217, 119, 87, .35));
  animation: fab-breath 3.6s ease-in-out infinite;
}
.fab-btn svg { width: 100%; height: 100%; display: block; }
.fab-btn .eye { animation: fab-blink 5.5s infinite; transform-origin: center; }
.fab-btn .ring {
  position: absolute; inset: -4px;
  border-radius: 50%;
  border: 2px solid rgba(217, 119, 87, .35);
  opacity: 0;
  animation: fab-pulse 2.4s ease-out infinite;
}
.fab-btn.talking { animation-duration: 1.4s; }
.fab-btn.talking .ring { opacity: 1; animation-duration: 1.2s; }
.fab-btn:hover { transform: scale(1.05); }
@keyframes fab-breath { 0%,100% { transform: scale(1); } 50% { transform: scale(1.06); } }
@keyframes fab-blink  { 0%,92%,96%,100% { transform: scaleY(1); } 94% { transform: scaleY(.1); } }
@keyframes fab-pulse  { 0% { transform: scale(.85); opacity: .55; } 100% { transform: scale(1.3); opacity: 0; } }

/* ==== Panel ==== */
.panel {
  width: 300px;
  max-width: calc(100vw - 36px);
  padding: 12px 12px 10px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 18px;
  box-shadow: 0 12px 36px rgba(0, 0, 0, .22);
  font-family: var(--font-ui);
}
.companion-fab.mode-chat .panel { width: 340px; padding-bottom: 10px; }
.companion-fab.mode-voice .panel { width: 360px; padding-bottom: 10px; }
@media (min-width: 768px) {
  .companion-fab.mode-chat .panel  { width: 380px; }
  .companion-fab.mode-voice .panel { width: 400px; }
}

.guest-gate {
  min-height: 310px;
  padding: 42px 30px 34px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
}
.guest-orb { width: 66px; height: 66px; margin-bottom: 16px; }
.guest-orb svg { width: 100%; height: 100%; filter: drop-shadow(0 10px 18px rgba(201, 100, 66, .25)); }
.guest-gate h3 { margin: 0 0 8px; font-size: 18px; color: var(--text); }
.guest-gate p { margin: 0 0 20px; max-width: 280px; color: var(--text-2); font-size: 13px; line-height: 1.7; }
.guest-login {
  padding: 10px 20px;
  border-radius: 999px;
  background: var(--grad-primary);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  box-shadow: 0 8px 20px rgba(201, 100, 66, .22);
}

.cmp-pop-enter-active, .cmp-pop-leave-active {
  transition: opacity .18s ease, transform .22s cubic-bezier(.2, 1.1, .3, 1);
  transform-origin: bottom right;
}
.cmp-pop-enter-from, .cmp-pop-leave-to { opacity: 0; transform: translateY(8px) scale(.96); }

/* ==== 顶部 ==== */
.p-head { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.who { display: flex; align-items: center; gap: 6px; flex: 1; min-width: 0; }
.who .dot { width: 8px; height: 8px; border-radius: 50%; background: #22c55e; box-shadow: 0 0 0 3px rgba(34,197,94,.18); }
.who .nm { font-weight: 700; color: var(--text); font-size: 14px; }
.who .rl { font-size: 11px; color: var(--text-3); letter-spacing: .04em; }
.demo-badge {
  padding: 2px 7px;
  border-radius: 999px;
  background: rgba(217, 119, 87, .14);
  border: 1px solid rgba(217, 119, 87, .28);
  color: var(--orange, #d97757);
  font-size: 10px;
  font-weight: 700;
}
.close {
  width: 24px; height: 24px;
  display: flex; align-items: center; justify-content: center;
  border: 0; background: transparent; cursor: pointer; color: var(--text-3);
  border-radius: 6px;
}
.close:hover { background: var(--bg-card-2); color: var(--text); }
.close svg { width: 14px; height: 14px; }

/* ==== Tabs ==== */
.p-tabs {
  display: flex; gap: 4px;
  padding: 3px;
  background: var(--bg-card-2);
  border-radius: 10px;
  margin-bottom: 8px;
}
.tab {
  flex: 1; padding: 6px 8px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--text-2);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all .15s;
  display: inline-flex; align-items: center; justify-content: center; gap: 4px;
}
.tab:hover { color: var(--text); }
.tab.active {
  background: var(--bg-card);
  color: var(--text);
  box-shadow: 0 1px 4px rgba(0,0,0,.08);
}
.tab .tab-new {
  font-size: 9px;
  font-weight: 700;
  padding: 1px 5px;
  border-radius: 6px;
  background: linear-gradient(135deg, #d97757, #c96442);
  color: #fff;
  letter-spacing: .04em;
  line-height: 1.4;
}

/* voice 区域只是个 wrapper，把上下边距收紧 */
.voice-area { padding: 2px 0 0; }

/* ==== mood badge (shared) ==== */
.mood {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 3px 10px;
  border-radius: 12px;
  font-size: 11px;
  background: var(--bg-card-2);
  color: var(--text-2);
}
.mood.pos { background: rgba(46,160,67,.14); color: #2ea043; }
.mood.neu { background: rgba(120,140,180,.14); color: var(--text-2); }
.mood.neg { background: rgba(217,87,87,.14); color: #d95757; }

/* ==== Chat 顶部一行：mood + 叙旧 ==== */
.chat-head-row {
  display: flex; align-items: center; gap: 6px;
  margin-bottom: 6px;
  min-height: 24px;
}
.chat-head-row .mood { margin-bottom: 0; }
.reminisce-btn {
  margin-left: auto;
  display: inline-flex; align-items: center; gap: 4px;
  padding: 4px 10px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--bg-card-2);
  color: var(--text-2);
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  transition: all .15s;
}
.reminisce-btn:hover:not(:disabled) { color: var(--cyan); border-color: var(--cyan); background: var(--cyan-dim); }
.reminisce-btn:disabled { opacity: .5; cursor: not-allowed; }
.reminisce-btn svg { width: 12px; height: 12px; }

/* 打字机光标 */
.caret {
  display: inline-block;
  margin-left: 1px;
  color: var(--cyan);
  animation: cmp-caret 1s steps(2) infinite;
}
@keyframes cmp-caret { 50% { opacity: 0; } }

/* system 型（叙旧前的"想想看…"那种过渡） */
.msg.system .bubble {
  background: transparent;
  color: var(--text-3);
  font-size: 11px;
  font-style: italic;
  border: 1px dashed var(--border);
  padding: 4px 10px;
}

/* ==== Chat area ==== */
.msg-list {
  height: 260px;
  overflow-y: auto;
  padding: 4px 2px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  scrollbar-width: thin;
}
.msg-list::-webkit-scrollbar { width: 4px; }
.msg-list::-webkit-scrollbar-thumb { background: var(--border); border-radius: 2px; }

.msg { display: flex; }
.msg.user { justify-content: flex-end; }
.msg.assistant { justify-content: flex-start; }
.msg .bubble {
  max-width: 84%;
  padding: 8px 11px;
  border-radius: 14px;
  font-size: 13px;
  line-height: 1.5;
  word-break: break-word;
}
.msg.assistant .bubble {
  background: var(--bg-card-2);
  color: var(--text);
  border-bottom-left-radius: 4px;
}
.msg.user .bubble {
  background: linear-gradient(135deg, rgba(217,119,87,.18), rgba(201,100,66,.10));
  color: var(--text);
  border: 1px solid rgba(217,119,87,.22);
  border-bottom-right-radius: 4px;
}
.msg.assistant.error .bubble { background: rgba(217,87,87,.10); color: #b05151; }

.msg .text { white-space: pre-wrap; }
.msg .meta-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 4px;
  gap: 8px;
}
.msg .t { font-size: 10px; color: var(--text-3); }
.msg .link {
  font-size: 11px;
  color: var(--cyan);
  background: transparent;
  border: 0;
  cursor: pointer;
  padding: 0;
}
.msg .link:hover { text-decoration: underline; }
.msg .link.replay {
  display: inline-flex; align-items: center;
  width: 18px; height: 18px;
  opacity: .55;
  transition: opacity .15s;
}
.msg .link.replay:hover { opacity: 1; text-decoration: none; }
.msg .link.replay svg { width: 12px; height: 12px; }

.recalled {
  margin-top: 6px;
  padding: 6px 8px;
  border-left: 2px solid var(--cyan);
  background: var(--cyan-dim);
  border-radius: 6px;
  font-size: 11px;
  color: var(--text-2);
}
.recalled-head {
  display: flex; align-items: center; gap: 4px;
  font-weight: 700; color: var(--cyan);
  margin-bottom: 2px;
}
.recalled-head svg { width: 12px; height: 12px; }
.recalled ul { list-style: none; margin: 0; padding: 0; }
.recalled li {
  padding: 1px 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
}

.typing .bubble {
  display: inline-flex; gap: 3px; align-items: center;
  padding: 10px 12px;
}
.dot-anim {
  width: 5px; height: 5px; border-radius: 50%;
  background: var(--text-3);
  animation: cmp-bounce 1.4s infinite both;
}
.dot-anim:nth-child(2) { animation-delay: .2s; }
.dot-anim:nth-child(3) { animation-delay: .4s; }
@keyframes cmp-bounce {
  0%, 80%, 100% { transform: scale(.6); opacity: .35; }
  40%           { transform: scale(1);  opacity: 1; }
}

.quick-row {
  display: flex; flex-wrap: wrap; gap: 4px;
  margin-top: 8px;
}
.chip {
  font-size: 11px;
  padding: 4px 9px;
  border-radius: 12px;
  background: var(--bg-card-2);
  color: var(--text-2);
  border: 1px solid var(--border);
  cursor: pointer;
  transition: all .15s;
}
.chip:hover { color: var(--cyan); border-color: var(--cyan); background: var(--cyan-dim); }
.chip:disabled { opacity: .5; cursor: not-allowed; }

.composer {
  display: flex; align-items: flex-end; gap: 6px;
  margin-top: 8px;
  background: var(--bg-card-2);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 6px 6px 6px 10px;
}
.composer textarea {
  flex: 1;
  border: 0;
  background: transparent;
  resize: none;
  font: inherit;
  font-size: 13px;
  color: var(--text);
  outline: none;
  max-height: 100px;
  line-height: 1.5;
}
.composer .send {
  width: 30px; height: 30px;
  flex-shrink: 0;
  border-radius: 50%;
  border: 0;
  background: var(--grad-primary, linear-gradient(135deg, #d97757, #c96442));
  color: #fff;
  cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  transition: all .15s;
}
.composer .send:disabled { opacity: .45; cursor: not-allowed; }
.composer .send svg { width: 14px; height: 14px; }

.foot-tools {
  display: flex; justify-content: flex-end;
  margin-top: 6px;
}
.mini {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 10px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--bg-card-2);
  color: var(--text-2);
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  transition: all .15s;
}
.mini:hover:not(:disabled) { color: var(--cyan); border-color: var(--cyan); background: var(--cyan-dim); }
.mini:disabled { opacity: .5; cursor: not-allowed; }
.mini.ghost { background: transparent; border-color: transparent; color: var(--text-3); }
.mini.ghost:hover { color: var(--text-2); background: var(--bg-card-2); }
.mini svg { width: 12px; height: 12px; }

/* ==== Snap area ==== */
.greet { font-size: 13px; color: var(--text); font-weight: 600; margin-bottom: 4px; }
.loading { margin: 10px 0 4px; font-size: 12px; color: var(--text-3); }
.snap-body { margin-top: 6px; display: flex; flex-direction: column; gap: 6px; }
.line { margin: 0; font-size: 12.5px; color: var(--text); line-height: 1.6; }
.line.muted { color: var(--text-2); }
.line.italic { font-style: italic; color: var(--text-2); border-left: 2px solid var(--orange, #d97757); padding-left: 8px; }

.snap-foot {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--border);
  display: flex; flex-wrap: wrap; gap: 6px;
}
</style>
