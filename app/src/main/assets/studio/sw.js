// The studio's service worker, emitted as /sw.js by the build (vite.config.ts serviceWorker()), which fills in the
// two placeholders: a version (a hash of the studio page's build output) and that output's precache list. Never
// served by the dev server. Plain JS on purpose: it runs in the ServiceWorkerGlobalScope, not the page.
//
// Routes (everything else, and every non-GET, cross-origin, /v1/, /v2/ or /models/ request, goes to the network
// untouched: no respondWith):
//   navigations to /studio, /studio.html  network-first, the precached studio.html when offline
//   /assets/*                             cache-first (hashed, immutable)
//   /web-engine/*, /theme-boot.js         stale-while-revalidate (unhashed engine.js, worker.js, codec-worker.js,
//                                         the theme boot script)
//
// No skipWaiting: a new version waits until every page the old one controls has closed, so an open studio keeps the
// old worker and the old cache, i.e. the asset set its studio.html was built with (activating early would delete that
// cache under it). clients.claim on activate only takes over pages nothing controls yet (the first visit), which then
// read this version's cache; a cache miss still falls back to the network.

const VERSION = "cfc70197b4a8";
const PRECACHE = [
  "./assets/inter-cyrillic-ext-wght-normal-BOeWTOD4.woff2",
  "./assets/inter-cyrillic-wght-normal-DqGufNeO.woff2",
  "./assets/inter-greek-ext-wght-normal-DlzME5K_.woff2",
  "./assets/inter-greek-wght-normal-CkhJZR-_.woff2",
  "./assets/inter-latin-ext-wght-normal-DO1Apj_S.woff2",
  "./assets/inter-latin-wght-normal-Dx4kXJAl.woff2",
  "./assets/inter-vietnamese-wght-normal-CBcvBZtf.woff2",
  "./assets/jetbrains-mono-latin-400-normal-6-qcROiO.woff",
  "./assets/jetbrains-mono-latin-400-normal-V6pRDFza.woff2",
  "./assets/jetbrains-mono-latin-500-normal-BWZEU5yA.woff2",
  "./assets/jetbrains-mono-latin-500-normal-CJOVTJB7.woff",
  "./assets/latin-500-D-HkBiV5.js",
  "./assets/latin-500-DKfSw4PI.css",
  "./assets/lines-CE41Ljcs.js",
  "./assets/studio-8pHFvK-B.css",
  "./assets/studio-DVkgLaPk.js",
  "./assets/web-engine-CkH2el7i.js",
  "./assets/web-engine-xKv10YAh.css",
  "./studio.html"",
  "./theme-boot.js""
];
const PREFIX = 'zvs-shell-';
const CACHE = PREFIX + VERSION;
const SHELL = '/studio.html';
const BYPASS = ['/v1/', '/v2/', '/models/'];

self.addEventListener('install', (event) => {
  // cache: 'reload' skips the HTTP cache, so the precache is what this build put on the server.
  event.waitUntil(
    caches
      .open(CACHE)
      .then((cache) => cache.addAll(PRECACHE.map((url) => new Request(url, { cache: 'reload' }))))
      // Pages an older worker controls are told: the app offers a reload (the new worker takes over once they close).
      .then(() => self.clients.matchAll({ includeUncontrolled: false }))
      .then((clients) => clients.forEach((c) => c.postMessage({ type: 'zvs-update', version: VERSION }))),
  );
});

self.addEventListener('activate', (event) => {
  // Only this app's older shell caches; never the engine's zonos3-web-models-* model cache or anyone else's.
  event.waitUntil(
    caches
      .keys()
      .then((names) => Promise.all(names.filter((name) => name.startsWith(PREFIX) && name !== CACHE).map((name) => caches.delete(name))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener('fetch', (event) => {
  const { request } = event;
  if (request.method !== 'GET') return;
  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;
  const path = url.pathname;
  if (BYPASS.some((prefix) => path.startsWith(prefix))) return;
  if (request.mode === 'navigate') {
    if (path === '/studio' || path === SHELL) event.respondWith(networkFirst(request));
    return;
  }
  if (path.startsWith('/assets/')) event.respondWith(cacheFirst(request));
  else if (path.startsWith('/web-engine/') || path === '/theme-boot.js') {
    const cache = caches.open(CACHE);
    const network = cache.then((c) => revalidate(c, request));
    event.waitUntil(network.catch(() => {}));
    event.respondWith(staleWhileRevalidate(cache, network, request));
  }
});

/** The network's page; this version's studio.html only when the network is unreachable (not on an HTTP error). */
async function networkFirst(request) {
  try {
    return await fetch(request);
  } catch (err) {
    const cached = await caches.match(SHELL, { cacheName: CACHE });
    if (cached) return cached;
    throw err;
  }
}

/** The cached file, else the network's (kept when OK: hashed names never change content). */
async function cacheFirst(request) {
  const cache = await caches.open(CACHE);
  const cached = await cache.match(request);
  return cached ?? revalidate(cache, request);
}

/** The network's file, kept in `cache` when OK. */
async function revalidate(cache, request) {
  const response = await fetch(request);
  if (response.ok) await cache.put(request, response.clone());
  return response;
}

/** The cached file at once while `network` refreshes it; the network's when nothing is cached yet. */
async function staleWhileRevalidate(cache, network, request) {
  const cached = await (await cache).match(request);
  return cached ?? network;
}
