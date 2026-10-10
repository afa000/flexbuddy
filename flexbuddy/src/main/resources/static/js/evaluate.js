// Block value calculator: what an offer nets per hour at a station, next to what the driver usually nets there.
// Loaded before app.js and uses its shared helpers (apiFetch, csrfHeaders, formatMoney, escapeHtml) at call time.
(() => {
    const STORAGE_KEY = 'flexbuddy-evaluate';
    const DEBOUNCE_MS = 300;
    const WIDE_SCREEN = '(min-width: 621px)';
    const DEFAULT_SUMMARY = t('js.evaluate.defaultSummary');
    const EMPTY_HINT = t('js.evaluate.emptyHint');
    const VERDICTS = {
        ABOVE_USUAL: {label: t('js.evaluate.aboveUsual'), tone: 'above'},
        ABOUT_USUAL: {label: t('js.evaluate.aboutUsual'), tone: 'about'},
        BELOW_USUAL: {label: t('js.evaluate.belowUsual'), tone: 'below'}
    };
    let el;
    let selectedHours = '';
    let timer;
    let pending;
    let sequence = 0;

    function init() {
        el = {
            panel: document.querySelector('#evaluatePanel'),
            summary: document.querySelector('#evaluateSummary'),
            form: document.querySelector('#evaluateForm'),
            station: document.querySelector('#evaluateStation'),
            stations: document.querySelector('#evaluateStations'),
            hourChoices: document.querySelector('#evaluateHourChoices'),
            hours: document.querySelector('#evaluateHours'),
            pay: document.querySelector('#evaluatePay'),
            tips: document.querySelector('#evaluateTips'),
            result: document.querySelector('#evaluateResult')
        };
        if (!el.panel) return;
        restore();
        // Open on wider screens; on a phone it stays a one-line summary until tapped.
        if (window.matchMedia(WIDE_SCREEN).matches) el.panel.open = true;
        el.hourChoices.addEventListener('click', event => {
            const button = event.target.closest('button[data-hours]');
            if (!button) return;
            selectedHours = button.dataset.hours;
            el.hours.value = '';
            syncHourChoices();
            schedule();
        });
        el.hours.addEventListener('input', () => {
            selectedHours = el.hours.value;
            syncHourChoices();
            schedule();
        });
        [el.station, el.pay, el.tips].forEach(input => input.addEventListener('input', schedule));
        el.form.addEventListener('submit', event => {
            event.preventDefault();
            evaluateNow();
        });
    }

    function restore() {
        try {
            const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}');
            if (typeof saved.station === 'string') el.station.value = saved.station;
            if (saved.hours) {
                selectedHours = String(saved.hours);
                if (!el.hourChoices.querySelector(`button[data-hours="${CSS.escape(selectedHours)}"]`)) el.hours.value = selectedHours;
            }
        } catch {
            // Storage can be unavailable; the card simply starts empty.
        }
        syncHourChoices();
    }

    function remember(offer) {
        try {
            localStorage.setItem(STORAGE_KEY, JSON.stringify({station: offer.station, hours: offer.hours}));
        } catch {
            // Remembering the last offer is a convenience only.
        }
    }

    function syncHourChoices() {
        el.hourChoices.querySelectorAll('button[data-hours]').forEach(button => {
            const active = el.hours.value === '' && button.dataset.hours === selectedHours;
            button.classList.toggle('is-active', active);
            button.setAttribute('aria-pressed', String(active));
        });
    }

    /** Returns the offer when every required field is valid, or null. */
    function readOffer() {
        const station = el.station.value.trim();
        const hours = Number(selectedHours);
        const pay = Number(el.pay.value);
        const tipsText = el.tips.value.trim();
        const tips = Number(tipsText);
        if (!station || station.length > 255) return null;
        if (!selectedHours || !Number.isFinite(hours) || hours < 0.5 || hours > 12) return null;
        if (!el.pay.value || !Number.isFinite(pay) || pay <= 0) return null;
        if (tipsText && (!Number.isFinite(tips) || tips < 0)) return null;
        return {
            station,
            hours: Math.round(hours * 100) / 100,
            offeredPay: Math.round(pay * 100) / 100,
            expectedTips: tipsText ? Math.round(tips * 100) / 100 : null
        };
    }

    function schedule() {
        window.clearTimeout(timer);
        timer = window.setTimeout(evaluateNow, DEBOUNCE_MS);
    }

    async function evaluateNow() {
        window.clearTimeout(timer);
        const offer = readOffer();
        pending?.abort();
        if (!offer) {
            sequence++;
            showEmpty();
            return;
        }
        const current = ++sequence;
        pending = new AbortController();
        el.result.classList.add('is-loading');
        try {
            const response = await apiFetch('/shifts/evaluate', {
                method: 'POST',
                headers: csrfHeaders({'Content-Type': 'application/json'}),
                body: JSON.stringify(offer),
                signal: pending.signal
            });
            if (!response.ok) throw new Error(t('js.evaluate.evaluateFailedCheck'));
            const estimate = await response.json();
            if (current !== sequence) return;
            render(estimate, offer);
            remember(offer);
        } catch (error) {
            if (error?.name === 'AbortError' || current !== sequence) return;
            showError(error.message || t('js.evaluate.evaluateFailed'));
        } finally {
            if (current === sequence) el.result.classList.remove('is-loading');
        }
    }

    function showEmpty() {
        el.result.classList.remove('is-loading');
        el.result.innerHTML = `<p class="evaluate-empty">${EMPTY_HINT}</p>`;
        setSummary(DEFAULT_SUMMARY, false);
    }

    function showError(message) {
        el.result.innerHTML = `<p class="evaluate-empty evaluate-error" role="alert">${escapeHtml(message)}</p>`;
        setSummary(DEFAULT_SUMMARY, false);
    }

    function setSummary(text, hasResult) {
        el.summary.textContent = text;
        el.summary.classList.toggle('has-result', hasResult);
    }

    function render(estimate, offer) {
        const verdict = VERDICTS[estimate.verdict];
        const pill = verdict
            ? `<span class="verdict-pill verdict-${verdict.tone}">${escapeHtml(verdict.label)} · ${formatPercent(estimate.differencePercent)}</span>`
            : '';
        const miles = estimate.estimatedMiles === null ? '' : ` · ${t('js.common.miles', Number(estimate.estimatedMiles).toFixed(1))}`;
        el.result.innerHTML = `
            <div class="evaluate-headline">
                <div class="evaluate-figure"><strong>${formatMoney(estimate.estimatedNetHourly)}</strong><span>${escapeHtml(t('js.evaluate.netPerHour'))}</span></div>
                ${pill}
            </div>
            <dl class="evaluate-breakdown">
                <div><dt>${escapeHtml(t('js.evaluate.offer'))}</dt><dd>${formatMoney(offer.offeredPay)} · ${escapeHtml(t('js.common.perHour', formatMoney(estimate.offeredHourly)))}</dd></div>
                ${Number(estimate.surgePay) > 0 ? `<div class="evaluate-surge"><dt>${escapeHtml(t('js.evaluate.includesSurge', formatMoney(estimate.usualBaseHourly)))}</dt><dd>${formatMoney(estimate.surgePay)}</dd></div>` : ''}
                <div><dt>${escapeHtml(offer.expectedTips === null ? t('js.evaluate.tipsAverage') : t('js.evaluate.tips'))}</dt><dd>+${formatMoney(estimate.estimatedTips)}</dd></div>
                <div><dt>${escapeHtml(t('js.evaluate.vehicleCost'))}</dt><dd>−${formatMoney(estimate.estimatedVehicleCost)}${miles}</dd></div>
                <div><dt>${escapeHtml(t('js.evaluate.tollsParkingOther'))}</dt><dd>−${formatMoney(estimate.estimatedOtherExpenses)}</dd></div>
                <div class="evaluate-total"><dt>${escapeHtml(t('js.evaluate.estimatedNet'))}</dt><dd>${formatMoney(estimate.estimatedNet)}</dd></div>
            </dl>
            <p class="evaluate-basis">${basisText(estimate)}</p>`;
        setSummary(verdict
            ? t('js.evaluate.summaryVerdict', formatMoney(estimate.estimatedNetHourly), verdict.label.toLowerCase())
            : t('js.evaluate.summaryNet', formatMoney(estimate.estimatedNetHourly)), true);
    }

    function basisText(estimate) {
        const count = estimate.sampleSize;
        const station = escapeHtml(estimate.station);
        const blocks = tn(count, 'js.common.blocks');
        let text;
        if (estimate.basis === 'STATION_90_DAYS') text = tn(count, 'js.evaluate.basedOn90Days', station);
        else if (estimate.basis === 'STATION_ALL_TIME') text = t('js.evaluate.basedOnAll', count, station);
        else if (count > 0) text = t('js.evaluate.fewBlocks', station, blocks);
        else return t('js.evaluate.noBlocksYet');
        if (estimate.usualNetHourly !== null) {
            text += ' ' + t(estimate.basis === 'ACCOUNT' ? 'js.evaluate.usuallyNetOverall' : 'js.evaluate.usuallyNetThere',
                formatMoney(estimate.usualNetHourly));
        }
        if (estimate.estimatedMiles === null && Number(estimate.estimatedVehicleCost) === 0) {
            text += ' ' + t('js.evaluate.logMiles');
        }
        return text;
    }

    function formatPercent(value) {
        const number = Number(value);
        const sign = number > 0 ? '+' : number < 0 ? '−' : '±';
        return `${sign}${Math.abs(number).toFixed(1)}%`;
    }

    function setStations(stations) {
        if (!el?.stations) return;
        el.stations.replaceChildren(...stations.map(station => new Option(station)));
    }

    /** Opens the card and puts the cursor where the driver starts typing, for the home screen shortcut. */
    function open() {
        if (!el?.panel) return;
        el.panel.open = true;
        el.panel.scrollIntoView({block: 'start'});
        (el.station.value ? el.pay : el.station).focus({preventScroll: true});
    }

    init();
    window.flexbuddyEvaluate = {setStations, open};
})();
