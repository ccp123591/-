/* FitCoach Service Worker v3.0
 * Offline-first with network fallback, CDN cache for MediaPipe
 */
const VERSION = 'fitcoach-v3-0-0';
const APP_SHELL = [
  '/',
  '/index.html',
  '/manifest.json',
  '/favicon.svg'
];
const CDN_WHITELIST = [
  'cdn.jsdelivr.net',
  'fonts.googleapis.com',
  'fonts.gstatic.com'
];

self.addEventListener('install', e => {
  e.waitUntil(
    caches.open(VERSION)
      .then(cache => cache.addAll(APP_SHELL))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', e => {
  e.waitUntil(
    caches.keys()
      .then(keys => Promise.all(keys.filter(k => k !== VERSION).map(k => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);

  // API 请求：网络优先，失败不缓存
  if (url.pathname.startsWith('/api/')) {
    e.respondWith(fetch(req).catch(() => new Response(JSON.stringify({ code: -1, message: 'offline' }), {
      headers: { 'Content-Type': 'application/json' }
    })));
    return;
  }

  // CDN：缓存优先 + 后台更新
  if (CDN_WHITELIST.some(d => url.hostname.includes(d))) {
    e.respondWith(
      caches.match(req).then(cached => {
        const fetchPromise = fetch(req).then(res => {
          const clone = res.clone();
          caches.open(VERSION).then(c => c.put(req, clone));
          return res;
        }).catch(() => cached);
        return cached || fetchPromise;
      })
    );
    return;
  }

  // 同源资源：缓存优先
  if (url.origin === location.origin) {
    e.respondWith(
      caches.match(req).then(cached => {
        return cached || fetch(req).then(res => {
          if (res.ok) {
            const clone = res.clone();
            caches.open(VERSION).then(c => c.put(req, clone));
          }
          return res;
        });
      })
    );
  }
});

// 接收主线程指令更新
self.addEventListener('message', e => {
  if (e.data && e.data.type === 'SKIP_WAITING') self.skipWaiting();
});
