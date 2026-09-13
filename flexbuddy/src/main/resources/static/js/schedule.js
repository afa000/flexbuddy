// Schedule screen: next block, blocks awaiting confirmation, the upcoming list, the week strip, and the month grid.
// Loaded before app.js and uses its shared helpers (apiFetch, formatMoney, openEditModal, ...) at call time.
(() => {
    const UPCOMING_DAYS = 14;
    const PENCIL = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="m4 20 4.2-1 10.9-10.9a2.1 2.1 0 0 0-3-3L5.2 16 4 20Zm10.5-13.5 3 3"/></svg>';
    const calendar = window.flexbuddyCalendar;
    const state = {upcoming: null, fetchedAt: 0, month: null, monthDays: [], selected: null, focusDate: null, countdownTimer: undefined};
    let el;

    function init() {
        if (el) return;
        el = {
            screen: document.querySelector('#scheduleScreen'),
            error: document.querySelector('#scheduleError'),
            confirmStrip: document.querySelector('#confirmStrip'),
            confirmList: document.querySelector('#confirmList'),
            nextUp: document.querySelector('#nextUpCard'),
            upcomingList: document.querySelector('#upcomingList'),
            weekStrip: document.querySelector('#weekStrip'),
            grid: document.querySelector('#calendarGrid'),
            monthLabel: document.querySelector('#calendarMonthLabel'),
            dayPanel: document.querySelector('#dayPanel'),
            nextShiftLink: document.querySelector('#nextShiftLink'),
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
            if (!response.ok) throw new Error(await response.text() || 'Your schedule could not be loaded.');
            state.upcoming = await response.json();
            state.fetchedAt = Date.now();
            hideMessage(el.error);
            renderHints();
            if (isVisible()) {
                renderConfirmStrip();
                renderNextUp();
                renderUpcomingList();
            }
        } catch (error) {
            if (isVisible()) showMessage(el.error, error.message || 'Your schedule could not be loaded.');
        }
    }

    function renderHints() {
        const {next, needsConfirmation} = state.upcoming;
        if (el.nextShiftLink) {
            el.nextShiftLink.classList.toggle('is-hidden', !next);
            if (next) el.nextShiftLink.textContent = `Next: ${next.station} · ${weekday(next.date)} ${formatTime(next.startTime)}`;
        }
        if (el.badge) {
            el.badge.textContent = needsConfirmation.length;
            el.badge.classList.toggle('is-hidden', needsConfirmation.length === 0);
            el.badge.setAttribute('aria-label', `${needsConfirmation.length} to confirm`);
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
                    <span>Did you work this block?</span></p>
                <div class="confirm-actions">
                    <button class="primary-button compact-button" type="button" data-action="completed">Completed</button>
                    <button class="secondary-button compact-button" type="button" data-action="cancelled">Cancelled</button>
                    <button class="danger-text-button" type="button" data-action="forfeited">Forfeited</button>
                </div>`;
            bindActions(row, shift);
            return row;
        }));
    }

    function renderNextUp() {
        const shift = state.upcoming.next;
        window.clearInterval(state.countdownTimer);
        if (!shift) {
            el.nextUp.innerHTML = `
                <div class="next-up-main">
                    <p class="step-label">NEXT UP</p>
                    <h2>No upcoming blocks</h2>
                    <p class="next-up-when">Add a scheduled shift, or import a screenshot of a block you accepted.</p>
                </div>`;
            return;
        }
        el.nextUp.innerHTML = `
            <div class="next-up-main">
                <p class="step-label">NEXT UP</p>
                <h2>${escapeHtml(shift.station)}</h2>
                <p class="next-up-when">${escapeHtml(dayLabel(shift.date))} · ${timeRange(shift)}</p>
                <p class="next-up-countdown"></p>
            </div>
            <div class="next-up-pay"><strong>${formatMoney(shift.basePay)}</strong><span>offered · ${formatMinutes(shift.timeWorked)}</span></div>
            <div class="next-up-actions">
                <a class="secondary-button compact-button" href="/shifts/${shift.id}.ics" download>Add to calendar</a>
                <button class="secondary-button compact-button" type="button" data-action="cancelled">Mark cancelled</button>
                <button class="danger-text-button" type="button" data-action="forfeited">Forfeit</button>
                <button class="text-button" type="button" data-action="edit">Edit</button>
            </div>`;
        bindActions(el.nextUp, shift);
        const countdown = el.nextUp.querySelector('.next-up-countdown');
        const tick = () => {
            if (!isVisible()) return;
            countdown.textContent = countdownText(shift);
        };
        tick();
        state.countdownTimer = window.setInterval(tick, 30000);
    }

    function renderUpcomingList() {
        const {days, weeks, conflicts} = state.upcoming;
        const overlapping = new Set(conflicts.flatMap(conflict => [conflict.firstShiftId, conflict.secondShiftId]));
        const weekTotals = new Map(weeks.map(week => [week.weekStart, week]));
        el.upcomingList.replaceChildren();
        if (!days.length) {
            el.upcomingList.innerHTML = `<p class="history-empty">Nothing scheduled in the next ${UPCOMING_DAYS} days.</p>`;
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
                        <span>${escapeHtml(shift.station)}${overlapping.has(shift.id) ? ' <small class="status-badge status-conflict">Overlaps</small>' : ''}</span></div>
                    <strong class="upcoming-pay">${formatMoney(shift.basePay)}</strong>
                    <button class="edit-shift-button" type="button" data-action="edit">${PENCIL}</button>`;
                row.querySelector('button').setAttribute('aria-label', `Edit ${shift.station} block on ${formatDate(shift.date)}`);
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
                footer.textContent = `Week of ${shortDay(week)} · ${total.shifts} ${total.shifts === 1 ? 'block' : 'blocks'} · `
                    + `${formatMinutes(total.plannedMinutes)} planned · ${formatMoney(total.expectedPay)} expected`;
                el.upcomingList.append(footer);
            }
        });
    }

    async function loadMonth(month) {
        state.month = month;
        const [year, number] = month.split('-').map(Number);
        el.monthLabel.textContent = new Date(year, number - 1, 1).toLocaleDateString(undefined, {month: 'long', year: 'numeric'});
        try {
            const response = await apiFetch(`/shifts/calendar?month=${month}`);
            if (!response.ok) throw new Error(await response.text() || 'The calendar could not be loaded.');
            const result = await response.json();
            if (state.month !== month) return;
            state.monthDays = result.days;
            renderGrid();
        } catch (error) {
            showMessage(el.error, error.message || 'The calendar could not be loaded.');
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
        el.dayPanel.innerHTML = '<p class="day-panel-empty">Loading…</p>';
        try {
            const params = new URLSearchParams({from: date, to: date, status: 'all', sort: 'date', dir: 'asc'});
            const response = await apiFetch(`/shifts?${params}`);
            if (!response.ok) throw new Error(await response.text() || 'This day could not be loaded.');
            renderDay(date, await response.json());
        } catch (error) {
            el.dayPanel.innerHTML = `<p class="day-panel-empty">${escapeHtml(error.message || 'This day could not be loaded.')}</p>`;
        }
    }

    function renderDay(date, shifts) {
        if (state.selected !== date) return;
        el.dayPanel.replaceChildren();
        const heading = document.createElement('div');
        heading.className = 'day-panel-heading';
        heading.innerHTML = `<h3>${escapeHtml(longDay(date))}</h3><button class="text-button" type="button" data-online-only>Add scheduled shift</button>`;
        const addButton = heading.querySelector('button');
        addButton.disabled = window.flexbuddyPwa?.isOffline() ?? false;
        addButton.addEventListener('click', () => addScheduled(date, addButton));
        el.dayPanel.append(heading);
        if (!shifts.length) {
            const empty = document.createElement('p');
            empty.className = 'day-panel-empty';
            empty.textContent = 'No shifts on this day.';
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
            row.querySelector('button').setAttribute('aria-label', `Edit ${shift.station} shift on ${formatDate(shift.date)}`);
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
                <small>${escapeHtml(parsed.toLocaleDateString(undefined, {weekday: 'short'}))}</small>
                <strong>${parsed.getDate()}</strong>
                <span>${dayShifts.length ? escapeHtml(formatTime(dayShifts[0].startTime)) : '—'}</span>
                <span class="calendar-dots" aria-hidden="true">${dayShifts.slice(0, 3)
                    .map(shift => `<i class="status-dot status-dot-${shift.status.toLowerCase()}"></i>`).join('')}</span>`;
            button.setAttribute('aria-label',
                `${longDay(date)}: ${dayShifts.length} ${dayShifts.length === 1 ? 'shift' : 'shifts'}`);
            button.addEventListener('click', () => selectDay(date));
            return button;
        }));
    }

    function bindActions(container, shift) {
        container.querySelectorAll('[data-action]').forEach(button => {
            const action = button.dataset.action;
            if (action !== 'edit') {
                button.dataset.onlineOnly = '';
                button.disabled = window.flexbuddyPwa?.isOffline() ?? false;
            }
            button.addEventListener('click', () => {
                if (action === 'completed') openEditModal(shift, button, {status: 'COMPLETED', focusPay: true});
                else if (action === 'cancelled') openEditModal(shift, button, {status: 'CANCELLED', focusPay: true});
                else if (action === 'forfeited') confirmForfeit(shift);
                else openEditModal(shift, button);
            });
        });
    }

    function confirmForfeit(shift) {
        openConfirm('Forfeit this block?',
            `${shift.station} on ${formatDate(shift.date)} will be marked forfeited. It earns nothing and counts toward this month's forfeits.`,
            () => changeStatus(shift, {status: 'FORFEITED'}, 'Block forfeited'),
            'Forfeit block');
    }

    async function changeStatus(shift, body, title) {
        const response = await apiFetch(`/shifts/${shift.id}/status`, {
            method: 'PATCH',
            headers: csrfHeaders({'Content-Type': 'application/json'}),
            body: JSON.stringify(body)
        });
        if (!response.ok) throw new Error(await response.text() || 'The shift could not be updated.');
        showToast(title, `${shift.station} on ${formatDate(shift.date)} is up to date.`);
        await loadDashboard();
        return response.json();
    }

    function addScheduled(date, trigger) {
        openEditModal({id: null, station: '', date, startTime: '', endTime: '', basePay: '', tips: 0, miles: null,
            status: 'SCHEDULED'}, trigger, {status: 'SCHEDULED'});
    }

    function countdownText(shift) {
        const start = localMs(shift.date, shift.startTime);
        const end = start + shift.timeWorked * 60000;
        const [nowDate, nowTime] = state.upcoming.now.split('T');
        const now = localMs(nowDate, nowTime) + (Date.now() - state.fetchedAt);
        if (now < start) return `Starts in ${duration(start - now)}`;
        if (now < end) return `In progress · ends in ${duration(end - now)}`;
        return 'Finished · confirm it when the list refreshes';
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
        if (days) return `${days} d ${hours} h`;
        if (hours) return `${hours} h ${minutes} m`;
        return `${minutes} m`;
    }

    function statusSummary(shift) {
        switch (shift.status) {
            case 'SCHEDULED': return `Scheduled · ${formatMoney(shift.basePay)} offered`;
            case 'CANCELLED': return `Cancelled · ${formatMoney(shift.earnedPay)} cancellation pay`;
            case 'FORFEITED': return 'Forfeited · earns nothing';
            default: return `Completed · ${formatMoney(shift.earnedPay)} earned`;
        }
    }

    function timeRange(shift) {
        return `${escapeHtml(formatTime(shift.startTime))}–${escapeHtml(formatTime(shift.endTime))}`;
    }

    function weekday(date) {
        return calendar.parseIso(date).toLocaleDateString(undefined, {weekday: 'short'});
    }

    function dayLabel(date) {
        return calendar.parseIso(date).toLocaleDateString(undefined, {weekday: 'short', month: 'short', day: 'numeric'});
    }

    function shortDay(date) {
        return calendar.parseIso(date).toLocaleDateString(undefined, {month: 'short', day: 'numeric'});
    }

    function longDay(date) {
        return calendar.parseIso(date).toLocaleDateString(undefined, {weekday: 'long', month: 'long', day: 'numeric'});
    }

    window.flexbuddySchedule = {show, refresh, changeStatus};
})();
