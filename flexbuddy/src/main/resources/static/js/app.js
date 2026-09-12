const elements = {
    screenshotInput: document.querySelector('#screenshotInput'),
    themeToggleButton: document.querySelector('#themeToggleButton'),
    dashboardButton: document.querySelector('#dashboardButton'),
    dashboardNavButton: document.querySelector('#dashboardNavButton'),
    importNavButton: document.querySelector('#importNavButton'),
    expensesNavButton: document.querySelector('#expensesNavButton'),
    dashboardScreens: [...document.querySelectorAll('[data-screen="dashboard"]')],
    importScreen: document.querySelector('#importScreen'),
    expensesScreen: document.querySelector('#expensesScreen'),
    dropZone: document.querySelector('#dropZone'),
    fileCard: document.querySelector('#fileCard'),
    imagePreview: document.querySelector('#imagePreview'),
    fileName: document.querySelector('#fileName'),
    fileSize: document.querySelector('#fileSize'),
    processingRow: document.querySelector('#processingRow'),
    removeFileButton: document.querySelector('#removeFileButton'),
    uploadError: document.querySelector('#uploadError'),
    ocrDetails: document.querySelector('#ocrDetails'),
    rawText: document.querySelector('#rawText'),
    readQuality: document.querySelector('#readQuality'),
    readQualityValue: document.querySelector('#readQualityValue'),
    readQualityBar: document.querySelector('#readQualityBar'),
    emptyPreview: document.querySelector('#emptyPreview'),
    previewForm: document.querySelector('#previewForm'),
    warningNotice: document.querySelector('#warningNotice'),
    warningList: document.querySelector('#warningList'),
    station: document.querySelector('#station'),
    date: document.querySelector('#date'),
    startTime: document.querySelector('#startTime'),
    endTime: document.querySelector('#endTime'),
    basePay: document.querySelector('#basePay'),
    tips: document.querySelector('#tips'),
    miles: document.querySelector('#miles'),
    saveButton: document.querySelector('#saveButton'),
    resetButton: document.querySelector('#resetButton'),
    saveError: document.querySelector('#saveError'),
    totalEarnings: document.querySelector('#totalEarnings'),
    totalShifts: document.querySelector('#totalShifts'),
    totalTime: document.querySelector('#totalTime'),
    averagePay: document.querySelector('#averagePay'),
    averageHourly: document.querySelector('#averageHourly'),
    hourlyBreakdown: document.querySelector('#hourlyBreakdown'),
    baseTipsTotal: document.querySelector('#baseTipsTotal'),
    baseShareBar: document.querySelector('#baseShareBar'),
    tipsShareBar: document.querySelector('#tipsShareBar'),
    tipsShare: document.querySelector('#tipsShare'),
    netEarnings: document.querySelector('#netEarnings'),
    netMargin: document.querySelector('#netMargin'),
    netHourly: document.querySelector('#netHourly'),
    expenseTotal: document.querySelector('#expenseTotal'),
    totalMiles: document.querySelector('#totalMiles'),
    mileageCost: document.querySelector('#mileageCost'),
    activeFilterSummary: document.querySelector('#activeFilterSummary'),
    filterFrom: document.querySelector('#filterFrom'),
    filterTo: document.querySelector('#filterTo'),
    filterStation: document.querySelector('#filterStation'),
    filterSort: document.querySelector('#filterSort'),
    sortDirectionButton: document.querySelector('#sortDirectionButton'),
    clearFiltersButton: document.querySelector('#clearFiltersButton'),
    filterError: document.querySelector('#filterError'),
    resultsSummary: document.querySelector('#resultsSummary'),
    presetChips: [...document.querySelectorAll('.preset-chip')],
    groupButtons: [...document.querySelectorAll('[data-group]')],
    earningsChartTitle: document.querySelector('#earningsChartTitle'),
    earningsChart: document.querySelector('#earningsChart'),
    payMixChart: document.querySelector('#payMixChart'),
    hourlyChart: document.querySelector('#hourlyChart'),
    breakdownBody: document.querySelector('#breakdownBody'),
    reportError: document.querySelector('#reportError'),
    historyList: document.querySelector('#historyList'),
    showMoreButton: document.querySelector('#showMoreButton'),
    refreshButton: document.querySelector('#refreshButton'),
    exportCsvButton: document.querySelector('#exportCsvButton'),
    trashSection: document.querySelector('#trashSection'),
    trashList: document.querySelector('#trashList'),
    trashCount: document.querySelector('#trashCount'),
    emptyTrashButton: document.querySelector('#emptyTrashButton'),
    editModal: document.querySelector('#editModal'),
    editForm: document.querySelector('#editForm'),
    editStation: document.querySelector('#editStation'),
    editDate: document.querySelector('#editDate'),
    editStartTime: document.querySelector('#editStartTime'),
    editEndTime: document.querySelector('#editEndTime'),
    editBasePay: document.querySelector('#editBasePay'),
    editTips: document.querySelector('#editTips'),
    editMiles: document.querySelector('#editMiles'),
    linkedExpensesList: document.querySelector('#linkedExpensesList'),
    editError: document.querySelector('#editError'),
    closeEditButton: document.querySelector('#closeEditButton'),
    cancelEditButton: document.querySelector('#cancelEditButton'),
    saveEditButton: document.querySelector('#saveEditButton'),
    editTimestamps: document.querySelector('#editTimestamps'),
    deleteShiftButton: document.querySelector('#deleteShiftButton'),
    deleteConfirm: document.querySelector('#deleteConfirm'),
    cancelDeleteButton: document.querySelector('#cancelDeleteButton'),
    confirmDeleteButton: document.querySelector('#confirmDeleteButton'),
    confirmModal: document.querySelector('#confirmModal'),
    confirmTitle: document.querySelector('#confirmTitle'),
    confirmMessage: document.querySelector('#confirmMessage'),
    cancelConfirmButton: document.querySelector('#cancelConfirmButton'),
    acceptConfirmButton: document.querySelector('#acceptConfirmButton'),
    expenseForm: document.querySelector('#expenseForm'), expenseFormTitle: document.querySelector('#expenseFormTitle'),
    expenseDate: document.querySelector('#expenseDate'), expenseCategory: document.querySelector('#expenseCategory'),
    expenseAmount: document.querySelector('#expenseAmount'), expenseShift: document.querySelector('#expenseShift'),
    expenseNote: document.querySelector('#expenseNote'), expenseError: document.querySelector('#expenseError'),
    saveExpenseButton: document.querySelector('#saveExpenseButton'), cancelExpenseEdit: document.querySelector('#cancelExpenseEdit'),
    expenseFrom: document.querySelector('#expenseFrom'), expenseTo: document.querySelector('#expenseTo'),
    expenseFilterCategory: document.querySelector('#expenseFilterCategory'), expenseQuery: document.querySelector('#expenseQuery'),
    expenseList: document.querySelector('#expenseList'), expenseSummaryTotal: document.querySelector('#expenseSummaryTotal'),
    expenseSummaryCount: document.querySelector('#expenseSummaryCount'), expenseFuel: document.querySelector('#expenseFuel'),
    expenseRoad: document.querySelector('#expenseRoad'), exportExpensesButton: document.querySelector('#exportExpensesButton'),
    expenseCostMethod: document.querySelector('#expenseCostMethod'), expenseCategoryBreakdown: document.querySelector('#expenseCategoryBreakdown'),
    expenseTrashSection: document.querySelector('#expenseTrashSection'), expenseTrashList: document.querySelector('#expenseTrashList'),
    expenseTrashCount: document.querySelector('#expenseTrashCount'), emptyExpenseTrashButton: document.querySelector('#emptyExpenseTrashButton')
};

let selectedFileUrl;
let editingShiftId;
let editSnapshot;
let pendingConfirmAction;
let lastFocusedElement;
let visibleShiftCount = 25;
let currentShifts = [];
let dashboardAbort;
let reportAbort;
let filterState = readFilterState();
let reportGroupBy = new URLSearchParams(window.location.search).get('groupBy') || 'month';
let editingExpenseId;
const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
const previewFields = {
    station: elements.station,
    date: elements.date,
    startTime: elements.startTime,
    endTime: elements.endTime,
    basePay: elements.basePay,
    tips: elements.tips
};

async function apiFetch(url, options = {}) {
    const response = await window.fetch(url, options);
    const responsePath = new URL(response.url, window.location.origin).pathname;
    if (response.status === 401 || response.status === 403 || (response.redirected && responsePath === '/login')) {
        window.location.assign('/login?expired');
        throw new Error('Your session expired. Sign in again.');
    }
    return response;
}

Object.values(previewFields).forEach(input => input.addEventListener('focus', () => {
    const sourceLine = input.dataset.lineIndex;
    highlightRawLine(sourceLine === '' || sourceLine === undefined ? null : Number(sourceLine));
}));

elements.themeToggleButton.addEventListener('click', toggleTheme);
elements.importNavButton.addEventListener('click', showImportScreen);
elements.expensesNavButton.addEventListener('click', showExpensesScreen);
elements.dashboardButton.addEventListener('click', () => showDashboard());
elements.dashboardNavButton.addEventListener('click', () => showDashboard());
elements.dropZone.addEventListener('click', openFilePicker);
elements.screenshotInput.addEventListener('change', event => {
    const [file] = event.target.files;
    if (file) processScreenshot(file);
});
elements.removeFileButton.addEventListener('click', resetImport);
elements.resetButton.addEventListener('click', resetImport);
elements.previewForm.addEventListener('submit', saveShift);
elements.refreshButton.addEventListener('click', loadDashboard);
elements.exportCsvButton.addEventListener('click', event => {
    if (elements.exportCsvButton.getAttribute('aria-disabled') === 'true') event.preventDefault();
});
elements.showMoreButton.addEventListener('click', () => {
    visibleShiftCount += 20;
    renderShifts(currentShifts);
});
elements.clearFiltersButton.addEventListener('click', clearFilters);
elements.sortDirectionButton.addEventListener('click', () => {
    filterState.dir = filterState.dir === 'asc' ? 'desc' : 'asc';
    syncFilterControls();
    applyFilters();
});
elements.filterFrom.addEventListener('change', handleCustomDates);
elements.filterTo.addEventListener('change', handleCustomDates);
elements.filterStation.addEventListener('change', () => updateFilter('station', elements.filterStation.value));
elements.filterSort.addEventListener('change', () => updateFilter('sort', elements.filterSort.value));
elements.presetChips.forEach(chip => chip.addEventListener('click', () => applyPreset(chip.dataset.preset)));
elements.groupButtons.forEach(button => button.addEventListener('click', () => {
    reportGroupBy = button.dataset.group;
    syncFilterControls();
    persistFilterState();
    loadEarningsReport(buildQuery(false));
}));
elements.editForm.addEventListener('submit', saveEditedShift);
elements.closeEditButton.addEventListener('click', closeEditModal);
elements.cancelEditButton.addEventListener('click', closeEditModal);
elements.deleteShiftButton.addEventListener('click', showDeleteConfirmation);
elements.cancelDeleteButton.addEventListener('click', hideDeleteConfirmation);
elements.confirmDeleteButton.addEventListener('click', deleteEditedShift);
elements.trashSection.addEventListener('toggle', () => {
    if (elements.trashSection.open) loadTrash();
});
elements.emptyTrashButton.addEventListener('click', () => openConfirm(
    'Empty Recently deleted?',
    'Every deleted shift will be permanently removed. This cannot be undone.',
    emptyTrash
));
elements.expenseForm.addEventListener('submit', saveExpense);
elements.cancelExpenseEdit.addEventListener('click', resetExpenseForm);
elements.expenseDate.addEventListener('change', () => populateExpenseShifts(currentShifts));
[elements.expenseFrom, elements.expenseTo, elements.expenseFilterCategory].forEach(input => input.addEventListener('change', loadExpenses));
elements.expenseQuery.addEventListener('input', () => { clearTimeout(elements.expenseQuery._timer); elements.expenseQuery._timer = setTimeout(loadExpenses, 250); });
elements.expenseTrashSection.addEventListener('toggle', () => { if (elements.expenseTrashSection.open) loadExpenseTrash(); });
elements.emptyExpenseTrashButton.addEventListener('click', () => openConfirm(
    'Empty deleted expenses?',
    'Every deleted expense will be permanently removed. This cannot be undone.',
    emptyExpenseTrash
));
elements.cancelConfirmButton.addEventListener('click', closeConfirm);
elements.acceptConfirmButton.addEventListener('click', async () => {
    const action = pendingConfirmAction;
    closeConfirm();
    if (!action) return;
    try {
        await action();
    } catch (error) {
        showToast('Action failed', error.message || 'The action could not be completed.', {alert: true});
    }
});
elements.editModal.addEventListener('click', event => {
    if (event.target === elements.editModal) closeEditModal();
});
document.addEventListener('keydown', event => {
    if (event.key === 'Escape' && !elements.confirmModal.classList.contains('is-hidden')) {
        closeConfirm();
        return;
    }
    if (event.key === 'Escape' && !elements.editModal.classList.contains('is-hidden')) {
        closeEditModal();
        return;
    }
    if (event.key === 'Tab' && !elements.confirmModal.classList.contains('is-hidden')) {
        trapFocus(elements.confirmModal, event);
    } else if (event.key === 'Tab' && !elements.editModal.classList.contains('is-hidden')) {
        trapFocus(elements.editModal, event);
    }
});

for (const eventName of ['dragenter', 'dragover']) {
    elements.dropZone.addEventListener(eventName, event => {
        event.preventDefault();
        elements.dropZone.classList.add('is-dragging');
    });
}

for (const eventName of ['dragleave', 'drop']) {
    elements.dropZone.addEventListener(eventName, event => {
        event.preventDefault();
        elements.dropZone.classList.remove('is-dragging');
    });
}

elements.dropZone.addEventListener('drop', event => {
    const [file] = event.dataTransfer.files;
    if (file) processScreenshot(file);
});

function openFilePicker() {
    elements.screenshotInput.click();
}

function toggleTheme() {
    const nextTheme = document.documentElement.dataset.theme === 'light' ? 'dark' : 'light';
    document.documentElement.dataset.theme = nextTheme;
    localStorage.setItem('flexbuddy-theme', nextTheme);
    updateThemeToggle(nextTheme);
}

function updateThemeToggle(theme) {
    const label = theme === 'light' ? 'Switch to dark mode' : 'Switch to light mode';
    elements.themeToggleButton.setAttribute('aria-label', label);
    elements.themeToggleButton.title = label;
}

function showDashboard(smooth = true) {
    elements.dashboardScreens.forEach(section => section.classList.remove('is-hidden'));
    elements.importScreen.classList.add('is-hidden');
    elements.expensesScreen.classList.add('is-hidden');
    setActiveNavigation('dashboard');
    window.scrollTo({top: 0, behavior: smooth ? 'smooth' : 'auto'});
}

function showImportScreen() {
    elements.dashboardScreens.forEach(section => section.classList.add('is-hidden'));
    elements.importScreen.classList.remove('is-hidden');
    elements.expensesScreen.classList.add('is-hidden');
    setActiveNavigation('import');
    window.scrollTo({top: 0, behavior: 'smooth'});
}

function showExpensesScreen() {
    elements.dashboardScreens.forEach(section => section.classList.add('is-hidden'));
    elements.importScreen.classList.add('is-hidden');
    elements.expensesScreen.classList.remove('is-hidden');
    setActiveNavigation('expenses');
    if (!elements.expenseDate.value) elements.expenseDate.value = toIsoDate(new Date());
    loadExpenses();
    window.scrollTo({top: 0, behavior: 'smooth'});
}

function setActiveNavigation(activeItem) {
    elements.dashboardNavButton.classList.toggle('is-active', activeItem === 'dashboard');
    elements.importNavButton.classList.toggle('is-active', activeItem === 'import');
    elements.expensesNavButton.classList.toggle('is-active', activeItem === 'expenses');
    setCurrentPage(elements.dashboardNavButton, activeItem === 'dashboard');
    setCurrentPage(elements.importNavButton, activeItem === 'import');
    setCurrentPage(elements.expensesNavButton, activeItem === 'expenses');
}

function setCurrentPage(button, current) {
    if (current) button.setAttribute('aria-current', 'page');
    else button.removeAttribute('aria-current');
}

async function processScreenshot(file) {
    hideMessage(elements.uploadError);

    if (!['image/png', 'image/jpeg'].includes(file.type)) {
        showMessage(elements.uploadError, 'Choose a PNG or JPEG screenshot.');
        return;
    }

    if (file.size > 5 * 1024 * 1024) {
        showMessage(elements.uploadError, 'The screenshot must be 5 MB or smaller.');
        return;
    }

    showSelectedFile(file);
    setProcessing(true);

    const formData = new FormData();
    formData.append('screenshot', file);

    try {
        const response = await apiFetch('/shifts/import-preview', {
            method: 'POST',
            headers: csrfHeaders(),
            body: formData
        });

        if (!response.ok) {
            throw new Error(await response.text() || 'The screenshot could not be processed.');
        }

        const preview = await response.json();
        populatePreview(preview);
    } catch (error) {
        showMessage(elements.uploadError, error.message || 'The screenshot could not be processed.');
        elements.emptyPreview.classList.remove('is-hidden');
        elements.previewForm.classList.add('is-hidden');
    } finally {
        setProcessing(false);
    }
}

function showSelectedFile(file) {
    if (selectedFileUrl) URL.revokeObjectURL(selectedFileUrl);
    selectedFileUrl = URL.createObjectURL(file);
    elements.imagePreview.src = selectedFileUrl;
    elements.fileName.textContent = file.name;
    elements.fileSize.textContent = formatFileSize(file.size);
    elements.dropZone.classList.add('is-hidden');
    elements.fileCard.classList.remove('is-hidden');
}

function populatePreview(preview) {
    const candidate = preview.shifts?.[0];
    if (!candidate) throw new Error('No shift details were found in this screenshot.');

    setPreviewField('station', candidate.station);
    setPreviewField('date', candidate.date);
    setPreviewField('startTime', candidate.startTime, trimTime);
    setPreviewField('endTime', candidate.endTime, trimTime);
    setPreviewField('basePay', candidate.basePay);
    setPreviewField('tips', candidate.tips, value => value ?? 0);
    renderRawText(preview.lines, preview.rawText);
    renderReadQuality(preview.meanConfidence);

    const warnings = candidate.warnings ?? [];
    renderWarnings(warnings);

    elements.warningNotice.classList.toggle('is-hidden', warnings.length === 0);
    elements.ocrDetails.classList.remove('is-hidden');
    elements.emptyPreview.classList.add('is-hidden');
    elements.previewForm.classList.remove('is-hidden');

    const missingField = Object.entries(previewFields)
            .find(([name]) => candidate[name]?.level === 'MISSING');
    if (missingField) missingField[1].focus();
}

function setPreviewField(name, parsedField, transform = value => value ?? '') {
    const input = previewFields[name];
    const field = parsedField ?? {value: null, level: 'MISSING', confidence: null, lineIndex: null};
    input.value = transform(field.value);
    input.dataset.lineIndex = field.lineIndex ?? '';
    const wrapper = input.closest('.field');
    wrapper.dataset.confidence = field.level;
    wrapper.querySelector('.confidence-badge')?.remove();
    if (field.level !== 'HIGH' || field.confidence < 90) {
        const badge = document.createElement('small');
        badge.className = `confidence-badge confidence-${field.level.toLowerCase()}`;
        badge.textContent = field.level === 'MISSING' ? 'Missing' : `${field.confidence}% read`;
        wrapper.querySelector('span:first-child').append(badge);
    }
}

function renderWarnings(warnings) {
    elements.warningList.replaceChildren();
    for (const severity of ['ERROR', 'WARNING', 'INFO']) {
        const group = warnings.filter(warning => warning.severity === severity);
        if (!group.length) continue;
        const heading = document.createElement('li');
        heading.className = `warning-group warning-${severity.toLowerCase()}`;
        heading.textContent = severity === 'ERROR' ? 'Needs attention' : severity === 'WARNING' ? 'Please check' : 'For your information';
        elements.warningList.append(heading);
        for (const warning of group) {
            const item = document.createElement('li');
            item.className = 'warning-item';
            item.textContent = warning.message;
            if (warning.field && previewFields[warning.field]) {
                item.tabIndex = 0;
                item.setAttribute('role', 'button');
                const focusField = () => previewFields[warning.field].focus();
                item.addEventListener('click', focusField);
                item.addEventListener('keydown', event => {
                    if (event.key === 'Enter' || event.key === ' ') focusField();
                });
            }
            elements.warningList.append(item);
        }
    }
}

function renderReadQuality(confidence) {
    const score = Math.max(0, Math.min(100, Number(confidence) || 0));
    elements.readQualityValue.textContent = `${score}%`;
    elements.readQualityBar.style.width = `${score}%`;
    elements.readQuality.dataset.level = score >= 80 ? 'HIGH' : score >= 60 ? 'MEDIUM' : 'LOW';
    elements.readQuality.classList.remove('is-hidden');
}

function renderRawText(lines, fallbackText) {
    elements.rawText.replaceChildren();
    if (!lines?.length) {
        elements.rawText.textContent = fallbackText || 'No readable text was found.';
        return;
    }
    lines.forEach((line, position) => {
        const row = document.createElement('span');
        row.className = 'ocr-line';
        row.dataset.lineIndex = line.index;
        row.textContent = line.text;
        elements.rawText.append(row);
        if (position < lines.length - 1) elements.rawText.append(document.createTextNode('\n'));
    });
}

function highlightRawLine(lineIndex) {
    elements.rawText.querySelectorAll('.ocr-line').forEach(line => {
        line.classList.toggle('is-highlighted', lineIndex !== null && Number(line.dataset.lineIndex) === lineIndex);
    });
}

async function saveShift(event) {
    event.preventDefault();
    hideMessage(elements.saveError);

    if (!elements.previewForm.reportValidity()) return;

    const shift = {
        station: elements.station.value.trim(),
        date: elements.date.value,
        startTime: elements.startTime.value,
        endTime: elements.endTime.value,
        basePay: Number(elements.basePay.value),
        tips: Number(elements.tips.value),
        miles: elements.miles.value === '' ? null : Number(elements.miles.value)
    };

    setSaving(true);

    try {
        const response = await apiFetch('/shifts', {
            method: 'POST',
            headers: csrfHeaders({'Content-Type': 'application/json'}),
            body: JSON.stringify(shift)
        });

        if (!response.ok) {
            const message = await response.text();
            throw new Error(message || 'The shift could not be saved. Check each field and try again.');
        }

        resetImport();
        showToast('Shift added', 'Your earnings history is up to date.');
        await loadStations();
        await loadDashboard();
        showDashboard();
    } catch (error) {
        showMessage(elements.saveError, error.message || 'The shift could not be saved.');
    } finally {
        setSaving(false);
    }
}

async function loadDashboard() {
    if (!validateDateRange()) return;
    const query = buildQuery();
    persistFilterState();
    dashboardAbort?.abort();
    dashboardAbort = new AbortController();
    const signal = dashboardAbort.signal;
    await Promise.all([
        loadStatistics(query, signal),
        loadShifts(query, signal),
        loadEarningsReport(buildQuery(false))
    ]);
}

async function loadStatistics(query, signal) {
    try {
        const response = await apiFetch(`/shifts/statistics?${query}`, {signal});
        if (!response.ok) throw new Error();
        const statistics = await response.json();

        elements.totalEarnings.textContent = formatMoney(statistics.totalEarnings);
        elements.totalShifts.textContent = statistics.totalShifts ?? 0;
        elements.totalTime.textContent = formatMinutes(statistics.totalTimeWorked);
        elements.averagePay.textContent = formatMoney(statistics.averagePayPerShift);
        elements.averageHourly.textContent = formatMoney(statistics.averageHourlyEarnings);
        elements.hourlyBreakdown.textContent = `${formatMoney(statistics.averageHourlyBasePay)} base · ${formatMoney(statistics.averageHourlyTips)} tips`;
        elements.baseTipsTotal.textContent = `${formatMoney(statistics.totalBasePay)} · ${formatMoney(statistics.totalTips)}`;
        const tipShare = Number(statistics.tipsShareOfEarnings || 0);
        elements.baseShareBar.style.width = `${100 - tipShare}%`;
        elements.tipsShareBar.style.width = `${tipShare}%`;
        elements.tipsShare.textContent = `${tipShare.toFixed(1)}% from tips`;
        elements.netEarnings.textContent = formatMoney(statistics.netEarnings);
        elements.netHourly.textContent = `${formatMoney(statistics.netHourlyRate)}/hr`;
        elements.netMargin.textContent = `After deductions · ${Number(statistics.netMargin || 0).toFixed(1)}% margin`;
        elements.expenseTotal.textContent = `${formatMoney(statistics.totalExpenses)} cash expenses`;
        elements.totalMiles.textContent = `${Number(statistics.totalMiles || 0).toFixed(1)} mi`;
        elements.mileageCost.textContent = `${formatMoney(statistics.mileageCost)} mileage cost`;
    } catch (error) {
        if (error?.name === 'AbortError') return;
        [elements.totalEarnings, elements.totalShifts, elements.totalTime, elements.averagePay,
            elements.averageHourly, elements.hourlyBreakdown, elements.baseTipsTotal,
            elements.tipsShare, elements.netEarnings, elements.netHourly, elements.netMargin,
            elements.expenseTotal, elements.totalMiles, elements.mileageCost].forEach(element => element.textContent = '—');
        elements.baseShareBar.style.width = '0%';
        elements.tipsShareBar.style.width = '0%';
    }
}

async function loadShifts(query, signal) {
    try {
        const response = await apiFetch(`/shifts?${query}`, {signal});
        if (!response.ok) throw new Error();
        currentShifts = await response.json();
        renderShifts(currentShifts);
        populateExpenseShifts(currentShifts);
        updateResultSummary(currentShifts);
    } catch (error) {
        if (error?.name === 'AbortError') return;
        currentShifts = [];
        elements.historyList.innerHTML = '<div class="history-empty">Shifts could not be loaded.</div>';
        elements.showMoreButton.classList.add('is-hidden');
        elements.resultsSummary.textContent = 'Shift results unavailable.';
        elements.exportCsvButton.classList.add('is-disabled');
        elements.exportCsvButton.setAttribute('aria-disabled', 'true');
        elements.exportCsvButton.title = 'Shift results are unavailable';
    }
}

async function loadEarningsReport(query) {
    hideMessage(elements.reportError);
    elements.earningsChartTitle.textContent = `Earnings by ${reportGroupBy}`;
    reportAbort?.abort();
    reportAbort = new AbortController();
    const signal = reportAbort.signal;
    try {
        const response = await apiFetch(`/shifts/reports/earnings?groupBy=${reportGroupBy}&${query}`, {signal});
        if (!response.ok) throw new Error(await response.text());
        const report = await response.json();
        window.flexbuddyCharts.renderEarningsChart(elements.earningsChart, report);
        window.flexbuddyCharts.renderDonut(elements.payMixChart, report.totals);
        window.flexbuddyCharts.renderHourlyChart(elements.hourlyChart, report);
        window.flexbuddyCharts.renderTable(elements.breakdownBody, report, drillIntoBucket);
    } catch (error) {
        if (error?.name === 'AbortError') return;
        showMessage(elements.reportError, error.message || 'The earnings report could not be loaded.');
        elements.earningsChart.replaceChildren();
        elements.payMixChart.replaceChildren();
        elements.hourlyChart.replaceChildren();
        elements.breakdownBody.replaceChildren();
    }
}

function renderShifts(shifts) {
    elements.historyList.replaceChildren();

    if (!shifts.length) {
        const empty = document.createElement('div');
        empty.className = 'history-empty';
        empty.innerHTML = 'No shifts match these filters. <button class="text-button" type="button">Clear filters</button>';
        empty.querySelector('button').addEventListener('click', clearFilters);
        elements.historyList.append(empty);
        elements.showMoreButton.classList.add('is-hidden');
        return;
    }

    const visibleShifts = shifts.slice(0, visibleShiftCount);

    for (const shift of visibleShifts) {
        const row = document.createElement('article');
        row.className = 'shift-row';

        const date = parseLocalDate(shift.date);
        const month = date.toLocaleDateString(undefined, {month: 'short'});
        const day = date.getDate();
        const weekday = date.toLocaleDateString(undefined, {weekday: 'short'});
        const total = shift.totalPay ?? (Number(shift.basePay || 0) + Number(shift.tips || 0));
        const edited = shift.createdAt && shift.updatedAt
                && new Date(shift.updatedAt) - new Date(shift.createdAt) > 60000;

        row.innerHTML = `
            <div class="date-badge"><small>${escapeHtml(month)}</small><strong>${day}</strong></div>
            <div class="shift-main"><strong>${escapeHtml(shift.station)}${edited ? '<small class="edited-tag">edited</small>' : ''}</strong><span>${escapeHtml(weekday)} shift</span></div>
            <div class="shift-time"><strong>${formatTime(shift.startTime)} – ${formatTime(shift.endTime)}</strong><span>Scheduled time</span></div>
            <div class="shift-pay"><strong>${formatMoney(total)}</strong><span>${formatMinutes(shift.timeWorked)} · ${formatMoney(shift.hourlyRate)}/hr gross</span><span>${shift.miles == null ? '' : `${Number(shift.miles).toFixed(1)} mi · `}${formatMoney(shift.netPay)} est. net · ${formatMoney(shift.netHourlyRate)}/hr est. net</span></div>
            <button class="edit-shift-button" type="button">
                <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m4 20 4.2-1 10.9-10.9a2.1 2.1 0 0 0-3-3L5.2 16 4 20Zm10.5-13.5 3 3"/></svg>
            </button>
        `;

        const editButton = row.querySelector('.edit-shift-button');
        editButton.setAttribute('aria-label', `Edit ${shift.station} shift on ${shift.date}`);
        editButton.addEventListener('click', () => openEditModal(shift, editButton));
        elements.historyList.append(row);
    }
    elements.showMoreButton.classList.toggle('is-hidden', visibleShiftCount >= shifts.length);
}

function openEditModal(shift, trigger) {
    editingShiftId = shift.id;
    lastFocusedElement = trigger;
    editSnapshot = {...shift};
    elements.editStation.value = shift.station ?? '';
    elements.editDate.value = shift.date ?? '';
    elements.editStartTime.value = trimTime(shift.startTime);
    elements.editEndTime.value = trimTime(shift.endTime);
    elements.editBasePay.value = shift.basePay ?? '';
    elements.editTips.value = shift.tips ?? 0;
    elements.editMiles.value = shift.miles ?? '';
    loadLinkedExpenses(shift.id);
    elements.editTimestamps.textContent = timestampSummary(shift);
    elements.editTimestamps.title = `Created ${formatTimestamp(shift.createdAt)} · Updated ${formatTimestamp(shift.updatedAt)}`;
    hideDeleteConfirmation();
    hideMessage(elements.editError);
    elements.editModal.classList.remove('is-hidden');
    document.body.classList.add('modal-open');
    elements.editStation.focus();
}

function closeEditModal() {
    elements.editModal.classList.add('is-hidden');
    document.body.classList.remove('modal-open');
    elements.editForm.reset();
    hideMessage(elements.editError);
    editingShiftId = undefined;
    editSnapshot = undefined;

    if (lastFocusedElement?.isConnected) lastFocusedElement.focus();
    lastFocusedElement = undefined;
}

async function saveEditedShift(event) {
    event.preventDefault();
    hideMessage(elements.editError);

    if (!elements.editForm.reportValidity() || editingShiftId === undefined) return;

    const shiftId = editingShiftId;
    const shift = {
        station: elements.editStation.value.trim(),
        date: elements.editDate.value,
        startTime: elements.editStartTime.value,
        endTime: elements.editEndTime.value,
        basePay: Number(elements.editBasePay.value),
        tips: Number(elements.editTips.value),
        miles: elements.editMiles.value === '' ? null : Number(elements.editMiles.value)
    };

    setEditSaving(true);

    try {
        const response = await apiFetch(`/shifts/${shiftId}`, {
            method: 'PUT',
            headers: csrfHeaders({'Content-Type': 'application/json'}),
            body: JSON.stringify(shift)
        });

        if (!response.ok) {
            const message = await response.text();
            throw new Error(message || 'The shift could not be updated. Check each field and try again.');
        }

        const previous = editSnapshot;
        closeEditModal();
        showToast('Shift updated', 'Your changes have been saved.', {
            actionLabel: 'Undo',
            duration: 8000,
            onAction: () => undoEdit(shiftId, previous)
        });
        await loadStations();
        await loadDashboard();
    } catch (error) {
        showMessage(elements.editError, error.message || 'The shift could not be updated.');
    } finally {
        setEditSaving(false);
    }
}

function showDeleteConfirmation() {
    elements.editForm.querySelector('.form-actions').classList.add('is-hidden');
    elements.deleteConfirm.classList.remove('is-hidden');
    elements.cancelDeleteButton.focus();
}

function hideDeleteConfirmation() {
    elements.editForm.querySelector('.form-actions').classList.remove('is-hidden');
    elements.deleteConfirm.classList.add('is-hidden');
}

async function deleteEditedShift() {
    const id = editingShiftId;
    if (id === undefined) return;
    elements.confirmDeleteButton.disabled = true;
    try {
        const response = await apiFetch(`/shifts/${id}`, {method: 'DELETE', headers: csrfHeaders()});
        if (!response.ok) throw new Error(await response.text());
        const batch = response.headers.get('X-Delete-Batch');
        closeEditModal();
        showToast('Shift deleted', 'It is available in Recently deleted for 30 days.', {
            actionLabel: 'Undo',
            duration: 8000,
            alert: true,
            onAction: () => restoreBatch(batch)
        });
        await loadStations();
        await loadDashboard();
    } catch (error) {
        showMessage(elements.editError, error.message || 'The shift could not be deleted.');
    } finally {
        elements.confirmDeleteButton.disabled = false;
    }
}

async function undoEdit(id, previous) {
    if (!previous) return;
    const response = await apiFetch(`/shifts/${id}`, {
        method: 'PUT',
        headers: csrfHeaders({'Content-Type': 'application/json'}),
        body: JSON.stringify({
            station: previous.station,
            date: previous.date,
            startTime: previous.startTime,
            endTime: previous.endTime,
            basePay: previous.basePay,
            tips: previous.tips,
            miles: previous.miles
        })
    });
    if (!response.ok) throw new Error(await response.text());
    showToast('Edit undone', 'The previous shift values were restored.');
    await loadDashboard();
}

async function restoreBatch(batch) {
    if (!batch) return;
    const response = await apiFetch(`/shifts/restore-batch/${encodeURIComponent(batch)}`, {
        method: 'POST', headers: csrfHeaders()
    });
    if (!response.ok) throw new Error(await response.text());
    showToast('Shift restored', 'The shift is back in your history.');
    await loadStations();
    await loadDashboard();
    if (elements.trashSection.open) await loadTrash();
}

async function loadTrash() {
    elements.trashList.innerHTML = '<p>Loading recently deleted shifts…</p>';
    try {
        const response = await apiFetch('/shifts/trash');
        if (!response.ok) throw new Error(await response.text());
        renderTrash(await response.json());
    } catch (error) {
        elements.trashList.innerHTML = `<p>${escapeHtml(error.message || 'Recently deleted could not be loaded.')}</p>`;
    }
}

function renderTrash(shifts) {
    elements.trashCount.textContent = shifts.length ? `(${shifts.length})` : '';
    elements.emptyTrashButton.classList.toggle('is-hidden', shifts.length === 0);
    elements.trashList.replaceChildren();
    if (!shifts.length) {
        elements.trashList.innerHTML = '<p>There are no recently deleted shifts.</p>';
        return;
    }
    shifts.forEach(shift => {
        const row = document.createElement('article');
        row.className = 'trash-row';
        row.innerHTML = `<div><strong>${escapeHtml(shift.station)} · ${formatDate(shift.date)}</strong><span>Deleted ${formatTimestamp(shift.deletedAt)}</span></div><div><button class="text-button restore-trash" type="button">Restore</button><button class="danger-text-button permanent-trash" type="button">Delete permanently</button></div>`;
        row.querySelector('.restore-trash').addEventListener('click', () => restoreTrashShift(shift.id));
        row.querySelector('.permanent-trash').addEventListener('click', () => openConfirm(
            'Delete this shift permanently?',
            `${shift.station} on ${formatDate(shift.date)} will be removed forever.`,
            () => permanentlyDeleteShift(shift.id)
        ));
        elements.trashList.append(row);
    });
}

async function restoreTrashShift(id) {
    const response = await apiFetch(`/shifts/${id}/restore`, {method: 'POST', headers: csrfHeaders()});
    if (!response.ok) throw new Error(await response.text());
    showToast('Shift restored', 'The shift is back in your history.');
    await loadTrash();
    await loadStations();
    await loadDashboard();
}

async function permanentlyDeleteShift(id) {
    const response = await apiFetch(`/shifts/trash/${id}`, {method: 'DELETE', headers: csrfHeaders()});
    if (!response.ok) throw new Error(await response.text());
    showToast('Shift permanently deleted', 'The shift can no longer be restored.', {alert: true});
    await loadTrash();
}

async function emptyTrash() {
    const response = await apiFetch('/shifts/trash', {method: 'DELETE', headers: csrfHeaders()});
    if (!response.ok) throw new Error(await response.text());
    const result = await response.json();
    showToast('Recently deleted emptied', `${result.deleted} shifts were permanently removed.`, {alert: true});
    await loadTrash();
}

function openConfirm(title, message, action) {
    pendingConfirmAction = action;
    lastFocusedElement = document.activeElement;
    elements.confirmTitle.textContent = title;
    elements.confirmMessage.textContent = message;
    elements.confirmModal.classList.remove('is-hidden');
    document.body.classList.add('modal-open');
    elements.cancelConfirmButton.focus();
}

function closeConfirm() {
    elements.confirmModal.classList.add('is-hidden');
    document.body.classList.remove('modal-open');
    pendingConfirmAction = undefined;
    if (lastFocusedElement?.isConnected) lastFocusedElement.focus();
}

function trapFocus(container, event) {
    const focusable = [...container.querySelectorAll('button, input, select, textarea, a[href]')]
            .filter(element => !element.disabled && element.getClientRects().length > 0);
    if (!focusable.length) return;
    const first = focusable[0];
    const last = focusable.at(-1);
    if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
    }
}

function resetImport() {
    if (selectedFileUrl) {
        URL.revokeObjectURL(selectedFileUrl);
        selectedFileUrl = undefined;
    }
    elements.screenshotInput.value = '';
    elements.previewForm.reset();
    elements.fileCard.classList.add('is-hidden');
    elements.dropZone.classList.remove('is-hidden');
    elements.previewForm.classList.add('is-hidden');
    elements.emptyPreview.classList.remove('is-hidden');
    elements.ocrDetails.classList.add('is-hidden');
    elements.ocrDetails.open = false;
    elements.readQuality.classList.add('is-hidden');
    elements.warningList.replaceChildren();
    Object.values(previewFields).forEach(input => {
        delete input.dataset.lineIndex;
        const wrapper = input.closest('.field');
        delete wrapper.dataset.confidence;
        wrapper.querySelector('.confidence-badge')?.remove();
    });
    hideMessage(elements.uploadError);
    hideMessage(elements.saveError);
}

function setProcessing(processing) {
    elements.processingRow.classList.toggle('is-hidden', !processing);
    elements.removeFileButton.disabled = processing;
}

function setSaving(saving) {
    elements.saveButton.disabled = saving;
    elements.saveButton.querySelector('span').textContent = saving ? 'Adding shift…' : 'Add shift';
}

function setEditSaving(saving) {
    elements.saveEditButton.disabled = saving;
    elements.saveEditButton.querySelector('span').textContent = saving ? 'Saving changes…' : 'Save changes';
}

function showMessage(element, message) {
    element.textContent = message;
    element.classList.remove('is-hidden');
}

function hideMessage(element) {
    element.textContent = '';
    element.classList.add('is-hidden');
}

window.flexbuddyToast.init({
    toast: '#successToast',
    title: '#toastTitle',
    message: '#toastMessage',
    action: '#toastAction',
    countdown: '#toastCountdown'
});

function showToast(title, message, options = {}) {
    window.flexbuddyToast.show(title, message, options);
}

function trimTime(time) {
    return time ? time.slice(0, 5) : '';
}

function formatTime(time) {
    if (!time) return '—';
    const [hours, minutes] = time.split(':').map(Number);
    return new Intl.DateTimeFormat(undefined, {hour: 'numeric', minute: '2-digit'})
        .format(new Date(2000, 0, 1, hours, minutes));
}

function formatMoney(value) {
    return new Intl.NumberFormat(undefined, {
        style: 'currency',
        currency: 'USD'
    }).format(Number(value || 0));
}

function formatMinutes(value) {
    const totalMinutes = Number(value || 0);
    const hours = Math.floor(totalMinutes / 60);
    const minutes = totalMinutes % 60;
    return `${hours}h ${minutes}m`;
}

function formatFileSize(bytes) {
    if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function parseLocalDate(value) {
    if (!value) return new Date();
    const [year, month, day] = value.split('-').map(Number);
    return new Date(year, month - 1, day);
}

function escapeHtml(value) {
    const element = document.createElement('span');
    element.textContent = value ?? '';
    return element.innerHTML;
}

function readFilterState() {
    const params = new URLSearchParams(window.location.search);
    return {
        preset: params.get('preset') || (params.has('from') || params.has('to') ? 'custom' : 'all'),
        from: params.get('from') || '',
        to: params.get('to') || '',
        station: params.get('station') || '',
        sort: params.get('sort') || 'date',
        dir: params.get('dir') === 'asc' ? 'asc' : 'desc'
    };
}

function initializeFilters() {
    if (!['all', 'week', 'month', 'year', '30days', 'custom'].includes(filterState.preset)) {
        filterState.preset = filterState.from || filterState.to ? 'custom' : 'all';
    }
    if (!['station', 'week', 'month', 'year'].includes(reportGroupBy)) reportGroupBy = 'month';
    syncFilterControls();
}

function syncFilterControls() {
    elements.filterFrom.value = filterState.from;
    elements.filterTo.value = filterState.to;
    elements.filterStation.value = filterState.station;
    elements.filterSort.value = filterState.sort;
    const custom = filterState.preset === 'custom';
    elements.filterFrom.disabled = !custom;
    elements.filterTo.disabled = !custom;
    elements.sortDirectionButton.textContent = filterState.dir === 'asc' ? '↑ Asc' : '↓ Desc';
    elements.sortDirectionButton.setAttribute('aria-label', `Sort ${filterState.dir === 'asc' ? 'ascending' : 'descending'}`);
    elements.presetChips.forEach(chip => chip.classList.toggle('is-active', chip.dataset.preset === filterState.preset));
    elements.groupButtons.forEach(button => button.classList.toggle('is-active', button.dataset.group === reportGroupBy));
    const presetLabel = elements.presetChips.find(chip => chip.dataset.preset === filterState.preset)?.textContent || 'Custom';
    elements.activeFilterSummary.textContent = `Showing: ${presetLabel}`;
}

function applyPreset(preset) {
    filterState.preset = preset;
    const today = startOfToday();
    let from = '';
    let to = '';
    if (preset === 'week') {
        const day = today.getDay() || 7;
        const monday = new Date(today);
        monday.setDate(today.getDate() - day + 1);
        from = toIsoDate(monday);
        to = toIsoDate(today);
    } else if (preset === 'month') {
        from = toIsoDate(new Date(today.getFullYear(), today.getMonth(), 1));
        to = toIsoDate(today);
    } else if (preset === 'year') {
        from = `${today.getFullYear()}-01-01`;
        to = toIsoDate(today);
    } else if (preset === '30days') {
        const start = new Date(today);
        start.setDate(start.getDate() - 29);
        from = toIsoDate(start);
        to = toIsoDate(today);
    } else if (preset === 'custom') {
        from = filterState.from;
        to = filterState.to;
    }
    filterState.from = from;
    filterState.to = to;
    syncFilterControls();
    if (preset === 'custom') elements.filterFrom.focus();
    applyFilters();
}

function handleCustomDates() {
    filterState.preset = 'custom';
    filterState.from = elements.filterFrom.value;
    filterState.to = elements.filterTo.value;
    syncFilterControls();
    applyFilters();
}

function updateFilter(name, value) {
    filterState[name] = value;
    applyFilters();
}

function applyFilters() {
    visibleShiftCount = 25;
    syncFilterControls();
    loadDashboard();
}

function clearFilters() {
    filterState = {preset: 'all', from: '', to: '', station: '', sort: 'date', dir: 'desc'};
    visibleShiftCount = 25;
    syncFilterControls();
    loadDashboard();
}

function validateDateRange() {
    if (filterState.from && filterState.to && filterState.from > filterState.to) {
        showMessage(elements.filterError, 'The start date must be on or before the end date.');
        return false;
    }
    hideMessage(elements.filterError);
    return true;
}

function buildQuery(includeSort = true) {
    const params = new URLSearchParams();
    for (const key of ['from', 'to', 'station']) {
        if (filterState[key]) params.set(key, filterState[key]);
    }
    if (includeSort) {
        params.set('sort', filterState.sort);
        params.set('dir', filterState.dir);
    }
    return params;
}

function persistFilterState() {
    const params = buildQuery();
    params.set('preset', filterState.preset);
    params.set('groupBy', reportGroupBy);
    window.history.replaceState({}, '', `${window.location.pathname}?${params}`);
}

async function loadStations() {
    try {
        const response = await apiFetch('/shifts/stations');
        if (!response.ok) throw new Error();
        const stations = await response.json();
        elements.filterStation.replaceChildren(new Option('All stations', ''));
        stations.forEach(station => elements.filterStation.add(new Option(station, station)));
        if (filterState.station && !stations.some(station => station.toLowerCase() === filterState.station.toLowerCase())) {
            elements.filterStation.add(new Option(filterState.station, filterState.station));
        }
        elements.filterStation.value = filterState.station;
    } catch {
        elements.filterStation.replaceChildren(new Option('All stations', ''));
    }
}

function updateResultSummary(shifts) {
    const count = shifts.length;
    let range = 'All dates';
    if (filterState.from || filterState.to) {
        range = `${filterState.from ? formatDate(filterState.from) : 'Beginning'} – ${filterState.to ? formatDate(filterState.to) : 'Today'}`;
    } else if (count) {
        const dates = shifts.map(shift => shift.date).sort();
        range = `${formatDate(dates[0])} – ${formatDate(dates.at(-1))}`;
    }
    const station = filterState.station ? ` · ${filterState.station}` : '';
    const expenseScope = filterState.station
        ? 'expenses linked to these shifts only'
        : 'expenses included by date';
    elements.resultsSummary.textContent = `${count} shift${count === 1 ? '' : 's'} · ${range}${station} · ${expenseScope} · export includes these`;
    elements.exportCsvButton.href = `/shifts/export.csv?${buildQuery()}`;
    elements.exportCsvButton.classList.toggle('is-disabled', count === 0);
    elements.exportCsvButton.setAttribute('aria-disabled', String(count === 0));
    elements.exportCsvButton.title = count === 0
            ? 'No shifts match the active filters'
            : `Export ${count} matching shifts`;
}

function drillIntoBucket(bucket, groupBy) {
    if (groupBy === 'station') {
        filterState.station = bucket.label;
    } else {
        filterState.preset = 'custom';
        filterState.from = bucket.periodStart || '';
        filterState.to = bucket.periodEnd || '';
    }
    syncFilterControls();
    applyFilters();
    document.querySelector('.filter-panel').scrollIntoView({behavior: 'smooth', block: 'start'});
}

function formatDate(value) {
    return parseLocalDate(value).toLocaleDateString(undefined, {month: 'short', day: 'numeric', year: 'numeric'});
}

function formatTimestamp(value) {
    return value ? new Date(value).toLocaleString() : 'Unknown';
}

function timestampSummary(shift) {
    if (!shift.createdAt) return '';
    const added = new Date(shift.createdAt).toLocaleDateString(undefined, {
        month: 'short', day: 'numeric', year: 'numeric'
    });
    const edited = shift.updatedAt && new Date(shift.updatedAt) - new Date(shift.createdAt) > 60000
            ? ` · Last edited ${new Date(shift.updatedAt).toLocaleDateString(undefined, {month: 'short', day: 'numeric', year: 'numeric'})}`
            : '';
    return `Added ${added}${edited}`;
}

function startOfToday() {
    const now = new Date();
    return new Date(now.getFullYear(), now.getMonth(), now.getDate());
}

function toIsoDate(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

function csrfHeaders(headers = {}) {
    if (csrfToken && csrfHeader) {
        headers[csrfHeader] = csrfToken;
    }
    return headers;
}

function populateExpenseShifts(shifts) {
    const selected = elements.expenseShift.value;
    elements.expenseShift.innerHTML = '<option value="">No linked shift</option>';
    const selectedDate = elements.expenseDate.value;
    shifts.filter(shift => !selectedDate || shift.date === selectedDate || String(shift.id) === selected).forEach(shift => {
        const option = document.createElement('option');
        option.value = shift.id;
        option.textContent = `${formatDate(shift.date)} · ${shift.station}`;
        elements.expenseShift.append(option);
    });
    if ([...elements.expenseShift.options].some(option => option.value === selected)) elements.expenseShift.value = selected;
}

function expenseQueryString() {
    const params = new URLSearchParams();
    if (elements.expenseFrom.value) params.set('from', elements.expenseFrom.value);
    if (elements.expenseTo.value) params.set('to', elements.expenseTo.value);
    if (elements.expenseFilterCategory.value) params.set('category', elements.expenseFilterCategory.value);
    if (elements.expenseQuery.value.trim()) params.set('q', elements.expenseQuery.value.trim());
    return params.toString();
}

async function loadExpenses() {
    const query = expenseQueryString();
    elements.exportExpensesButton.href = `/expenses/export.csv${query ? `?${query}` : ''}`;
    try {
        const [listResponse, summaryResponse, settingsResponse] = await Promise.all([
            apiFetch(`/expenses${query ? `?${query}` : ''}`),
            apiFetch(`/expenses/summary${query ? `?${query}` : ''}`),
            apiFetch('/account/settings')
        ]);
        if (!listResponse.ok) throw new Error(await listResponse.text());
        if (!summaryResponse.ok) throw new Error(await summaryResponse.text());
        if (!settingsResponse.ok) throw new Error(await settingsResponse.text());
        renderExpenses(await listResponse.json());
        renderExpenseSummary(await summaryResponse.json());
        const settings = await settingsResponse.json();
        elements.expenseCostMethod.textContent = settings.vehicleCostMethod === 'ACTUAL_EXPENSES'
            ? 'Actual expenses' : `Standard mileage · ${formatMoney(settings.mileageRate)}/mi`;
    } catch (error) {
        elements.expenseList.innerHTML = `<p class="history-empty">${escapeHtml(error.message || 'Expenses could not be loaded.')}</p>`;
    }
}

function renderExpenseSummary(summary) {
    elements.expenseSummaryTotal.textContent = formatMoney(summary.total);
    elements.expenseSummaryCount.textContent = `${summary.count || 0} ${summary.count === 1 ? 'entry' : 'entries'}`;
    elements.expenseFuel.textContent = formatMoney(summary.byCategory?.FUEL);
    elements.expenseRoad.textContent = formatMoney(Number(summary.byCategory?.TOLL || 0) + Number(summary.byCategory?.PARKING || 0));
    const categories = ['FUEL', 'TOLL', 'PARKING', 'MAINTENANCE', 'OTHER'];
    const max = Math.max(...categories.map(category => Number(summary.byCategory?.[category] || 0)), 1);
    elements.expenseCategoryBreakdown.replaceChildren(...categories.map(category => {
        const row = document.createElement('div');
        const label = document.createElement('span');
        label.textContent = category.charAt(0) + category.slice(1).toLowerCase();
        const track = document.createElement('div');
        const bar = document.createElement('span');
        bar.style.width = `${Number(summary.byCategory?.[category] || 0) / max * 100}%`;
        track.append(bar);
        const value = document.createElement('strong');
        value.textContent = formatMoney(summary.byCategory?.[category]);
        row.append(label, track, value);
        return row;
    }));
}

function renderExpenses(expenses) {
    elements.expenseList.replaceChildren();
    if (!expenses.length) {
        elements.expenseList.innerHTML = '<p class="history-empty">No expenses match these filters.</p>';
        return;
    }
    expenses.forEach(expense => {
        const row = document.createElement('article');
        row.className = 'expense-row';
        row.innerHTML = `<div class="expense-category-icon">${escapeHtml(expense.category.slice(0, 1))}</div><div class="expense-main"><strong>${escapeHtml(expense.category.replace('_', ' '))}</strong><span>${formatDate(expense.date)}${expense.station ? ` · ${escapeHtml(expense.station)}` : ''}</span><small>${escapeHtml(expense.note || 'No note')}</small></div><strong class="expense-amount">${formatMoney(expense.amount)}</strong><div class="expense-row-actions"><button class="text-button edit-expense" type="button">Edit</button><button class="danger-text-button delete-expense" type="button">Delete</button></div>`;
        row.querySelector('.edit-expense').addEventListener('click', () => editExpense(expense));
        row.querySelector('.delete-expense').addEventListener('click', () => deleteExpense(expense));
        elements.expenseList.append(row);
    });
}

function editExpense(expense) {
    editingExpenseId = expense.id;
    elements.expenseFormTitle.textContent = 'Edit expense';
    elements.saveExpenseButton.textContent = 'Save expense';
    elements.cancelExpenseEdit.classList.remove('is-hidden');
    elements.expenseDate.value = expense.date;
    elements.expenseCategory.value = expense.category;
    elements.expenseAmount.value = expense.amount;
    elements.expenseShift.value = expense.shiftId ?? '';
    elements.expenseNote.value = expense.note ?? '';
    elements.expenseForm.scrollIntoView({behavior: 'smooth', block: 'center'});
}

function resetExpenseForm() {
    editingExpenseId = undefined;
    elements.expenseForm.reset();
    elements.expenseDate.value = toIsoDate(new Date());
    elements.expenseFormTitle.textContent = 'Log an expense';
    elements.saveExpenseButton.textContent = 'Add expense';
    elements.cancelExpenseEdit.classList.add('is-hidden');
    hideMessage(elements.expenseError);
}

async function saveExpense(event) {
    event.preventDefault();
    hideMessage(elements.expenseError);
    if (!elements.expenseForm.reportValidity()) return;
    const body = {date: elements.expenseDate.value, category: elements.expenseCategory.value,
        amount: Number(elements.expenseAmount.value), note: elements.expenseNote.value.trim() || null,
        shiftId: elements.expenseShift.value ? Number(elements.expenseShift.value) : null};
    const id = editingExpenseId;
    elements.saveExpenseButton.disabled = true;
    try {
        const response = await apiFetch(id ? `/expenses/${id}` : '/expenses', {method: id ? 'PUT' : 'POST',
            headers: csrfHeaders({'Content-Type': 'application/json'}), body: JSON.stringify(body)});
        if (!response.ok) throw new Error(await response.text());
        resetExpenseForm();
        showToast(id ? 'Expense updated' : 'Expense added', 'Net earnings have been recalculated.');
        await Promise.all([loadExpenses(), loadDashboard()]);
    } catch (error) { showMessage(elements.expenseError, error.message || 'The expense could not be saved.'); }
    finally { elements.saveExpenseButton.disabled = false; }
}

async function deleteExpense(expense) {
    const response = await apiFetch(`/expenses/${expense.id}`, {method: 'DELETE', headers: csrfHeaders()});
    if (!response.ok) return showToast('Delete failed', await response.text(), {alert: true});
    const batch = response.headers.get('X-Delete-Batch');
    showToast('Expense deleted', 'It is available in Recently deleted for 30 days.', {actionLabel: 'Undo', duration: 8000,
        onAction: async () => { await apiFetch(`/expenses/restore-batch/${encodeURIComponent(batch)}`, {method:'POST', headers:csrfHeaders()}); await loadExpenses(); await loadDashboard(); }});
    await Promise.all([loadExpenses(), loadDashboard()]);
}

async function loadExpenseTrash() {
    const response = await apiFetch('/expenses/trash');
    if (!response.ok) return;
    const expenses = await response.json();
    elements.expenseTrashCount.textContent = expenses.length ? `(${expenses.length})` : '';
    elements.emptyExpenseTrashButton.classList.toggle('is-hidden', expenses.length === 0);
    elements.expenseTrashList.replaceChildren();
    if (!expenses.length) return elements.expenseTrashList.innerHTML = '<p>There are no recently deleted expenses.</p>';
    expenses.forEach(expense => {
        const row = document.createElement('article'); row.className = 'trash-row';
        row.innerHTML = `<div><strong>${escapeHtml(expense.category)} · ${formatMoney(expense.amount)}</strong><span>${formatDate(expense.date)}</span></div><div><button class="text-button" type="button">Restore</button><button class="danger-text-button" type="button">Delete permanently</button></div>`;
        const [restore, remove] = row.querySelectorAll('button');
        restore.addEventListener('click', async () => { await apiFetch(`/expenses/${expense.id}/restore`, {method:'POST',headers:csrfHeaders()}); await loadExpenseTrash(); await loadExpenses(); await loadDashboard(); });
        remove.addEventListener('click', () => openConfirm('Delete this expense permanently?', 'This expense cannot be recovered.', async () => { await apiFetch(`/expenses/trash/${expense.id}`, {method:'DELETE',headers:csrfHeaders()}); await loadExpenseTrash(); }));
        elements.expenseTrashList.append(row);
    });
}

async function emptyExpenseTrash() {
    const response = await apiFetch('/expenses/trash', {method: 'DELETE', headers: csrfHeaders()});
    if (!response.ok) throw new Error(await response.text() || 'Deleted expenses could not be emptied.');
    await loadExpenseTrash();
    showToast('Deleted expenses emptied', 'The deleted expenses were permanently removed.');
}

async function loadLinkedExpenses(shiftId) {
    elements.linkedExpensesList.innerHTML = '<small>Loading…</small>';
    try {
        const response = await apiFetch(`/shifts/${shiftId}/expenses`);
        if (!response.ok) throw new Error();
        const expenses = await response.json();
        elements.linkedExpensesList.innerHTML = expenses.length
            ? expenses.map(expense => `<small>${escapeHtml(expense.category)} · ${formatMoney(expense.amount)} · ${formatDate(expense.date)}</small>`).join('')
            : '<small>No expenses linked to this shift.</small>';
    } catch { elements.linkedExpensesList.innerHTML = '<small>Linked expenses could not be loaded.</small>'; }
}

updateThemeToggle(document.documentElement.dataset.theme);
initializeFilters();
showDashboard(false);
loadStations();
loadDashboard();
