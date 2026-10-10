// Schedule screen: next block, blocks awaiting confirmation, the upcoming list, the week strip, and the month grid.
// Loaded before app.js and uses its shared helpers (apiFetch, formatMoney, openEditModal, ...) at call time.
(() => {
    const UPCOMING_DAYS = 14;
    // Matches the server: a block can be started from 2 hours before its scheduled start.
    const EARLIEST_START_MS = 2 * 60 * 60000;
    // The deadline turns amber this close to the forfeit cutoff.
    const DEADLINE_WARNING_MS = 15 * 60000;
    const PENCIL = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="m4 20 4.2-1 10.9-10.9a2.1 2.1 0 0 0-3-3L5.2 16 4 20Zm10.5-13.5 3 3"/></svg>';
    const calendar = window.flexbuddyCalendar;
    const state = {upcoming: null, fetchedAt: 0, month: null, monthDays: [], selected: null, focusDate: null, countdownTimers: new Map()};
    let el;

    function init() {
        if (el) return;
        el = {
            screen: document.querySelector('#scheduleScreen'),
            error: document.querySelector('#scheduleError'),
            confirmStrip: document.querySelector('#confirmStrip'),
            confirmList: document.querySelector('#confirmList'),
            nextUp: document.querySelector('#nextUpCard'),
            homeNext: document.querySelector('#homeNextBlock'),
            upcomingList: document.querySelector('#upcomingList'),
            weekStrip: document.querySelector('#weekStrip'),
            grid: document.querySelector('#calendarGrid'),
            monthLabel: document.querySelector('#calendarMonthLabel'),
            dayPanel: document.querySelector('#dayPanel'),
            badge: document.querySelector('#scheduleBadge')
        };
        document.querySelector('#prevMonthButton').addEventListener('click', () => changeMonth(-1));
        document.querySelector('#nextMonthButton').addEventListener('click', () => changeMonth(1));
        document.querySelector('#todayMonthButton').addEventListener('click', () => selectDay(today()));
        document.querySelector('#addScheduledShiftButton').addEventListener('click',
            event => addScheduled(state.selected || today(), event.currentTarget));
        document.addEventListener('flexbuddy:online', () => refresh());
    }

    function isVisible() {
        return !el.screen.classList.contains('is-hidden');
    }

    /** "Today" in the driver's saved time zone once the server has answered, the browser's date before that. */
    function today() {
        return state.upcoming?.now?.slice(0, 10) || calendar.toIso(new Date());
    }

    async function show() {
        init();
        await refresh();
    }

    async function refresh() {
        init();
        await loadUpcoming();
        if (!isVisible()) return;
        await Promise.all([loadMonth(state.month || today().slice(0, 7)), loadWeekStrip()]);
        if (state.selected) await loadDay(state.selected);
    }

    async function loadUpcoming() {
        try {
            const response = await apiFetch(`/shifts/upcoming?days=${UPCOMING_DAYS}`);
            if (!response.ok) throw new Error(await response.text() || t('js.schedule.loadFailed'));
            state.upcoming = await response.json();
            state.fetchedAt = Date.now();
            hideMessage(el.error);
            renderHints();
            // Home shows the next block too, so it is drawn whether or not the Schedule screen is open.
            if (el.homeNext) renderNextInto(el.homeNext);
            if (isVisible()) {
                renderConfirmStrip();
                renderNextInto(el.nextUp);
                renderUpcomingList();
            }
        } catch (error) {
            if (isVisible()) showMessage(el.error, error.message || t('js.schedule.loadFailed'));
        }
    }

    function renderHints() {
        const {needsConfirmation} = state.upcoming;
        if (el.badge) {
            el.badge.textContent = needsConfirmation.length;
            el.badge.classList.toggle('is-hidden', needsConfirmation.length === 0);
            el.badge.setAttribute('aria-label', t('js.schedule.toConfirm', needsConfirmation.length));
        }
    }

    function renderConfirmStrip() {
        const shifts = state.upcoming.needsConfirmation;
        el.confirmStrip.classList.toggle('is-hidden', shifts.length === 0);
        el.confirmList.replaceChildren(...shifts.map(shift => {
            const row = document.createElement('article');
            row.className = 'confirm-row';
            row.innerHTML = `
                <p><strong>${escapeHtml(dayLabel(shift.date))} · ${escapeHtml(shift.station)} ${timeRange(shift)}</strong>
                    <span>${escapeHtml(t('js.schedule.didYouWork'))}</span></p>
                <div class="confirm-actions">
                    <button class="primary-button compact-button" type="button" data-action="completed">${escapeHtml(t('js.common.completed'))}</button>
                    <button class="secondary-button compact-button" type="button" data-action="cancelled">${escapeHtml(t('js.common.cancelled'))}</button>
                    <button class="danger-text-button" type="button" data-action="forfeited">${escapeHtml(t('js.common.forfeited'))}</button>
                </div>`;
            bindActions(row, shift);
            return row;
        }));
    }

    /**
     * Draws the next block into a container: the Schedule screen's card and Home's. Each container keeps its own
     * countdown timer, so drawing one never stops the other, and a timer does nothing while its card is out of sight.
     */
    function renderNextInto(container) {
        const shift = state.upcoming.next;
        window.clearInterval(state.countdownTimers.get(container));
        state.countdownTimers.delete(container);
        if (!shift) {
            container.innerHTML = `
                <div class="next-up-main">
                    <p class="step-label">${escapeHtml(t('js.schedule.nextUp'))}</p>
                    <h2>${escapeHtml(t('js.schedule.noUpcoming'))}</h2>
                    <p class="next-up-when">${escapeHtml(t('js.schedule.noUpcomingHint'))}</p>
                </div>`;
            return;
        }
        const started = Boolean(shift.details?.actualStart);
        const current = started || nowMs() >= localMs(shift.date, shift.startTime) - EARLIEST_START_MS;
        const blockActions = !current ? ''
            : started ? `<button class="primary-button compact-button" type="button" data-action="finish">${escapeHtml(t('js.finish.finishBlock'))}</button>`
            : `<button class="primary-button compact-button" type="button" data-action="start">${escapeHtml(t('js.quickActions.startBlock'))}</button>
               <button class="secondary-button compact-button" type="button" data-action="finish">${escapeHtml(t('js.finish.finishBlock'))}</button>`;
        container.innerHTML = `
            <div class="next-up-main">
                <p class="step-label">${escapeHtml(started ? t('js.schedule.inProgress') : t('js.schedule.nextUp'))}</p>
                <h2>${escapeHtml(shift.station)}</h2>
                <p class="next-up-when">${escapeHtml(dayLabel(shift.date))} · ${timeRange(shift)}</p>
                <p class="next-up-countdown"></p>
                <p class="forfeit-deadline"></p>
            </div>
            <div class="next-up-pay"><strong>${formatMoney(shift.basePay)}</strong><span>${escapeHtml(t('js.schedule.offeredFor', formatMinutes(shift.timeWorked)))}</span></div>
            <div class="next-up-actions">
                ${blockActions}
                <a class="secondary-button compact-button" href="/shifts/${shift.id}.ics" download>${escapeHtml(t('js.schedule.addToCalendar'))}</a>
                <button class="secondary-button compact-button" type="button" data-action="cancelled">${escapeHtml(t('js.schedule.markCancelled'))}</button>
                <button class="danger-text-button" type="button" data-action="forfeited">${escapeHtml(t('js.schedule.forfeit'))}</button>
                <button class="text-button" type="button" data-action="edit">${escapeHtml(t('js.common.edit'))}</button>
            </div>`;
        bindActions(container, shift);
        const countdown = container.querySelector('.next-up-countdown');
        const deadline = container.querySelector('.forfeit-deadline');
        const update = () => {
            countdown.textContent = countdownText(shift);
            const {text, state: deadlineState} = deadlineText(shift);
            deadline.textContent = text;
            deadline.dataset.state = deadlineState;
            deadline.hidden = !text;
        };
        // Drawn once even when hidden, so the text is there the moment the screen opens; the timer only ticks in view.
        update();
        state.countdownTimers.set(container, window.setInterval(() => {
            if (container.getClientRects().length) update();
        }, 30000));
    }

    function renderUpcomingList() {
        const {days, weeks, conflicts} = state.upcoming;
        const overlapping = new Set(conflicts.flatMap(conflict => [conflict.firstShiftId, conflict.secondShiftId]));
        const weekTotals = new Map(weeks.map(week => [week.weekStart, week]));
        el.upcomingList.replaceChildren();
        if (!days.length) {
            el.upcomingList.innerHTML = `<p class="history-empty">${escapeHtml(t('js.schedule.nothingScheduled', UPCOMING_DAYS))}</p>`;
            return;
        }
        days.forEach((day, index) => {
            const section = document.createElement('section');
            section.className = 'upcoming-day';
            section.innerHTML = `<h3><span>${escapeHtml(longDay(day.date))}</span><small>${formatMinutes(day.plannedMinutes)} · ${formatMoney(day.expectedPay)}</small></h3>`;
            day.shifts.forEach(shift => {
                const row = document.createElement('article');
                row.className = 'upcoming-row';
                row.innerHTML = `
                    <div><strong>${timeRange(shift)}</strong>
                        <span>${escapeHtml(shift.station)}${shift.forfeitDeadline ? ` · ${escapeHtml(t('js.schedule.forfeitBy', formatTime(shift.forfeitDeadline.slice(11, 16))))}` : ''}${overlapping.has(shift.id) ? ` <small class="status-badge status-conflict">${escapeHtml(t('js.schedule.overlaps'))}</small>` : ''}</span></div>
                    <strong class="upcoming-pay">${formatMoney(shift.basePay)}</strong>
                    <button class="edit-shift-button" type="button" data-action="edit">${PENCIL}</button>`;
                row.querySelector('button').setAttribute('aria-label', t('js.schedule.editBlockOn', shift.station, formatDate(shift.date)));
                bindActions(row, shift);
                section.append(row);
            });
            el.upcomingList.append(section);

            const week = calendar.weekStart(day.date);
            const nextWeek = days[index + 1] ? calendar.weekStart(days[index + 1].date) : null;
            if (week !== nextWeek && weekTotals.has(week)) {
                const total = weekTotals.get(week);
                const footer = document.createElement('p');
                footer.className = 'week-footer';
                footer.textContent = t('js.schedule.weekFooter', shortDay(week), tn(total.shifts, 'js.common.blocks'),
                    formatMinutes(total.plannedMinutes), formatMoney(total.expectedPay));
                el.upcomingList.append(footer);
            }
        });
    }

    async function loadMonth(month) {
        state.month = month;
        const [year, number] = month.split('-').map(Number);
        el.monthLabel.textContent = new Date(year, number - 1, 1).toLocaleDateString(appLocale(), {month: 'long', year: 'numeric'});
        try {
            const response = await apiFetch(`/shifts/calendar?month=${month}`);
            if (!response.ok) throw new Error(await response.text() || t('js.schedule.calendarFailed'));
            const result = await response.json();
            if (state.month !== month) return;
            state.monthDays = result.days;
            renderGrid();
        } catch (error) {
            showMessage(el.error, error.message || t('js.schedule.calendarFailed'));
        }
    }

    function renderGrid() {
        calendar.render(el.grid, {
            month: state.month,
            days: state.monthDays,
            today: today(),
            selected: state.selected,
            focusDate: state.focusDate,
            formatMoney,
            onSelect: date => selectDay(date, true),
            onMonthChange: (month, focusDate) => {
                state.focusDate = focusDate;
                loadMonth(month);
            }
        });
        state.focusDate = null;
    }

    function changeMonth(delta) {
        loadMonth(calendar.addMonths(`${state.month || today().slice(0, 7)}-01`, delta).slice(0, 7));
    }

    function selectDay(date, keepFocus = false) {
        state.selected = date;
        if (keepFocus) state.focusDate = date;
        if (date.slice(0, 7) !== state.month) loadMonth(date.slice(0, 7));
        else renderGrid();
        loadDay(date);
    }

    async function loadDay(date) {
        el.dayPanel.innerHTML = `<p class="day-panel-empty">${escapeHtml(t('js.common.loading'))}</p>`;
        try {
            const params = new URLSearchParams({from: date, to: date, status: 'all', sort: 'date', dir: 'asc'});
            const response = await apiFetch(`/shifts?${params}`);
            if (!response.ok) throw new Error(await response.text() || t('js.schedule.dayFailed'));
            renderDay(date, await response.json());
        } catch (error) {
            el.dayPanel.innerHTML = `<p class="day-panel-empty">${escapeHtml(error.message || t('js.schedule.dayFailed'))}</p>`;
        }
    }

    function renderDay(date, shifts) {
        if (state.selected !== date) return;
        el.dayPanel.replaceChildren();
        const heading = document.createElement('div');
        heading.className = 'day-panel-heading';
        heading.innerHTML = `<h3>${escapeHtml(longDay(date))}</h3><button class="text-button" type="button">${escapeHtml(t('js.common.addScheduledShift'))}</button>`;
        const addButton = heading.querySelector('button');
        addButton.addEventListener('click', () => addScheduled(date, addButton));
        el.dayPanel.append(heading);
        if (!shifts.length) {
            const empty = document.createElement('p');
            empty.className = 'day-panel-empty';
            empty.textContent = t('js.schedule.noShiftsToday');
            el.dayPanel.append(empty);
            return;
        }
        shifts.forEach(shift => {
            const row = document.createElement('article');
            row.className = 'day-shift';
            row.innerHTML = `
                <span class="status-dot status-dot-${shift.status.toLowerCase()}" aria-hidden="true"></span>
                <div><strong>${escapeHtml(shift.station)} · ${timeRange(shift)}</strong><span>${escapeHtml(statusSummary(shift))}</span></div>
                <button class="edit-shift-button" type="button" data-action="edit">${PENCIL}</button>`;
            row.querySelector('button').setAttribute('aria-label', t('js.schedule.editShiftOn', shift.station, formatDate(shift.date)));
            bindActions(row, shift);
            el.dayPanel.append(row);
        });
    }

    async function loadWeekStrip() {
        const start = calendar.weekStart(today());
        try {
            const params = new URLSearchParams({from: start, to: calendar.addDays(start, 6), status: 'all', sort: 'date', dir: 'asc'});
            const response = await apiFetch(`/shifts?${params}`);
            if (!response.ok) throw new Error();
            renderWeekStrip(start, await response.json());
        } catch {
            el.weekStrip.replaceChildren();
        }
    }

    function renderWeekStrip(start, shifts) {
        el.weekStrip.replaceChildren(...Array.from({length: 7}, (_, offset) => {
            const date = calendar.addDays(start, offset);
            const dayShifts = shifts.filter(shift => shift.date === date);
            const parsed = calendar.parseIso(date);
            const button = document.createElement('button');
            button.type = 'button';
            button.className = 'week-strip-day';
            button.classList.toggle('is-today', date === today());
            button.innerHTML = `
                <small>${escapeHtml(parsed.toLocaleDateString(appLocale(), {weekday: 'short'}))}</small>
                <strong>${parsed.getDate()}</strong>
                <span>${dayShifts.length ? escapeHtml(formatTime(dayShifts[0].startTime)) : '—'}</span>
                <span class="calendar-dots" aria-hidden="true">${dayShifts.slice(0, 3)
                    .map(shift => `<i class="status-dot status-dot-${shift.status.toLowerCase()}"></i>`).join('')}</span>`;
            button.setAttribute('aria-label',
                t('js.schedule.dayShiftCount', longDay(date), tn(dayShifts.length, 'js.schedule.shifts')));
            button.addEventListener('click', () => selectDay(date));
            return button;
        }));
    }

    function bindActions(container, shift) {
        container.querySelectorAll('[data-action]').forEach(button => {
            const action = button.dataset.action;
            // Finishing a block is queued when there is no connection; the other status changes still need one.
            if (action !== 'edit' && action !== 'completed' && action !== 'finish') {
                button.dataset.onlineOnly = '';
                button.disabled = window.flexbuddyPwa?.isOffline() ?? false;
            }
            button.addEventListener('click', () => {
                if (action === 'completed' || action === 'finish') window.flexbuddyFinish.open(shift, button);
                else if (action === 'start') startBlock(shift, button);
                else if (action === 'cancelled') openEditModal(shift, button, {status: 'CANCELLED', focusPay: true});
                else if (action === 'forfeited') confirmForfeit(shift);
                else openEditModal(shift, button);
            });
        });
    }

    async function startBlock(shift, button) {
        if (button) button.disabled = true;
        try {
            const response = await apiFetch(`/shifts/${shift.id}/start`, {method: 'POST', headers: csrfHeaders()});
            if (!response.ok) throw new Error(await response.text() || t('js.schedule.startFailed'));
            const started = await response.json();
            showToast(t('js.schedule.startedTitle'), t('js.schedule.startedMessage', formatTime(started.details.actualStart)));
            await loadDashboard();
        } catch (error) {
            showToast(t('js.schedule.notStartedTitle'), error.message || t('js.schedule.startFailed'), {alert: true});
            if (button) button.disabled = false;
        }
    }

    function confirmForfeit(shift) {
        if (insideForfeitWindow(shift)) {
            openConfirm(t('js.common.lateForfeit'),
                t('js.schedule.lateForfeitMessage', shift.station, formatDate(shift.date)),
                () => changeStatus(shift, {status: 'FORFEITED'}, t('js.schedule.forfeitedLateTitle')),
                t('js.schedule.forfeitAnyway'));
            return;
        }
        openConfirm(t('js.schedule.forfeitThisBlock'),
            t('js.schedule.forfeitMessage', shift.station, formatDate(shift.date)),
            () => changeStatus(shift, {status: 'FORFEITED'}, t('js.schedule.forfeitedTitle')),
            t('js.schedule.forfeitBlock'));
    }

    function deadlineMs(shift) {
        if (!shift.forfeitDeadline) return null;
        const [date, time] = shift.forfeitDeadline.split('T');
        return localMs(date, time);
    }

    function insideForfeitWindow(shift) {
        const deadline = deadlineMs(shift);
        return deadline !== null && state.upcoming && nowMs() > deadline;
    }

    /** "Forfeit deadline 2:30 PM · in 1 h 10 m", amber in the last 15 minutes and red once it has passed. */
    function deadlineText(shift) {
        const deadline = deadlineMs(shift);
        if (deadline === null || shift.details?.actualStart) return {text: '', state: ''};
        const at = formatTime(shift.forfeitDeadline.slice(11, 16));
        const remaining = deadline - nowMs();
        if (remaining < 0) return {text: t('js.schedule.insideWindow', at), state: 'passed'};
        return {
            text: t('js.schedule.deadlineIn', at, duration(remaining)),
            state: remaining <= DEADLINE_WARNING_MS ? 'soon' : ''
        };
    }

    async function changeStatus(shift, body, title) {
        const response = await apiFetch(`/shifts/${shift.id}/status`, {
            method: 'PATCH',
            headers: csrfHeaders({'Content-Type': 'application/json'}),
            body: JSON.stringify(body)
        });
        if (!response.ok) throw new Error(await response.text() || t('js.schedule.updateFailed'));
        showToast(title, t('js.schedule.upToDate', shift.station, formatDate(shift.date)));
        await loadDashboard();
        return response.json();
    }

    /**
     * The next block when the server would let it start now: scheduled, not started, from two hours before its start
     * until its scheduled end, by the schedule's own clock. Null when there is no such block or the schedule has not
     * loaded yet.
     */
    function startable() {
        const shift = state.upcoming?.next;
        if (!shift || shift.status !== 'SCHEDULED' || shift.details?.actualStart) return null;
        const start = localMs(shift.date, shift.startTime);
        const now = nowMs();
        return now >= start - EARLIEST_START_MS && now <= start + shift.timeWorked * 60000 ? shift : null;
    }

    function addScheduled(date, trigger) {
        openEditModal({id: null, station: '', date, startTime: '', endTime: '', basePay: '', tips: 0, miles: null,
            status: 'SCHEDULED'}, trigger, {status: 'SCHEDULED'});
    }

    function countdownText(shift) {
        const start = localMs(shift.date, shift.startTime);
        const end = start + shift.timeWorked * 60000;
        const now = nowMs();
        if (shift.details?.actualStart) {
            const ends = now < end ? t('js.schedule.scheduledToEndIn', duration(end - now)) : t('js.schedule.pastScheduledEnd');
            return `${t('js.schedule.startedAt', formatTime(shift.details.actualStart))} · ${ends}`;
        }
        if (now < start) return t('js.schedule.startsIn', duration(start - now));
        if (now < end) return t('js.schedule.inProgressEndsIn', duration(end - now));
        return t('js.schedule.finishedConfirm');
    }

    /** The current time in the driver's zone, from the server's clock when the schedule loaded. */
    function nowMs() {
        const [nowDate, nowTime] = state.upcoming.now.split('T');
        return localMs(nowDate, nowTime) + (Date.now() - state.fetchedAt);
    }

    function localMs(date, time) {
        const [year, month, day] = date.split('-').map(Number);
        const [hours, minutes, seconds = 0] = time.split(':').map(Number);
        return new Date(year, month - 1, day, hours, minutes, seconds).getTime();
    }

    function duration(milliseconds) {
        const totalMinutes = Math.max(1, Math.ceil(milliseconds / 60000));
        const days = Math.floor(totalMinutes / 1440);
        const hours = Math.floor((totalMinutes % 1440) / 60);
        const minutes = totalMinutes % 60;
        if (days) return t('js.schedule.durationDaysHours', days, hours);
        if (hours) return t('js.schedule.durationHoursMinutes', hours, minutes);
        return t('js.schedule.durationMinutes', minutes);
    }

    function statusSummary(shift) {
        switch (shift.status) {
            case 'SCHEDULED': return t('js.schedule.summaryScheduled', formatMoney(shift.basePay));
            case 'CANCELLED': return t('js.schedule.summaryCancelled', formatMoney(shift.earnedPay));
            case 'FORFEITED': return t('js.schedule.summaryForfeited');
            default: return t('js.schedule.summaryCompleted', formatMoney(shift.earnedPay));
        }
    }

    function timeRange(shift) {
        return `${escapeHtml(formatTime(shift.startTime))}–${escapeHtml(formatTime(shift.endTime))}`;
    }

    function dayLabel(date) {
        return calendar.parseIso(date).toLocaleDateString(appLocale(), {weekday: 'short', month: 'short', day: 'numeric'});
    }

    function shortDay(date) {
        return calendar.parseIso(date).toLocaleDateString(appLocale(), {month: 'short', day: 'numeric'});
    }

    function longDay(date) {
        return calendar.parseIso(date).toLocaleDateString(appLocale(), {weekday: 'long', month: 'long', day: 'numeric'});
    }

    window.flexbuddySchedule = {show, refresh, changeStatus, startable, start: startBlock, renderNextInto,
        add: trigger => addScheduled(state.selected || today(), trigger)};
})();
