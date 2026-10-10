(() => {
    const SVG_NS = 'http://www.w3.org/2000/svg';

    function svgElement(name, attributes = {}) {
        const node = document.createElementNS(SVG_NS, name);
        for (const [key, value] of Object.entries(attributes)) node.setAttribute(key, value);
        return node;
    }

    function money(value) {
        return new Intl.NumberFormat(appLocale(), {style: 'currency', currency: 'USD', maximumFractionDigits: 0})
            .format(Number(value || 0));
    }

    function fullMoney(value) {
        return new Intl.NumberFormat(appLocale(), {style: 'currency', currency: 'USD'}).format(Number(value || 0));
    }

    function hours(minutes) {
        return (Number(minutes || 0) / 60).toFixed(1);
    }

    const GROUP_LABELS = {
        station: 'js.charts.groupStation', week: 'js.charts.groupWeek', month: 'js.charts.groupMonth', year: 'js.charts.groupYear'
    };

    function empty(container, message) {
        const text = document.createElement('p');
        text.className = 'chart-empty';
        text.textContent = message;
        container.replaceChildren(text);
    }

    function tooltip(bucket) {
        return [
            bucket.label,
            tn(bucket.shifts, 'js.charts.shifts'),
            t('js.charts.base', fullMoney(bucket.basePay)),
            t('js.charts.tips', fullMoney(bucket.tips)),
            t('js.charts.total', fullMoney(bucket.totalEarnings)),
            t('js.charts.hoursLine', hours(bucket.minutesWorked)),
            t('js.charts.hourly', fullMoney(bucket.hourlyRate))
        ].join('\n');
    }

    function renderEarningsChart(container, report) {
        const buckets = report?.buckets ?? [];
        if (!buckets.length) {
            empty(container, t('js.charts.noEarningsToChart'));
            return;
        }

        const stationMode = report.groupBy === 'station';
        const max = Math.max(...buckets.map(bucket => Number(bucket.totalEarnings || 0)), 1);
        const width = stationMode ? 720 : Math.max(620, buckets.length * 92 + 80);
        const height = stationMode ? Math.max(260, buckets.length * 58 + 45) : 330;
        const svg = svgElement('svg', {
            viewBox: `0 0 ${width} ${height}`,
            role: 'img',
            'aria-label': t('js.charts.groupedBy', GROUP_LABELS[report.groupBy] ? t(GROUP_LABELS[report.groupBy]) : report.groupBy),
            preserveAspectRatio: 'xMinYMin meet'
        });
        svg.classList.add('earnings-svg');

        if (stationMode) renderHorizontal(svg, buckets, max, width, height);
        else renderVertical(svg, buckets, max, width, height);

        container.replaceChildren(svg);
    }

    function renderVertical(svg, buckets, max, width, height) {
        const top = 34;
        const bottom = 56;
        const left = 42;
        const chartHeight = height - top - bottom;
        const slot = (width - left - 18) / buckets.length;
        const barWidth = Math.min(48, slot * .58);

        for (let index = 0; index < buckets.length; index++) {
            const bucket = buckets[index];
            const total = Number(bucket.totalEarnings || 0);
            const base = Number(bucket.basePay || 0);
            const tips = Number(bucket.tips || 0);
            const baseHeight = total ? chartHeight * (base / max) : 0;
            const tipsHeight = total ? chartHeight * (tips / max) : 0;
            const x = left + slot * index + (slot - barWidth) / 2;
            const bottomY = top + chartHeight;

            const group = svgElement('g');
            const title = svgElement('title');
            title.textContent = tooltip(bucket);
            group.append(title);
            group.append(svgElement('rect', {
                x, y: bottomY - baseHeight, width: barWidth, height: baseHeight,
                rx: 3, class: 'chart-base'
            }));
            group.append(svgElement('rect', {
                x, y: bottomY - baseHeight - tipsHeight, width: barWidth, height: tipsHeight,
                rx: 3, class: 'chart-tips'
            }));

            const value = svgElement('text', {x: x + barWidth / 2, y: Math.max(17, bottomY - baseHeight - tipsHeight - 8), class: 'chart-value'});
            value.textContent = money(total);
            group.append(value);
            const label = svgElement('text', {x: x + barWidth / 2, y: bottomY + 23, class: 'chart-label'});
            label.textContent = bucket.label.length > 14 ? `${bucket.label.slice(0, 12)}…` : bucket.label;
            group.append(label);
            svg.append(group);
        }
    }

    function renderHorizontal(svg, buckets, max, width) {
        const left = 128;
        const right = 80;
        const available = width - left - right;
        buckets.forEach((bucket, index) => {
            const total = Number(bucket.totalEarnings || 0);
            const baseWidth = total ? available * (Number(bucket.basePay || 0) / max) : 0;
            const tipsWidth = total ? available * (Number(bucket.tips || 0) / max) : 0;
            const y = 30 + index * 58;
            const group = svgElement('g');
            const title = svgElement('title');
            title.textContent = tooltip(bucket);
            group.append(title);

            const label = svgElement('text', {x: left - 10, y: y + 17, class: 'chart-station-label'});
            label.textContent = bucket.label.length > 16 ? `${bucket.label.slice(0, 14)}…` : bucket.label;
            group.append(label);
            group.append(svgElement('rect', {x: left, y, width: baseWidth, height: 24, rx: 3, class: 'chart-base'}));
            group.append(svgElement('rect', {x: left + baseWidth, y, width: tipsWidth, height: 24, rx: 3, class: 'chart-tips'}));
            const value = svgElement('text', {x: Math.min(width - 4, left + baseWidth + tipsWidth + 8), y: y + 17, class: 'chart-horizontal-value'});
            value.textContent = money(total);
            group.append(value);
            svg.append(group);
        });
    }

    function renderDonut(container, totals) {
        const base = Number(totals?.basePay || 0);
        const tips = Number(totals?.tips || 0);
        const total = base + tips;
        if (!total) {
            empty(container, t('js.charts.noEarningsToCompare'));
            return;
        }
        const tipPercent = tips / total * 100;
        const svg = svgElement('svg', {viewBox: '0 0 260 220', role: 'img',
            'aria-label': t('js.charts.tipsPercentLabel', tipPercent.toFixed(1))});
        const group = svgElement('g', {transform: 'rotate(-90 130 104)'});
        group.append(svgElement('circle', {cx: 130, cy: 104, r: 70, class: 'donut-base', 'stroke-width': 28, fill: 'none'}));
        group.append(svgElement('circle', {cx: 130, cy: 104, r: 70, class: 'donut-tips', 'stroke-width': 28, fill: 'none',
            'stroke-dasharray': `${tipPercent} ${100 - tipPercent}`, pathLength: 100}));
        svg.append(group);
        const percent = svgElement('text', {x: 130, y: 102, class: 'donut-percent'});
        percent.textContent = `${tipPercent.toFixed(1)}%`;
        svg.append(percent);
        const caption = svgElement('text', {x: 130, y: 126, class: 'donut-caption'});
        caption.textContent = t('js.charts.fromTips');
        svg.append(caption);
        container.replaceChildren(svg);
    }

    /** Average minutes a bucket's timed blocks finished before (or after) their scheduled end. */
    function pace(earlyMinutes) {
        if (earlyMinutes == null) return '—';
        if (earlyMinutes === 0) return t('js.charts.onTime');
        return earlyMinutes > 0 ? t('js.charts.minEarly', earlyMinutes) : t('js.charts.minOver', -earlyMinutes);
    }

    function optional(value) {
        return value == null ? '—' : Number(value).toFixed(1);
    }

    function renderTable(tbody, report, onDrill) {
        tbody.replaceChildren();
        const buckets = report?.buckets ?? [];
        if (!buckets.length) {
            const row = document.createElement('tr');
            const cell = document.createElement('td');
            cell.colSpan = 14;
            cell.className = 'table-empty';
            cell.textContent = t('js.charts.noShiftsMatch');
            row.append(cell);
            tbody.append(row);
            return;
        }

        for (const bucket of buckets) {
            const row = document.createElement('tr');
            row.tabIndex = 0;
            row.title = t('js.charts.filterTo', bucket.label);
            const values = [
                bucket.label, bucket.shifts, hours(bucket.minutesWorked),
                fullMoney(bucket.totalEarnings), Number(bucket.miles || 0).toFixed(1),
                fullMoney(bucket.mileageCost), fullMoney(bucket.expenses), fullMoney(bucket.deductions),
                fullMoney(bucket.netEarnings), fullMoney(bucket.netHourlyRate), pace(bucket.averageFinishedEarlyMinutes),
                optional(bucket.averageStops), optional(bucket.averageMinutesPerStop),
                bucket.returnsRate == null ? '—' : `${Number(bucket.returnsRate).toFixed(1)}%`
            ];
            values.forEach((value, index) => {
                const cell = document.createElement(index === 0 ? 'th' : 'td');
                if (index === 0) cell.scope = 'row';
                cell.textContent = value;
                row.append(cell);
            });
            const drill = () => onDrill(bucket, report.groupBy);
            row.addEventListener('click', drill);
            row.addEventListener('keydown', event => {
                if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault();
                    drill();
                }
            });
            tbody.append(row);
        }
    }

    function renderHourlyChart(container, report) {
        const buckets = report?.buckets ?? [];
        container.replaceChildren();
        if (!buckets.length) return empty(container, t('js.charts.noShiftsMatch'));
        const max = Math.max(...buckets.flatMap(bucket => [Number(bucket.hourlyRate || 0), Number(bucket.netHourlyRate || 0)]), 1);
        const list = document.createElement('div');
        list.className = 'hourly-comparison';
        buckets.forEach(bucket => {
            const row = document.createElement('div');
            row.className = 'hourly-comparison-row';
            const label = document.createElement('strong');
            label.textContent = bucket.label;
            const grossTrack = document.createElement('div');
            const grossBar = document.createElement('span');
            grossBar.className = 'gross-bar';
            grossBar.style.width = `${Math.max(0, Number(bucket.hourlyRate || 0) / max * 100)}%`;
            grossTrack.append(grossBar);
            const grossValue = document.createElement('small');
            grossValue.textContent = t('js.charts.gross', fullMoney(bucket.hourlyRate));
            const netTrack = document.createElement('div');
            const netBar = document.createElement('span');
            netBar.className = 'net-bar';
            netBar.style.width = `${Math.max(0, Number(bucket.netHourlyRate || 0) / max * 100)}%`;
            netTrack.append(netBar);
            const netValue = document.createElement('small');
            netValue.textContent = t('js.charts.estNet', fullMoney(bucket.netHourlyRate));
            row.append(label, grossTrack, grossValue, netTrack, netValue);
            list.append(row);
        });
        container.append(list);
    }

    const WEEKDAYS = ['js.charts.dayMonday', 'js.charts.dayTuesday', 'js.charts.dayWednesday', 'js.charts.dayThursday',
        'js.charts.dayFriday', 'js.charts.daySaturday', 'js.charts.daySunday'];
    const WEEKDAYS_SHORT = ['js.common.weekdayMon', 'js.common.weekdayTue', 'js.common.weekdayWed', 'js.common.weekdayThu',
        'js.common.weekdayFri', 'js.common.weekdaySat', 'js.common.weekdaySun'];

    function heatmapValue(value, metric) {
        if (metric === 'SHIFTS') return String(Number(value));
        return metric === 'AVERAGE_PAY' ? money(value) : t('js.common.perHour', fullMoney(value));
    }

    /**
     * Weekdays down the side and start-time bands across, each cell shaded by which colour step its value falls in.
     * A table rather than a drawing, so screen readers read it as one; cells with a single block are hatched.
     */
    function renderHeatmap(table, legend, response) {
        const cells = new Map(response.cells.map(cell => [`${cell.weekday}:${cell.band}`, cell]));
        const head = document.createElement('thead');
        const headRow = document.createElement('tr');
        const corner = document.createElement('th');
        corner.scope = 'col';
        corner.innerHTML = `<span class="sr-only">${escapeHtml(t('js.charts.weekday'))}</span>`;
        headRow.append(corner, ...response.bands.map(label => {
            const th = document.createElement('th');
            th.scope = 'col';
            th.textContent = label;
            return th;
        }));
        head.append(headRow);
        const body = document.createElement('tbody');
        WEEKDAYS.forEach((dayKey, index) => {
            const name = t(dayKey);
            const row = document.createElement('tr');
            const label = document.createElement('th');
            label.scope = 'row';
            label.textContent = t(WEEKDAYS_SHORT[index]);
            label.title = name;
            row.append(label);
            response.bands.forEach((band, bandIndex) => {
                const cell = cells.get(`${index + 1}:${bandIndex}`);
                const td = document.createElement('td');
                if (!cell) {
                    td.className = 'heatmap-empty';
                    td.innerHTML = `<span class="sr-only">${escapeHtml(t('js.charts.noBlocks'))}</span>`;
                } else {
                    const blocks = tn(cell.shifts, 'js.common.blocks');
                    td.textContent = heatmapValue(cell.value, response.metric);
                    td.title = t('js.charts.heatmapCell', name, band, heatmapValue(cell.value, response.metric), blocks);
                    if (cell.sparse) {
                        td.className = 'heatmap-sparse';
                    } else {
                        const step = response.scale.findIndex(bound => Number(cell.value) <= Number(bound));
                        td.dataset.level = String(step === -1 ? response.scale.length : step + 1);
                        td.dataset.steps = String(response.scale.length);
                    }
                    if (response.best && cell.weekday === response.best.weekday && cell.band === response.best.band) {
                        td.classList.add('is-best');
                    }
                }
                row.append(td);
            });
            body.append(row);
        });
        table.replaceChildren(head, body);
        legend.textContent = response.scale.length
            ? t('js.charts.heatmapLegend', response.scale.map(bound => heatmapValue(bound, response.metric)).join(', '))
            : t('js.charts.heatmapNeedTwo');
    }

    const STANDING_ROWS = {FANTASTIC: 22, GREAT: 50, FAIR: 78, AT_RISK: 106};
    const STANDING_LABELS = {FANTASTIC: 'js.standing.fantastic', GREAT: 'js.standing.great', FAIR: 'js.standing.fair', AT_RISK: 'js.standing.atRisk'};
    const STANDING_EVENT_LABELS = {LATE_FORFEIT: 'js.common.lateForfeit', FORFEITED: 'js.standing.forfeit', CANCELLED: 'js.common.cancelledByAmazon'};
    const DAY_MS = 86400000;

    /** Whole days since a fixed point, from an ISO date, so daylight saving never shifts a column. */
    function dayNumber(value) {
        const [year, month, day] = value.split('-').map(Number);
        return Math.round(Date.UTC(year, month - 1, day) / DAY_MS);
    }

    /**
     * The last stretch of days as steps: one row per standing level, a horizontal line for each entry until the next,
     * and forfeits, late forfeits and cancellations marked on a band below. Nothing is drawn before the first entry,
     * because no level is known there. It scales to its container, so it never widens the page.
     */
    function renderStanding(container, data) {
        container.replaceChildren();
        const first = dayNumber(data.from);
        const span = Math.max(1, dayNumber(data.to) - first);
        const left = 62;
        const width = 288;
        const x = date => left + Math.min(Math.max((dayNumber(date) - first) / span, 0), 1) * width;

        const svg = svgElement('svg', {viewBox: '0 0 360 170', class: 'standing-svg', role: 'img'});
        const counts = {LATE_FORFEIT: 0, FORFEITED: 0, CANCELLED: 0};
        data.events.forEach(event => counts[event.kind] += 1);
        const entries = data.entries;
        const summary = entries.length
            ? entries.map(entry => t('js.charts.standingFrom', t(STANDING_LABELS[entry.level]), shortDate(entry.recordedOn))).join(', ')
            : t('js.charts.noStandingLogged');
        svg.setAttribute('aria-label', t('js.charts.standingSummary', span + 1, summary,
            tn(counts.LATE_FORFEIT, 'js.charts.lateForfeits'), tn(counts.FORFEITED, 'js.charts.forfeits'),
            tn(counts.CANCELLED, 'js.charts.cancellations')));

        Object.entries(STANDING_ROWS).forEach(([level, y]) => {
            svg.append(svgElement('line', {x1: left, x2: left + width, y1: y, y2: y, class: 'standing-grid'}));
            const label = svgElement('text', {x: left - 6, y: y + 3.5, class: 'standing-axis-label', 'text-anchor': 'end'});
            label.textContent = t(STANDING_LABELS[level]);
            svg.append(label);
        });

        entries.forEach((entry, index) => {
            const y = STANDING_ROWS[entry.level];
            const start = x(entry.recordedOn);
            const end = index + 1 < entries.length ? x(entries[index + 1].recordedOn) : left + width;
            if (index > 0) {
                const before = STANDING_ROWS[entries[index - 1].level];
                if (before !== y) {
                    svg.append(svgElement('path', {d: `M${start} ${before}V${y}`, class: 'standing-step', 'data-level': entry.level}));
                }
            }
            svg.append(svgElement('path', {d: `M${start} ${y}H${end}`, class: 'standing-step', 'data-level': entry.level}));
            // A dot marks where each entry was logged, and shows a lone entry on the last day.
            if (dayNumber(entry.recordedOn) >= first) {
                svg.append(svgElement('circle', {cx: start, cy: y, r: 3, class: 'standing-dot', 'data-level': entry.level}));
            }
        });

        svg.append(svgElement('line', {x1: left, x2: left + width, y1: 134, y2: 134, class: 'standing-grid'}));
        const stacked = new Map();
        data.events.forEach(event => {
            const height = stacked.get(event.date) ?? 0;
            stacked.set(event.date, height + 1);
            const cx = x(event.date);
            const cy = 134 - height * 9;
            const mark = event.kind === 'CANCELLED'
                ? svgElement('circle', {cx, cy, r: 3.5, class: 'standing-event standing-event-cancelled'})
                : svgElement('path', {d: `M${cx} ${cy - 4.5}L${cx + 4.2} ${cy + 3.5}H${cx - 4.2}Z`,
                    class: `standing-event standing-event-${event.kind === 'LATE_FORFEIT' ? 'late' : 'forfeit'}`});
            const title = svgElement('title');
            title.textContent = `${t(STANDING_EVENT_LABELS[event.kind])} · ${event.station} · ${shortDate(event.date)}`;
            mark.append(title);
            svg.append(mark);
        });

        // First-of-month ticks, labelled under the axis.
        for (let day = first; day <= first + span; day++) {
            const date = new Date(day * DAY_MS);
            if (date.getUTCDate() !== 1) continue;
            const tick = x(date.toISOString().slice(0, 10)); // utc-day: day numbers here are UTC throughout
            svg.append(svgElement('line', {x1: tick, x2: tick, y1: 140, y2: 146, class: 'standing-grid'}));
            const label = svgElement('text', {x: tick, y: 162, class: 'standing-axis-label', 'text-anchor': 'middle'});
            label.textContent = date.toLocaleDateString(appLocale(), {month: 'short', timeZone: 'UTC'});
            svg.append(label);
        }
        container.append(svg);
    }

    function shortDate(value) {
        const [year, month, day] = value.split('-').map(Number);
        return new Date(year, month - 1, day).toLocaleDateString(appLocale(), {month: 'short', day: 'numeric'});
    }

    window.flexbuddyCharts = {renderEarningsChart, renderDonut, renderTable, renderHourlyChart, renderHeatmap, renderStanding};
})();
