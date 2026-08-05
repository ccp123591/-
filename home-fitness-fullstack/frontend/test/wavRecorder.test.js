import test from 'node:test';
import assert from 'node:assert/strict';
import { encodeWav, downsample, rms, TARGET_SAMPLE_RATE } from '../src/modules/wavRecorder.js';

const bytesOf = async (blob) => Buffer.from(await blob.arrayBuffer());

test('encodeWav emits a standard 44-byte RIFF/WAVE header', async () => {
  const buf = await bytesOf(encodeWav(new Float32Array(1600), 16000));

  assert.equal(buf.toString('ascii', 0, 4), 'RIFF');
  assert.equal(buf.toString('ascii', 8, 12), 'WAVE');
  assert.equal(buf.toString('ascii', 12, 16), 'fmt ');
  assert.equal(buf.toString('ascii', 36, 40), 'data');
  assert.equal(buf.readUInt16LE(20), 1, 'format must be PCM');
  assert.equal(buf.readUInt16LE(22), 1, 'must be mono');
  assert.equal(buf.readUInt16LE(34), 16, 'must be 16-bit');
  assert.equal(buf.length, 44 + 1600 * 2);
});

/**
 * 跨端契约：后端 MimoAsrProvider.estimateDuration 从偏移 28 读 ByteRate 估算时长。
 * 改动 WAV 头布局前请同步检查后端。
 */
test('encodeWav writes ByteRate at offset 28 for backend duration estimation', async () => {
  const buf = await bytesOf(encodeWav(new Float32Array(16000), 16000));

  assert.equal(buf.readUInt32LE(24), 16000, 'sample rate');
  assert.equal(buf.readUInt32LE(28), 32000, 'byte rate = 16000Hz * 2 bytes');
  // 后端据此算出的时长应为 1.0s
  assert.equal((buf.length - 44) / buf.readUInt32LE(28), 1);
});

test('encodeWav clamps out-of-range samples instead of wrapping', async () => {
  const buf = await bytesOf(encodeWav(Float32Array.from([2, -2, 0]), 16000));

  assert.equal(buf.readInt16LE(44), 32767);
  assert.equal(buf.readInt16LE(46), -32768);
  assert.equal(buf.readInt16LE(48), 0);
});

test('downsample converts 48kHz to the 16kHz upload rate', () => {
  const out = downsample(new Float32Array(4800), 48000, TARGET_SAMPLE_RATE);

  assert.equal(out.length, 1600);
});

test('downsample leaves audio untouched when already at or below target rate', () => {
  const input = new Float32Array(100);
  assert.equal(downsample(input, 16000, 16000), input);
  assert.equal(downsample(input, 8000, 16000), input);
});

test('downsample preserves a constant signal', () => {
  const out = downsample(new Float32Array(4800).fill(0.5), 48000, TARGET_SAMPLE_RATE);

  for (const v of out) assert.ok(Math.abs(v - 0.5) < 1e-6);
});

test('rms separates silence from speech-level audio', () => {
  assert.equal(rms(new Float32Array(64)), 0);
  assert.equal(rms([]), 0);
  assert.equal(rms(null), 0);
  assert.ok(Math.abs(rms(new Float32Array(64).fill(0.5)) - 0.5) < 1e-6);
  // 静音底噪应低于 wavRecorder 的绝对判定下限 0.008
  assert.ok(rms(new Float32Array(64).fill(0.001)) < 0.008);
});
