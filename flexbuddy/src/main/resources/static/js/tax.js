// Taxes card on the account page: the set-aside percentage, the year's estimated reserve by quarter, and the
// payments the driver recorded. Every figure is arithmetic on the driver's own numbers and is labelled an estimate.
// Loaded after account.js and uses its helpers (apiFetch, csrfHeaders, showToast, responseError) at call time.
(() => {
    const card = document.querySelector('#taxes');
    if (!card) return;
    const el = {
        percentForm: card.querySelector('#taxPercentForm'),
        percent: card.querySelector('#taxPercent'),
        percentError: card.querySelector('#taxPercentError'),
        year: card.querySelector('#taxYear'),
        csv: card.querySelector('#taxCsvLink'),
        print: card.querySelector('#taxPrintLink'),
        remind: card.querySelector('#remindTax'),
        figures: card.querySelector('#taxFigures'),
        quarters: card.querySelector('#taxQuarters'),
        payments: card.querySelector('#taxPayments'),
        paymentForm: card.querySelector('#taxPaymentForm'),
        paidOn: card.querySelector('#taxPaidOn'),
        amount: card.querySelector('#taxPaymentAmount'),
        quarter: card.querySelector('#taxPaymentQuarter'),
        note: card.querySelector('#taxPaymentNote'),
        paymentError: card.querySelector('#taxPaymentError')
    };
    const money = new Intl.NumberFormat(appLocale(), {style: 'currency', currency: 'USD'});
    const format = value => value == null ? '—' : money.format(Number(value));
    const day = value => value
        ? new Date(`${value}T00:00:00`).toLocaleDateString(appLocale(), {month: 'short', day: 'numeric', year: 'numeric'})
        : '—';

    function init() {
        const now = new Date();
        const thisYear = now.getFullYear();
        el.year.replaceChildren(...[thisYear, thisYear - 1].map(year => new Option(String(year), String(year))));
        // The local date: toISOString is UTC, which is already tomorrow on a US evening.
        el.paidOn.value = [thisYear, now.getMonth() + 1, now.getDate()].map(part => String(part).padStart(2, '0')).join('-');
        el.year.addEventListener('change', load);
        el.percentForm.addEventListener('submit', savePercent);
        el.paymentForm.addEventListener('submit', addPayment);
        load();
    }

    async function load() {
        const year = el.year.value;
        el.csv.href = `/tax/summary.csv?year=${encodeURIComponent(year)}`;
        el.print.href = `/tax/year-summary?year=${encodeURIComponent(year)}`;
        try {
            const response = await apiFetch(`/tax/summary?year=${encodeURIComponent(year)}`);
            if (!response.ok) throw new Error();
            render(await response.json());
        } catch {
            el.figures.replaceChildren(text('p', t('js.tax.loadFailed')));
        }
    }

    function render(summary) {
        el.percent.value = summary.percent ?? '';
        const rows = [
            [t('js.tax.netEarnings'), format(summary.netYearToDate)],
            [t('js.tax.setAsideSoFar'), summary.percent == null ? t('js.tax.choosePercent') : format(summary.reserveToDate)],
            [t('js.tax.paymentsRecorded'), format(summary.paid)],
            [t('js.tax.stillToSetAside'), summary.percent == null ? '—' : format(summary.remaining)],
            [t('js.tax.nextDueDate'), summary.nextDueDate ? day(summary.nextDueDate) : t('js.tax.noneLeft')]
        ];
        el.figures.replaceChildren(...rows.map(([label, value]) => {
            const row = document.createElement('div');
            row.append(text('dt', label), text('dd', value));
            return row;
        }));

        const body = el.quarters.querySelector('tbody');
        body.replaceChildren(...summary.quarters.map(quarter => {
            const row = document.createElement('tr');
            row.append(text('th', t('js.tax.quarterShort', quarter.quarter)), text('td', `${day(quarter.from)} – ${day(quarter.to)}`),
                text('td', day(quarter.dueDate)), text('td', format(quarter.net)),
                text('td', summary.percent == null ? '—' : format(quarter.setAside)), text('td', format(quarter.paid)));
            row.firstChild.scope = 'row';
            return row;
        }));

        el.payments.replaceChildren(...(summary.payments.length ? summary.payments.map(payment => {
            const row = document.createElement('li');
            const label = payment.quarter
                ? t('js.tax.paymentOnQuarter', format(payment.amount), day(payment.paidOn), payment.quarter)
                : t('js.tax.paymentOn', format(payment.amount), day(payment.paidOn));
            row.append(text('span', label));
            if (payment.note) row.append(text('small', payment.note));
            const remove = text('button', t('js.common.delete'));
            remove.type = 'button';
            remove.className = 'danger-text-button';
            remove.setAttribute('aria-label', t('js.tax.deletePaymentLabel', label));
            remove.addEventListener('click', () => deletePayment(payment, remove));
            row.append(remove);
            return row;
        }) : [text('li', t('js.tax.noPayments'))]));
    }

    async function savePercent(event) {
        event.preventDefault();
        el.percentError.classList.add('is-hidden');
        const value = el.percent.value.trim();
        try {
            const response = await apiFetch('/account/tax', {
                method: 'PUT',
                headers: csrfHeaders({'Content-Type': 'application/json'}),
                body: JSON.stringify({taxSetAsidePercent: value === '' ? null : Number(value), remindTax: el.remind.checked})
            });
            if (!response.ok) throw await responseError(response, t('js.tax.percentInvalid'));
            const reminders = el.remind.checked ? t('js.tax.remindersOn') : t('js.tax.remindersOff');
            showToast(t('js.tax.savedTitle'), `${value === '' ? t('js.tax.estimateOff') : t('js.tax.settingAside', value)} ${reminders}`);
            await load();
            // The section's summary line reads the saved settings, so they are loaded again.
            await loadSettings();
        } catch (error) {
            el.percentError.textContent = error.message;
            el.percentError.classList.remove('is-hidden');
        }
    }

    async function addPayment(event) {
        event.preventDefault();
        el.paymentError.classList.add('is-hidden');
        try {
            const response = await apiFetch('/tax/payments', {
                method: 'POST',
                headers: csrfHeaders({'Content-Type': 'application/json'}),
                body: JSON.stringify({
                    taxYear: Number(el.year.value),
                    quarter: el.quarter.value === '' ? null : Number(el.quarter.value),
                    paidOn: el.paidOn.value,
                    amount: Number(el.amount.value),
                    note: el.note.value.trim() || null
                })
            });
            if (!response.ok) throw await responseError(response, t('js.tax.paymentInvalid'));
            el.amount.value = '';
            el.note.value = '';
            showToast(t('js.tax.recordedTitle'), t('js.tax.recordedMessage', el.year.value));
            await load();
        } catch (error) {
            el.paymentError.textContent = error.message;
            el.paymentError.classList.remove('is-hidden');
        }
    }

    async function deletePayment(payment, button) {
        if (!window.confirm(t('js.tax.confirmDelete', format(payment.amount), day(payment.paidOn)))) return;
        button.disabled = true;
        try {
            const response = await apiFetch(`/tax/payments/${payment.id}`, {method: 'DELETE', headers: csrfHeaders()});
            if (!response.ok) throw new Error();
            showToast(t('js.tax.deletedTitle'), t('js.tax.deletedMessage'));
            await load();
        } catch {
            showToast(t('js.tax.notDeletedTitle'), t('js.tax.notDeletedMessage'), {alert: true});
            button.disabled = false;
        }
    }

    function text(tag, value) {
        const element = document.createElement(tag);
        element.textContent = value;
        return element;
    }

    init();
})();
