const elements = {
    screenshotInput: document.querySelector('#screenshotInput'),
    themeToggleButton: document.querySelector('#themeToggleButton'),
    dashboardButton: document.querySelector('#dashboardButton'),
    dashboardNavButton: document.querySelector('#dashboardNavButton'),
    importNavButton: document.querySelector('#importNavButton'),
    importWorkspace: document.querySelector('#import-workspace'),
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
    activeFilterSummary: document.querySelector('#activeFilterSummary'),
    filterFrom: document.querySelector('#filterFrom'),
    filterTo: document.querySelector('#filterTo'),
    filterStation: document.querySelector('#filterStation'),
    filterQuery: document.querySelector('#filterQuery'),
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
    breakdownBody: document.querySelector('#breakdownBody'),
    reportError: document.querySelector('#reportError'),
    historyList: document.querySelector('#historyList'),
    showMoreButton: document.querySelector('#showMoreButton'),
    refreshButton: document.querySelector('#refreshButton'),
    successToast: document.querySelector('#successToast'),
    toastTitle: document.querySelector('#toastTitle'),
    toastMessage: document.querySelector('#toastMessage'),
    editModal: document.querySelector('#editModal'),
    editForm: document.querySelector('#editForm'),
    editStation: document.querySelector('#editStation'),
    editDate: document.querySelector('#editDate'),
    editStartTime: document.querySelector('#editStartTime'),
    editEndTime: document.querySelector('#editEndTime'),
    editBasePay: document.querySelector('#editBasePay'),
    editTips: document.querySelector('#editTips'),
    editError: document.querySelector('#editError'),
    closeEditButton: document.querySelector('#closeEditButton'),
    cancelEditButton: document.querySelector('#cancelEditButton'),
    saveEditButton: document.querySelector('#saveEditButton')
};

let selectedFileUrl;
let toastTimer;
let editingShiftId;
let lastFocusedElement;
let visibleShiftCount = 25;
let currentShifts = [];
let searchTimer;
let filterState = readFilterState();
let reportGroupBy = new URLSearchParams(window.location.search).get('groupBy') || 'month';
const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;

elements.themeToggleButton.addEventListener('click', toggleTheme);
elements.importNavButton.addEventListener('click', () => {
    setActiveNavigation('import');
    elements.importWorkspace.scrollIntoView({behavior: 'smooth', block: 'start'});
    openFilePicker();
});
elements.dashboardButton.addEventListener('click', showDashboard);
elements.dashboardNavButton.addEventListener('click', showDashboard);
elements.dropZone.addEventListener('click', openFilePicker);
elements.screenshotInput.addEventListener('change', event => {
    const [file] = event.target.files;
    if (file) processScreenshot(file);
});
elements.removeFileButton.addEventListener('click', resetImport);
elements.resetButton.addEventListener('click', resetImport);
elements.previewForm.addEventListener('submit', saveShift);
elements.refreshButton.addEventListener('click', loadDashboard);
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
elements.filterQuery.addEventListener('input', () => {
    window.clearTimeout(searchTimer);
    searchTimer = window.setTimeout(() => updateFilter('q', elements.filterQuery.value.trim()), 250);
});
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
elements.editModal.addEventListener('click', event => {
    if (event.target === elements.editModal) closeEditModal();
});
document.addEventListener('keydown', event => {
    if (event.key === 'Escape' && !elements.editModal.classList.contains('is-hidden')) {
        closeEditModal();
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

function showDashboard() {
    setActiveNavigation('dashboard');
    window.scrollTo({top: 0, behavior: 'smooth'});
}

function setActiveNavigation(activeItem) {
    elements.dashboardNavButton.classList.toggle('is-active', activeItem === 'dashboard');
    elements.importNavButton.classList.toggle('is-active', activeItem === 'import');
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
        const response = await fetch('/shifts/import-preview', {
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
    elements.station.value = preview.station ?? '';
    elements.date.value = preview.date ?? '';
    elements.startTime.value = trimTime(preview.startTime);
    elements.endTime.value = trimTime(preview.endTime);
    elements.basePay.value = preview.basePay ?? '';
    elements.tips.value = preview.tips ?? 0;
    elements.rawText.textContent = preview.rawText || 'No readable text was found.';

    elements.warningList.replaceChildren();
    const warnings = preview.warnings ?? [];
    for (const warning of warnings) {
        const item = document.createElement('li');
        item.textContent = warning;
        elements.warningList.append(item);
    }

    elements.warningNotice.classList.toggle('is-hidden', warnings.length === 0);
    elements.ocrDetails.classList.remove('is-hidden');
    elements.emptyPreview.classList.add('is-hidden');
    elements.previewForm.classList.remove('is-hidden');

    if (!preview.station) elements.station.focus();
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
        tips: Number(elements.tips.value)
    };

    setSaving(true);

    try {
        const response = await fetch('/shifts', {
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
    await Promise.all([loadStatistics(query), loadShifts(query), loadEarningsReport(buildQuery(false))]);
}

async function loadStatistics(query) {
    try {
        const response = await fetch(`/shifts/statistics?${query}`);
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
    } catch {
        [elements.totalEarnings, elements.totalShifts, elements.totalTime, elements.averagePay,
            elements.averageHourly, elements.baseTipsTotal].forEach(element => element.textContent = '—');
    }
}

async function loadShifts(query) {
    try {
        const response = await fetch(`/shifts?${query}`);
        if (!response.ok) throw new Error();
        currentShifts = await response.json();
        renderShifts(currentShifts);
        updateResultSummary(currentShifts);
    } catch {
        elements.historyList.innerHTML = '<div class="history-empty">Shifts could not be loaded.</div>';
        elements.resultsSummary.textContent = 'Shift results unavailable.';
    }
}

async function loadEarningsReport(query) {
    hideMessage(elements.reportError);
    elements.earningsChartTitle.textContent = `Earnings by ${reportGroupBy}`;
    try {
        const response = await fetch(`/shifts/reports/earnings?groupBy=${reportGroupBy}&${query}`);
        if (!response.ok) throw new Error(await response.text());
        const report = await response.json();
        window.flexbuddyCharts.renderEarningsChart(elements.earningsChart, report);
        window.flexbuddyCharts.renderDonut(elements.payMixChart, report.totals);
        window.flexbuddyCharts.renderTable(elements.breakdownBody, report, drillIntoBucket);
    } catch (error) {
        showMessage(elements.reportError, error.message || 'The earnings report could not be loaded.');
        elements.earningsChart.replaceChildren();
        elements.payMixChart.replaceChildren();
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

        row.innerHTML = `
            <div class="date-badge"><small>${escapeHtml(month)}</small><strong>${day}</strong></div>
            <div class="shift-main"><strong>${escapeHtml(shift.station)}</strong><span>${escapeHtml(weekday)} shift</span></div>
            <div class="shift-time"><strong>${formatTime(shift.startTime)} – ${formatTime(shift.endTime)}</strong><span>Scheduled time</span></div>
            <div class="shift-pay"><strong>${formatMoney(total)}</strong><span>${formatMinutes(shift.timeWorked)} · ${formatMoney(shift.hourlyRate)}/hr</span><span>${formatMoney(shift.basePay)} base · ${formatMoney(shift.tips)} tips</span></div>
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
    elements.editStation.value = shift.station ?? '';
    elements.editDate.value = shift.date ?? '';
    elements.editStartTime.value = trimTime(shift.startTime);
    elements.editEndTime.value = trimTime(shift.endTime);
    elements.editBasePay.value = shift.basePay ?? '';
    elements.editTips.value = shift.tips ?? 0;
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
        tips: Number(elements.editTips.value)
    };

    setEditSaving(true);

    try {
        const response = await fetch(`/shifts/${shiftId}`, {
            method: 'PUT',
            headers: csrfHeaders({'Content-Type': 'application/json'}),
            body: JSON.stringify(shift)
        });

        if (!response.ok) {
            const message = await response.text();
            throw new Error(message || 'The shift could not be updated. Check each field and try again.');
        }

        closeEditModal();
        showToast('Shift updated', 'Your changes have been saved.');
        await loadStations();
        await loadDashboard();
    } catch (error) {
        showMessage(elements.editError, error.message || 'The shift could not be updated.');
    } finally {
        setEditSaving(false);
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
    hideMessage(elements.uploadError);
    hideMessage(elements.saveError);
    setActiveNavigation('dashboard');
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

function showToast(title, message) {
    window.clearTimeout(toastTimer);
    elements.toastTitle.textContent = title;
    elements.toastMessage.textContent = message;
    elements.successToast.classList.remove('is-hidden');
    toastTimer = window.setTimeout(() => elements.successToast.classList.add('is-hidden'), 3500);
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
        q: params.get('q') || '',
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
    elements.filterQuery.value = filterState.q;
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
    filterState = {preset: 'all', from: '', to: '', station: '', q: '', sort: 'date', dir: 'desc'};
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
    for (const key of ['from', 'to', 'station', 'q']) {
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
        const response = await fetch('/shifts/stations');
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
    elements.resultsSummary.textContent = `${count} shift${count === 1 ? '' : 's'} · ${range}${station}`;
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

updateThemeToggle(document.documentElement.dataset.theme);
initializeFilters();
loadStations();
loadDashboard();
