// Review this block: one checklist for a worked block's actual times, miles, tolls and parking, and payout, each row
// opening the screen that already handles it (finish.js, the Expenses form, payouts.js). The sheet saves nothing
// itself except "No miles", the existing explicit-zero save. Loaded before app.js and uses its shared helpers
// (apiFetch, formatMoney, formatTime, formatMinutes, toIsoDate, ...) at call time; the row rules touch no page
// elements, so they run under `node --test`.
(() => {
    const COSTS = ['TOLL', 'PARKING'];
    const TO_CHECK = ['missing', 'attention'];
    const STATE_KEYS = {
        done: 'js.blockReview.stateDone', zero: 'js.blockReview.stateZero', missing: 'js.blockReview.stateMissing',
        optional: 'js.blockReview.stateOptional', attention: 'js.blockReview.stateAttention',
        waiting: 'js.blockReview.stateWaiting'
    };
    const NAME_KEYS = {
        times: 'js.blockReview.timesName', miles: 'js.blockReview.milesName', costs: 'js.blockReview.costsName',
        payout: 'js.blockReview.payoutName'
    };
    const ACTION_KEYS = {
        addTimes: 'js.blockReview.addTimes', change: 'js.blockReview.change', addMiles: 'js.blockReview.addMiles',
        noMiles: 'js.blockReview.noMiles', costs: 'js.blockReview.addCost', payout: 'js.blockReview.checkPayout'
    };

    function timesRow(shift, fmt) {
        const details = shift.details ?? {};
        if (details.actualStart && details.actualEnd) {
            const range = [fmt.time(details.actualStart), fmt.time(details.actualEnd)];
            const text = details.actualMinutes != null
                ? t('js.blockReview.timesDone', ...range, fmt.minutes(details.actualMinutes))
                : t('js.blockReview.timesRange', ...range);
            return {key: 'times', state: 'done', text, actions: ['change']};
        }
        return {key: 'times', state: 'optional', text: t('js.blockReview.timesOptional'), actions: ['addTimes']};
    }

    /** Missing, explicitly zero and driven miles read differently; only missing miles need attention. */
    function milesRow(shift) {
        if (shift.miles == null) {
            return {key: 'miles', state: 'missing', text: t('js.blockReview.milesMissing'), actions: ['addMiles', 'noMiles']};
        }
        if (Number(shift.miles) === 0) return {key: 'miles', state: 'zero', text: t('js.blockReview.milesZero'), actions: ['change']};
        return {key: 'miles', state: 'done', text: t('js.blockReview.milesDone', Number(shift.miles).toFixed(1)), actions: ['change']};
    }

    /** Tolls and parking linked to the block; `expenses` is null when they couldn't be loaded. */
    function costsRow(expenses, fmt) {
        if (expenses == null) return {key: 'costs', state: 'optional', text: t('js.blockReview.costsUnknown'), actions: ['costs']};
        const costs = expenses.filter(expense => COSTS.includes(expense.category));
        if (!costs.length) return {key: 'costs', state: 'optional', text: t('js.blockReview.costsNone'), actions: ['costs']};
        const total = costs.reduce((sum, expense) => sum + Number(expense.amount || 0), 0);
        return {key: 'costs', state: 'done', text: tn(costs.length, 'js.blockReview.costsLinked', fmt.money(total)), actions: ['costs']};
    }

    /** The payout whose period holds the block's day, or undefined when none of the loaded periods does. */
    function periodFor(date, periods) {
        return (periods ?? []).find(period => period.from <= date && date <= period.to);
    }

    /** Where the block's payout stands. Completed never means paid: that is only what the Payouts sheet records. */
    function payoutRow(shift, periods, fmt) {
        const period = periodFor(shift.date, periods);
        if (!period) return {key: 'payout', state: 'optional', text: t('js.blockReview.payoutUnknown'), actions: ['payout']};
        const day = fmt.day(period.payoutDate);
        switch (period.status) {
            case 'UPCOMING': return {key: 'payout', state: 'waiting', text: t('js.blockReview.payoutUpcoming', day), actions: []};
            case 'MATCHED': return {key: 'payout', state: 'done', text: t('js.blockReview.payoutMatched', day), actions: []};
            case 'OVER':
                return {key: 'payout', state: 'done', text: t('js.blockReview.payoutOver', day, fmt.money(period.difference)), actions: ['payout']};
            case 'SHORT':
                return {key: 'payout', state: 'attention', text: t('js.blockReview.payoutShort', day, fmt.money(-Number(period.difference))), actions: ['payout']};
            default:
                return {key: 'payout', state: 'attention', text: t('js.blockReview.payoutUnchecked', day), actions: ['payout']};
        }
    }

    /** The four rows for a completed block. */
    function rows(shift, {expenses, periods}, fmt) {
        return [timesRow(shift, fmt), milesRow(shift), costsRow(expenses, fmt), payoutRow(shift, periods, fmt)];
    }

    /** How many rows need the driver: missing miles, a payout not checked or short. Optional rows never count. */
    function toCheck(items) {
        return items.filter(item => TO_CHECK.includes(item.state)).length;
    }

    function summary(items) {
        const count = toCheck(items);
        return count ? tn(count, 'js.blockReview.toCheck') : t('js.blockReview.allChecked');
    }

    /**
     * The newest worked block from `sinceIso` (yesterday) to `todayIso`, for Home's "Your last block" card. A block
     * dated later can't have been worked yet, even when it is marked completed by mistake.
     */
    function lastBlock(shifts, sinceIso, todayIso) {
        return (shifts ?? [])
            .filter(shift => shift.status === 'COMPLETED' && shift.date >= sinceIso && shift.date <= todayIso)
            .sort((first, second) => `${second.date} ${second.startTime}`.localeCompare(`${first.date} ${first.startTime}`))[0];
    }

    // ----- The sheet and the Home card, which use app.js helpers at call time -----

    let el;
    let current;
    let data;
    let trigger;

    function init() {
        if (el) return;
        el = {
            modal: document.querySelector('#blockReviewModal'),
            title: document.querySelector('#blockReviewTitle'),
            description: document.querySelector('#blockReviewDescription'),
            summary: document.querySelector('#blockReviewSummary'),
            list: document.querySelector('#blockReviewList'),
            error: document.querySelector('#blockReviewError'),
            card: document.querySelector('#lastBlockCard'),
            cardTitle: document.querySelector('#lastBlockTitle'),
            cardSummary: document.querySelector('#lastBlockSummary'),
            cardButton: document.querySelector('#lastBlockButton')
        };
        document.querySelector('#closeBlockReviewButton').addEventListener('click', close);
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
        el.list.addEventListener('click', event => {
            const button = event.target.closest('button[data-action]');
            if (button) act(button.dataset.action, button);
        });
        el.cardButton.addEventListener('click', event => open(event.currentTarget.dataset.shiftId, event.currentTarget));
    }

    function formats() {
        const day = iso => parseLocalDate(iso).toLocaleDateString(appLocale(), {weekday: 'short', month: 'short', day: 'numeric'});
        return {money: formatMoney, time: formatTime, minutes: formatMinutes, day};
    }

    async function json(request) {
        const response = await request;
        if (!response.ok) throw new Error();
        return response.json();
    }

    /** The block as saved, its linked expenses and the recent payouts; only the block itself is required. */
    async function load(id) {
        const [shift, expenses, periods] = await Promise.allSettled([
            json(apiFetch(`/shifts/${encodeURIComponent(id)}`)),
            json(apiFetch(`/shifts/${encodeURIComponent(id)}/expenses`)),
            json(apiFetch('/shifts/pay-periods?count=6'))
        ]);
        if (shift.status !== 'fulfilled') throw new Error();
        return {
            shift: shift.value,
            expenses: expenses.status === 'fulfilled' ? expenses.value : null,
            periods: periods.status === 'fulfilled' ? periods.value.periods : []
        };
    }

    /** Opens the checklist for the block with this id; only a worked block has one. */
    async function open(id, from) {
        init();
        trigger = from;
        hideMessage(el.error);
        el.title.textContent = t('js.common.loading');
        el.description.textContent = '';
        el.summary.textContent = '';
        el.list.replaceChildren();
        el.modal.classList.remove('is-hidden');
        document.body.classList.add('modal-open');
        document.querySelector('#closeBlockReviewButton').focus();
        try {
            const loaded = await load(id);
            if (loaded.shift.status !== 'COMPLETED') throw new Error();
            current = loaded.shift;
            data = loaded;
            render();
        } catch {
            current = undefined;
            el.title.textContent = t('js.blockReview.title');
            showMessage(el.error, t('js.blockReview.notFound'));
        }
    }

    function render() {
        const fmt = formats();
        const items = rows(current, data, fmt);
        el.title.textContent = current.station;
        el.description.textContent = t('js.blockReview.description', fmt.day(current.date), fmt.time(current.startTime),
            fmt.time(current.endTime), fmt.money(current.earnedPay ?? current.totalPay ?? 0));
        el.summary.textContent = summary(items);
        el.list.replaceChildren(...items.map(item => {
            const row = document.createElement('li');
            row.className = 'review-row';
            row.dataset.state = item.state;
            const text = document.createElement('div');
            text.className = 'review-text';
            const name = document.createElement('strong');
            name.textContent = t(NAME_KEYS[item.key]);
            const state = document.createElement('span');
            state.className = 'review-state';
            state.textContent = t(STATE_KEYS[item.state]);
            const detail = document.createElement('small');
            detail.textContent = item.text;
            name.append(' ', state);
            text.append(name, detail);
            const actions = document.createElement('div');
            actions.className = 'review-actions';
            item.actions.forEach((action, index) => {
                const button = document.createElement('button');
                button.type = 'button';
                button.className = index === 0 && TO_CHECK.includes(item.state)
                    ? 'primary-button compact-button' : 'secondary-button compact-button';
                button.dataset.action = action;
                button.textContent = t(ACTION_KEYS[action]);
                if (action === 'noMiles') button.dataset.onlineOnly = '';
                actions.append(button);
            });
            row.append(text, actions);
            return row;
        }));
    }

    async function act(action, button) {
        const shift = current;
        if (!shift) return;
        if (action === 'noMiles') {
            button.disabled = true;
            if (await window.flexbuddyFinish.noMiles(shift, button)) {
                data = await load(shift.id).catch(() => data);
                current = data.shift;
                render();
            }
            return;
        }
        const from = trigger;
        close({restoreFocus: false});
        if (action === 'costs') startExpenseForBlock(shift);
        else if (action === 'payout') window.flexbuddyPayouts.open(from);
        else window.flexbuddyFinish.open(shift, from);
    }

    function close({restoreFocus = true} = {}) {
        el.modal.classList.add('is-hidden');
        document.body.classList.remove('modal-open');
        current = undefined;
        data = undefined;
        if (restoreFocus && trigger?.isConnected) trigger.focus();
        trigger = undefined;
    }

    /** Home's "Your last block" card: shown for the newest block worked today or yesterday, with what is left to check. */
    async function renderCard(shifts) {
        init();
        const today = toIsoDate(new Date());
        const block = lastBlock(shifts, window.flexbuddyCalendar.addDays(today, -1), today);
        el.card.hidden = !block;
        if (!block) return;
        const fmt = formats();
        el.cardTitle.textContent = `${block.station} · ${fmt.day(block.date)}`;
        el.cardButton.dataset.shiftId = block.id;
        el.cardSummary.textContent = '';
        try {
            const loaded = await load(block.id);
            if (el.cardButton.dataset.shiftId !== String(block.id)) return;
            el.cardSummary.textContent = summary(rows(loaded.shift, loaded, fmt));
        } catch {
            el.cardSummary.textContent = '';
        }
    }

    window.flexbuddyBlockReview = {rows, toCheck, summary, periodFor, lastBlock, open, renderCard};
})();
