// Outbox: keeps a shift add, an expense add, or a block's finish details on the phone when there is no connection, and
// sends them once the app is open with one. Each item's id is also its Idempotency-Key, made before the first attempt,
// so a request that reached the server but lost its response is recognised when it is replayed.
//
// The decisions live in pure functions (classify, nextDelay, conflictRows, canQueue) that touch no DOM, IndexedDB or
// fetch, and are exported for the tests in src/test/js. Everything else runs in the page: there is no Background Sync.
// Loaded on both pages, after pwa.js and before the scripts that save; it uses their helpers (apiFetch, retryHeaders,
// loadDashboard, loadExpenses, openConfirm, formatTime, formatMoney, escapeHtml, trapFocus) at call time, and the
// account page only needs list, count, drain, discard, discardAll and clear.
(() => {
    'use strict';
    const DB_NAME = 'flexbuddy-outbox';
    const STORE = 'items';
    const MAX_ITEMS = 50;
    const REQUEST_TIMEOUT_MS = 20000;
    const LOCK_NAME = 'flexbuddy-outbox';
    const BACKOFF_MS = [30000, 60000, 120000, 300000];
    const OFFLINE_MESSAGE = t('js.outbox.offlineMessage');
    const TOO_MANY_MESSAGE = t('js.outbox.tooManyMessage');

    // What each queued finish can change, in the order the conflict sheet shows it.
    const CONFLICT_FIELDS = [
        {label: 'js.outbox.fieldStatus', kind: 'text', key: 'status'},
        {label: 'js.outbox.fieldStarted', kind: 'time', key: 'actualStart', group: 'details'},
        {label: 'js.outbox.fieldFinished', kind: 'time', key: 'actualEnd', group: 'details'},
        {label: 'js.outbox.fieldOdometerStart', kind: 'number', key: 'odometerStart', group: 'details'},
        {label: 'js.outbox.fieldOdometerEnd', kind: 'number', key: 'odometerEnd', group: 'details'},
        {label: 'js.outbox.fieldMiles', kind: 'number', key: 'miles'},
        {label: 'js.outbox.fieldStops', kind: 'number', key: 'stops', group: 'details'},
        {label: 'js.outbox.fieldPackages', kind: 'number', key: 'packages', group: 'details'},
        {label: 'js.outbox.fieldReturns', kind: 'number', key: 'returns', group: 'details'},
        {label: 'js.outbox.fieldBasePay', kind: 'money', key: 'basePay'}
    ];

    // ----- Pure decisions -----

    /**
     * What to do with the response to a queued item: delete it (delivered, or already delivered), park it as a
     * conflict, mark it failed, fetch a fresh token and retry, stop because the session is gone, or stop because the
     * server or network is not answering. A status of 0 stands for a network error or a timeout.
     */
    function classify(status, redirectedToLogin, body) {
        if (redirectedToLogin || status === 401) return 'signed-out';
        if (!status) return 'stop';
        if (status >= 200 && status < 300) return 'delete';
        if (status === 409) {
            const code = body && body.code;
            return code === 'DUPLICATE' ? 'delete' : code === 'CONFLICT' ? 'conflict' : 'failed';
        }
        if (status === 403) return 'retry-token';
        if (status === 408 || status === 429 || status >= 500) return 'stop';
        return 'failed';
    }

    /** Seconds between drain attempts while items wait: 30, 60, 120, then 300 for good. */
    function nextDelay(attempt) {
        return BACKOFF_MS[Math.min(Math.max(attempt, 0), BACKOFF_MS.length - 1)];
    }

    function canQueue(count) {
        return count < MAX_ITEMS;
    }

    function normalise(value, kind) {
        if (value === undefined || value === null || value === '') return null;
        if (kind === 'time') return String(value).slice(0, 5);
        if (kind === 'number' || kind === 'money') return Number(value);
        return String(value);
    }

    /**
     * The rows of the conflict sheet: only the fields the queued change sets, each with the driver's value, the saved
     * value, and whether they differ. Times compare at minute precision, and a value against nothing counts as a
     * difference.
     */
    function conflictRows(queuedBody, current) {
        const body = queuedBody || {};
        const saved = current || {};
        const rows = [];
        for (const field of CONFLICT_FIELDS) {
            const mineSource = field.group === 'details' ? body.details || {} : body;
            if (!Object.prototype.hasOwnProperty.call(mineSource, field.key)) continue;
            const savedSource = field.group === 'details' ? saved.details || {} : saved;
            const mine = normalise(mineSource[field.key], field.kind);
            const theirs = normalise(savedSource[field.key], field.kind);
            rows.push({label: t(field.label), kind: field.kind, mine, saved: theirs, differs: mine !== theirs});
        }
        return rows;
    }

    // ----- Storage -----

    let dbPromise = null;
    let items = [];
    let signedOut = false;
    let draining = false;
    let timer = null;
    let attempt = 0;
    let persistAsked = false;
    let started = false;

    function openDb() {
        if (dbPromise) return dbPromise;
        dbPromise = new Promise((resolve, reject) => {
            if (typeof indexedDB === 'undefined') {
                reject(new Error('IndexedDB is not available.'));
                return;
            }
            const request = indexedDB.open(DB_NAME, 1);
            request.onupgradeneeded = () => request.result.createObjectStore(STORE, {keyPath: 'id'});
            request.onsuccess = () => resolve(request.result);
            request.onerror = () => reject(request.error);
            request.onblocked = () => reject(new Error('IndexedDB is blocked.'));
        });
        dbPromise.catch(() => {
            dbPromise = null;
        });
        return dbPromise;
    }

    async function run(mode, work) {
        const db = await openDb();
        return new Promise((resolve, reject) => {
            const transaction = db.transaction(STORE, mode);
            const request = work(transaction.objectStore(STORE));
            transaction.oncomplete = () => resolve(request ? request.result : undefined);
            transaction.onerror = () => reject(transaction.error);
            transaction.onabort = () => reject(transaction.error);
        });
    }

    const readAll = () => run('readonly', store => store.getAll());
    const putItem = item => run('readwrite', store => store.put(item));
    const removeItem = id => run('readwrite', store => store.delete(id));

    /** Reloads the list from the device, oldest first, and tells the page. A device without storage has no items. */
    async function refresh() {
        try {
            items = (await readAll()).sort((a, b) => a.createdAt - b.createdAt);
        } catch {
            items = [];
        }
        announce();
    }

    function announce() {
        document.dispatchEvent(new CustomEvent('flexbuddy:outbox'));
    }

    const accountId = () => document.querySelector('meta[name="flexbuddy-account"]')?.content || '';
    const offlineNow = () => Boolean(window.flexbuddyPwa?.isOffline()) || navigator.onLine === false;
    const mine = () => items.filter(item => item.accountId === accountId());
    const pending = () => items.filter(item => item.state === 'pending');

    function list() {
        return items.slice();
    }

    /** How many changes on this device would be lost by signing out. */
    function count() {
        return mine().length;
    }

    function pendingShiftIds() {
        return new Set(mine().filter(item => item.kind === 'shift-finish' && item.state !== 'failed')
            .map(item => item.shiftId));
    }

    // ----- Queueing -----

    function isNetworkFailure(error) {
        return Boolean(error) && (error.offline === true || error.name === 'AbortError'
            || (error.name === 'TypeError' && /fetch|network|load failed/i.test(error.message || '')));
    }

    /**
     * Saves through the outbox. Online it sends at once, with the item's id as the Idempotency-Key, and returns
     * {sent: Response} for any HTTP answer so the caller handles it as it always did. When the network fails or there
     * is no connection, the change is kept on the phone and it returns {queued: true}.
     */
    async function submit(kind, {method, url, body, summary, shiftId, expectedUpdatedAt}) {
        const id = crypto.randomUUID();
        if (!offlineNow()) {
            const controller = new AbortController();
            const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
            try {
                const response = await apiFetch(url, {
                    method, signal: controller.signal, body: JSON.stringify(body),
                    headers: retryHeaders(id, {'Content-Type': 'application/json'})
                });
                return {sent: response};
            } catch (error) {
                if (!isNetworkFailure(error)) throw error;
            } finally {
                clearTimeout(timeout);
            }
        }
        await enqueue({id, kind, method, url, body, summary, shiftId, expectedUpdatedAt});
        return {queued: true};
    }

    async function enqueue(item) {
        const owner = accountId();
        // Without an account or storage the change cannot be kept safely, so it behaves as it always did offline.
        if (!owner) throw new Error(OFFLINE_MESSAGE);
        if (!canQueue(items.length)) throw new Error(TOO_MANY_MESSAGE);
        const stored = {...item, accountId: owner, createdAt: Date.now(), state: 'pending'};
        try {
            await putItem(stored);
        } catch {
            throw new Error(OFFLINE_MESSAGE);
        }
        if (!persistAsked) {
            persistAsked = true;
            // Chrome can otherwise delete a site's storage when the phone runs low; a refusal only leaves it unprotected.
            try {
                navigator.storage?.persist?.();
            } catch {
                // Not being protected from eviction is not a reason to lose the change.
            }
        }
        await refresh();
        scheduleDrain(0);
    }

    // ----- Draining -----

    async function fetchCredentials() {
        try {
            const response = await fetch('/csrf', {cache: 'no-store', credentials: 'same-origin'});
            const toLogin = response.redirected && new URL(response.url, window.location.origin).pathname === '/login';
            if (toLogin || response.status === 401 || response.status === 403) return {status: 'signed-out'};
            if (!response.ok) return {status: 'stop'};
            const data = await response.json();
            return {status: 'ok', headerName: data.headerName, token: data.token, accountId: String(data.accountId)};
        } catch {
            return {status: 'stop'};
        }
    }

    async function sendItem(item, credentials) {
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
        // A queued finish says which version of the block it was made against; nothing sends this online.
        const body = item.kind === 'shift-finish' ? {...item.body, expectedUpdatedAt: item.expectedUpdatedAt} : item.body;
        try {
            const response = await fetch(item.url, {
                method: item.method, signal: controller.signal, cache: 'no-store', credentials: 'same-origin',
                headers: {'Content-Type': 'application/json', [credentials.headerName]: credentials.token, 'Idempotency-Key': item.id},
                body: JSON.stringify(body)
            });
            const redirectedToLogin = response.redirected && new URL(response.url, window.location.origin).pathname === '/login';
            let json = null;
            let text = '';
            if (!redirectedToLogin && response.status >= 400) {
                text = await response.text();
                try {
                    json = JSON.parse(text);
                } catch {
                    json = null;
                }
            }
            return {status: response.status, redirectedToLogin, json, text};
        } catch {
            return {status: 0, redirectedToLogin: false, json: null, text: ''};
        } finally {
            clearTimeout(timeout);
        }
    }

    function drain() {
        if (!pending().length) return Promise.resolve();
        if (typeof navigator.locks?.request === 'function') {
            // Only one tab drains at a time; a tab that does not get the lock leaves the work to the one that has it.
            return navigator.locks.request(LOCK_NAME, {ifAvailable: true}, async lock => {
                if (lock) await drainLocked();
            });
        }
        if (draining) return Promise.resolve();
        draining = true;
        return drainLocked().finally(() => {
            draining = false;
        });
    }

    async function drainLocked() {
        await refresh();
        if (!pending().length || offlineNow()) return;
        let credentials = await fetchCredentials();
        if (credentials.status === 'signed-out') return setSignedOut(true);
        if (credentials.status !== 'ok') return scheduleDrain();
        setSignedOut(false);
        let synced = 0;
        let conflicts = 0;
        let stopped = false;
        for (const item of pending()) {
            // A queued change is never sent under a different account than the one it was made in.
            if (item.accountId !== credentials.accountId) continue;
            let retriedToken = false;
            for (;;) {
                const result = await sendItem(item, credentials);
                const outcome = classify(result.status, result.redirectedToLogin, result.json);
                if (outcome === 'retry-token' && !retriedToken) {
                    retriedToken = true;
                    credentials = await fetchCredentials();
                    if (credentials.status === 'ok') continue;
                    setSignedOut(credentials.status === 'signed-out');
                    stopped = true;
                    break;
                }
                if (outcome === 'delete') {
                    await removeItem(item.id);
                    synced++;
                } else if (outcome === 'conflict') {
                    await putItem({...item, state: 'conflict', current: result.json && result.json.current});
                    conflicts++;
                } else if (outcome === 'failed') {
                    await putItem({...item, state: 'failed', error: (result.text || t('js.outbox.serverRefused')).slice(0, 200)});
                } else if (outcome === 'stop') {
                    stopped = true;
                } else {
                    // Signed out, or a token that stayed refused: keep everything for after the next sign-in.
                    setSignedOut(true);
                    stopped = true;
                }
                break;
            }
            if (stopped) break;
        }
        await refresh();
        if (stopped && !signedOut) scheduleDrain();
        if (synced) {
            attempt = 0;
            reportSynced(synced, conflicts);
        }
    }

    /** The words of a queued item's label: a [key, ...arguments] pair, or plain text kept from an older version. */
    function summaryText(part) {
        return Array.isArray(part) ? t(part[0], ...part.slice(1)) : (part ?? '');
    }

    function reportSynced(synced, conflicts) {
        const title = conflicts
            ? `${tn(synced, 'js.outbox.changesSynced')} · ${t('js.outbox.needsChoiceCount', conflicts)}`
            : t('js.outbox.allSynced');
        window.flexbuddyToast?.show(title, t('js.outbox.nowInHistory'));
        if (typeof loadDashboard === 'function') loadDashboard();
        const expenses = document.querySelector('#expensesScreen');
        if (typeof loadExpenses === 'function' && expenses && !expenses.classList.contains('is-hidden')) loadExpenses();
    }

    function setSignedOut(value) {
        signedOut = value;
        announce();
    }

    /** Tries again after a growing delay while anything is waiting and the page is showing. */
    function scheduleDrain(delay) {
        clearTimeout(timer);
        if (!pending().length || document.visibilityState === 'hidden') return;
        const wait = delay === undefined ? nextDelay(attempt++) : delay;
        timer = setTimeout(() => drain(), wait);
    }

    // ----- Discarding and conflicts -----

    async function discard(id) {
        try {
            await removeItem(id);
        } finally {
            await refresh();
        }
    }

    async function discardAll() {
        for (const item of mine()) await removeItem(item.id).catch(() => {});
        await refresh();
    }

    /** 'mine' sends the driver's change again against the saved version; 'saved' drops it. */
    async function resolveConflict(id, choice) {
        const item = items.find(entry => entry.id === id);
        if (!item) return;
        if (choice === 'saved') {
            await discard(id);
            return;
        }
        await putItem({...item, state: 'pending', expectedUpdatedAt: item.current && item.current.updatedAt, current: undefined});
        await refresh();
        drain();
    }

    /** Deletes the whole queue, for sign-out and for anything that wipes this driver's cached data. */
    async function clear() {
        items = [];
        if (dbPromise) {
            try {
                (await dbPromise).close();
            } catch {
                // It never opened, so there is nothing to close.
            }
        }
        dbPromise = null;
        if (typeof indexedDB !== 'undefined') {
            await new Promise(resolve => {
                const request = indexedDB.deleteDatabase(DB_NAME);
                request.onsuccess = request.onerror = request.onblocked = () => resolve();
            });
        }
        announce();
    }

    // ----- Page: the strip, the conflict sheet, and the account line -----

    const $ = selector => document.querySelector(selector);
    const esc = value => (typeof escapeHtml === 'function' ? escapeHtml(value) : String(value ?? ''));

    function tagFor(item, foreign) {
        if (foreign) return {text: t('js.outbox.fromAnotherAccount'), className: 'is-foreign'};
        if (item.state === 'conflict') return {text: t('js.outbox.needsChoice'), className: 'is-conflict'};
        if (item.state === 'failed') return {text: t('js.outbox.couldNotSync'), className: 'is-failed'};
        return {text: t('js.outbox.pending'), className: 'is-pending'};
    }

    function renderStrip() {
        const strip = $('#outboxStrip');
        if (!strip) return;
        strip.hidden = items.length === 0;
        if (!items.length) return;
        const owner = accountId();
        const attention = signedOut || items.some(item => item.state !== 'pending' || item.accountId !== owner);
        strip.classList.toggle('needs-attention', attention);
        const title = $('#outboxTitle');
        const sync = $('#outboxSyncButton');
        const signIn = $('#outboxSignIn');
        const waiting = mine().length;
        title.textContent = signedOut ? tn(waiting, 'js.outbox.signInToSync') : t('js.outbox.waitingToSync', items.length);
        signIn.hidden = !signedOut;
        sync.hidden = signedOut;
        sync.disabled = offlineNow();
        sync.title = sync.disabled ? t('js.pwa.availableWhenOnline') : '';
        $('#outboxList').replaceChildren(...items.map(item => row(item, item.accountId !== owner)));
    }

    function row(item, foreign) {
        const tag = tagFor(item, foreign);
        const li = document.createElement('li');
        li.innerHTML = `
            <div class="outbox-main">
                <strong>${esc(summaryText(item.summary && item.summary.title))}</strong>
                <span>${esc(summaryText(item.summary && item.summary.detail))}</span>
                ${item.state === 'failed' && !foreign ? `<p class="outbox-error">${esc(item.error)}</p>` : ''}
            </div>
            <div class="outbox-actions">
                <span class="outbox-tag ${tag.className}">${esc(tag.text)}</span>
                ${item.state === 'conflict' && !foreign ? `<button class="text-button" type="button" data-action="choose">${esc(t('js.outbox.choose'))}</button>` : ''}
                <button class="text-button danger-text-button" type="button" data-action="discard">${esc(t('js.common.discard'))}</button>
            </div>`;
        li.querySelector('[data-action="choose"]')?.addEventListener('click', event => openConflict(item, event.currentTarget));
        li.querySelector('[data-action="discard"]').addEventListener('click', () => {
            // A change that was never sent cannot be recovered, so it asks first; one that failed or is foreign does not.
            if (item.state === 'pending' && !foreign && typeof openConfirm === 'function') {
                openConfirm(t('js.outbox.discardTitle'), t('js.outbox.discardMessage'), () => discard(item.id), t('js.common.discard'));
            } else {
                discard(item.id);
            }
        });
        return li;
    }

    let conflictTrigger = null;
    let conflictItem = null;

    function displayValue(row, value) {
        if (value === null) return '—';
        if (row.kind === 'time' && typeof formatTime === 'function') return formatTime(value);
        if (row.kind === 'money' && typeof formatMoney === 'function') return formatMoney(value);
        return String(value);
    }

    function openConflict(item, trigger) {
        const modal = $('#conflictModal');
        if (!modal) return;
        conflictItem = item;
        conflictTrigger = trigger;
        const saved = item.current || {};
        $('#conflictShift').textContent = `${saved.station || summaryText(item.summary && item.summary.title) || t('js.outbox.thisBlock')}${saved.date ? ` · ${saved.date}` : ''}`;
        // A field that is empty on both sides has nothing to choose between, so the sheet leaves it out.
        const rows = conflictRows(item.body, item.current).filter(entry => entry.mine !== null || entry.saved !== null);
        $('#conflictRows').replaceChildren(...rows.map(entry => {
            const tr = document.createElement('tr');
            const cell = (text, bold) => {
                const td = document.createElement('td');
                if (bold) {
                    const b = document.createElement('b');
                    b.textContent = text;
                    td.append(b);
                } else {
                    td.textContent = text;
                }
                return td;
            };
            const th = document.createElement('th');
            th.scope = 'row';
            th.textContent = entry.label;
            tr.append(th, cell(displayValue(entry, entry.mine), entry.differs), cell(displayValue(entry, entry.saved), entry.differs));
            return tr;
        }));
        modal.classList.remove('is-hidden');
        document.body.classList.add('modal-open');
        $('#conflictKeepSaved').focus();
    }

    function closeConflict() {
        $('#conflictModal').classList.add('is-hidden');
        document.body.classList.remove('modal-open');
        if (conflictTrigger?.isConnected) conflictTrigger.focus();
        conflictItem = null;
        conflictTrigger = null;
    }

    function bindConflictSheet() {
        const modal = $('#conflictModal');
        if (!modal) return;
        $('#closeConflictButton').addEventListener('click', closeConflict);
        modal.addEventListener('click', event => {
            if (event.target === modal) closeConflict();
        });
        modal.addEventListener('keydown', event => {
            if (event.key === 'Escape') {
                event.stopPropagation();
                closeConflict();
            } else if (event.key === 'Tab' && typeof trapFocus === 'function') {
                trapFocus(modal, event);
            }
        });
        $('#conflictKeepSaved').addEventListener('click', async () => {
            const id = conflictItem && conflictItem.id;
            closeConflict();
            if (id) await resolveConflict(id, 'saved');
        });
        $('#conflictUseMine').addEventListener('click', async () => {
            const id = conflictItem && conflictItem.id;
            closeConflict();
            if (id) await resolveConflict(id, 'mine');
        });
    }

    function renderAccountLine() {
        const status = $('#outboxStatus');
        if (!status) return;
        const waiting = mine().length;
        $('#outboxAccountRow').hidden = waiting === 0;
        status.textContent = tn(waiting, signedOut ? 'js.outbox.waitingSignIn' : 'js.outbox.waitingCount');
        $('#outboxAccountSync').disabled = offlineNow();
    }

    function render() {
        renderStrip();
        renderAccountLine();
    }

    // ----- Start -----

    function start() {
        if (started) return;
        started = true;
        bindConflictSheet();
        document.addEventListener('flexbuddy:outbox', render);
        $('#outboxSyncButton')?.addEventListener('click', () => drain());
        $('#outboxAccountSync')?.addEventListener('click', () => drain());
        $('#outboxAccountDiscard')?.addEventListener('click', () => {
            const n = mine().length;
            if (n && window.confirm(tn(n, 'js.outbox.confirmDiscardAll'))) discardAll();
        });
        const kick = () => {
            render();
            drain();
        };
        window.addEventListener('online', kick);
        document.addEventListener('flexbuddy:online', kick);
        window.addEventListener('offline', render);
        document.addEventListener('visibilitychange', () => {
            if (document.visibilityState === 'visible') kick();
        });
        refresh().then(() => {
            if (!offlineNow()) setTimeout(() => drain(), 1000);
        });
    }

    document.addEventListener('DOMContentLoaded', start);

    window.flexbuddyOutbox = {
        submit, drain, list, discard, discardAll, count, resolveConflict, pendingShiftIds, clear,
        classify, nextDelay, conflictRows, canQueue
    };
})();
