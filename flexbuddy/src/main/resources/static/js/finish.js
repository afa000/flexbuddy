// Finish block sheet: records when a block started and finished, and marks a scheduled block completed, in one save.
// Loaded before app.js and uses its shared helpers (apiFetch, csrfHeaders, showToast, loadDashboard, ...) at call time.
(() => {
    const LATE_FINISH_MINUTES = 180;
    let el;
    let shift;
    let trigger;

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
            summary: document.querySelector('#finishSummary'),
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
        [el.start, el.end].forEach(input => input.addEventListener('input', updateSummary));
        el.form.addEventListener('submit', save);
    }

    function completing() {
        return shift.status === 'SCHEDULED';
    }

    /** Opens the sheet for a scheduled block being finished, or for a completed block's details. */
    function open(target, from) {
        init();
        shift = target;
        trigger = from;
        el.label.textContent = completing() ? 'FINISH BLOCK' : 'BLOCK DETAILS';
        el.title.textContent = shift.station;
        el.description.textContent = `${formatDate(shift.date)} · ${formatTime(shift.startTime)}–${formatTime(shift.endTime)} scheduled`;
        el.start.value = trimTime(shift.details?.actualStart) || trimTime(shift.startTime);
        el.end.value = trimTime(shift.details?.actualEnd) || defaultEnd();
        el.payField.classList.toggle('is-hidden', !completing());
        el.pay.value = shift.basePay ?? '';
        el.save.querySelector('span').textContent = completing() ? 'Finish block' : 'Save details';
        el.save.disabled = false;
        hideMessage(el.error);
        updateSummary();
        el.modal.classList.remove('is-hidden');
        document.body.classList.add('modal-open');
        el.end.focus();
    }

    function close() {
        el.modal.classList.add('is-hidden');
        document.body.classList.remove('modal-open');
        shift = undefined;
        if (trigger?.isConnected) trigger.focus();
        trigger = undefined;
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

    function clockedMinutes() {
        if (!el.start.value || !el.end.value) return null;
        const [startHours, startMinutes] = el.start.value.split(':').map(Number);
        const [endHours, endMinutes] = el.end.value.split(':').map(Number);
        const minutes = (endHours * 60 + endMinutes) - (startHours * 60 + startMinutes);
        return minutes < 0 ? minutes + 1440 : minutes;
    }

    function updateSummary() {
        const minutes = clockedMinutes();
        if (minutes === null || minutes === 0) {
            el.summary.textContent = 'Enter both times to see how long the block took.';
            return;
        }
        const early = shift.timeWorked - minutes;
        const pace = early > 0 ? `${formatMinutes(early)} early` : early < 0 ? `${formatMinutes(-early)} over` : 'right on schedule';
        el.summary.textContent = `${formatMinutes(minutes)} on the clock · ${pace}`;
    }

    function problem() {
        if (el.end.value && !el.start.value) return 'Enter when the block started.';
        if (el.start.value && el.start.value === el.end.value) return 'The finish time must be after the start time.';
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
        const body = {
            status: 'COMPLETED',
            details: {actualStart: el.start.value || null, actualEnd: el.end.value || null}
        };
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

    window.flexbuddyFinish = {open};
})();
