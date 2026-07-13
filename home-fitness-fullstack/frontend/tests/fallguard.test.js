import test from 'node:test';
import assert from 'node:assert/strict';
import { FallGuard } from '../src/modules/fallguard.js';

function landmarks({ hipX = 0.5, hipY = 0.5, shoulderX = 0.5, shoulderY = 0.25 } = {}) {
  const points = Array.from({ length: 25 }, () => ({ x: 0.5, y: 0.5, visibility: 1 }));
  points[11] = { x: shoulderX, y: shoulderY, visibility: 1 };
  points[12] = { x: shoulderX, y: shoulderY, visibility: 1 };
  points[23] = { x: hipX, y: hipY, visibility: 1 };
  points[24] = { x: hipX, y: hipY, visibility: 1 };
  return points;
}

test('upright posture does not trigger an alert', () => {
  const guard = new FallGuard();
  assert.equal(guard.update(landmarks(), 'squat', 10_000), null);
  assert.equal(guard.update(landmarks({ hipY: 0.53 }), 'squat', 10_400), null);
});

test('rapid downward hip movement triggers risk warning', () => {
  const guard = new FallGuard();
  guard.update(landmarks({ hipY: 0.08, shoulderY: 0.02 }), 'squat', 10_000);
  const alert = guard.update(landmarks({ hipY: 0.70, shoulderY: 0.30 }), 'squat', 10_350);
  assert.equal(alert?.level, 'risk');
});

test('low horizontal torso triggers fall alert', () => {
  const guard = new FallGuard();
  const alert = guard.update(landmarks({
    hipX: 0.55, hipY: 0.72, shoulderX: 0.20, shoulderY: 0.70
  }), 'squat', 10_000);
  assert.equal(alert?.level, 'fall');
});

test('horizontal training actions are exempt from single-frame fall posture rule', () => {
  const guard = new FallGuard();
  const alert = guard.update(landmarks({
    hipX: 0.55, hipY: 0.72, shoulderX: 0.20, shoulderY: 0.70
  }), 'plank', 10_000);
  assert.equal(alert, null);
});
