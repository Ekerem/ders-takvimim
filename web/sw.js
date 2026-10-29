/* Çevrimdışı katman: internet varken her açılışta en yeni sürümü indirir ve saklar,
   internet yokken saklanan son sürümü açar. */
const CACHE = 'ders-takvimim';
const ROOT = new URL('./', self.location).href;

self.addEventListener('install', e => {
  e.waitUntil(
    caches.open(CACHE)
      .then(c => c.addAll([ROOT, ROOT + 'version.json']))
      .catch(() => {})
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', e => e.waitUntil(self.clients.claim()));

// Önce internet; olmazsa saklanan kopya. Sorgu (?app=...) yok sayılır.
async function networkFirst(req, key) {
  const cache = await caches.open(CACHE);
  try {
    const res = await fetch(req, { cache: 'no-store' });
    if (res && res.ok) cache.put(key, res.clone());
    return res;
  } catch (err) {
    const hit = await cache.match(key, { ignoreSearch: true });
    if (hit) return hit;
    throw err;
  }
}

// Yazı tipleri değişmez: önce saklanan, yoksa internet.
async function cacheFirst(req) {
  const cache = await caches.open(CACHE);
  const hit = await cache.match(req);
  if (hit) return hit;
  const res = await fetch(req);
  if (res && (res.ok || res.type === 'opaque')) cache.put(req, res.clone());
  return res;
}

self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);

  if (req.mode === 'navigate' && url.href.startsWith(ROOT)) {
    e.respondWith(networkFirst(req, ROOT));
  } else if (url.origin === self.location.origin) {
    e.respondWith(networkFirst(req, url.origin + url.pathname));
  } else if (url.hostname === 'fonts.googleapis.com' || url.hostname === 'fonts.gstatic.com') {
    e.respondWith(cacheFirst(req));
  }
});
