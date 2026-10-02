// Account sections: the pure helpers behind the account page's folded sections, which sections a link opens and the
// one-line summary each shows while folded. Loaded before account.js, which owns the page. Nothing here touches the
// page at load, so the helpers run under `node --test`.
(() => {
    const SECTIONS = ['costs', 'taxes-section', 'payouts-section', 'reminders', 'backup', 'account'];
    // The anchors that existed before the sections did, each now inside one of them.
    const ANCHORS = {goals: 'costs', taxes: 'taxes-section', payouts: 'payouts-section'};
    const DAY_NAMES = {MONDAY: 'Mon', TUESDAY: 'Tue', WEDNESDAY: 'Wed', THURSDAY: 'Thu', FRIDAY: 'Fri', SATURDAY: 'Sat', SUNDAY: 'Sun'};
    const WEEK = Object.keys(DAY_NAMES);
    const rate = new Intl.NumberFormat('en-US', {style: 'currency', currency: 'USD', minimumFractionDigits: 2, maximumFractionDigits: 3});
    const dollars = new Intl.NumberFormat('en-US', {style: 'currency', currency: 'USD'});
    const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

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
            ? 'Actual expenses' : `Standard mileage · ${rate.format(Number(settings.mileageRate))}/mi`;
    }

    function costs(settings) {
        const method = costMethod(settings);
        if (settings.weeklyGoal != null) return `${method} · Goal ${goalAmount(settings.weeklyGoal)}/wk`;
        if (settings.monthlyGoal != null) return `${method} · Goal ${goalAmount(settings.monthlyGoal)}/mo`;
        return method;
    }

    function taxes(settings, extra) {
        if (settings.taxSetAsidePercent == null) return 'Off';
        return `${Number(settings.taxSetAsidePercent)}% set aside${extra.remindTax ? ' · reminders on' : ''}`;
    }

    function payouts(settings) {
        const days = WEEK.filter(day => (settings.payoutDays || []).includes(day)).map(day => DAY_NAMES[day]);
        const lag = settings.payoutLagDays ?? 1;
        const after = lag === 0 ? 'paid the same day' : `paid ${lag} ${lag === 1 ? 'day' : 'days'} after`;
        return `${days.join(' & ')} · ${after}`;
    }

    function leadTime(minutes) {
        if (minutes == null) return 'No reminder before blocks';
        if (minutes < 60) return `${minutes} min before`;
        const hours = minutes / 60;
        return `${Number.isInteger(hours) ? hours : hours.toFixed(1)} ${hours === 1 ? 'hour' : 'hours'} before`;
    }

    function reminders(settings) {
        return `${leadTime(settings.remindBeforeMinutes)} · confirm ${settings.remindConfirm ? 'on' : 'off'}`
            + ` · miles ${settings.remindMiles ? 'on' : 'off'}`;
    }

    /** "Last backup: Sep 14" in the device's own time zone, from the instant the server stored. */
    function backup(lastBackupAt) {
        if (!lastBackupAt) return 'Never backed up';
        const date = new Date(lastBackupAt);
        return `Last backup: ${MONTHS[date.getMonth()]} ${date.getDate()}`;
    }

    /**
     * One line for each section, from the settings the page loads and a few facts that are not settings. Before the
     * settings arrive, the four sections that depend on them say so.
     */
    function summaries(settings, extra = {}) {
        const loaded = Boolean(settings);
        return {
            costs: loaded ? costs(settings) : 'Loading…',
            taxes: loaded ? taxes(settings, extra) : 'Loading…',
            payouts: loaded ? payouts(settings) : 'Loading…',
            reminders: loaded ? reminders(settings) : 'Loading…',
            backup: backup(extra.lastBackupAt),
            account: `${extra.displayName} · ${extra.email}`
        };
    }

    window.flexbuddyAccountSections = {sectionForHash, summaries, costMethod, payouts};
})();
