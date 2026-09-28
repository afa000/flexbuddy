// Finish block sheet: records when a block started and finished and how far it went, and marks a scheduled block
// completed, in one save. Loaded before app.js and uses its shared helpers (apiFetch, csrfHeaders, showToast,
// loadDashboard, ...) at call time.
(() => {
    const LATE_FINISH_MINUTES = 180;
    const SNOOZE_KEY = 'flexbuddy-miles-snoozed';
    const SNOOZE_MS = 24 * 60 * 60000;
    let el;
    let shift;
    let trigger;
    let latest;
    let useOdometer = true;

    function init() {
        if (el) return;
        el = {
            modal: document.querySelector('#finishModal'),
            label: document.querySelector('#finishLabel'),
            title: document.querySelector('#finishTitle'),
            description: document.querySelector('#finishDescription'),
            form: document.querySelector('#finishForm'),
            start: document.querySelector('#finishStart'),
            end: document.querySelector('#finishEnd'),
            odometer: document.querySelector('#finishOdometer'),
            odometerStart: document.querySelector('#finishOdometerStart'),
            odometerEnd: document.querySelector('#finishOdometerEnd'),
            odometerHint: document.querySelector('#finishOdometerHint'),
            milesField: document.querySelector('#finishMilesField'),
            miles: document.querySelector('#finishMiles'),
            mode: document.querySelector('#finishModeButton'),
            summary: document.querySelector('#finishSummary'),
            route: document.querySelector('#finishRoute'),
            routeSummary: document.querySelector('#finishRouteSummary'),
            stops: document.querySelector('#finishStops'),
            packages: document.querySelector('#finishPackages'),
            returns: document.querySelector('#finishReturns'),
            payField: document.querySelector('#finishPayField'),
            pay: document.querySelector('#finishPay'),
            error: document.querySelector('#finishError'),
            save: document.querySelector('#saveFinishButton')
        };
        document.querySelector('#closeFinishButton').addEventListener('click', close);
        document.querySelector('#cancelFinishButton').addEventListener('click', close);
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
        [el.start, el.end, el.odometerStart, el.odometerEnd, el.miles, el.stops, el.packages, el.returns]
            .forEach(input => input.addEventListener('input', updateSummary));
        el.mode.addEventListener('click', () => {
            setMode(!useOdometer);
            (useOdometer ? el.odometerEnd : el.miles).focus();
        });
        el.form.addEventListener('submit', save);
    }

    function completing() {
        return shift.status === 'SCHEDULED';
    }

    /** Opens the sheet for a scheduled block being finished, or for a completed block's details. */
    async function open(target, from) {
        init();
        shift = target;
        trigger = from;
        latest = await latestReading();
        el.label.textContent = completing() ? 'FINISH BLOCK' : 'BLOCK DETAILS';
        el.title.textContent = shift.station;
        el.description.textContent = `${formatDate(shift.date)} · ${formatTime(shift.startTime)}–${formatTime(shift.endTime)} scheduled`;
        el.start.value = trimTime(shift.details?.actualStart) || trimTime(shift.startTime);
        el.end.value = trimTime(shift.details?.actualEnd) || defaultEnd();
        const details = shift.details ?? {};
        el.odometerStart.value = details.odometerStart ?? latest?.reading ?? '';
        el.odometerEnd.value = details.odometerEnd ?? '';
        el.miles.value = shift.miles ?? '';
        el.stops.value = details.stops ?? '';
        el.packages.value = details.packages ?? '';
        el.returns.value = details.returns ?? '';
        el.route.open = details.stops != null || details.packages != null || details.returns != null;
        // Drivers who log readings keep getting the odometer; everyone else starts with a plain miles field.
        setMode(details.odometerStart != null || details.odometerEnd != null || (latest != null && shift.miles == null));
        el.payField.classList.toggle('is-hidden', !completing());
        el.pay.value = shift.basePay ?? '';
        el.save.querySelector('span').textContent = completing() ? 'Finish block' : 'Save details';
        el.save.disabled = false;
        hideMessage(el.error);
        updateSummary();
        el.modal.classList.remove('is-hidden');
        document.body.classList.add('modal-open');
        (useOdometer ? el.odometerEnd : el.miles).focus();
    }

    function close() {
        el.modal.classList.add('is-hidden');
        document.body.classList.remove('modal-open');
        shift = undefined;
        if (trigger?.isConnected) trigger.focus();
        trigger = undefined;
    }

    /** The last reading from a block before this one, or null when there is none or it cannot be loaded. */
    async function latestReading() {
        try {
            const before = `${shift.date}T${trimTime(shift.startTime)}`;
            const response = await apiFetch(`/shifts/odometer/latest?before=${encodeURIComponent(before)}`);
            return response.status === 200 ? await response.json() : null;
        } catch {
            return null;
        }
    }

    function setMode(odometer) {
        useOdometer = odometer;
        el.odometer.classList.toggle('is-hidden', !odometer);
        el.milesField.classList.toggle('is-hidden', odometer);
        el.mode.textContent = odometer ? 'Enter miles instead' : 'Use the odometer';
        updateSummary();
    }

    /** The current time while the block could still be running, otherwise its scheduled end. */
    function defaultEnd() {
        const start = parseLocalDate(shift.date);
        const [hours, minutes] = shift.startTime.split(':').map(Number);
        start.setHours(hours, minutes, 0, 0);
        const scheduledEnd = start.getTime() + shift.timeWorked * 60000;
        const now = new Date();
        if (now >= start && now.getTime() <= scheduledEnd + LATE_FINISH_MINUTES * 60000) {
            return `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
        }
        return trimTime(shift.endTime);
    }

    function odometerMiles() {
        if (el.odometerStart.value === '' || el.odometerEnd.value === '') return null;
        return Number(el.odometerEnd.value) - Number(el.odometerStart.value);
    }

    function updateSummary() {
        if (!shift) return;
        const parts = [];
        const minutes = minutesBetween(el.start.value, el.end.value);
        if (minutes) {
            const early = shift.timeWorked - minutes;
            const pace = early > 0 ? `${formatMinutes(early)} early` : early < 0 ? `${formatMinutes(-early)} over` : 'right on schedule';
            parts.push(`${formatMinutes(minutes)} on the clock · ${pace}`);
        }
        const miles = useOdometer ? odometerMiles() : (el.miles.value === '' ? null : Number(el.miles.value));
        if (miles !== null && miles >= 0) parts.push(`${miles.toFixed(1)} mi`);
        el.summary.textContent = parts.join(' · ') || 'Enter both times to see how long the block took.';
        el.routeSummary.textContent = routeSummary(count(el.stops), count(el.returns), minutes || shift.timeWorked) || 'Optional';
        updateOdometerHint();
    }

    function updateOdometerHint() {
        if (!latest) {
            el.odometerHint.textContent = '';
            return;
        }
        const from = `${formatDate(latest.date)} · ${latest.station}`;
        const start = el.odometerStart.value === '' ? null : Number(el.odometerStart.value);
        el.odometerHint.textContent = start !== null && start < Number(latest.reading)
            ? `Lower than your last reading, ${latest.reading} on ${from}. Different car?`
            : `Last reading ${latest.reading}, from ${from}.`;
    }

    function count(input) {
        return input.value === '' ? null : Number(input.value);
    }

    function problem() {
        if (el.end.value && !el.start.value) return 'Enter when the block started.';
        if (el.start.value && el.start.value === el.end.value) return 'The finish time must be after the start time.';
        if (useOdometer && odometerMiles() !== null && odometerMiles() < 0) {
            return 'The odometer end reading must be at least the start reading.';
        }
        if (count(el.returns) !== null && count(el.packages) !== null && count(el.returns) > count(el.packages)) {
            return 'Returns cannot be more than the packages carried.';
        }
        if (completing() && !(Number(el.pay.value) > 0)) return 'Enter the base pay for this block.';
        return null;
    }

    async function save(event) {
        event.preventDefault();
        const message = problem();
        if (message) {
            showMessage(el.error, message);
            return;
        }
        hideMessage(el.error);
        const reading = input => useOdometer && input.value !== '' ? Number(input.value) : null;
        const body = {
            status: 'COMPLETED',
            details: {
                actualStart: el.start.value || null,
                actualEnd: el.end.value || null,
                odometerStart: reading(el.odometerStart),
                odometerEnd: reading(el.odometerEnd),
                stops: count(el.stops),
                packages: count(el.packages),
                returns: count(el.returns)
            }
        };
        // With both readings the server works out the miles; a typed miles value is sent as is.
        if (!useOdometer && el.miles.value !== '') body.miles = Number(el.miles.value);
        if (completing()) body.basePay = Number(el.pay.value);
        const finished = shift;
        el.save.disabled = true;
        try {
            const response = await apiFetch(`/shifts/${finished.id}/status`, {
                method: 'PATCH',
                headers: csrfHeaders({'Content-Type': 'application/json'}),
                body: JSON.stringify(body)
            });
            if (!response.ok) throw new Error(await response.text() || 'The block could not be saved.');
            const wasScheduled = completing();
            close();
            showToast(wasScheduled ? 'Block finished' : 'Block details saved',
                `${finished.station} on ${formatDate(finished.date)} is up to date.`);
            await loadDashboard();
        } catch (error) {
            showMessage(el.error, error.message || 'The block could not be saved.');
            el.save.disabled = false;
        }
    }

    /** Opens the sheet for a shift named in a link, such as the one in the log-your-miles push notification. */
    async function openById(id, from) {
        try {
            const response = await apiFetch(`/shifts/${encodeURIComponent(id)}`);
            if (!response.ok) throw new Error();
            const target = await response.json();
            if (target.status === 'SCHEDULED' || target.status === 'COMPLETED') await open(target, from);
        } catch {
            showToast('Block not found', 'That block could not be opened. It may have been deleted.', {alert: true});
        }
    }

    function snoozed() {
        try {
            const saved = JSON.parse(localStorage.getItem(SNOOZE_KEY) || '{}');
            const now = Date.now();
            return Object.fromEntries(Object.entries(saved).filter(([, until]) => until > now));
        } catch {
            return {};
        }
    }

    function snooze(id) {
        try {
            localStorage.setItem(SNOOZE_KEY, JSON.stringify({...snoozed(), [id]: Date.now() + SNOOZE_MS}));
        } catch {
            // Without storage the entry simply comes back on the next load.
        }
    }

    /** The dashboard strip of recent completed blocks with no miles, minus any the driver put off for a day. */
    async function loadMissing() {
        const section = document.querySelector('#missingMiles');
        if (!section) return;
        let shifts = [];
        try {
            const response = await apiFetch('/shifts/missing-miles?days=7');
            if (response.ok) shifts = await response.json();
        } catch {
            // The strip is a reminder only; when it cannot load it stays hidden.
        }
        const skipped = snoozed();
        const visible = shifts.filter(shift => !skipped[shift.id]);
        section.hidden = visible.length === 0;
        document.querySelector('#missingMilesCount').textContent =
            `${visible.length} ${visible.length === 1 ? 'block' : 'blocks'} this week`;
        const offline = window.flexbuddyPwa?.isOffline() ?? false;
        document.querySelector('#missingMilesList').replaceChildren(...visible.map(shift => {
            const row = document.createElement('article');
            row.className = 'missing-row';
            row.innerHTML = `
                <p><strong>${escapeHtml(formatDate(shift.date))} · ${escapeHtml(shift.station)}</strong>
                    <span>${escapeHtml(formatTime(shift.startTime))}–${escapeHtml(formatTime(shift.endTime))}</span></p>
                <div class="confirm-actions">
                    <button class="primary-button compact-button" type="button" data-action="add" data-online-only>Add miles</button>
                    <button class="secondary-button compact-button" type="button" data-action="none" data-online-only>No miles</button>
                    <button class="text-button" type="button" data-action="later">Not now</button>
                </div>`;
            row.querySelectorAll('[data-online-only]').forEach(button => button.disabled = offline);
            row.querySelector('[data-action="add"]').addEventListener('click', event => open(shift, event.currentTarget));
            row.querySelector('[data-action="none"]').addEventListener('click', event => noMiles(shift, event.currentTarget));
            row.querySelector('[data-action="later"]').addEventListener('click', () => {
                snooze(shift.id);
                loadMissing();
            });
            return row;
        }));
    }

    /** Records that the block had no driving to log, which also stops the reminders for it. */
    async function noMiles(target, button) {
        button.disabled = true;
        try {
            const response = await apiFetch(`/shifts/${target.id}/status`, {
                method: 'PATCH',
                headers: csrfHeaders({'Content-Type': 'application/json'}),
                body: JSON.stringify({status: 'COMPLETED', miles: 0})
            });
            if (!response.ok) throw new Error(await response.text() || 'The block could not be updated.');
            showToast('Saved as no miles', `${target.station} on ${formatDate(target.date)} will not ask again.`);
            await loadDashboard();
        } catch (error) {
            showToast('Not saved', error.message || 'The block could not be updated.', {alert: true});
            button.disabled = false;
        }
    }

    window.flexbuddyFinish = {open, openById, loadMissing};
})();
