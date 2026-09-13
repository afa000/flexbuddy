// Maven resource filtering replaces the build id on every build, so each deploy gets fresh caches.
const BUILD_ID = '@buildId@';
const STATIC_CACHE = `flexbuddy-static-${BUILD_ID}`;
const PAGES_CACHE = `flexbuddy-pages-${BUILD_ID}`;
const DATA_CACHE = 'flexbuddy-data';

const VERSIONED_ASSETS = [
    '/css/styles.css', '/js/toast.js', '/js/charts.js', '/js/pwa.js', '/js/calendar.js',
    '/js/schedule.js', '/js/app.js', '/js/account.js'
].map(path => `${path}?v=${BUILD_ID}`);
const STATIC_ASSETS = [
    '/manifest.webmanifest', '/offline.html', '/icons/icon-192.png', '/icons/icon-512.png',
    '/icons/icon-maskable-512.png', '/icons/apple-touch-icon.png',
    '/screenshots/dashboard-narrow.png', '/screenshots/dashboard-wide.png'
];
const SHELL_PAGES = new Set(['/', '/account']);
const STATIC_PATHS = [/^\/css\//, /^\/js\//, /^\/icons\//, /^\/screenshots\//,
    /^\/manifest\.webmanifest$/, /^\/offline\.html$/];
const DATA_PATHS = [/^\/shifts(\/|$)/, /^\/expenses(\/|$)/, /^\/account\/settings$/];
const NETWORK_ONLY = [/\.csv$/, /\.ics$/, /^\/shifts\/import-preview$/, /^\/account\/backup$/, /^\/push\//];

self.addEventListener('install', event => {
    event.waitUntil(caches.open(STATIC_CACHE).then(cache => cache.addAll([...VERSIONED_ASSETS, ...STATIC_ASSETS])));
});

self.addEventListener('activate', event => {
    event.waitUntil((async () => {
        const keep = new Set([STATIC_CACHE, PAGES_CACHE, DATA_CACHE]);
        const keys = await caches.keys();
        await Promise.all(keys.filter(key => key.startsWith('flexbuddy-') && !keep.has(key))
            .map(key => caches.delete(key)));
        await self.clients.claim();
    })());
});

self.addEventListener('message', event => {
    if (event.data?.type === 'skip-waiting') self.skipWaiting();
    if (event.data?.type === 'clear-data') event.waitUntil(clearUserData());
    if (event.data?.type === 'cache-shell') {
        event.waitUntil(cacheShell(event.data.path).then(cached => event.ports[0]?.postMessage({cached})));
    }
});

// Writes, logins, downloads, and uploads are never intercepted: only GET requests on this origin are handled.
self.addEventListener('fetch', event => {
    const request = event.request;
    if (request.method !== 'GET') return;
    const url = new URL(request.url);
    if (url.origin !== self.location.origin) return;
    const path = url.pathname;
    if (NETWORK_ONLY.some(pattern => pattern.test(path))) return;
    if (request.mode === 'navigate') {
        if (SHELL_PAGES.has(path)) event.respondWith(networkFirstPage(request, path));
        return;
    }
    if (STATIC_PATHS.some(pattern => pattern.test(path))) {
        event.respondWith(cacheFirst(request));
        return;
    }
    if (DATA_PATHS.some(pattern => pattern.test(path))) event.respondWith(networkFirstData(request));
});

async function cacheShell(path) {
    const url = new URL(path || '/', self.location.origin);
    if (url.origin !== self.location.origin || !SHELL_PAGES.has(url.pathname)) return false;
    const response = await fetch(url.pathname, {credentials: 'include', cache: 'no-store'});
    if (!response.ok || response.redirected || response.type !== 'basic') return false;
    const cache = await caches.open(PAGES_CACHE);
    await cache.put(url.pathname, response.clone());
    return true;
}
async function cacheFirst(request) {
    const cache = await caches.open(STATIC_CACHE);
    const cached = await cache.match(request);
    if (cached) return cached;
    const response = await fetch(request);
    if (response.ok && response.type === 'basic') await cache.put(request, response.clone());
    return response;
}

// Only a 200 that was not redirected is stored, so a redirect to the login page is never cached as the shell.
async function networkFirstPage(request, path) {
    try {
        const response = await fetch(request);
        if (response.ok && !response.redirected && response.type === 'basic') {
            const cache = await caches.open(PAGES_CACHE);
            await cache.put(path, response.clone());
        }
        return response;
    } catch (error) {
        const cached = await (await caches.open(PAGES_CACHE)).match(path);
        if (cached) return markAsCachedShell(cached);
        return (await caches.match('/offline.html')) || Response.error();
    }
}

// The page reads this marker to reload once when the connection returns, which refreshes its CSRF token.
async function markAsCachedShell(response) {
    const html = await response.text();
    return new Response(html.replace('<head>', '<head><meta name="flexbuddy-shell" content="cache">'), {
        status: 200,
        headers: response.headers
    });
}

async function networkFirstData(request) {
    const cache = await caches.open(DATA_CACHE);
    try {
        const response = await fetch(request);
        const contentType = response.headers.get('Content-Type') || '';
        if (response.ok && !response.redirected && response.type === 'basic' && contentType.includes('application/json')) {
            await cache.put(request, response.clone());
        }
        return response;
    } catch (error) {
        const cached = await cache.match(request);
        if (!cached) throw error;
        const headers = new Headers(cached.headers);
        headers.set('X-FlexBuddy-Cache', 'hit');
        return new Response(await cached.blob(), {status: cached.status, statusText: cached.statusText, headers});
    }
}

async function clearUserData() {
    const keys = await caches.keys();
    await Promise.all(keys.filter(key => key === DATA_CACHE || key.startsWith('flexbuddy-pages-'))
        .map(key => caches.delete(key)));
}

self.addEventListener('push', event => {
    let message = {};
    try {
        message = event.data ? event.data.json() : {};
    } catch {
        message = {body: event.data?.text() || ''};
    }
    event.waitUntil(self.registration.showNotification(message.title || 'FlexBuddy', {
        body: message.body || '',
        tag: message.tag,
        icon: '/icons/icon-192.png',
        badge: '/icons/icon-192.png',
        data: {url: message.url || '/?screen=schedule'}
    }));
});

self.addEventListener('notificationclick', event => {
    event.notification.close();
    const target = new URL(event.notification.data?.url || '/?screen=schedule', self.location.origin).href;
    event.waitUntil((async () => {
        const windows = await self.clients.matchAll({type: 'window', includeUncontrolled: true});
        const existing = windows.find(client => new URL(client.url).origin === self.location.origin);
        if (existing) {
            await existing.focus();
            if ('navigate' in existing) await existing.navigate(target);
            return;
        }
        await self.clients.openWindow(target);
    })());
});
