<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import { useAppStore } from '@/stores/app';
import { storage } from '@/modules/storage';

const router = useRouter();
const auth = useAuthStore();
const app = useAppStore();

const stats = ref({ total: 0, sessions: 0, bestScore: 0 });
const badges = ref([
  { id: 1, name: '初次训练', icon: '🎯', unlocked: true, desc: '完成第一次训练' },
  { id: 2, name: '百次达成', icon: '💯', unlocked: false, desc: '累计完成 100 次' },
  { id: 3, name: '七日连续', icon: '🔥', unlocked: false, desc: '连续打卡 7 天' },
  { id: 4, name: '完美评分', icon: '⭐', unlocked: false, desc: '单次评分 95+' },
  { id: 5, name: '节奏大师', icon: '🎵', unlocked: false, desc: '节奏评分 100' },
  { id: 6, name: '坚持不懈', icon: '💪', unlocked: false, desc: '累计 30 天' }
]);

async function loadStats() {
  const all = await storage.getAllSessions();
  stats.value.sessions = all.length;
  stats.value.total = all.reduce((s, r) => s + (r.reps || 0), 0);
  stats.value.bestScore = all.length ? Math.max(...all.map(r => r.score || 0)) : 0;
  // 本地判断徽章
  if (all.length >= 1) badges.value[0].unlocked = true;
  if (stats.value.total >= 100) badges.value[1].unlocked = true;
  if (stats.value.bestScore >= 95) badges.value[3].unlocked = true;
}

async function logout() {
  const ok = await app.showConfirm('退出登录', '确定要退出登录吗？');
  if (ok) {
    auth.logout();
    app.showToast('已退出', 'success');
    router.push('/login');
  }
}

onMounted(loadStats);
</script>

<template>
  <div class="page-wrap profile-page">
    <!-- 头部 Hero -->
    <div class="profile-hero">
      <div class="avatar-big" :style="{ background: auth.avatar ? `url(${auth.avatar}) center/cover` : 'var(--grad-primary)' }">
        <span v-if="!auth.avatar">{{ auth.displayName?.[0]?.toUpperCase() }}</span>
      </div>
      <div class="profile-name">{{ auth.displayName }}</div>
      <div class="profile-role">
        <span v-if="auth.isLogin" class="role-tag role-tag-cyan">已登录</span>
        <span v-else-if="auth.guestMode" class="role-tag role-tag-gray">游客模式</span>
        <span v-else class="role-tag role-tag-gray">未登录</span>
      </div>

      <div class="hero-stats">
        <div class="hero-stat">
          <div class="hs-v">{{ stats.sessions }}</div>
          <div class="hs-l">训练次数</div>
        </div>
        <div class="hs-divider"></div>
        <div class="hero-stat">
          <div class="hs-v">{{ stats.total }}</div>
          <div class="hs-l">总动作</div>
        </div>
        <div class="hs-divider"></div>
        <div class="hero-stat">
          <div class="hs-v">{{ stats.bestScore }}</div>
          <div class="hs-l">最高分</div>
        </div>
      </div>
    </div>

    <!-- 徽章 -->
    <div class="section">
      <div class="section-head">
        <h3>成就徽章</h3>
        <span class="more">{{ badges.filter(b => b.unlocked).length }} / {{ badges.length }}</span>
      </div>
      <div class="badges-grid">
        <div v-for="b in badges" :key="b.id" :class="['badge-card', { unlocked: b.unlocked }]">
          <div class="b-ico">{{ b.icon }}</div>
          <div class="b-name">{{ b.name }}</div>
          <div class="b-desc">{{ b.desc }}</div>
        </div>
      </div>
    </div>

    <!-- 菜单 -->
    <div class="menu-list">
      <router-link to="/settings" class="menu-item">
        <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"><circle cx="10" cy="10" r="2.5"/><path d="M10 3v1.5 M10 15.5V17 M3 10h1.5 M15.5 10H17 M5 5l1 1 M14 14l1 1 M5 15l1-1 M14 6l1-1"/></svg>
        <span>训练设置</span>
        <svg class="arrow" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M8 5l5 5-5 5"/></svg>
      </router-link>
      <router-link v-if="auth.isAdmin" to="/admin" class="menu-item">
        <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"><rect x="3" y="3" width="14" height="14" rx="2"/><path d="M3 8h14 M8 17V8"/></svg>
        <span>管理后台</span>
        <svg class="arrow" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M8 5l5 5-5 5"/></svg>
      </router-link>
      <button v-if="auth.isLogin" class="menu-item danger" @click="logout">
        <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"><path d="M13 4h3a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1h-3 M8 10h9 M13 6l4 4-4 4"/></svg>
        <span>退出登录</span>
      </button>
      <router-link v-else to="/login" class="menu-item login">
        <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"><path d="M9 4h-4a1 1 0 0 0-1 1v10a1 1 0 0 0 1 1h4 M13 10H4 M10 6l4 4-4 4"/></svg>
        <span>登录 / 注册</span>
      </router-link>
    </div>
  </div>
</template>

<style scoped>
.profile-hero {
  padding: 24px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 20px;
  text-align: center;
  margin-bottom: 16px;
  position: relative;
  overflow: hidden;
}
.profile-hero::before {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(ellipse at top, rgba(124, 106, 255, .08), transparent 70%);
  pointer-events: none;
}
.avatar-big {
  width: 72px; height: 72px;
  border-radius: 50%;
  margin: 0 auto 12px;
  background: var(--grad-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 28px;
  font-weight: 800;
  box-shadow: 0 8px 24px rgba(0, 240, 255, .3);
}
.profile-name { font-size: 18px; font-weight: 800; margin-bottom: 6px; }
.profile-role { margin-bottom: 20px; }
.role-tag {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 100px;
  font-size: 11px;
  font-weight: 600;
}
.role-tag-cyan { background: var(--cyan-dim); color: var(--cyan); }
.role-tag-gray { background: var(--bg-card-2); color: var(--text-3); }

.hero-stats {
  display: flex;
  justify-content: space-around;
  align-items: center;
  padding: 14px 0 0;
  border-top: 1px solid var(--border);
}
.hero-stat { flex: 1; }
.hs-v { font-size: 22px; font-weight: 800; color: var(--cyan); }
.hs-l { font-size: 11px; color: var(--text-3); margin-top: 2px; }
.hs-divider { width: 1px; height: 32px; background: var(--border); }

.section { margin-bottom: 16px; }
.section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 4px;
  margin-bottom: 10px;
}
.section-head h3 { font-size: 15px; font-weight: 700; }
.more { font-size: 12px; color: var(--text-3); }

.badges-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}
@media (min-width: 480px) { .badges-grid { grid-template-columns: repeat(6, 1fr); } }
.badge-card {
  padding: 14px 8px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 14px;
  text-align: center;
  opacity: .45;
  filter: grayscale(80%);
  transition: all var(--transition);
}
.badge-card.unlocked {
  opacity: 1;
  filter: none;
  border-color: rgba(255, 159, 67, .3);
  background: linear-gradient(135deg, rgba(255, 159, 67, .04), rgba(255, 90, 90, .04));
}
.b-ico { font-size: 28px; margin-bottom: 4px; }
.b-name { font-size: 11px; font-weight: 700; color: var(--text); }
.b-desc { font-size: 9px; color: var(--text-3); margin-top: 2px; }

.menu-list {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 14px;
  overflow: hidden;
}
.menu-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  font-size: 14px;
  color: var(--text);
  cursor: pointer;
  width: 100%;
  text-align: left;
  background: transparent;
  border: none;
  border-bottom: 1px solid var(--border);
  transition: background var(--transition);
}
.menu-item:last-child { border-bottom: none; }
.menu-item:hover { background: var(--bg-card-2); }
.menu-item svg { width: 18px; height: 18px; color: var(--text-2); flex-shrink: 0; }
.menu-item span { flex: 1; }
.menu-item .arrow { width: 14px; height: 14px; color: var(--text-3); }
.menu-item.danger { color: var(--red); }
.menu-item.danger svg { color: var(--red); }
.menu-item.login { color: var(--cyan); font-weight: 600; }
.menu-item.login svg { color: var(--cyan); }
</style>
