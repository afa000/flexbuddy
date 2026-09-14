(() => {
    const THEME_COLORS = {dark: '#075c76', light: '#075c76'};
    const USER_CACHE_PREFIXES = ['flexbuddy-data', 'flexbuddy-pages-'];
    const shellFromCache = Boolean(document.querySelector('meta[name="flexbuddy-shell"]'));
    const installListeners = [];
    const disabledByOffline = new Set();
    let deferredInstallPrompt;
    let offline = false;
    let updateRequested = false;
    let reloading = false;

    function isStandalone() {
        return window.matchMedia('(display-mode: standalone)').matches || window.navigator.standalone === true;
    }

    function isIos() {
        return /iphone|ipad|ipod/i.test(navigator.userAgent)
            || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
    }

    /** 'installed', 'prompt' (Chrome can show its install dialog), 'ios' (Share > Add to Home Screen), or 'unavailable'. */
    function installState() {
        if (isStandalone()) return 'installed';
        if (deferredInstallPrompt) return 'prompt';
        if (isIos()) return 'ios';
        return 'unavailable';
    }

    async function promptInstall() {
        if (!deferredInstallPrompt) return false;
        const prompt = deferredInstallPrompt;
        deferredInstallPrompt = undefined;
        prompt.prompt();
        const choice = await prompt.userChoice;
        notifyInstallListeners();
        return choice.outcome === 'accepted';
    }

    function onInstallChange(listener) {
        installListeners.push(listener);
        listener(installState());
    }

    function notifyInstallListeners() {
        installListeners.forEach(listener => listener(installState()));
    }

    window.addEventListener('beforeinstallprompt', event => {
        event.preventDefault();
        deferredInstallPrompt = event;
        notifyInstallListeners();
    });
    window.addEventListener('appinstalled', () => {
        deferredInstallPrompt = undefined;
        notifyInstallListeners();
    });

    function applyThemeColor(theme) {
        const color = THEME_COLORS[theme] || THEME_COLORS.dark;
        document.querySelectorAll('meta[name="theme-color"]').forEach(meta => meta.setAttribute('content', color));
    }

    function formatSyncTime(dateHeader) {
        const date = dateHeader ? new Date(dateHeader) : null;
        if (!date || Number.isNaN(date.getTime())) return '';
        return date.toLocaleTimeString(undefined, {hour: 'numeric', minute: '2-digit'});
    }

    function setOffline(dateHeader) {
        const syncTime = formatSyncTime(dateHeader);
        const banner = document.querySelector('#offlineBanner');
        if (banner) {
            if (syncTime || !offline) {
                banner.textContent = syncTime
                    ? `Offline · showing your last synced data from ${syncTime}`
                    : 'Offline · showing your last synced data';
            }
            banner.classList.remove('is-hidden');
        }
        if (offline) return;
        offline = true;
        document.body.classList.add('is-offline');
        document.querySelectorAll('[data-online-only], form button[type="submit"], #dropZone').forEach(element => {
            if (element.disabled) return;
            element.disabled = true;
            element.dataset.offlineTitle = element.title || '';
            element.title = 'Available when you are back online';
            disabledByOffline.add(element);
        });
    }

    function setOnline() {
        if (!offline) return;
        offline = false;
        document.body.classList.remove('is-offline');
        document.querySelector('#offlineBanner')?.classList.add('is-hidden');
        disabledByOffline.forEach(element => {
            element.disabled = false;
            element.title = element.dataset.offlineTitle || '';
            delete element.dataset.offlineTitle;
        });
        disabledByOffline.clear();
        document.dispatchEvent(new CustomEvent('flexbuddy:online'));
    }

    /** Called by apiFetch for every response; the service worker marks responses it served from cache. */
    function noteResponse(response) {
        if (response.headers.get('X-FlexBuddy-Cache') === 'hit') setOffline(response.headers.get('Date'));
        else if (navigator.onLine) setOnline();
    }

    function noteNetworkFailure() {
        setOffline();
    }

    function isOffline() {
        return offline || !navigator.onLine;
    }

    window.addEventListener('offline', () => setOffline());
    window.addEventListener('online', () => {
        // A shell served from cache carries a stale CSRF token, so reload it once before any write can happen.
        if (shellFromCache && !reloading) {
            reloading = true;
            window.location.reload();
            return;
        }
        setOnline();
    });

    async function clearUserData() {
        if ('caches' in window) {
            const keys = await caches.keys();
            await Promise.all(keys.filter(key => USER_CACHE_PREFIXES.some(prefix => key.startsWith(prefix)))
                .map(key => caches.delete(key)));
        }
        navigator.serviceWorker?.controller?.postMessage({type: 'clear-data'});
    }

    function promptUpdate(worker) {
        if (!window.flexbuddyToast) return;
        window.flexbuddyToast.show('FlexBuddy updated', 'Reload to use the latest version.', {
            actionLabel: 'Reload',
            duration: 10 * 60 * 1000,
            onAction: () => {
                updateRequested = true;
                worker.postMessage({type: 'skip-waiting'});
            }
        });
    }

    async function cacheCurrentShell(registration) {
        if (!['/', '/account'].includes(window.location.pathname) || !registration.active) return;
        await new Promise(resolve => {
            const channel = new MessageChannel();
            const timeout = window.setTimeout(resolve, 3000);
            channel.port1.onmessage = () => {
                window.clearTimeout(timeout);
                resolve();
            };
            registration.active.postMessage(
                {type: 'cache-shell', path: window.location.pathname},
                [channel.port2]
            );
        });
    }
    async function registerServiceWorker() {
        if (!('serviceWorker' in navigator)) return;
        navigator.serviceWorker.addEventListener('controllerchange', () => {
            if (!updateRequested || reloading) return;
            reloading = true;
            window.location.reload();
        });
        try {
            const registration = await navigator.serviceWorker.register('/sw.js');
            await navigator.serviceWorker.ready;
            await cacheCurrentShell(registration);
            document.dispatchEvent(new CustomEvent('flexbuddy:cache-ready'));
            if (registration.waiting && navigator.serviceWorker.controller) promptUpdate(registration.waiting);
            registration.addEventListener('updatefound', () => {
                const worker = registration.installing;
                worker?.addEventListener('statechange', () => {
                    if (worker.state === 'installed' && navigator.serviceWorker.controller) promptUpdate(worker);
                });
            });
        } catch {
            // The app works without a service worker; it only loses offline support.
        }
    }

    if (shellFromCache || !navigator.onLine) {
        document.addEventListener('DOMContentLoaded', () => setOffline());
    }
    window.addEventListener('load', registerServiceWorker);
    applyThemeColor(document.documentElement.dataset.theme);

    window.flexbuddyPwa = {
        installState, promptInstall, onInstallChange, isIos, isStandalone, applyThemeColor,
        noteResponse, noteNetworkFailure, isOffline, clearUserData
    };
})();
