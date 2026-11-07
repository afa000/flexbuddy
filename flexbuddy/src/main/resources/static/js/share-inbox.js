// Screenshots shared from another app (Photos, Files) wait in Cache Storage until the import screen takes them: sw.js
// keeps each file of a share under /share-inbox/<id>/<n>, and a share kept by an older sw.js sits under
// /share-inbox/<id>. A share is a person's screenshots on this device, so it is opened at most once, deleted after
// ten minutes, and deleted at sign-out with the other cached data (pwa.js).
(() => {
    const CACHE = 'flexbuddy-share-inbox';
    const MAX_AGE_MS = 10 * 60 * 1000;
    const PATH = /^\/share-inbox\/([0-9a-f-]{36})(?:\/(\d{1,2}))?$/i;

    async function openInbox() {
        if (typeof caches === 'undefined') return null;
        try {
            return await caches.open(CACHE);
        } catch {
            return null;
        }
    }

    /**
     * The shares still waiting, newest first, as {id, sharedAt, entries: [{index, request, response}]}. Entries older
     * than ten minutes, or that aren't a share at all, are deleted on the way.
     */
    async function waitingShares(cache, now) {
        const shares = new Map();
        for (const request of await cache.keys()) {
            const match = PATH.exec(new URL(request.url).pathname);
            const response = match ? await cache.match(request) : undefined;
            const sharedAt = Number(response?.headers.get('X-Shared-At') || 0);
            if (!match || !response || now - sharedAt > MAX_AGE_MS) {
                await cache.delete(request);
                continue;
            }
            const id = match[1].toLowerCase();
            const share = shares.get(id) ?? {id, sharedAt, entries: []};
            share.sharedAt = Math.max(share.sharedAt, sharedAt);
            share.entries.push({index: Number(match[2] ?? 0), request, response});
            shares.set(id, share);
        }
        return [...shares.values()].sort((first, second) => second.sharedAt - first.sharedAt);
    }

    /**
     * Reads every file of a share, in the order it was shared, and deletes them so it never opens twice. The other
     * waiting shares are deleted too: they are usually the same screenshots shared again while the first one waited,
     * and must not open on some later visit.
     */
    async function readShare(cache, share, waiting) {
        for (const other of waiting) {
            if (other === share) continue;
            for (const {request} of other.entries) await cache.delete(request);
        }
        const files = [];
        for (const {request, response} of share.entries.sort((first, second) => first.index - second.index)) {
            await cache.delete(request);
            const blob = await response.blob();
            const name = decodeURIComponent(response.headers.get('X-File-Name') || 'shared-screenshot');
            files.push(new File([blob], name, {type: blob.type || response.headers.get('Content-Type') || ''}));
        }
        return files;
    }

    /** The files of share `id`, taken out of the inbox; none when it is unknown, expired or already taken. */
    async function take(id, now = Date.now()) {
        if (!/^[0-9a-f-]{36}$/i.test(String(id))) return [];
        const cache = await openInbox();
        if (!cache) return [];
        try {
            const waiting = await waitingShares(cache, now);
            const share = waiting.find(entry => entry.id === String(id).toLowerCase());
            return share ? await readShare(cache, share, waiting) : [];
        } catch {
            return [];
        }
    }

    /** The newest waiting share, taken out of the inbox (one made while signed out, opened after sign-in). */
    async function takeNewest(now = Date.now()) {
        const cache = await openInbox();
        if (!cache) return [];
        try {
            const waiting = await waitingShares(cache, now);
            return waiting.length ? await readShare(cache, waiting[0], waiting) : [];
        } catch {
            return [];
        }
    }

    /** True while a share younger than ten minutes waits; deletes the older ones. */
    async function hasWaiting(now = Date.now()) {
        const cache = await openInbox();
        if (!cache) return false;
        try {
            return (await waitingShares(cache, now)).length > 0;
        } catch {
            return false;
        }
    }

    // The Sign in page says when a share is waiting for the sign-in; the image itself is never shown there.
    document.addEventListener('DOMContentLoaded', () => {
        const notice = document.querySelector('#sharedWaitingNotice');
        if (notice) hasWaiting().then(waiting => notice.classList.toggle('is-hidden', !waiting));
    });

    window.flexbuddyShareInbox = {CACHE, MAX_AGE_MS, take, takeNewest, hasWaiting};
})();
