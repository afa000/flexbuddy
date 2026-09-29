// Payouts sheet: the recent pay periods with what their blocks earned, where the driver records what actually landed
// and sees any gap. Loaded before app.js and uses its shared helpers (apiFetch, csrfHeaders, showToast, formatMoney,
// loadPayPeriods, ...) at call time.
(() => {
    const PERIODS = 8;
    let el;
    let trigger;
    let periods = [];
    let editing = null;

    function init() {
        if (el) return;
        el = {
            modal: document.querySelector('#payoutsModal'),
            list: document.querySelector('#payoutsList'),
            error: document.querySelector('#payoutsError')
        };
        document.querySelector('#closePayoutsButton').addEventListener('click', close);
        el.modal.addEventListener('click', event => {
            if (event.target === el.modal) close();
        });
        el.modal.addEventListener('keydown', event => {
            if (event.key === 'Escape') {
                event.stopPropagation();
                close();
            } else if (event.key === 'Tab') {
                trapFocus(el.modal, event);
            }
        });
    }

    async function open(from) {
        init();
        trigger = from;
        editing = null;
        hideMessage(el.error);
        el.list.replaceChildren();
        el.modal.classList.remove('is-hidden');
        document.body.classList.add('modal-open');
        // Focus stays off the amount fields so a phone does not raise its keyboard before the driver picks a payout.
        document.querySelector('#closePayoutsButton').focus();
        await load();
    }

    function close() {
        el.modal.classList.add('is-hidden');
        document.body.classList.remove('modal-open');
        if (trigger?.isConnected) trigger.focus();
        trigger = undefined;
    }

    async function load() {
        try {
            const response = await apiFetch(`/shifts/pay-periods?count=${PERIODS}`);
            if (!response.ok) throw new Error();
            periods = (await response.json()).periods;
            render();
        } catch {
            showMessage(el.error, 'Payouts could not be loaded.');
        }
    }

    function render() {
        el.list.replaceChildren(...periods.map(row));
    }

    const payoutDate = value => parseLocalDate(value).toLocaleDateString(undefined, {weekday: 'short', month: 'short', day: 'numeric'});
    const shortDate = value => parseLocalDate(value).toLocaleDateString(undefined, {month: 'short', day: 'numeric'});
    const blocks = count => `${count} ${count === 1 ? 'block' : 'blocks'}`;

    // A payout with no logged blocks and nothing recorded has nothing to check, so it gets no form unless asked.
    const empty = period => period.status === 'UNCHECKED' && period.blocks === 0 && editing !== period.payoutDate;

    function statusLabel(period) {
        const gap = formatMoney(Math.abs(Number(period.difference)));
        if (empty(period)) return 'No blocks';
        return {
            UPCOMING: 'Coming',
            UNCHECKED: 'Not checked',
            MATCHED: 'Matches',
            SHORT: `${gap} short`,
            OVER: `${gap} over`
        }[period.status];
    }

    function gapSentence(period) {
        const gap = formatMoney(Math.abs(Number(period.difference)));
        if (period.status === 'SHORT') {
            return `${gap} less than your logged blocks earned. Compare it with this payout in the Flex app.`;
        }
        if (period.status === 'OVER') {
            return `${gap} more than your logged blocks earned, often tips from earlier blocks or a block not logged here.`;
        }
        return '';
    }

    function row(period) {
        const item = document.createElement('li');
        item.className = `payout-row is-${period.status.toLowerCase()}`;
        const range = period.from === period.to ? shortDate(period.from) : `${shortDate(period.from)}–${shortDate(period.to)}`;
        item.innerHTML = `
            <div class="payout-row-head">
                <strong>${escapeHtml(payoutDate(period.payoutDate))}</strong>
                <span class="payout-status">${escapeHtml(statusLabel(period))}</span>
            </div>
            <p class="payout-row-meta">Blocks ${escapeHtml(range)} · ${escapeHtml(blocks(period.blocks))} · earned ${escapeHtml(formatMoney(period.earned))}</p>`;
        if (period.status === 'UPCOMING') return item;
        const offline = window.flexbuddyPwa?.isOffline() ?? false;
        if (empty(period)) {
            item.classList.add('is-empty');
            const record = document.createElement('button');
            record.className = 'text-button payout-record';
            record.type = 'button';
            record.textContent = 'Record a deposit';
            record.disabled = offline;
            record.addEventListener('click', () => startEditing(period));
            item.append(record);
            return item;
        }
        if (period.received != null && editing !== period.payoutDate) {
            const received = document.createElement('div');
            received.className = 'payout-received';
            received.innerHTML = `
                <p>Received <strong>${escapeHtml(formatMoney(period.received))}</strong>${period.note ? ` · ${escapeHtml(period.note)}` : ''}</p>
                ${gapSentence(period) ? `<p class="payout-gap">${escapeHtml(gapSentence(period))}</p>` : ''}
                <div class="confirm-actions">
                    <button class="secondary-button compact-button" type="button" data-action="edit">Change</button>
                    <button class="text-button danger-text-button" type="button" data-action="remove">Remove</button>
                </div>`;
            received.querySelectorAll('button').forEach(button => button.disabled = offline);
            received.querySelector('[data-action="edit"]').addEventListener('click', () => startEditing(period));
            received.querySelector('[data-action="remove"]').addEventListener('click', event => remove(period, event.currentTarget));
            item.append(received);
            return item;
        }
        item.append(form(period, offline));
        return item;
    }

    function startEditing(period) {
        editing = period.payoutDate;
        render();
        el.list.querySelector(`[data-payout="${period.payoutDate}"] input`)?.focus();
    }

    function form(period, offline) {
        const id = `payout-${period.payoutDate}`;
        const form = document.createElement('form');
        form.className = 'payout-form';
        form.dataset.payout = period.payoutDate;
        form.noValidate = true;
        form.innerHTML = `
            <label class="field money-field"><span>What landed</span>
                <span class="input-with-prefix"><b>$</b><input id="${id}-amount" type="number" min="0" max="99999.99" step="0.01"
                    inputmode="decimal" enterkeyhint="done"></span></label>
            <label class="field"><span>Note <small>(optional)</small></span>
                <input id="${id}-note" type="text" maxlength="255" autocomplete="off"></label>
            <div class="notice error-notice is-hidden" role="alert"></div>
            <div class="confirm-actions">
                <button class="primary-button compact-button" type="submit">Save</button>
                ${period.received == null && Number(period.earned) > 0
                    ? `<button class="secondary-button compact-button" type="button" data-action="exact">It was ${escapeHtml(formatMoney(period.earned))}</button>` : ''}
                ${editing === period.payoutDate ? '<button class="text-button" type="button" data-action="cancel">Cancel</button>' : ''}
            </div>`;
        const amount = form.querySelector('input[type="number"]');
        const note = form.querySelector('input[type="text"]');
        amount.value = period.received ?? '';
        note.value = period.note ?? '';
        form.querySelectorAll('button').forEach(button => button.disabled = offline);
        form.addEventListener('submit', event => {
            event.preventDefault();
            save(period, form, amount.value, note.value);
        });
        form.querySelector('[data-action="exact"]')?.addEventListener('click', () => save(period, form, period.earned, note.value));
        form.querySelector('[data-action="cancel"]')?.addEventListener('click', () => {
            editing = null;
            render();
        });
        return form;
    }

    async function save(period, form, value, note) {
        const error = form.querySelector('.error-notice');
        const amount = Number(value);
        if (value === '' || !Number.isFinite(amount) || amount < 0 || amount > 99999.99) {
            showMessage(error, 'Enter the amount that landed, such as 84.50.');
            return;
        }
        hideMessage(error);
        form.querySelectorAll('button').forEach(button => button.disabled = true);
        try {
            const response = await apiFetch(`/payouts/${period.payoutDate}`, {
                method: 'PUT',
                headers: csrfHeaders({'Content-Type': 'application/json'}),
                body: JSON.stringify({amount: Math.round(amount * 100) / 100, note: note.trim() || null})
            });
            if (!response.ok) throw new Error(await response.text() || 'The payout could not be saved.');
            editing = null;
            await load();
            loadPayPeriods();
        } catch (failure) {
            showMessage(error, failure.message || 'The payout could not be saved.');
            form.querySelectorAll('button').forEach(button => button.disabled = false);
        }
    }

    async function remove(period, button) {
        if (!window.confirm(`Forget the ${formatMoney(period.received)} recorded for ${payoutDate(period.payoutDate)}?`)) return;
        button.disabled = true;
        try {
            const response = await apiFetch(`/payouts/${period.payoutDate}`, {method: 'DELETE', headers: csrfHeaders()});
            if (!response.ok) throw new Error();
            await load();
            loadPayPeriods();
        } catch {
            showToast('Payout not removed', 'The recorded amount could not be removed. Try again.', {alert: true});
            button.disabled = false;
        }
    }

    window.flexbuddyPayouts = {open};
})();
