// Feedback: "Send feedback" links that open the driver's email app with the app version, the screen, the device type and
// today's date already filled in, so a message says where it came from. Loaded before app.js. The helpers are pure; the
// wiring only adds one delegated click listener.
(() => {
    const ADDRESS = 'flexbuddysupport@gmail.com';
    const SCREENS = {dashboard: 'Home', home: 'Home', reports: 'Reports', schedule: 'Schedule', import: 'Import', expenses: 'Expenses'};

    function platformLabel(userAgent) {
        const agent = String(userAgent || '');
        if (/Android/.test(agent)) return 'Android';
        if (/iPhone/.test(agent)) return 'iPhone';
        if (/iPad/.test(agent)) return 'iPad';
        if (/Windows/.test(agent)) return 'Windows';
        if (/Macintosh/.test(agent)) return 'Mac';
        return 'Other';
    }

    function screenLabel(pathname, search) {
        if (pathname === '/account') return 'Account';
        const screen = new URLSearchParams(search || '').get('screen');
        if (screen === null) return 'Home';
        return SCREENS[screen] || 'Other';
    }

    /** YYYY-MM-DD from the device's own calendar day, never from UTC, which is already tomorrow on a US evening. */
    function localIsoDate(date) {
        const pad = value => String(value).padStart(2, '0');
        return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
    }

    /** encodeURIComponent, not URLSearchParams, which writes "+" for a space and some mail apps show it literally. */
    function feedbackMailto({buildId, screen, platform, installed, date}) {
        const body = [
            t('js.feedback.prompt'), '', '', '', '—',
            `App version: ${buildId}`,
            `Screen: ${screen}`,
            `Device: ${platform} · ${installed ? 'installed app' : 'browser'}`,
            `Date: ${date}`
        ].join('\n');
        return `mailto:${ADDRESS}?subject=${encodeURIComponent('FlexBuddy feedback')}&body=${encodeURIComponent(body)}`;
    }

    window.flexbuddyFeedback = {platformLabel, screenLabel, localIsoDate, feedbackMailto};

    if (typeof document.addEventListener !== 'function') return;

    let buildId = 'unknown';
    try {
        buildId = new URL(document.currentScript.src).searchParams.get('v') || 'unknown';
    } catch {
        // Without a script address the version is reported as unknown.
    }

    // The link is rewritten just before the browser follows it, so the screen is the one the driver is on now.
    document.addEventListener('click', event => {
        const link = event.target.closest?.('a[data-feedback]');
        if (!link) return;
        link.href = feedbackMailto({
            buildId,
            screen: screenLabel(window.location.pathname, window.location.search),
            platform: platformLabel(window.navigator.userAgent),
            installed: window.flexbuddyPwa?.isStandalone?.() ?? false,
            date: localIsoDate(new Date())
        });
    });
})();
