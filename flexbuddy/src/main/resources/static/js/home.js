// Home: the first screen. This week's earnings and goal, the last seven days, the next block and payout, what needs
// attention, and the last few blocks. Loaded before app.js and uses its shared helpers (apiFetch, formatMoney,
// formatMinutes, formatTime, formatDate, escapeHtml, openEditModal, showReportsScreen, showScheduleScreen) and the
// helpers of setup.js (flexbuddySetup) at call time. The pure helpers touch no page elements, so they run under `node --test`.
(() => {
    const calendar = window.flexbuddyCalendar;
    const DAYS = ['js.common.weekdaySun', 'js.common.weekdayMon', 'js.common.weekdayTue', 'js.common.weekdayWed',
        'js.common.weekdayThu', 'js.common.weekdayFri', 'js.common.weekdaySat'];
    const MONTHS = ['js.common.monthJan', 'js.common.monthFeb', 'js.common.monthMar', 'js.common.monthApr', 'js.common.monthMay', 'js.common.monthJun', 'js.common.monthJul', 'js.common.monthAug', 'js.common.monthSep', 'js.common.monthOct', 'js.common.monthNov', 'js.common.monthDec'];
    const RECENT_STATUSES = ['COMPLETED', 'CANCELLED', 'FORFEITED'];
    let el;
    let weekEarned = null;

    /** "Mon Sep 28" from a local YYYY-MM-DD string, read as written so the time zone can never shift the day. */
    function dayLabel(iso) {
        const date = calendar.parseIso(iso);
        return t('js.common.dayLabel', t(DAYS[date.getDay()]), t(MONTHS[date.getMonth()]), date.getDate());
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

    /** The rows of the Needs attention card, in order, each with the text to show and the key of its action. */
    function attentionRows({needsConfirmation = 0, missingMiles = 0, backupDue = false, installable = false}) {
        const rows = [];
        if (needsConfirmation > 0) rows.push({key: 'confirm', text: tn(needsConfirmation, 'js.home.blocksToConfirm'), action: t('js.home.review')});
        if (missingMiles > 0) {
            rows.push({key: 'miles', text: tn(missingMiles, 'js.home.blocksNoMiles'), action: t('js.home.addMiles')});
        }
        if (backupDue) rows.push({key: 'backup', text: t('js.home.noBackup'), action: t('js.home.backUp')});
        if (installable) rows.push({key: 'install', text: t('js.home.installApp'), action: t('js.home.install')});
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
        el.today.textContent = new Date().toLocaleDateString(appLocale(), {weekday: 'short', month: 'short', day: 'numeric'});
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
        el.weekLabel.textContent = t('js.home.thisWeek', week.label);
        const [weekStats, sevenStats, shifts] = await Promise.allSettled([
            fetchJson(statisticsUrl(week)),
            fetchJson(statisticsUrl(lastSevenRange(today))),
            fetchJson('/shifts?sort=date&dir=desc')
        ]);

        if (weekStats.status === 'fulfilled') {
            weekEarned = weekStats.value.totalEarnings;
            el.weekNetHourly.textContent = t('js.common.perHour', formatMoney(weekStats.value.netHourlyRate));
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
            el.sevenDetail.textContent = `${formatMoney(stats.totalEarnings)} · ${tn(stats.totalShifts ?? 0, 'js.common.blocks')}`;
        } else {
            el.sevenHours.textContent = '—';
            el.sevenDetail.textContent = '';
        }

        renderRecent(shifts.status === 'fulfilled' ? shifts.value : null);
        // Adding a first block ticks its row in the setup card on the next refresh, with no page reload.
        if (shifts.status === 'fulfilled') window.flexbuddySetup?.update({hasBlocks: shifts.value.length > 0});
        renderAttention();
    }

    /** What the week card says in place of "$93.50 of $400" when the driver has no weekly goal yet, or null until known. */
    function noGoalText() {
        return weekEarned == null ? null : t('js.home.weekEarned', formatMoney(weekEarned));
    }

    function renderRecent(shifts) {
        if (!shifts) {
            el.recentList.innerHTML = `<p class="history-empty">${escapeHtml(t('js.home.recentLoadFailed'))}</p>`;
            return;
        }
        const recent = recentBlocks(shifts);
        if (!recent.length) {
            el.recentList.innerHTML = `<p class="history-empty">${escapeHtml(t('js.home.recentEmpty'))}</p>`;
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
                ? (shift.miles == null ? formatMinutes(minutes)
                    : t('js.home.timeAndMiles', formatMinutes(minutes), Number(shift.miles).toFixed(1)))
                : shift.status === 'CANCELLED' ? t('js.common.cancelled')
                    : shift.lateForfeit ? t('js.common.lateForfeit') : t('js.common.forfeited');
            row.innerHTML = `
                <span class="date-badge"><small>${escapeHtml(t(MONTHS[date.getMonth()]))}</small><strong>${date.getDate()}</strong></span>
                <span class="recent-main"><strong>${escapeHtml(shift.station)}</strong><span>${escapeHtml(detail)}</span></span>
                <span class="recent-pay"><strong>${formatMoney(total)}</strong>${worked ? `<span>${escapeHtml(t('js.common.perHour', formatMoney(shift.hourlyRate)))}</span>` : ''}</span>`;
            row.setAttribute('aria-label', t('js.home.editBlockOn', shift.station, shift.date));
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
