/**
 * storage.js — IndexedDB 训练记录 + localStorage 配置管理
 */
const Storage = (() => {
  const DB_NAME = 'FitnessPWA';
  const DB_VERSION = 1;
  const STORE = 'sessions';
  const CONFIG_KEY = 'fitness_config';

  let db = null;

  /* ---------- IndexedDB ---------- */
  function open() {
    return new Promise((resolve, reject) => {
      if (db) return resolve(db);
      const req = indexedDB.open(DB_NAME, DB_VERSION);
      req.onupgradeneeded = e => {
        const d = e.target.result;
        if (!d.objectStoreNames.contains(STORE)) {
          const store = d.createObjectStore(STORE, { keyPath: 'id', autoIncrement: true });
          store.createIndex('date', 'date');
          store.createIndex('action', 'action');
        }
      };
      req.onsuccess = e => { db = e.target.result; resolve(db); };
      req.onerror = e => reject(e.target.error);
    });
  }

  async function saveSession(session) {
    const d = await open();
    return new Promise((resolve, reject) => {
      const tx = d.transaction(STORE, 'readwrite');
      const store = tx.objectStore(STORE);
      const req = store.add(session);
      req.onsuccess = () => resolve(req.result);
      req.onerror = e => reject(e.target.error);
    });
  }

  async function getAllSessions() {
    const d = await open();
    return new Promise((resolve, reject) => {
      const tx = d.transaction(STORE, 'readonly');
      const req = tx.objectStore(STORE).getAll();
      req.onsuccess = () => resolve(req.result.sort((a, b) => new Date(b.date) - new Date(a.date)));
      req.onerror = e => reject(e.target.error);
    });
  }

  async function clearSessions() {
    const d = await open();
    return new Promise((resolve, reject) => {
      const tx = d.transaction(STORE, 'readwrite');
      const req = tx.objectStore(STORE).clear();
      req.onsuccess = () => resolve();
      req.onerror = e => reject(e.target.error);
    });
  }

  /* ---------- localStorage 配置 ---------- */
  const DEFAULT_CONFIG = {
    squat: { down: 90, up: 160 },
    stretch: { down: 60, up: 160 },
    bpm: 30,
    ttsRate: 1,
    theme: 'dark',
    weeklyGoal: 50
  };

  function getConfig() {
    try {
      const raw = localStorage.getItem(CONFIG_KEY);
      if (raw) return { ...DEFAULT_CONFIG, ...JSON.parse(raw) };
    } catch (_) { /* ignore */ }
    return { ...DEFAULT_CONFIG };
  }

  function saveConfig(cfg) {
    localStorage.setItem(CONFIG_KEY, JSON.stringify(cfg));
  }

  function resetConfig() {
    localStorage.removeItem(CONFIG_KEY);
    return { ...DEFAULT_CONFIG };
  }

  /* ---------- CSV 工具 ---------- */
  function sessionsToCSV(sessions) {
    const header = 'ID,日期,动作,次数,评分,时长(秒),节奏评分,稳定度评分,备注\n';
    const rows = sessions.map(s =>
      [s.id, s.date, s.action, s.reps, s.score, s.duration,
       s.rhythmScore ?? '', s.stabilityScore ?? '', `"${s.notes || ''}"`].join(',')
    ).join('\n');
    return header + rows;
  }

  function downloadCSV(content, filename) {
    const blob = new Blob(['\uFEFF' + content], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  }

  return {
    open, saveSession, getAllSessions, clearSessions,
    getConfig, saveConfig, resetConfig, DEFAULT_CONFIG,
    sessionsToCSV, downloadCSV
  };
})();
