(() => {
    const SVG_NS = 'http://www.w3.org/2000/svg';

    function svgElement(name, attributes = {}) {
        const node = document.createElementNS(SVG_NS, name);
        for (const [key, value] of Object.entries(attributes)) node.setAttribute(key, value);
        return node;
    }

    function money(value) {
        return new Intl.NumberFormat(undefined, {style: 'currency', currency: 'USD', maximumFractionDigits: 0})
            .format(Number(value || 0));
    }

    function fullMoney(value) {
        return new Intl.NumberFormat(undefined, {style: 'currency', currency: 'USD'}).format(Number(value || 0));
    }

    function hours(minutes) {
        return (Number(minutes || 0) / 60).toFixed(1);
    }

    function empty(container, message) {
        const text = document.createElement('p');
        text.className = 'chart-empty';
        text.textContent = message;
        container.replaceChildren(text);
    }

    function tooltip(bucket) {
        return [
            bucket.label,
            `${bucket.shifts} shift${bucket.shifts === 1 ? '' : 's'}`,
            `Base: ${fullMoney(bucket.basePay)}`,
            `Tips: ${fullMoney(bucket.tips)}`,
            `Total: ${fullMoney(bucket.totalEarnings)}`,
            `Hours: ${hours(bucket.minutesWorked)}`,
            `Hourly: ${fullMoney(bucket.hourlyRate)}`
        ].join('\n');
    }

    function renderEarningsChart(container, report) {
        const buckets = report?.buckets ?? [];
        if (!buckets.length) {
            empty(container, 'No earnings to chart for these filters.');
            return;
        }

        const stationMode = report.groupBy === 'station';
        const max = Math.max(...buckets.map(bucket => Number(bucket.totalEarnings || 0)), 1);
        const width = stationMode ? 720 : Math.max(620, buckets.length * 92 + 80);
        const height = stationMode ? Math.max(260, buckets.length * 58 + 45) : 330;
        const svg = svgElement('svg', {
            viewBox: `0 0 ${width} ${height}`,
            role: 'img',
            'aria-label': `Earnings grouped by ${report.groupBy}`,
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
            empty(container, 'No earnings to compare.');
            return;
        }
        const tipPercent = tips / total * 100;
        const svg = svgElement('svg', {viewBox: '0 0 260 220', role: 'img',
            'aria-label': `${tipPercent.toFixed(1)} percent of earnings came from tips`});
        const group = svgElement('g', {transform: 'rotate(-90 130 104)'});
        group.append(svgElement('circle', {cx: 130, cy: 104, r: 70, class: 'donut-base', 'stroke-width': 28, fill: 'none'}));
        group.append(svgElement('circle', {cx: 130, cy: 104, r: 70, class: 'donut-tips', 'stroke-width': 28, fill: 'none',
            'stroke-dasharray': `${tipPercent} ${100 - tipPercent}`, pathLength: 100}));
        svg.append(group);
        const percent = svgElement('text', {x: 130, y: 102, class: 'donut-percent'});
        percent.textContent = `${tipPercent.toFixed(1)}%`;
        svg.append(percent);
        const caption = svgElement('text', {x: 130, y: 126, class: 'donut-caption'});
        caption.textContent = 'from tips';
        svg.append(caption);
        container.replaceChildren(svg);
    }

    /** Average minutes a bucket's timed blocks finished before (or after) their scheduled end. */
    function pace(earlyMinutes) {
        if (earlyMinutes == null) return '—';
        if (earlyMinutes === 0) return 'On time';
        return earlyMinutes > 0 ? `${earlyMinutes} min early` : `${-earlyMinutes} min over`;
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
            cell.textContent = 'No shifts match these filters.';
            row.append(cell);
            tbody.append(row);
            return;
        }

        for (const bucket of buckets) {
            const row = document.createElement('tr');
            row.tabIndex = 0;
            row.title = `Filter to ${bucket.label}`;
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
        if (!buckets.length) return empty(container, 'No shifts match these filters.');
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
            grossValue.textContent = `Gross ${fullMoney(bucket.hourlyRate)}`;
            const netTrack = document.createElement('div');
            const netBar = document.createElement('span');
            netBar.className = 'net-bar';
            netBar.style.width = `${Math.max(0, Number(bucket.netHourlyRate || 0) / max * 100)}%`;
            netTrack.append(netBar);
            const netValue = document.createElement('small');
            netValue.textContent = `Est. net ${fullMoney(bucket.netHourlyRate)}`;
            row.append(label, grossTrack, grossValue, netTrack, netValue);
            list.append(row);
        });
        container.append(list);
    }

    const WEEKDAYS = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];

    function heatmapValue(value, metric) {
        if (metric === 'SHIFTS') return String(Number(value));
        return metric === 'AVERAGE_PAY' ? money(value) : `${fullMoney(value)}/hr`;
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
        corner.innerHTML = '<span class="sr-only">Weekday</span>';
        headRow.append(corner, ...response.bands.map(label => {
            const th = document.createElement('th');
            th.scope = 'col';
            th.textContent = label;
            return th;
        }));
        head.append(headRow);
        const body = document.createElement('tbody');
        WEEKDAYS.forEach((name, index) => {
            const row = document.createElement('tr');
            const label = document.createElement('th');
            label.scope = 'row';
            label.textContent = name.slice(0, 3);
            label.title = name;
            row.append(label);
            response.bands.forEach((band, bandIndex) => {
                const cell = cells.get(`${index + 1}:${bandIndex}`);
                const td = document.createElement('td');
                if (!cell) {
                    td.className = 'heatmap-empty';
                    td.innerHTML = '<span class="sr-only">No blocks</span>';
                } else {
                    const blocks = `${cell.shifts} ${cell.shifts === 1 ? 'block' : 'blocks'}`;
                    td.textContent = heatmapValue(cell.value, response.metric);
                    td.title = `${name} ${band}: ${heatmapValue(cell.value, response.metric)} from ${blocks}`;
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
            ? `Darker is higher. Steps up to ${response.scale.map(bound => heatmapValue(bound, response.metric)).join(', ')}. Hatched slots have only one block and are not ranked.`
            : 'Slots need at least two blocks to be ranked.';
    }

    window.flexbuddyCharts = {renderEarningsChart, renderDonut, renderTable, renderHourlyChart, renderHeatmap};
})();
