// Home: the first screen. This week's earnings and goal, the last seven days, the next block and payout, what needs
// attention, and the last few blocks. Loaded before app.js and uses its shared helpers (apiFetch, formatMoney,
// formatMinutes, formatTime, formatDate, escapeHtml, openEditModal, showReportsScreen, showScheduleScreen) at call
// time. The pure helpers touch no page elements, so they run under `node --test`.
(() => {
    const calendar = window.flexbuddyCalendar;
    const DAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
    const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    const RECENT_STATUSES = ['COMPLETED', 'CANCELLED', 'FORFEITED'];
    let el;
    let weekEarned = null;

    /** "Mon Sep 28" from a local YYYY-MM-DD string, read as written so the time zone can never shift the day. */
    function dayLabel(iso) {
        const date = calendar.parseIso(iso);
        return `${DAYS[date.getDay()]} ${MONTHS[date.getMonth()]} ${date.getDate()}`;
    }

    /** The Monday-to-Sunday week that contains today, and its label, for example "Mon Sep 28 – Sun Oct 4". */
    function weekRange(todayIso) {
        const from = calendar.weekStart(todayIso);
        const to = calendar.addDays(from, 6);
        return {from, to, label: `${dayLabel(from)} – ${dayLabel(to)}`};
    }

    /** Today and the six days before it. */
    function lastSevenRange(todayIso) {
        return {from: calendar.addDays(todayIso, -6), to: todayIso};
    }

    function count(number, one, many) {
        return `${number} ${number === 1 ? one : many}`;
    }

    /** The rows of the Needs attention card, in order, each with the text to show and the key of its action. */
    function attentionRows({needsConfirmation = 0, missingMiles = 0, backupDue = false, installable = false}) {
        const rows = [];
        if (needsConfirmation > 0) rows.push({key: 'confirm', text: `${count(needsConfirmation, 'block', 'blocks')} to confirm`, action: 'Review'});
        if (missingMiles > 0) {
            rows.push({key: 'miles', text: `${count(missingMiles, 'block has', 'blocks have')} no miles`, action: 'Add miles'});
        }
        if (backupDue) rows.push({key: 'backup', text: 'No backup in 30 days', action: 'Back up'});
        if (installable) rows.push({key: 'install', text: 'Install FlexBuddy', action: 'Install'});
        return rows;
    }

    /** The newest blocks that were worked, cancelled or forfeited, in the order given; scheduled blocks are not recent. */
    function recentBlocks(shifts, n = 3) {
        return shifts.filter(shift => RECENT_STATUSES.includes(shift.status)).slice(0, n);
    }

    function init() {
        if (el) return;
        el = {
            today: document.querySelector('#homeToday'),
            weekLabel: document.querySelector('#homeWeekLabel'),
            goalCard: document.querySelector('#goalCard'),
            goalProgress: document.querySelector('#goalProgress'),
            sevenHours: document.querySelector('#rollingSevenDayTime'),
            sevenDetail: document.querySelector('#lastSevenDetail'),
            weekNetHourly: document.querySelector('#weekNetHourly'),
            attention: document.querySelector('#homeAttention'),
            attentionList: document.querySelector('#attentionList'),
            missingMiles: document.querySelector('#missingMiles'),
            installBanner: document.querySelector('#installBanner'),
            recentList: document.querySelector('#recentList'),
            recentAll: document.querySelector('#recentAllButton')
        };
        el.recentAll.addEventListener('click', () => showReportsScreen(true, 'history'));
        document.addEventListener('flexbuddy:attention', renderAttention);
    }

    function renderToday() {
        init();
        el.today.textContent = new Date().toLocaleDateString(undefined, {weekday: 'short', month: 'short', day: 'numeric'});
    }

    async function fetchJson(url) {
        const response = await apiFetch(url);
        if (!response.ok) throw new Error();
        return response.json();
    }

    function statisticsUrl({from, to}) {
        return `/shifts/statistics?${new URLSearchParams({from, to})}`;
    }

    let needsConfirmation = 0;

    /** Loads every figure on Home. One call failing leaves a dash in its own place and does not blank the rest. */
    async function load() {
        init();
        renderToday();
        const today = calendar.toIso(new Date());
        const week = weekRange(today);
        el.weekLabel.textContent = `This week · ${week.label}`;
        const [weekStats, sevenStats, shifts] = await Promise.allSettled([
            fetchJson(statisticsUrl(week)),
            fetchJson(statisticsUrl(lastSevenRange(today))),
            fetchJson('/shifts?sort=date&dir=desc')
        ]);

        if (weekStats.status === 'fulfilled') {
            weekEarned = weekStats.value.totalEarnings;
            el.weekNetHourly.textContent = `${formatMoney(weekStats.value.netHourlyRate)}/hr`;
            needsConfirmation = weekStats.value.needsConfirmation ?? 0;
        } else {
            el.weekNetHourly.textContent = '—';
        }
        // With no weekly goal there is no "of $400", so the card shows what was earned.
        if (el.goalCard.dataset.state === 'none') {
            const text = noGoalText();
            if (text) el.goalProgress.textContent = text;
        }

        if (sevenStats.status === 'fulfilled') {
            const stats = sevenStats.value;
            el.sevenHours.textContent = formatMinutes(stats.totalTimeWorked);
            el.sevenDetail.textContent = `${formatMoney(stats.totalEarnings)} · ${count(stats.totalShifts ?? 0, 'block', 'blocks')}`;
        } else {
            el.sevenHours.textContent = '—';
            el.sevenDetail.textContent = '';
        }

        renderRecent(shifts.status === 'fulfilled' ? shifts.value : null);
        renderAttention();
    }

    /** What the week card says in place of "$93.50 of $400" when the driver has no weekly goal yet, or null until known. */
    function noGoalText() {
        return weekEarned == null ? null : `${formatMoney(weekEarned)} earned`;
    }

    function renderRecent(shifts) {
        if (!shifts) {
            el.recentList.innerHTML = '<p class="history-empty">Recent blocks could not be loaded.</p>';
            return;
        }
        const recent = recentBlocks(shifts);
        if (!recent.length) {
            el.recentList.innerHTML = '<p class="history-empty">No blocks yet. Add one from the + button.</p>';
            return;
        }
        el.recentList.replaceChildren(...recent.map(shift => {
            const row = document.createElement('button');
            row.type = 'button';
            row.className = 'recent-row';
            const date = calendar.parseIso(shift.date);
            const worked = shift.status === 'COMPLETED';
            const total = shift.earnedPay ?? shift.totalPay ?? (Number(shift.basePay || 0) + Number(shift.tips || 0));
            const minutes = shift.details?.actualMinutes ?? shift.timeWorked;
            const detail = worked
                ? `${formatMinutes(minutes)}${shift.miles == null ? '' : ` · ${Number(shift.miles).toFixed(1)} mi`}`
                : shift.status === 'CANCELLED' ? 'Cancelled' : shift.lateForfeit ? 'Late forfeit' : 'Forfeited';
            row.innerHTML = `
                <span class="date-badge"><small>${MONTHS[date.getMonth()]}</small><strong>${date.getDate()}</strong></span>
                <span class="recent-main"><strong>${escapeHtml(shift.station)}</strong><span>${escapeHtml(detail)}</span></span>
                <span class="recent-pay"><strong>${formatMoney(total)}</strong>${worked ? `<span>${formatMoney(shift.hourlyRate)}/hr</span>` : ''}</span>`;
            row.setAttribute('aria-label', `Edit ${shift.station} block on ${shift.date}`);
            row.addEventListener('click', () => openEditModal(shift, row));
            return row;
        }));
    }

    /** Lists only what applies, and hides the whole card when nothing does. */
    function renderAttention() {
        init();
        const missing = window.flexbuddyFinish?.missingCount() ?? 0;
        const installable = el.installBanner.dataset.available === 'true';
        const rows = attentionRows({
            needsConfirmation, missingMiles: missing,
            backupDue: el.attention.dataset.backupDue === 'true', installable
        });
        el.attention.hidden = rows.length === 0;
        if (missing === 0) el.missingMiles.hidden = true;
        if (!installable) el.installBanner.hidden = true;
        el.attentionList.replaceChildren(...rows.map(row => {
            const item = document.createElement('li');
            const button = document.createElement('button');
            button.type = 'button';
            button.className = 'attention-row';
            button.innerHTML = `<span>${escapeHtml(row.text)}</span><b>${escapeHtml(row.action)}</b>`;
            button.addEventListener('click', () => openAttention(row.key));
            item.append(button);
            return item;
        }));
    }

    async function openAttention(key) {
        if (key === 'confirm') {
            await showScheduleScreen();
            document.querySelector('#confirmStrip')?.scrollIntoView({block: 'start'});
        } else if (key === 'miles') {
            el.missingMiles.hidden = !el.missingMiles.hidden;
        } else if (key === 'backup') {
            window.location.assign('/account#backup');
        } else if (key === 'install') {
            el.installBanner.hidden = !el.installBanner.hidden;
        }
    }

    window.flexbuddyHome = {load, renderToday, noGoalText, weekRange, lastSevenRange, attentionRows, recentBlocks};
})();
