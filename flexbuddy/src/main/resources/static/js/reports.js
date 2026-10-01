// Reports: the pure helpers behind the Reports screen's range button and its address. Loaded before app.js, which owns
// the screen itself. Nothing here touches the page at load, so the helpers run under `node --test`.
(() => {
    const PRESET_LABELS = {
        all: 'All time', week: 'This week', month: 'This month', year: 'This year', '30days': 'Last 30 days',
        payperiod: 'This pay period', lastpayperiod: 'Last pay period'
    };
    const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

    /** "Sep 1" from a local YYYY-MM-DD string, read as written so a late-evening time zone can never shift the day. */
    function shortDay(value) {
        const [, month, day] = value.split('-').map(Number);
        return `${MONTHS[month - 1]} ${day}`;
    }

    /** The text of the range button, for example "This month ▾" or "Sep 1 – Sep 15 ▾". */
    function rangeLabel(filterState) {
        const {preset, from, to} = filterState;
        let label = PRESET_LABELS[preset];
        if (!label) {
            if (from && to) label = `${shortDay(from)} – ${shortDay(to)}`;
            else if (from) label = `From ${shortDay(from)}`;
            else if (to) label = `Until ${shortDay(to)}`;
            else label = 'Custom';
        }
        return `${label} ▾`;
    }

    /** The query string that restores the Reports screen: the range, sort, grouping and, for History, the tab. */
    function reportParams(filterState, groupBy, tab) {
        const params = new URLSearchParams();
        params.set('screen', 'reports');
        for (const key of ['from', 'to', 'station']) {
            if (filterState[key]) params.set(key, filterState[key]);
        }
        params.set('sort', filterState.sort);
        params.set('dir', filterState.dir);
        params.set('preset', filterState.preset);
        params.set('groupBy', groupBy);
        if (tab === 'history') params.set('tab', 'history');
        return params;
    }

    /** The inverse of reportParams for what the filters don't already read: which tab was open. */
    function parseReportParams(search) {
        return {tab: new URLSearchParams(search).get('tab') === 'history' ? 'history' : 'charts'};
    }

    /** The line under the range button: how many blocks the range holds and the stations they were worked at. */
    function summaryLine(shifts) {
        if (!shifts.length) return 'No blocks in this range';
        const counts = new Map();
        for (const shift of shifts) {
            if (shift.station) counts.set(shift.station, (counts.get(shift.station) || 0) + 1);
        }
        const stations = [...counts].sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0])).map(([station]) => station);
        const shown = stations.slice(0, 3).join(', ');
        const more = stations.length > 3 ? ` +${stations.length - 3} more` : '';
        const blocks = `${shifts.length} ${shifts.length === 1 ? 'block' : 'blocks'}`;
        return shown ? `${blocks} · ${shown}${more}` : blocks;
    }

    window.flexbuddyReports = {rangeLabel, reportParams, parseReportParams, summaryLine};
})();
