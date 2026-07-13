import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const trainPath = new URL('../src/views/Train.vue', import.meta.url);

test('demo mode exposes a camera-free fall alert trigger', async () => {
  const source = await readFile(trainPath, 'utf8');
  assert.match(source, /data-testid="demo-fall-alert"/);
  assert.match(source, /v-if="config\.demoMode && !fallAlert"/);
  assert.match(source, /function simulateDemoFallAlert/);
  assert.match(source, /无需摄像头/);
  assert.match(source, /DEMO_FALL_CONFIRM_SEC\s*=\s*10/);
  assert.match(source, /raiseFallAlert\([\s\S]*DEMO_FALL_CONFIRM_SEC\)/);
});
