(() => {
    const WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
    const STATUSES = [
        ['scheduled', 'SCHEDULED', 'scheduled'],
        ['completed', 'COMPLETED', 'completed'],
        ['cancelled', 'CANCELLED', 'cancelled'],
        ['forfeited', 'FORFEITED', 'forfeited']
    ];
    const MAX_DOTS = 4;

    function pad(value) {
        return String(value).padStart(2, '0');
    }

    function toIso(date) {
        return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
    }

    function parseIso(value) {
        const [year, month, day] = value.split('-').map(Number);
        return new Date(year, month - 1, day);
    }

    function addDays(iso, days) {
        const date = parseIso(iso);
        date.setDate(date.getDate() + days);
        return toIso(date);
    }

    function addMonths(iso, months) {
        const date = parseIso(iso);
        const target = new Date(date.getFullYear(), date.getMonth() + months, 1);
        const lastDay = new Date(target.getFullYear(), target.getMonth() + 1, 0).getDate();
        target.setDate(Math.min(date.getDate(), lastDay));
        return toIso(target);
    }

    /** Monday of the ISO week that contains the date, matching the weekly reports. */
    function weekStart(iso) {
        const date = parseIso(iso);
        return addDays(iso, -((date.getDay() + 6) % 7));
    }

    function describe(date, summary, formatMoney) {
        const label = parseIso(date).toLocaleDateString(undefined, {weekday: 'long', month: 'long', day: 'numeric'});
        if (!summary) return `${label}: no shifts`;
        const parts = STATUSES.filter(([key]) => summary[key] > 0).map(([key, , word]) => `${summary[key]} ${word}`);
        if (Number(summary.earned) > 0) parts.push(`${formatMoney(summary.earned)} earned`);
        if (Number(summary.expectedPay) > 0) parts.push(`${formatMoney(summary.expectedPay)} expected`);
        return `${label}: ${parts.join(', ')}`;
    }

    /**
     * Renders a Monday-first month grid. Arrow keys move between days, Home and End jump within the week,
     * PageUp and PageDown change month, and Enter or Space (native button activation) opens a day.
     */
    function render(container, options) {
        const {month, days = [], today, selected, focusDate, onSelect, onMonthChange, formatMoney} = options;
        const [year, monthNumber] = month.split('-').map(Number);
        const daysInMonth = new Date(year, monthNumber, 0).getDate();
        const leading = (new Date(year, monthNumber - 1, 1).getDay() + 6) % 7;
        const byDate = new Map(days.map(summary => [summary.date, summary]));
        const inMonth = value => value && value.startsWith(`${month}-`);
        const tabbable = [focusDate, selected, today].find(inMonth) || `${month}-01`;

        container.replaceChildren();
        const header = document.createElement('div');
        header.className = 'calendar-row calendar-weekdays';
        header.setAttribute('role', 'row');
        WEEKDAYS.forEach(name => {
            const cell = document.createElement('span');
            cell.setAttribute('role', 'columnheader');
            cell.textContent = name;
            header.append(cell);
        });
        container.append(header);

        let row;
        const cells = Math.ceil((leading + daysInMonth) / 7) * 7;
        for (let index = 0; index < cells; index++) {
            if (index % 7 === 0) {
                row = document.createElement('div');
                row.className = 'calendar-row';
                row.setAttribute('role', 'row');
                container.append(row);
            }
            const dayNumber = index - leading + 1;
            if (dayNumber < 1 || dayNumber > daysInMonth) {
                const blank = document.createElement('span');
                blank.className = 'calendar-day is-outside';
                blank.setAttribute('role', 'gridcell');
                blank.setAttribute('aria-hidden', 'true');
                row.append(blank);
                continue;
            }
            const date = `${month}-${pad(dayNumber)}`;
            const summary = byDate.get(date);
            const button = document.createElement('button');
            button.type = 'button';
            button.className = 'calendar-day';
            button.dataset.date = date;
            button.setAttribute('role', 'gridcell');
            button.setAttribute('aria-label', describe(date, summary, formatMoney));
            button.setAttribute('aria-selected', String(date === selected));
            button.tabIndex = date === tabbable ? 0 : -1;
            button.classList.toggle('is-today', date === today);
            button.classList.toggle('is-selected', date === selected);

            const number = document.createElement('span');
            number.className = 'calendar-number';
            number.textContent = dayNumber;
            button.append(number);

            if (summary) {
                const dots = document.createElement('span');
                dots.className = 'calendar-dots';
                dots.setAttribute('aria-hidden', 'true');
                let shown = 0;
                let total = 0;
                STATUSES.forEach(([key]) => {
                    total += summary[key];
                    for (let count = 0; count < summary[key] && shown < MAX_DOTS; count++, shown++) {
                        const dot = document.createElement('i');
                        dot.className = `status-dot status-dot-${key}`;
                        dots.append(dot);
                    }
                });
                if (total > shown) {
                    const more = document.createElement('small');
                    more.textContent = `+${total - shown}`;
                    dots.append(more);
                }
                button.append(dots);

                const amount = Number(summary.earned) > 0 ? summary.earned : summary.expectedPay;
                if (Number(amount) > 0) {
                    const totalLabel = document.createElement('span');
                    totalLabel.className = 'calendar-total';
                    totalLabel.setAttribute('aria-hidden', 'true');
                    totalLabel.textContent = formatMoney(amount);
                    button.append(totalLabel);
                }
            }
            button.addEventListener('click', () => onSelect(date));
            row.append(button);
        }

        container.onkeydown = event => {
            const current = event.target.closest?.('[data-date]');
            if (!current) return;
            const date = current.dataset.date;
            const weekday = (parseIso(date).getDay() + 6) % 7;
            const moves = {ArrowLeft: -1, ArrowRight: 1, ArrowUp: -7, ArrowDown: 7, Home: -weekday, End: 6 - weekday};
            let target;
            if (event.key in moves) target = addDays(date, moves[event.key]);
            else if (event.key === 'PageUp') target = addMonths(date, -1);
            else if (event.key === 'PageDown') target = addMonths(date, 1);
            else return;
            event.preventDefault();
            if (inMonth(target)) focus(container, target);
            else onMonthChange(target.slice(0, 7), target);
        };

        if (focusDate && inMonth(focusDate)) focus(container, focusDate);
    }

    function focus(container, date) {
        const next = container.querySelector(`[data-date="${date}"]`);
        if (!next) return;
        container.querySelectorAll('[data-date]').forEach(button => { button.tabIndex = -1; });
        next.tabIndex = 0;
        next.focus();
    }

    window.flexbuddyCalendar = {render, toIso, parseIso, addDays, addMonths, weekStart};
})();
