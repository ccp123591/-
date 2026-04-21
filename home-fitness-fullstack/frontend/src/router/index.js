import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '@/stores/auth';

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { layout: 'none', public: true }
  },
  {
    path: '/',
    redirect: '/train'
  },
  {
    path: '/train',
    name: 'Train',
    component: () => import('@/views/Train.vue'),
    meta: { tab: 'train', title: '训练' }
  },
  {
    path: '/records',
    name: 'Records',
    component: () => import('@/views/Records.vue'),
    meta: { tab: 'records', title: '记录' }
  },
  {
    path: '/records/:id',
    name: 'RecordDetail',
    component: () => import('@/views/RecordDetail.vue'),
    meta: { tab: 'records', title: '记录详情' }
  },
  {
    path: '/plans',
    name: 'Plans',
    component: () => import('@/views/Plans.vue'),
    meta: { tab: 'plans', title: '训练计划' }
  },
  {
    path: '/leaderboard',
    name: 'Leaderboard',
    component: () => import('@/views/Leaderboard.vue'),
    meta: { tab: 'social', title: '排行榜' }
  },
  {
    path: '/feed',
    name: 'Feed',
    component: () => import('@/views/Feed.vue'),
    meta: { tab: 'social', title: '动态' }
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('@/views/Profile.vue'),
    meta: { tab: 'profile', title: '我的' }
  },
  {
    path: '/settings',
    name: 'Settings',
    component: () => import('@/views/Settings.vue'),
    meta: { tab: 'profile', title: '设置' }
  },
  {
    path: '/admin',
    name: 'Admin',
    component: () => import('@/views/Admin.vue'),
    meta: { tab: 'profile', title: '管理后台', admin: true }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/train'
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 };
  }
});

router.beforeEach((to, from, next) => {
  const auth = useAuthStore();
  // 公开页放行
  if (to.meta.public) return next();
  // 未登录直接进入（游客模式），后续真实落地时可改为 next('/login')
  if (!auth.isLogin && to.path !== '/login' && !auth.guestMode) {
    auth.enterGuest();
  }
  next();
});

export default router;
