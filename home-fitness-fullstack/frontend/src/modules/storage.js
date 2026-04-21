/**
 * storage.js — IndexedDB 本地训练记录 + 同步队列
 * 离线优先：所有训练数据先写本地，再按队列推送服务端
 */

const DB_NAME = 'FitCoachDB';
const DB_VERSION = 2;
const STORE_SESSIONS = 'sessions';
const STORE_QUEUE = 'sync_queue';

let dbPromise = null;

function openDB() {
  if (dbPromise) return dbPromise;
  dbPromise = new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = e => {
      const d = e.target.result;
      if (!d.objectStoreNames.contains(STORE_SESSIONS)) {
        const s = d.createObjectStore(STORE_SESSIONS, { keyPath: 'localId', autoIncrement: true });
        s.createIndex('date', 'date');
        s.createIndex('action', 'action');
        s.createIndex('synced', 'synced');
      }
      if (!d.objectStoreNames.contains(STORE_QUEUE)) {
        d.createObjectStore(STORE_QUEUE, { keyPath: 'id', autoIncrement: true });
      }
    };
    req.onsuccess = e => resolve(e.target.result);
    req.onerror = e => reject(e.target.error);
  });
  return dbPromise;
}

async function tx(store, mode = 'readonly') {
  const d = await openDB();
  return d.transaction(store, mode).objectStore(store);
}

export const storage = {
  async saveSession(session) {
    const s = await tx(STORE_SESSIONS, 'readwrite');
    const record = { ...session, synced: 0, createdAt: Date.now() };
    return new Promise((resolve, reject) => {
      const req = s.add(record);
      req.onsuccess = () => resolve(req.result);
      req.onerror = e => reject(e.target.error);
    });
  },

  async getAllSessions() {
    const s = await tx(STORE_SESSIONS);
    return new Promise((resolve, reject) => {
      const req = s.getAll();
      req.onsuccess = () => resolve(req.result.sort((a, b) => new Date(b.date) - new Date(a.date)));
      req.onerror = e => reject(e.target.error);
    });
  },

  async getSession(localId) {
    const s = await tx(STORE_SESSIONS);
    return new Promise((resolve, reject) => {
      const req = s.get(localId);
      req.onsuccess = () => resolve(req.result);
      req.onerror = e => reject(e.target.error);
    });
  },

  async clearSessions() {
    const s = await tx(STORE_SESSIONS, 'readwrite');
    return new Promise((resolve, reject) => {
      const req = s.clear();
      req.onsuccess = () => resolve();
      req.onerror = e => reject(e.target.error);
    });
  },

  async markSynced(localId, remoteId) {
    const s = await tx(STORE_SESSIONS, 'readwrite');
    return new Promise((resolve, reject) => {
      const req = s.get(localId);
      req.onsuccess = () => {
        const rec = req.result;
        if (!rec) return resolve();
        rec.synced = 1;
        rec.remoteId = remoteId;
        const upd = s.put(rec);
        upd.onsuccess = () => resolve();
        upd.onerror = e => reject(e.target.error);
      };
      req.onerror = e => reject(e.target.error);
    });
  },

  async getUnsynced() {
    const s = await tx(STORE_SESSIONS);
    return new Promise((resolve, reject) => {
      const req = s.getAll();
      req.onsuccess = () => resolve(req.result.filter(r => !r.synced));
      req.onerror = e => reject(e.target.error);
    });
  },

  /* ==== CSV 导出工具 ==== */
  sessionsToCSV(sessions) {
    const header = 'ID,日期,动作,次数,综合,节奏,稳定,深度,对称,时长(s)\n';
    const rows = sessions.map(s => [
      s.remoteId || s.localId, s.date, s.action, s.reps,
      s.score, s.rhythmScore ?? '', s.stabilityScore ?? '',
      s.depthScore ?? '', s.symmetryScore ?? '', s.duration
    ].join(',')).join('\n');
    return header + rows;
  },

  download(content, filename, type = 'text/csv;charset=utf-8;') {
    const blob = new Blob(['\uFEFF' + content], { type });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  }
};
