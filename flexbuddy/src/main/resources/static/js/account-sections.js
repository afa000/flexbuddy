// Account sections: the pure helpers behind the account page's folded sections, which sections a link opens and the
// one-line summary each shows while folded. Loaded before account.js, which owns the page. Nothing here touches the
// page at load, so the helpers run under `node --test`.
(() => {
    const SECTIONS = ['costs', 'taxes-section', 'payouts-section', 'reminders', 'backup', 'account'];
    // The anchors that existed before the sections did, each now inside one of them.
    const ANCHORS = {goals: 'costs', taxes: 'taxes-section', payouts: 'payouts-section'};
    const DAY_NAMES = {
        MONDAY: 'js.common.weekdayMon', TUESDAY: 'js.common.weekdayTue', WEDNESDAY: 'js.common.weekdayWed',
        THURSDAY: 'js.common.weekdayThu', FRIDAY: 'js.common.weekdayFri', SATURDAY: 'js.common.weekdaySat',
        SUNDAY: 'js.common.weekdaySun'
    };
    const WEEK = Object.keys(DAY_NAMES);
    const rate = new Intl.NumberFormat(appLocale(), {style: 'currency', currency: 'USD', minimumFractionDigits: 2, maximumFractionDigits: 3});
    const dollars = new Intl.NumberFormat(appLocale(), {style: 'currency', currency: 'USD'});
    const MONTHS = ['js.common.monthJan', 'js.common.monthFeb', 'js.common.monthMar', 'js.common.monthApr', 'js.common.monthMay', 'js.common.monthJun', 'js.common.monthJul', 'js.common.monthAug', 'js.common.monthSep', 'js.common.monthOct', 'js.common.monthNov', 'js.common.monthDec'];

    /** The section a link's hash opens: "#goals" opens Earnings & costs, a section's own id opens itself, else null. */
    function sectionForHash(hash) {
        const id = String(hash || '').replace(/^#/, '');
        if (SECTIONS.includes(id)) return id;
        return ANCHORS[id] ?? null;
    }

    /** A whole-dollar goal without the cents, "$400", and a part-dollar one with them. */
    function goalAmount(value) {
        return dollars.format(Number(value)).replace(/\.00$/, '');
    }

    /** "Standard mileage · $0.70/mi" or "Actual expenses". */
    function costMethod(settings) {
        return settings.vehicleCostMethod === 'ACTUAL_EXPENSES'
            ? t('js.accountSections.actualExpenses') : t('js.accountSections.standardMileage', rate.format(Number(settings.mileageRate)));
    }

    function costs(settings) {
        const method = costMethod(settings);
        if (settings.weeklyGoal != null) return t('js.accountSections.goalWeekly', method, goalAmount(settings.weeklyGoal));
        if (settings.monthlyGoal != null) return t('js.accountSections.goalMonthly', method, goalAmount(settings.monthlyGoal));
        return method;
    }

    function taxes(settings, extra) {
        if (settings.taxSetAsidePercent == null) return t('js.accountSections.taxOff');
        const percent = Number(settings.taxSetAsidePercent);
        return extra.remindTax ? t('js.accountSections.setAsideReminders', percent) : t('js.accountSections.setAside', percent);
    }

    function payouts(settings) {
        const days = WEEK.filter(day => (settings.payoutDays || []).includes(day)).map(day => t(DAY_NAMES[day]));
        const lag = settings.payoutLagDays ?? 1;
        const after = lag === 0 ? t('js.accountSections.paidSameDay') : tn(lag, 'js.accountSections.paidAfter');
        return `${days.join(` ${t('js.accountSections.dayAnd')} `)} · ${after}`;
    }

    function leadTime(minutes) {
        if (minutes == null) return t('js.accountSections.noReminder');
        if (minutes < 60) return t('js.accountSections.minutesBefore', minutes);
        const hours = minutes / 60;
        const shown = Number.isInteger(hours) ? hours : hours.toFixed(1);
        return t(hours === 1 ? 'js.accountSections.hoursBefore.one' : 'js.accountSections.hoursBefore.other', shown);
    }

    function reminders(settings) {
        return t('js.accountSections.reminderSummary', leadTime(settings.remindBeforeMinutes),
            t(settings.remindConfirm ? 'js.common.on' : 'js.common.off'), t(settings.remindMiles ? 'js.common.on' : 'js.common.off'));
    }

    /** "Last backup: Sep 14" in the device's own time zone, from the instant the server stored. */
    function backup(lastBackupAt) {
        if (!lastBackupAt) return t('js.accountSections.neverBackedUp');
        const date = new Date(lastBackupAt);
        return t('js.accountSections.lastBackup', t('js.common.monthDay', t(MONTHS[date.getMonth()]), date.getDate()));
    }

    /**
     * One line for each section, from the settings the page loads and a few facts that are not settings. Before the
     * settings arrive, the four sections that depend on them say so.
     */
    function summaries(settings, extra = {}) {
        const loaded = Boolean(settings);
        return {
            costs: loaded ? costs(settings) : t('js.common.loading'),
            taxes: loaded ? taxes(settings, extra) : t('js.common.loading'),
            payouts: loaded ? payouts(settings) : t('js.common.loading'),
            reminders: loaded ? reminders(settings) : t('js.common.loading'),
            backup: backup(extra.lastBackupAt),
            account: `${extra.displayName} · ${extra.email}`
        };
    }

    window.flexbuddyAccountSections = {sectionForHash, summaries, costMethod, payouts};
})();
