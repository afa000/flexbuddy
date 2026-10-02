// Setup: the "Get set up" card at the top of Home for a driver with a new account. Two steps count, adding a first block
// and setting a goal, and tick themselves off from real data. The vehicle costs, payouts and taxes rows only show the
// current setting with a link to change it, because their defaults already work and cannot be "detected" as set; taxes
// is an estimate, never counted, and leaving it off is a finished state. Loaded before app.js and uses its helpers
// (apiFetch, csrfHeaders, escapeHtml) at call time.
(() => {
    const HIDDEN_KEY = 'flexbuddy-setup-hidden';
    const sections = () => window.flexbuddyAccountSections;

    /** Off, or the driver's own percentage, always called an estimate; no percentage is suggested. */
    function taxDetail(settings) {
        if (!settings) return 'Loading…';
        if (settings.taxSetAsidePercent == null) return 'Off · an estimate to help you save, not tax advice';
        return `${Number(settings.taxSetAsidePercent)}% set aside · estimate, not tax advice`;
    }

    /** The rows and progress. `settings` is null while they load. */
    function setupState({hasBlocks, settings}) {
        const goalSet = settings?.weeklyGoal != null || settings?.monthlyGoal != null;
        const taxOn = settings?.taxSetAsidePercent != null;
        const doneCount = (hasBlocks ? 1 : 0) + (goalSet ? 1 : 0);
        const complete = doneCount === 2;
        return {
            rows: [
                {key: 'block', text: 'Add your first block', detail: 'Import a screenshot, add one by hand, or restore a backup in Account',
                    done: hasBlocks, action: hasBlocks ? null : 'Add'},
                {key: 'goal', text: 'Set a weekly goal', detail: 'See your progress on Home each week', done: goalSet,
                    action: goalSet ? null : 'Set goal'},
                {key: 'costs', text: 'Vehicle costs', detail: settings ? sections().costMethod(settings) : 'Loading…', review: true, action: 'Change'},
                {key: 'payouts', text: 'Payouts', detail: settings ? sections().payouts(settings) : 'Loading…', review: true, action: 'Change'},
                {key: 'taxes', text: 'Taxes (optional)', detail: taxDetail(settings), review: true, action: taxOn ? 'Change' : 'Set up'}
            ],
            doneCount,
            total: 2,
            complete,
            progress: complete ? "You're set up" : `${doneCount} of 2 done`
        };
    }

    const DESTINATIONS = {goal: '/account#goals', costs: '/account#costs', payouts: '/account#payouts-section', taxes: '/account#taxes'};

    let el;
    let state = {hasBlocks: false, settings: null};

    function remember(hidden) {
        try {
            if (hidden) localStorage.setItem(HIDDEN_KEY, '1');
            else localStorage.removeItem(HIDDEN_KEY);
        } catch {
            // Without storage the dismissal still reaches the server when it can.
        }
    }

    function wasHidden() {
        try {
            return localStorage.getItem(HIDDEN_KEY) === '1';
        } catch {
            return false;
        }
    }

    /** Tells the server, which keeps it hidden on every device. A failure keeps the local flag for the next online load. */
    async function sendDismissal() {
        try {
            const response = await apiFetch('/account/setup/dismiss', {method: 'POST', headers: csrfHeaders()});
            if (response.ok) remember(false);
        } catch {
            // Offline or signed out: the flag stays, and the next online load tries again.
        }
    }

    function dismiss() {
        remember(true);
        el.card.remove();
        document.querySelector('#homeGreeting')?.focus();
        sendDismissal();
    }

    function open(row) {
        if (row.key === 'block') window.flexbuddyQuickActions?.open();
        else if (DESTINATIONS[row.key]) window.location.assign(DESTINATIONS[row.key]);
    }

    function render() {
        if (!el?.card.isConnected) return;
        const view = setupState(state);
        el.progress.textContent = view.progress;
        el.done.hidden = !view.complete;
        el.hide.hidden = view.complete;
        el.list.replaceChildren(...view.rows.map(row => {
            const item = document.createElement('li');
            const mark = row.review ? '' : row.done ? '✓' : '○';
            const inner = `<span class="setup-mark" aria-hidden="true">${mark}</span>`
                + `<span>${escapeHtml(row.text)}<small>${escapeHtml(row.detail)}</small></span>`
                + (row.action ? `<b>${escapeHtml(row.action)}</b>` : '');
            if (row.done) {
                item.innerHTML = `<div class="setup-row is-done">${inner}<span class="sr-only">Done</span></div>`;
            } else {
                item.innerHTML = `<button class="setup-row" type="button">${inner}</button>`;
                item.querySelector('button').addEventListener('click', () => open(row));
            }
            return item;
        }));
    }

    /** Re-renders with a new block count, so adding the first block ticks its row without a page reload. */
    function update({hasBlocks}) {
        state = {...state, hasBlocks};
        render();
    }

    async function loadSettings() {
        try {
            const response = await apiFetch('/account/settings');
            if (!response.ok) throw new Error();
            state = {...state, settings: await response.json()};
        } catch {
            // The review rows keep saying Loading… and the two counted steps still work from the block count.
        }
        render();
    }

    function start() {
        const card = document.querySelector('#setupCard');
        if (!card) return;
        // A Hide pressed offline, or a cached page that still has the card: keep it gone and retry the server.
        if (wasHidden()) {
            card.remove();
            if (navigator.onLine) sendDismissal();
            return;
        }
        el = {
            card, progress: card.querySelector('#setupProgress'), list: card.querySelector('#setupList'),
            done: card.querySelector('#setupDone'), hide: card.querySelector('#setupHide')
        };
        state = {hasBlocks: card.dataset.hasBlocks === 'true', settings: null};
        el.done.addEventListener('click', dismiss);
        el.hide.addEventListener('click', dismiss);
        render();
        loadSettings();
    }

    window.flexbuddySetup = {setupState, taxDetail, update};

    // The scripts run deferred and in order, so the helpers in app.js exist once the document finishes loading.
    if (typeof document.addEventListener === 'function') {
        if (document.readyState === 'complete') start();
        else document.addEventListener('DOMContentLoaded', start);
    }
})();
