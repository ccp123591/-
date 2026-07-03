<script setup>
import { ref, onMounted } from 'vue';
import { socialApi } from '@/api/social';
import { useAuthStore } from '@/stores/auth';
import { useAppStore } from '@/stores/app';
import EmptyState from '@/components/common/EmptyState.vue';

const auth = useAuthStore();
const app = useAppStore();
const posts = ref([]);
const loading = ref(false);
const newPost = ref('');
const posting = ref(false);

async function publish() {
  const text = newPost.value.trim();
  if (!text || posting.value) return;
  posting.value = true;
  try {
    await socialApi.post({ content: text, visibility: 'PUBLIC' });
    newPost.value = '';
    app.showToast('已发布', 'success');
    await load();
  } catch (_) { /* 拦截器已提示 */ } finally { posting.value = false; }
}

async function load() {
  if (!auth.isLogin) return;
  loading.value = true;
  try {
    const res = await socialApi.feed({ page: 1, size: 20 });
    posts.value = (res?.items || []).map(p => ({
      ...p, showComments: false, comments: null, draft: '', sending: false
    }));
  } finally { loading.value = false; }
}

async function toggleLike(p) {
  try {
    if (p.liked) { await socialApi.unlike(p.id); p.liked = false; p.likes = Math.max(0, (p.likes || 0) - 1); }
    else { await socialApi.like(p.id); p.liked = true; p.likes = (p.likes || 0) + 1; }
  } catch (_) { /* 拦截器已提示 */ }
}

async function toggleComments(p) {
  p.showComments = !p.showComments;
  if (p.showComments && p.comments === null) {
    p.comments = (await socialApi.comments(p.id)) || [];
  }
}

async function submitComment(p) {
  const text = (p.draft || '').trim();
  if (!text || p.sending) return;
  p.sending = true;
  try {
    await socialApi.comment(p.id, text);
    if (p.comments === null) p.comments = [];
    p.comments.push({ id: Date.now(), content: text, nickname: auth.displayName });
    p.commentsCount = (p.commentsCount || 0) + 1;
    p.draft = '';
  } catch (_) { /* 拦截器已提示 */ } finally { p.sending = false; }
}

function fmtTime(t) {
  if (!t) return '';
  const d = new Date(t), diff = (Date.now() - d.getTime()) / 1000;
  if (diff < 60) return '刚刚';
  if (diff < 3600) return `${Math.floor(diff / 60)} 分钟前`;
  if (diff < 86400) return `${Math.floor(diff / 3600)} 小时前`;
  if (diff < 2592000) return `${Math.floor(diff / 86400)} 天前`;
  return d.toLocaleDateString('zh-CN');
}

// 根据姓名生成暖色系 HSL 渐变，保持与排行榜一致的视觉语言
function avatarStyle(name) {
  let h = 0;
  for (const ch of (name || '?')) h = (h * 31 + ch.charCodeAt(0)) | 0;
  const hue = 15 + (Math.abs(h) % 40);
  const c1 = `hsl(${hue}, 52%, 62%)`;
  const c2 = `hsl(${(hue + 340) % 360}, 42%, 44%)`;
  return { background: `linear-gradient(135deg, ${c1}, ${c2})` };
}

onMounted(load);
</script>

<template>
  <div class="page-wrap feed-page">
    <div class="page-head">
      <h2>训练动态</h2>
      <p class="sub">看看大家都在练什么</p>
    </div>

    <template v-if="auth.isLogin">
      <div class="composer">
        <textarea v-model="newPost" rows="2" maxlength="500" placeholder="分享一下今天的训练…"></textarea>
        <div class="composer-foot">
          <button class="post-btn" :disabled="posting || !newPost.trim()" @click="publish">发布</button>
        </div>
      </div>

      <div v-for="p in posts" :key="p.id" class="post-card">
        <div class="post-head">
          <div class="p-avatar" :style="avatarStyle(p.nickname)">{{ (p.nickname || '健')[0] }}</div>
          <div>
            <div class="p-user">{{ p.nickname || '健友' }}</div>
            <div class="p-time">{{ fmtTime(p.createdAt) }}</div>
          </div>
        </div>
        <div class="post-content">{{ p.content }}</div>
        <div class="post-actions">
          <button :class="['a-btn', p.liked ? 'liked' : '']" @click="toggleLike(p)">
            <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
              <path d="M10 17s-5.5-3-7-7 1.5-7 4-7 3 2 3 2 .5-2 3-2 5.5 3 4 7-7 7-7 7z" :fill="p.liked ? 'currentColor' : 'none'"/>
            </svg>
            {{ p.likes || 0 }}
          </button>
          <button :class="['a-btn', p.showComments ? 'active' : '']" @click="toggleComments(p)">
            <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"><path d="M3 12a8 8 0 1 1 3.5 6.6L3 19l.5-3.5A8 8 0 0 1 3 12z"/></svg>
            {{ p.commentsCount || 0 }}
          </button>
        </div>

        <div v-if="p.showComments" class="comments">
          <div v-for="c in (p.comments || [])" :key="c.id" class="comment">
            <span class="c-name">{{ c.nickname || '健友' }}</span>
            <span class="c-text">{{ c.content }}</span>
          </div>
          <div v-if="p.comments && !p.comments.length" class="c-empty">还没有评论，来抢沙发</div>
          <div class="c-input">
            <input v-model="p.draft" type="text" placeholder="写下你的评论…" maxlength="200"
                   @keyup.enter="submitComment(p)" />
            <button :disabled="p.sending || !p.draft.trim()" @click="submitComment(p)">发送</button>
          </div>
        </div>
      </div>

      <div v-if="loading" class="placeholder">加载中…</div>
      <EmptyState v-else-if="!posts.length" title="还没有动态" desc="完成训练后分享给大家吧" />
    </template>

    <EmptyState v-else title="登录后查看动态" desc="和健友们互相激励、点赞、评论" />
  </div>
</template>

<style scoped>
.page-head { margin-bottom: 16px; }
.page-head h2 { font-family: var(--font-heading); font-size: 28px; font-weight: 700; color: var(--text); letter-spacing: -.04em; }
.page-head .sub { font-size: 12px; color: var(--text-2); margin-top: 4px; }

.post-card {
  padding: 16px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 16px;
  margin-bottom: 12px;
}
.post-head { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.p-avatar {
  width: 40px; height: 40px;
  border-radius: 50%;
  color: #fff;
  font-weight: 800;
  display: flex; align-items: center; justify-content: center;
  position: relative;
  overflow: hidden;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, .28), 0 2px 8px rgba(60, 44, 30, .18);
}
.p-avatar::after {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(circle at 30% 22%, rgba(255, 255, 255, .32), transparent 55%);
  pointer-events: none;
}
.p-user { font-size: 13px; font-weight: 700; }
.p-time { font-size: 11px; color: var(--text-3); }

.post-content {
  font-size: 13px;
  color: var(--text);
  line-height: 1.6;
  margin-bottom: 12px;
}

.workout-card {
  padding: 12px 14px;
  background: linear-gradient(135deg, var(--cyan-dim), var(--purple-dim));
  border: 1px solid rgba(217, 119, 87, .15);
  border-radius: 12px;
  margin-bottom: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.w-action {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 700;
}
.w-dot {
  width: 8px; height: 8px;
  border-radius: 50%;
  background: var(--cyan);
  box-shadow: 0 0 8px var(--cyan);
}
.w-stats { display: flex; gap: 14px; }
.w-stats div { text-align: center; }
.w-stats span { font-family: var(--font-mono); font-variant-numeric: tabular-nums; font-size: 16px; font-weight: 600; color: var(--text); }
.w-stats small { display: block; font-size: 9px; color: var(--text-3); }

.post-actions {
  display: flex;
  gap: 14px;
  padding-top: 10px;
  border-top: 1px solid var(--border);
}
.a-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--text-2);
  transition: color var(--transition);
}
.a-btn svg { width: 16px; height: 16px; }
.a-btn:hover { color: var(--text); }
.a-btn.liked { color: var(--red); }
.a-btn.active { color: var(--cyan); }

.comments { margin-top: 12px; padding-top: 12px; border-top: 1px solid var(--border); }
.comment { font-size: 13px; line-height: 1.6; margin-bottom: 6px; }
.c-name { font-weight: 700; color: var(--text); margin-right: 6px; }
.c-text { color: var(--text-2); }
.c-empty { font-size: 12px; color: var(--text-3); padding: 4px 0 8px; }
.c-input { display: flex; gap: 8px; margin-top: 8px; }
.c-input input {
  flex: 1;
  padding: 8px 12px;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--bg-card-2);
  color: var(--text);
  font-size: 13px;
}
.c-input button {
  padding: 8px 14px;
  border-radius: 10px;
  background: var(--grad-primary);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
}
.c-input button:disabled { opacity: .5; }

.placeholder { text-align: center; padding: 40px 20px; color: var(--text-3); font-size: 13px; }

.composer {
  padding: 14px 16px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 16px;
  margin-bottom: 12px;
}
.composer textarea {
  width: 100%;
  border: none;
  background: transparent;
  color: var(--text);
  font: inherit;
  font-size: 13px;
  resize: vertical;
  outline: none;
}
.composer-foot { display: flex; justify-content: flex-end; margin-top: 8px; }
.post-btn {
  padding: 8px 18px;
  border-radius: 100px;
  background: var(--grad-primary);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
}
.post-btn:disabled { opacity: .5; }
</style>
