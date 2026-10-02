// Error reports: tells the developer when one of the app's own scripts crashes on a phone, which otherwise leaves no
// trace anywhere. It is loaded first, so it is listening before the other scripts run. It sends a short, scrubbed
// message and a code location, never page contents, and at most three reports per page load.
(() => {
    const MAX_PER_PAGE = 3;
    const MAX_MESSAGE = 300;
    const FETCH_FAILURE = /fetch|NetworkError|Load failed/i;
    const SCRIPT_IN_STACK = /(https?:\/\/[^\s)]+\/js\/[a-z0-9-]+\.js)(?:\?[^\s:)]*)?:(\d+):(\d+)/;

    /** True when the file is one of this site's own scripts under /js/. */
    function isOwnScript(filename) {
        try {
            const url = new URL(filename, location.href);
            return url.origin === location.origin && url.pathname.startsWith('/js/');
        } catch {
            return false;
        }
    }

    /**
     * Decides whether an error is worth a report. Offline failures already show the offline banner, aborted requests
     * are deliberate, "Script error." says nothing, and the same error is not sent twice from one page.
     */
    function shouldReport({message, filename, line, reason, online, sent, seen}) {
        if (online === false || sent >= MAX_PER_PAGE) return false;
        if (!message || message === 'Script error.' || message.startsWith('ResizeObserver loop')) return false;
        if (!isOwnScript(filename)) return false;
        if (seen && seen.has(`${message}|${filename}|${line}`)) return false;
        if (reason) {
            if (reason.name === 'AbortError') return false;
            if (reason.name === 'TypeError' && FETCH_FAILURE.test(reason.message || '')) return false;
        }
        return true;
    }

    /** The request body: the message cut to 300 characters and the filename reduced to its path. */
    function buildReport({message, filename, lineno, colno, screen, buildId}) {
        let path = filename;
        try {
            path = new URL(filename, location.href).pathname;
        } catch {
            // Keep the filename as given.
        }
        return {
            message: String(message).slice(0, MAX_MESSAGE),
            source: path,
            line: lineno || 0,
            column: colno || 0,
            screen,
            buildId
        };
    }

    window.flexbuddyErrors = {shouldReport, buildReport};

    // The pure helpers above load anywhere; the listeners below need a real page.
    if (typeof window.addEventListener !== 'function') return;

    const buildId = new URL(document.currentScript?.src || location.href).searchParams.get('v') || 'unknown';
    const seen = new Set();
    let sent = 0;

    function screen() {
        if (location.pathname === '/account') return 'account';
        return new URLSearchParams(location.search).get('screen') || 'home';
    }

    function send(report) {
        const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
        const headers = {'Content-Type': 'application/json'};
        if (csrfToken && csrfHeader) headers[csrfHeader] = csrfToken;
        // Plain fetch, not apiFetch: apiFetch may be the very thing that broke, and it changes the offline banner.
        fetch('/client-errors', {method: 'POST', keepalive: true, credentials: 'same-origin', headers, body: JSON.stringify(report)})
            .catch(() => {});
    }

    function handle(details, reason) {
        const candidate = {...details, reason, online: navigator.onLine, sent, seen};
        if (!shouldReport(candidate)) return;
        seen.add(`${details.message}|${details.filename}|${details.line}`);
        sent++;
        send(buildReport({...details, lineno: details.line, colno: details.column, screen: screen(), buildId}));
    }

    window.addEventListener('error', event => {
        handle({message: event.message, filename: event.filename, line: event.lineno, column: event.colno});
    });

    window.addEventListener('unhandledrejection', event => {
        const reason = event.reason;
        const stack = reason?.stack || '';
        const match = SCRIPT_IN_STACK.exec(stack);
        // Without one of our own scripts in the trace, the rejection is not ours to report.
        if (!match) return;
        handle({message: reason?.message || String(reason), filename: match[1], line: Number(match[2]), column: Number(match[3])}, reason);
    });
})();
