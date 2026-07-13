import test from 'node:test';
import assert from 'node:assert/strict';
import { createDemoResponse, getDemoSessions, resetDemoState } from '../src/api/demoMock.js';

const request = (url, method = 'get', data, params) => createDemoResponse({
  url, method, data: data == null ? undefined : JSON.stringify(data), params
});

test('demo API supplies useful data for core pages', async () => {
  const [sessions, plans, posts, challenges, stats] = await Promise.all([
    request('/sessions', 'get', null, { page: 1, size: 20 }),
    request('/plans/official'),
    request('/posts/feed', 'get', null, { page: 1, size: 20 }),
    request('/challenges'),
    request('/users/me/stats')
  ]);
  assert.ok(sessions.items.length >= 3);
  assert.ok(plans.length >= 3);
  assert.ok(posts.items.length >= 2);
  assert.ok(challenges.length >= 2);
  assert.ok(stats.totalSessions > 0);
  for (const key of ['sessionDate', 'score', 'formScore', 'rhythmScore', 'stabilityScore', 'targetReps']) {
    assert.ok(key in sessions.items[0], `session is missing ${key}`);
  }
});

test('demo writes are reflected in subsequent reads', async () => {
  const created = await request('/posts', 'post', { content: 'Mock 演示动态' });
  const feed = await request('/posts/feed', 'get', null, { page: 1, size: 20 });
  assert.equal(feed.items[0].id, created.id);
  assert.equal(feed.items[0].content, 'Mock 演示动态');
});

test('demo emotion and safety endpoints never require backend services', async () => {
  const emotion = await request('/emotion/analyze', 'post', { text: '今天完成训练很开心' });
  const alert = await request('/safety/fall-alert', 'post', { contactEmail: 'demo@example.com' });
  assert.equal(emotion.emotion, 'positive');
  assert.equal(alert.mailProvider, 'frontend-demo');
  assert.equal(alert.notified, false);
});

test('demo contracts match leaderboard, room, coach and admin consumers', async () => {
  const [leaders, room, feedback, suggestion, weekly, dashboard, analytics] = await Promise.all([
    request('/leaderboard/weekly'), request('/room/me'),
    request('/coach/feedback', 'post', { sessionId: 1003 }), request('/coach/suggestion'),
    request('/coach/weekly-plan'), request('/admin/dashboard'), request('/admin/analytics')
  ]);
  assert.ok(leaders.every(x => x.name && Number.isFinite(x.reps)));
  assert.ok(leaders.some(x => x.userId === 900001));
  assert.equal(room.visionModel, 'frontend-demo');
  assert.equal(typeof room.summaryText, 'string');
  assert.equal(typeof room.safetyScore, 'number');
  assert.ok(Array.isArray(room.features.recommendedActions));
  for (const coach of [feedback, suggestion, weekly]) {
    for (const key of ['review', 'suggestion', 'encouragement', 'nextGoal']) assert.equal(typeof coach[key], 'string');
  }
  for (const key of ['users', 'sessions', 'todaySessions', 'dau', 'pv7d']) assert.ok(key in dashboard);
  for (const key of ['retention7d', 'retention30d', 'avgScore', 'actionDistribution']) assert.ok(key in analytics);
});

test('feed uses commentsCount and increments it after a mock comment', async () => {
  const before = await request('/posts/feed', 'get', null, { page: 1, size: 20 });
  const post = before.items.find(x => x.id === 21);
  assert.ok('commentsCount' in post);
  await request('/posts/21/comments', 'post', { content: '契约测试评论' });
  const after = await request('/posts/feed', 'get', null, { page: 1, size: 20 });
  assert.equal(after.items.find(x => x.id === 21).commentsCount, post.commentsCount + 1);
});

test('demo session access is cloned and reset restores seeded content', async () => {
  const original = getDemoSessions();
  original[0].score = -1;
  assert.notEqual(getDemoSessions()[0].score, -1);
  await request('/sessions', 'post', { action: 'squat', reps: 1, targetReps: 10, score: 50 });
  assert.equal(getDemoSessions().length, 4);
  resetDemoState();
  assert.equal(getDemoSessions().length, 3);
  assert.equal(getDemoSessions()[0].score, 92);
});
