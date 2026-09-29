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
    const money = new Intl.NumberFormat(undefined, {style: 'currency', currency: 'USD'});
    const format = value => value == null ? '—' : money.format(Number(value));
    const day = value => value
        ? new Date(`${value}T00:00:00`).toLocaleDateString(undefined, {month: 'short', day: 'numeric', year: 'numeric'})
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
        try {
            const response = await apiFetch(`/tax/summary?year=${encodeURIComponent(year)}`);
            if (!response.ok) throw new Error();
            render(await response.json());
        } catch {
            el.figures.replaceChildren(text('p', 'The tax summary could not be loaded.'));
        }
    }

    function render(summary) {
        el.percent.value = summary.percent ?? '';
        const rows = [
            ['Net earnings this year', format(summary.netYearToDate)],
            ['Set aside so far (estimate)', summary.percent == null ? 'Choose a percentage' : format(summary.reserveToDate)],
            ['Payments recorded', format(summary.paid)],
            ['Still to set aside (estimate)', summary.percent == null ? '—' : format(summary.remaining)],
            ['Next due date', summary.nextDueDate ? day(summary.nextDueDate) : 'None left this year']
        ];
        el.figures.replaceChildren(...rows.map(([label, value]) => {
            const row = document.createElement('div');
            row.append(text('dt', label), text('dd', value));
            return row;
        }));

        const body = el.quarters.querySelector('tbody');
        body.replaceChildren(...summary.quarters.map(quarter => {
            const row = document.createElement('tr');
            row.append(text('th', `Q${quarter.quarter}`), text('td', `${day(quarter.from)} – ${day(quarter.to)}`),
                text('td', day(quarter.dueDate)), text('td', format(quarter.net)),
                text('td', summary.percent == null ? '—' : format(quarter.setAside)), text('td', format(quarter.paid)));
            row.firstChild.scope = 'row';
            return row;
        }));

        el.payments.replaceChildren(...(summary.payments.length ? summary.payments.map(payment => {
            const row = document.createElement('li');
            const label = `${format(payment.amount)} on ${day(payment.paidOn)}${payment.quarter ? ` · Q${payment.quarter}` : ''}`;
            row.append(text('span', label));
            if (payment.note) row.append(text('small', payment.note));
            const remove = text('button', 'Delete');
            remove.type = 'button';
            remove.className = 'danger-text-button';
            remove.setAttribute('aria-label', `Delete the ${label} payment`);
            remove.addEventListener('click', () => deletePayment(payment, remove));
            row.append(remove);
            return row;
        }) : [text('li', 'No payments recorded for this year.')]));
    }

    async function savePercent(event) {
        event.preventDefault();
        el.percentError.classList.add('is-hidden');
        const value = el.percent.value.trim();
        try {
            const response = await apiFetch('/account/tax', {
                method: 'PUT',
                headers: csrfHeaders({'Content-Type': 'application/json'}),
                body: JSON.stringify({taxSetAsidePercent: value === '' ? null : Number(value)})
            });
            if (!response.ok) throw await responseError(response, 'Choose a percentage from 1 to 60, or leave it empty to turn this off.');
            showToast('Tax reserve saved', value === '' ? 'The set-aside estimate is off.' : `Setting aside ${value}% of net earnings.`);
            await load();
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
            if (!response.ok) throw await responseError(response, 'Enter the date and an amount above zero.');
            el.amount.value = '';
            el.note.value = '';
            showToast('Payment recorded', `Counted against your ${el.year.value} reserve.`);
            await load();
        } catch (error) {
            el.paymentError.textContent = error.message;
            el.paymentError.classList.remove('is-hidden');
        }
    }

    async function deletePayment(payment, button) {
        if (!window.confirm(`Delete the ${format(payment.amount)} payment from ${day(payment.paidOn)}?`)) return;
        button.disabled = true;
        try {
            const response = await apiFetch(`/tax/payments/${payment.id}`, {method: 'DELETE', headers: csrfHeaders()});
            if (!response.ok) throw new Error();
            showToast('Payment deleted', 'It no longer counts against your reserve.');
            await load();
        } catch {
            showToast('Not deleted', 'The payment could not be deleted.', {alert: true});
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
