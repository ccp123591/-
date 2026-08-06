import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const componentPath = new URL('../src/components/companion/CompanionWidget.vue', import.meta.url);
const configPath = new URL('../src/stores/config.js', import.meta.url);
const settingsPath = new URL('../src/views/Settings.vue', import.meta.url);

test('demoMode is a persisted global setting and defaults to disabled', async () => {
  const source = await readFile(configPath, 'utf8');
  // 演示模式默认关闭：真实用户拿到的应是连后端的真实链路，演示是评委/离线场景的显式开关。
  assert.match(source, /demoMode:\s*false/);
  assert.match(source, /demoMode:\s*demoMode\.value/);
  assert.match(source, /demoMode\.value\s*=\s*data\.demoMode/);
  assert.match(source, /coachEnabled, demoMode,/);
});

test('settings exposes the global demo switch and explains backend isolation', async () => {
  const source = await readFile(settingsPath, 'utf8');
  assert.match(source, /全站演示模式/);
  assert.match(source, /v-model="config\.demoMode"/);
  assert.match(source, /不连接 AI、情绪分析或语音服务/);
});

test('companion demo supports guests with deterministic local branches', async () => {
  const source = await readFile(componentPath, 'utf8');
  assert.match(source, /auth\.isLogin \|\| config\.demoMode/);
  assert.match(source, />演示模式</);
  assert.match(source, /provider: 'frontend-demo'/);
  assert.match(source, /低落\|难过\|焦虑/);
  assert.match(source, /本周\|计划\|安排/);
  assert.match(source, /练什么\|训练\|动作/);
  assert.match(source, /上次\|聊到\|记得/);
});

test('demo branches return before coach, emotion and TTS API calls', async () => {
  const source = await readFile(componentPath, 'utf8');
  const sendStart = source.indexOf('async function sendMessage');
  const analyzeCall = source.indexOf('emotionApi.analyze', sendStart);
  const sendDemoGuard = source.indexOf('if (config.demoMode)', sendStart);
  assert.ok(sendDemoGuard > sendStart && sendDemoGuard < analyzeCall);

  for (const name of ['speak', 'speakReply', 'tryAutoSpeak']) {
    const start = source.indexOf(`async function ${name}`);
    const guard = source.indexOf('if (config.demoMode) return;', start);
    assert.ok(start >= 0 && guard > start && guard - start < 180);
  }

  assert.match(source, /config\.demoMode \? Promise\.resolve\(\) : tryAutoSpeak/);
  assert.match(source, /v-if="auth\.isLogin"[^>]+tab === 'voice'/);
  assert.match(source, /v-else-if="auth\.isLogin && tab === 'voice'"/);
  assert.doesNotMatch(source, /enabled && tab\.value === 'voice'/);
});
