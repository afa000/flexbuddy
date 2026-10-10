// Standing: the Flex standing the driver logs by hand, shown as a pill on the Forfeits tile and as a card on the
// Schedule screen with the last 90 days drawn as steps next to forfeits, late forfeits and cancellations. FlexBuddy
// never works standing out; it is only what was logged. Loaded before app.js and uses its shared helpers (apiFetch,
// csrfHeaders, showToast, showMessage, hideMessage, formatDate, toIsoDate, escapeHtml, showScheduleScreen) at call time.
(() => {
    const WINDOW_DAYS = 90;
    const LABELS = {FANTASTIC: 'js.standing.fantastic', GREAT: 'js.standing.great', FAIR: 'js.standing.fair', AT_RISK: 'js.standing.atRisk'};
    const EVENT_LABELS = {LATE_FORFEIT: 'js.common.lateForfeit', FORFEITED: 'js.standing.forfeit', CANCELLED: 'js.common.cancelledByAmazon'};
    const el = {
        pill: document.querySelector('#standingPill'),
        link: document.querySelector('#standingLink'),
        panel: document.querySelector('#standingPanel'),
        current: document.querySelector('#standingCurrent'),
        chart: document.querySelector('#standingChart'),
        form: document.querySelector('#standingForm'),
        date: document.querySelector('#standingDate'),
        levels: document.querySelector('#standingLevels'),
        note: document.querySelector('#standingNote'),
        hint: document.querySelector('#standingReplaceHint'),
        error: document.querySelector('#standingError'),
        save: document.querySelector('#saveStandingButton'),
        list: document.querySelector('#standingList')
    };
    if (!el.panel) return;
    let data = null;
    let level = null;

    const today = () => toIsoDate(new Date());
    const levelButtons = () => [...el.levels.querySelectorAll('button')];
    const offline = () => window.flexbuddyPwa?.isOffline() ?? false;

    async function load() {
        try {
            const response = await apiFetch(`/standing?days=${WINDOW_DAYS}`);
            if (!response.ok) throw new Error();
            data = await response.json();
        } catch {
            return;
        }
        renderTile();
        renderCard();
    }

    function setPill(pill, current) {
        pill.classList.toggle('is-hidden', !current);
        pill.dataset.level = current?.level ?? '';
        pill.textContent = current ? t(LABELS[current.level]) : '';
        pill.title = current ? t('js.standing.logged', formatDate(current.recordedOn)) : '';
    }

    function renderTile() {
        setPill(el.pill, data.current);
        el.link.textContent = data.current ? t('js.standing.updateStanding') : t('js.standing.logStanding');
    }

    function renderCard() {
        setPill(el.current, data.current);
        window.flexbuddyCharts.renderStanding(el.chart, data);
        if (!el.date.value) resetForm();
        updateHint();
        renderList();
    }

    function resetForm() {
        el.date.value = today();
        el.date.max = today();
        el.note.value = '';
        chooseLevel(null);
        hideMessage(el.error);
    }

    function chooseLevel(next) {
        level = next;
        levelButtons().forEach(button => {
            const chosen = button.dataset.level === next;
            button.classList.toggle('is-active', chosen);
            button.setAttribute('aria-pressed', String(chosen));
        });
        el.save.disabled = !next;
    }

    /** Says so when the chosen day already has an entry, since logging it again replaces that one. */
    function updateHint() {
        const existing = data?.entries.find(entry => entry.recordedOn === el.date.value);
        el.hint.textContent = existing ? t('js.standing.replaces', t(LABELS[existing.level]), formatDate(existing.recordedOn)) : '';
    }

    function renderList() {
        const rows = [
            ...data.entries.map(entry => ({date: entry.recordedOn, entry})),
            ...data.events.map(event => ({date: event.date, event}))
        ].sort((a, b) => b.date.localeCompare(a.date));
        if (!rows.length) {
            const empty = document.createElement('li');
            empty.className = 'history-empty';
            empty.textContent = t('js.standing.noneLogged');
            el.list.replaceChildren(empty);
            return;
        }
        el.list.replaceChildren(...rows.map(row => (row.entry ? entryRow(row.entry) : eventRow(row.event))));
    }

    function entryRow(entry) {
        const item = document.createElement('li');
        item.className = 'standing-row';
        item.innerHTML = `
            <div class="standing-row-main">
                <strong>${escapeHtml(formatDate(entry.recordedOn))}</strong>
                <span class="standing-pill" data-level="${escapeHtml(entry.level)}">${escapeHtml(t(LABELS[entry.level]))}</span>
                ${entry.note ? `<p>${escapeHtml(entry.note)}</p>` : ''}
            </div>
            <div class="standing-row-actions">
                <button class="text-button" type="button" data-action="edit" data-online-only>${escapeHtml(t('js.common.edit'))}</button>
                <button class="text-button danger-text-button" type="button" data-action="delete" data-online-only>${escapeHtml(t('js.common.delete'))}</button>
            </div>`;
        item.querySelectorAll('button').forEach(button => button.disabled = offline());
        item.querySelector('[data-action="edit"]').addEventListener('click', () => edit(entry));
        item.querySelector('[data-action="delete"]').addEventListener('click', event => remove(entry, event.currentTarget));
        return item;
    }

    function eventRow(event) {
        const item = document.createElement('li');
        item.className = 'standing-row standing-row-event';
        item.innerHTML = `
            <div class="standing-row-main">
                <strong>${escapeHtml(formatDate(event.date))}</strong>
                <span class="standing-event-label" data-kind="${escapeHtml(event.kind)}">${escapeHtml(t(EVENT_LABELS[event.kind]))}</span>
                <p>${escapeHtml(event.station)} · ${escapeHtml(formatTime(event.startTime))}</p>
            </div>`;
        return item;
    }

    /** Loads an entry into the form; saving it replaces that day's entry. */
    function edit(entry) {
        el.date.value = entry.recordedOn;
        el.note.value = entry.note ?? '';
        chooseLevel(entry.level);
        updateHint();
        hideMessage(el.error);
        el.form.scrollIntoView({block: 'center'});
        levelButtons().find(button => button.dataset.level === entry.level)?.focus();
    }

    async function put(date, body) {
        const response = await apiFetch(`/standing/${date}`, {
            method: 'PUT',
            headers: csrfHeaders({'Content-Type': 'application/json'}),
            body: JSON.stringify(body)
        });
        if (!response.ok) throw new Error(await response.text() || t('js.standing.saveFailed'));
    }

    async function save(event) {
        event.preventDefault();
        if (!level) {
            showMessage(el.error, t('js.standing.chooseOne'));
            return;
        }
        hideMessage(el.error);
        const date = el.date.value;
        el.save.disabled = true;
        try {
            await put(date, {level, note: el.note.value.trim() || null});
            showToast(t('js.standing.loggedTitle'), t('js.standing.loggedMessage', t(LABELS[level]), formatDate(date)));
            resetForm();
            await load();
        } catch (error) {
            showMessage(el.error, error.message || t('js.standing.saveFailed'));
            el.save.disabled = !level;
        }
    }

    async function remove(entry, button) {
        button.disabled = true;
        try {
            const response = await apiFetch(`/standing/${entry.recordedOn}`, {method: 'DELETE', headers: csrfHeaders()});
            if (!response.ok) throw new Error();
            await load();
            showToast(t('js.standing.removedTitle'), t('js.standing.removedMessage', t(LABELS[entry.level]), formatDate(entry.recordedOn)), {
                actionLabel: t('js.common.undo'),
                duration: 8000,
                onAction: async () => {
                    try {
                        await put(entry.recordedOn, {level: entry.level, note: entry.note});
                        await load();
                    } catch (error) {
                        showToast(t('js.standing.notRestoredTitle'), error.message || t('js.standing.notRestoredMessage'), {alert: true});
                    }
                }
            });
        } catch {
            showToast(t('js.standing.notRemovedTitle'), t('js.standing.notRemovedMessage'), {alert: true});
            button.disabled = false;
        }
    }

    levelButtons().forEach(button => button.addEventListener('click', () => chooseLevel(button.dataset.level)));
    el.date.addEventListener('input', () => data && updateHint());
    el.form.addEventListener('submit', save);
    el.link.addEventListener('click', async () => {
        // The schedule loads above the card, so scrolling waits until it has settled or the card would be pushed away.
        await showScheduleScreen(false);
        el.panel.scrollIntoView({block: 'start'});
        levelButtons()[0]?.focus({preventScroll: true});
    });

    window.flexbuddyStanding = {load, focusForm: () => levelButtons()[0]?.focus()};
})();
