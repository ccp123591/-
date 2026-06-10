/**
 * sync.js — 离线训练记录同步
 * 登录后 / 网络恢复时，把 IndexedDB 里未同步的训练记录批量推到服务端。
 */
import { storage } from './storage';
import { sessionApi } from '@/api/session';

let syncing = false;

/** 同步所有未上传的本地记录，返回成功条数（无可同步/失败返回 0）。 */
export async function syncOfflineSessions() {
  if (syncing) return 0;
  syncing = true;
  try {
    const unsynced = await storage.getUnsynced();
    if (!unsynced.length) return 0;
    const payload = unsynced.map(r => ({
      action: r.action,
      actionLabel: r.actionLabel,
      reps: r.reps,
      targetReps: r.targetReps,
      duration: r.duration,
      score: r.score,
      rhythmScore: r.rhythmScore,
      stabilityScore: r.stabilityScore,
      depthScore: r.depthScore,
      symmetryScore: r.symmetryScore,
      completionScore: r.completionScore,
      sessionDate: (r.sessionDate || r.date || '').slice(0, 10),
      notes: r.notes
    }));
    const res = await sessionApi.batchSync(payload);
    for (const r of unsynced) {
      await storage.markSynced(r.localId, r.remoteId ?? null);
    }
    return res?.inserted ?? unsynced.length;
  } catch (_) {
    return 0;   // 失败留在本地，下次再试
  } finally {
    syncing = false;
  }
}
