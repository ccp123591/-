const now = () => new Date().toISOString();
const daysAgo = n => new Date(Date.now() - n * 86400000).toISOString();
const clone = value => JSON.parse(JSON.stringify(value));

const exercises = [
  { code: 'squat', name: '深蹲', category: '下肢', description: '强化腿部与臀部力量', difficulty: 'NEWBIE' },
  { code: 'pushup', name: '俯卧撑', category: '上肢', description: '胸肩与核心综合训练', difficulty: 'INTERMEDIATE' },
  { code: 'plank', name: '平板支撑', category: '核心', description: '稳定核心与躯干', difficulty: 'NEWBIE' },
  { code: 'jumpingJack', name: '开合跳', category: '有氧', description: '快速热身并提升心率', difficulty: 'NEWBIE' }
];

const plans = [
  { id: 1, title: '7 天居家焕活', description: '每天 15 分钟，适合重新建立运动习惯', days: 7, level: 'NEWBIE', cover: '#d98963', adoptCount: 128 },
  { id: 2, title: '核心稳定进阶', description: '平板支撑与臀桥组合，改善身体控制', days: 14, level: 'INTERMEDIATE', cover: '#618c83', adoptCount: 86 },
  { id: 3, title: '全身燃脂循环', description: '低门槛间歇循环，兼顾力量和心肺', days: 21, level: 'INTERMEDIATE', cover: '#8b6f9f', adoptCount: 203 }
];

const challenges = [
  { id: 1, title: '一周 300 次深蹲', description: '把目标拆成每天一点，稳稳完成。', action: 'squat', targetReps: 300, participantCount: 286, endDate: '2026-12-31', joined: true, myProgress: 168, myCompleted: false, cover: 'linear-gradient(135deg,#d77b58,#b84d40)' },
  { id: 2, title: '核心耐力挑战', description: '累计完成 600 秒平板支撑。', action: 'plank', targetReps: 600, participantCount: 154, endDate: '2026-12-31', joined: false, myProgress: 0, myCompleted: false, cover: 'linear-gradient(135deg,#5f9186,#426b70)' }
];

const state = {
  sessions: [
    { id: 1003, action: 'squat', reps: 24, targetReps: 24, score: 92, formScore: 94, rhythmScore: 89, stabilityScore: 91, duration: 185, calories: 32, sessionDate: daysAgo(0).slice(0, 10), createdAt: daysAgo(0), startedAt: daysAgo(0) },
    { id: 1002, action: 'plank', reps: 60, targetReps: 60, score: 88, formScore: 90, rhythmScore: 86, stabilityScore: 89, duration: 140, calories: 18, sessionDate: daysAgo(1).slice(0, 10), createdAt: daysAgo(1), startedAt: daysAgo(1) },
    { id: 1001, action: 'jumpingJack', reps: 48, targetReps: 50, score: 95, formScore: 93, rhythmScore: 97, stabilityScore: 94, duration: 210, calories: 46, sessionDate: daysAgo(3).slice(0, 10), createdAt: daysAgo(3), startedAt: daysAgo(3) }
  ],
  posts: [
    { id: 21, userId: 2, nickname: '晨跑小林', content: '今天完成了 20 分钟全身循环，最后一组深蹲很有成就感！', likes: 18, commentsCount: 2, liked: false, createdAt: daysAgo(0) },
    { id: 20, userId: 3, nickname: '阿宁', content: '连续训练第 7 天，动作慢一点反而更能找到发力感。', likes: 31, commentsCount: 1, liked: true, createdAt: daysAgo(1) }
  ],
  comments: { 21: [{ id: 1, nickname: 'FitCoach 用户', content: '太棒了，继续保持！', createdAt: now() }] },
  emotions: [
    { id: 3, text: '今天训练完成得很顺利，很有成就感', emotion: 'positive', score: 0.91, tags: ['训练', '成就感'], createdAt: daysAgo(0) },
    { id: 2, text: '工作有点忙，只做了十分钟拉伸', emotion: 'neutral', score: 0.58, tags: ['忙碌', '拉伸'], createdAt: daysAgo(2) },
    { id: 1, text: '第一次完成完整训练计划', emotion: 'positive', score: 0.87, tags: ['计划', '坚持'], createdAt: daysAgo(4) }
  ],
  adopted: [{ planId: 1, ...plans[0], progressDay: 4 }]
};
const initialState = clone(state);

const user = { id: 900001, nickname: '演示学员', displayName: '演示学员', email: 'demo@fitcoach.local', role: 'USER', status: 'ACTIVE', avatar: null, demo: true };
const ranks = [
  { rank: 1, userId: 8, name: '健身达人', nickname: '健身达人', reps: 300, progressReps: 300, completed: true, score: 98 },
  { rank: 2, userId: 2, name: '晨跑小林', nickname: '晨跑小林', reps: 276, progressReps: 276, completed: false, score: 91 },
  { rank: 3, userId: 900001, name: '演示学员', nickname: '演示学员', reps: 168, progressReps: 168, completed: false, score: 86 },
  { rank: 4, userId: 5, name: '瑜伽阿宁', nickname: '瑜伽阿宁', reps: 142, progressReps: 142, completed: false, score: 84 }
];

function page(items, config) {
  const pageNo = Number(config.params?.page || 1);
  const size = Number(config.params?.size || 20);
  return { items: clone(items.slice((pageNo - 1) * size, pageNo * size)), total: items.length, page: pageNo, size };
}

function body(config) {
  if (!config.data) return {};
  if (typeof config.data === 'string') { try { return JSON.parse(config.data); } catch (_) { return {}; } }
  return config.data;
}

function emotionFor(text) {
  const negative = /累|难过|焦虑|疼|糟|失败/.test(text);
  const positive = /开心|顺利|完成|棒|成就|坚持|很好/.test(text);
  return negative ? 'negative' : positive ? 'positive' : 'neutral';
}

export async function createDemoResponse(config) {
  await new Promise(resolve => setTimeout(resolve, 80));
  const method = (config.method || 'get').toLowerCase();
  const url = String(config.url || '').replace(/^\/api/, '').split('?')[0];
  const data = body(config);

  if (url.startsWith('/auth/login') || url === '/auth/register') return { accessToken: 'demo-access-token', refreshToken: 'demo-refresh-token', user };
  if (url === '/auth/me') return user;
  if (url === '/auth/refresh') return { accessToken: 'demo-access-token' };
  if (url.startsWith('/auth/')) return { success: true };

  if (url === '/exercises') return clone(exercises);
  if (/^\/exercises\//.test(url)) return clone(exercises.find(x => x.code === url.split('/').pop()) || exercises[0]);
  if (url === '/badges' || url === '/badges/mine') return [
    { code: 'FIRST_TRAINING', name: '初次训练', description: '完成第一次训练', unlocked: true },
    { code: 'WEEK_STREAK', name: '七日坚持', description: '连续打卡 7 天', unlocked: true },
    { code: 'PERFECT_SCORE', name: '完美评分', description: '单次评分达到 95', unlocked: true },
    { code: 'HUNDRED_REPS', name: '百次达成', description: '累计完成 100 次动作', unlocked: false }
  ];
  if (url === '/badges/check') return [];

  if (url === '/sessions' && method === 'get') return page(state.sessions, config);
  if (url === '/sessions' && method === 'post') { const item = { id: Date.now(), formScore: data.score ?? 0, rhythmScore: data.rhythmScore ?? data.score ?? 0, stabilityScore: data.stabilityScore ?? data.score ?? 0, ...data, sessionDate: data.sessionDate || now().slice(0, 10), createdAt: now() }; state.sessions.unshift(item); return clone(item); }
  if (/^\/sessions\/\d+$/.test(url)) { const id = Number(url.split('/').pop()); const index = state.sessions.findIndex(x => Number(x.id) === id); if (method === 'delete') { if (index >= 0) state.sessions.splice(index, 1); return { deleted: true }; } if (method === 'put' && index >= 0) { state.sessions[index] = { ...state.sessions[index], ...data }; return clone(state.sessions[index]); } return clone(state.sessions[index] || state.sessions[0]); }
  if (url === '/sessions/batch') return { synced: data.sessions?.length || 0 };
  if (url === '/sessions/export/csv') return new Blob(['动作,次数,评分\n深蹲,24,92'], { type: 'text/csv' });

  if (url === '/plans/official' || (url === '/plans' && method === 'get')) return clone(plans);
  if (url === '/plans/market') return page(plans.slice().reverse(), config);
  if (url === '/plans/mine') return clone(state.adopted);
  if (/^\/plans\/\d+\/adopt$/.test(url)) { const id = Number(url.split('/')[2]); const p = plans.find(x => x.id === id); if (method === 'post' && p && !state.adopted.some(x => x.planId === id)) state.adopted.push({ planId: id, ...p, progressDay: 1 }); return { adopted: method === 'post' }; }
  if (/^\/plans\/\d+$/.test(url)) return clone(plans.find(x => x.id === Number(url.split('/')[2])) || plans[0]);

  if (url === '/posts/feed') return page(state.posts, config);
  if (url === '/posts' && method === 'post') { const post = { id: Date.now(), userId: user.id, nickname: user.nickname, content: data.content, likes: 0, commentsCount: 0, liked: false, createdAt: now() }; state.posts.unshift(post); return clone(post); }
  let match = url.match(/^\/posts\/(\d+)\/(like|comments)$/);
  if (match) { const id = Number(match[1]); const post = state.posts.find(x => x.id === id); if (match[2] === 'like') { if (post) post.liked = method === 'post'; return { liked: method === 'post' }; } if (method === 'get') return clone(state.comments[id] || []); const c = { id: Date.now(), nickname: user.nickname, content: data.content, createdAt: now() }; (state.comments[id] ||= []).push(c); if (post) post.commentsCount = (post.commentsCount || 0) + 1; return clone(c); }
  if (/^\/posts\/\d+$/.test(url)) return clone(state.posts.find(x => x.id === Number(url.split('/')[2])) || state.posts[0]);

  if (url.startsWith('/leaderboard/')) return clone(ranks.map((x, i) => ({ ...x, rank: i + 1 })));
  if (url === '/challenges') return clone(challenges);
  match = url.match(/^\/challenges\/(\d+)(?:\/(join|rank))?$/);
  if (match) { const c = challenges.find(x => x.id === Number(match[1])) || challenges[0]; if (match[2] === 'rank') return clone(ranks); if (match[2] === 'join') c.joined = true; return clone(c); }

  if (url === '/users/me/stats') return { totalSessions: 12, totalReps: 486, totalMinutes: 138, bestScore: 95, streakDays: 7 };
  if (url === '/users/me' || url === '/users/me/profile') return { ...user, totalSessions: 12, totalReps: 486, dominantAction: 'squat' };
  if (url === '/users/me/profile/refresh') return { ...user, totalSessions: 12, totalReps: 486, dominantAction: 'squat', refreshedAt: now() };
  if (url === '/users/me/calendar') return state.sessions.map(x => ({ date: x.createdAt.slice(0, 10), count: 1 }));
  if (/^\/users\/\d+\/(follow|followers|followings)$/.test(url)) return method === 'get' ? [user] : { success: true };
  if (url.endsWith('/follow')) return { success: true };

  if (url === '/emotion/analyze') { const emotion = emotionFor(data.text || ''); const item = { id: Date.now(), text: data.text, emotion, score: emotion === 'positive' ? 0.9 : emotion === 'negative' ? 0.28 : 0.56, tags: ['演示记录'], createdAt: now() }; state.emotions.unshift(item); return clone(item); }
  if (url === '/emotion/history') return page(state.emotions, config);
  if (url === '/emotion/summary') return { total: state.emotions.length, positive: state.emotions.filter(x => x.emotion === 'positive').length, neutral: state.emotions.filter(x => x.emotion === 'neutral').length, negative: state.emotions.filter(x => x.emotion === 'negative').length, avgScore: 0.73, dominantEmotion: 'positive' };

  if (url === '/room/me' || url === '/room/scan' || url.endsWith('/area-override')) return { id: 1, areaSqm: Number(data.areaSqm || 16.8), safetyScore: 92, summaryText: '活动区域宽敞，光线良好，适合进行居家力量与拉伸训练。', visionModel: 'frontend-demo', features: { areaConfidence: 0.93, lighting: 'good', roomType: 'living-room', obstacles: [{ label: 'sofa', side: 'left', distanceM: 1.8 }], recommendedActions: ['squat', 'plank', 'stretch'], discouragedActions: [], warnings: ['训练前请确认地面防滑'] }, updatedAt: now() };
  if (url === '/room/history') return page([], config);
  if (url === '/safety/fall-alert') return { accepted: true, notified: false, mailProvider: 'frontend-demo', message: '演示模式：已模拟发送跌倒预警' };

  if (url === '/coach/suggestion') return { id: 102, review: '最近一周训练频率稳定，深蹲完成质量最好。', suggestion: '今天先热身 3 分钟，再完成 3 组深蹲，每组 12 次。', encouragement: '保持这个节奏，你正在建立可靠的运动习惯。', nextGoal: '本周累计完成 5 次训练', provider: 'frontend-demo' };
  if (url === '/coach/weekly-plan') return { id: 103, review: '本周已完成 3 次训练，力量与恢复安排较均衡。', suggestion: '周一深蹲，周三核心，周五全身循环；周二和周末安排拉伸恢复。', encouragement: '计划不求塞满，稳定完成最重要。', nextGoal: '下周训练 5 天、恢复 2 天', provider: 'frontend-demo' };
  if (url === '/coach/chat') return { reply: /累|疼/.test(data.message || '') ? '先把强度降下来，做两分钟呼吸和轻柔拉伸。如果疼痛持续，请停止训练。' : '收到。演示模式下我会结合你的训练记录：今天可以从 3 分钟热身和一组标准深蹲开始。', provider: 'frontend-demo', tokensUsed: 0, recalled: ['本周已完成 3 次训练'] };
  if (url === '/coach/reminisce') return { reply: '还记得你第一次完整做完训练计划时特别开心。现在你已经连续坚持一周了。', provider: 'frontend-demo', recalled: ['第一次完整训练', '连续训练 7 天'] };
  if (url === '/coach/history') return page([], config);
  if (url === '/coach/feedback') return { id: 101, review: '本次动作节奏稳定，完成度很好。', suggestion: '继续注意膝盖与脚尖方向一致，下蹲时保持核心收紧。', encouragement: '这一组很扎实，继续保持。', nextGoal: '下一组完成 26 次', provider: 'frontend-demo' };
  if (url === '/coach/form-critique') return { summary: '动作节奏稳定，继续注意膝盖与脚尖方向一致。', issues: [], tips: ['下蹲时保持核心收紧'], formScore: 92, model: 'frontend-demo' };
  if (url === '/coach/scene') return { summary: '演示画面：用户位于整洁的居家训练区域。', personPresent: true, model: 'frontend-demo' };
  if (url.startsWith('/tts/')) return { fallbackText: data.text || '演示语音播报', provider: 'browser' };

  if (url === '/admin/dashboard') return { users: 1286, sessions: 8960, todaySessions: 126, dau: 342, pv7d: [72, 88, 91, 104, 98, 121, 126] };
  if (url === '/admin/users') return page([user, { ...user, id: 2, nickname: '晨跑小林' }], config);
  if (url === '/admin/analytics') return { retention7d: 0.68, retention30d: 0.41, avgScore: 88.6, actionDistribution: { squat: 3760, plank: 2510, pushup: 1610, jumpingJack: 1080 } };

  return { demo: true, success: true };
}

export function resetDemoState() {
  const seed = clone(initialState);
  state.sessions = seed.sessions;
  state.posts = seed.posts;
  state.comments = seed.comments;
  state.emotions = seed.emotions;
  state.adopted = seed.adopted;
}

export function getDemoSessions() {
  return clone(state.sessions);
}
