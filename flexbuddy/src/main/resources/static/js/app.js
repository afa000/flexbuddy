const elements = {
    screenshotInput: document.querySelector('#screenshotInput'),
    themeToggleButton: document.querySelector('#themeToggleButton'),
    homeLink: document.querySelector('#homeLink'),
    dashboardNavButton: document.querySelector('#dashboardNavButton'),
    reportsNavButton: document.querySelector('#reportsNavButton'),
    expensesNavButton: document.querySelector('#expensesNavButton'),
    dashboardScreens: [...document.querySelectorAll('[data-screen="dashboard"]')],
    importScreen: document.querySelector('#importScreen'),
    reportsScreen: document.querySelector('#reportsScreen'),
    rangeChip: document.querySelector('#rangeChip'),
    rangeSheet: document.querySelector('#rangeSheet'),
    rangeDoneButton: document.querySelector('#rangeDoneButton'),
    chartsTab: document.querySelector('#chartsTab'),
    historyTab: document.querySelector('#historyTab'),
    chartsPanel: document.querySelector('#chartsPanel'),
    historyPanel: document.querySelector('#historyPanel'),
    expensesScreen: document.querySelector('#expensesScreen'),
    dropZone: document.querySelector('#dropZone'),
    fileCard: document.querySelector('#fileCard'),
    imagePreview: document.querySelector('#imagePreview'),
    fileName: document.querySelector('#fileName'),
    fileSize: document.querySelector('#fileSize'),
    processingRow: document.querySelector('#processingRow'),
    removeFileButton: document.querySelector('#removeFileButton'),
    fileActions: document.querySelector('#fileActions'),
    chooseAgainButton: document.querySelector('#chooseAgainButton'),
    cancelImportButton: document.querySelector('#cancelImportButton'),
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
    skipImportButton: document.querySelector('#skipImportButton'),
    importQueue: document.querySelector('#importQueue'),
    importQueueStatus: document.querySelector('#importQueueStatus'),
    importQueueList: document.querySelector('#importQueueList'),
    resumeQueueButton: document.querySelector('#resumeQueueButton'),
    stopQueueButton: document.querySelector('#stopQueueButton'),
    finishQueueButton: document.querySelector('#finishQueueButton'),
    saveError: document.querySelector('#saveError'),
    totalEarnings: document.querySelector('#totalEarnings'),
    totalShifts: document.querySelector('#totalShifts'),
    totalShiftsDetail: document.querySelector('#totalShiftsDetail'),
    totalTime: document.querySelector('#totalTime'),
    averagePay: document.querySelector('#averagePay'),
    averageHourly: document.querySelector('#averageHourly'),
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
    heatmapCard: document.querySelector('#heatmapCard'),
    heatmapSummary: document.querySelector('#heatmapSummary'),
    heatmapMetric: document.querySelector('#heatmapMetric'),
    heatmapTable: document.querySelector('#heatmapTable'),
    heatmapLegend: document.querySelector('#heatmapLegend'),
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
    timeWorkedDetail: document.querySelector('#timeWorkedDetail'),
    editActualTimes: document.querySelector('#editActualTimes'),
    editOdometer: document.querySelector('#editOdometer'),
    editRoute: document.querySelector('#editRoute'),
    editRouteSummary: document.querySelector('#editRouteSummary'),
    editStops: document.querySelector('#editStops'),
    editPackages: document.querySelector('#editPackages'),
    editReturns: document.querySelector('#editReturns'),
    editOdometerSummary: document.querySelector('#editOdometerSummary'),
    editOdometerStart: document.querySelector('#editOdometerStart'),
    editOdometerEnd: document.querySelector('#editOdometerEnd'),
    editActualSummary: document.querySelector('#editActualSummary'),
    editActualStart: document.querySelector('#editActualStart'),
    editActualEnd: document.querySelector('#editActualEnd'),
    sameAsScheduledButton: document.querySelector('#sameAsScheduledButton'),
    linkedExpensesList: document.querySelector('#linkedExpensesList'),
    editError: document.querySelector('#editError'),
    closeEditButton: document.querySelector('#closeEditButton'),
    cancelEditButton: document.querySelector('#cancelEditButton'),
    saveEditButton: document.querySelector('#saveEditButton'),
    editTimestamps: document.querySelector('#editTimestamps'),
    deleteShiftButton: document.querySelector('#deleteShiftButton'),
    duplicateShiftButton: document.querySelector('#duplicateShiftButton'),
    deleteConfirm: document.querySelector('#deleteConfirm'),
    cancelDeleteButton: document.querySelector('#cancelDeleteButton'),
    confirmDeleteButton: document.querySelector('#confirmDeleteButton'),
    notWorkedButton: document.querySelector('#notWorkedButton'),
    notWorkedConfirm: document.querySelector('#notWorkedConfirm'),
    notWorkedCleared: document.querySelector('#notWorkedCleared'),
    cancelNotWorkedButton: document.querySelector('#cancelNotWorkedButton'),
    confirmNotWorkedButton: document.querySelector('#confirmNotWorkedButton'),
    confirmModal: document.querySelector('#confirmModal'),
    confirmTitle: document.querySelector('#confirmTitle'),
    confirmMessage: document.querySelector('#confirmMessage'),
    cancelConfirmButton: document.querySelector('#cancelConfirmButton'),
    acceptConfirmButton: document.querySelector('#acceptConfirmButton'),
    expenseForm: document.querySelector('#expenseForm'), expenseFormTitle: document.querySelector('#expenseFormTitle'),
    expenseDate: document.querySelector('#expenseDate'), expenseCategory: document.querySelector('#expenseCategory'),
    expenseCategoryHint: document.querySelector('#expenseCategoryHint'),
    expenseAmount: document.querySelector('#expenseAmount'), expenseShift: document.querySelector('#expenseShift'),
    expenseNote: document.querySelector('#expenseNote'), expenseError: document.querySelector('#expenseError'),
    saveExpenseButton: document.querySelector('#saveExpenseButton'), cancelExpenseEdit: document.querySelector('#cancelExpenseEdit'),
    expenseFrom: document.querySelector('#expenseFrom'), expenseTo: document.querySelector('#expenseTo'),
    expenseFilterCategory: document.querySelector('#expenseFilterCategory'), expenseQuery: document.querySelector('#expenseQuery'),
    expenseList: document.querySelector('#expenseList'), expenseSummaryTotal: document.querySelector('#expenseSummaryTotal'),
    expenseSummaryCount: document.querySelector('#expenseSummaryCount'), expenseFuel: document.querySelector('#expenseFuel'),
    expenseRoad: document.querySelector('#expenseRoad'), exportExpensesButton: document.querySelector('#exportExpensesButton'),
    expenseCostMethod: document.querySelector('#expenseCostMethod'), expenseFuelNote: document.querySelector('#expenseFuelNote'), expenseCategoryBreakdown: document.querySelector('#expenseCategoryBreakdown'),
    expenseTrashSection: document.querySelector('#expenseTrashSection'), expenseTrashList: document.querySelector('#expenseTrashList'),
    expenseTrashCount: document.querySelector('#expenseTrashCount'), emptyExpenseTrashButton: document.querySelector('#emptyExpenseTrashButton'),
    scheduleNavButton: document.querySelector('#scheduleNavButton'),
    scheduleScreen: document.querySelector('#scheduleScreen'),
    forfeitsMonth: document.querySelector('#forfeitsMonth'),
    goalCard: document.querySelector('#goalCard'),
    nextPayoutAmount: document.querySelector('#nextPayoutAmount'),
    taxWeekAmount: document.querySelector('#taxWeekAmount'),
    taxWeekDetail: document.querySelector('#taxWeekDetail'),
    nextPayoutDetail: document.querySelector('#nextPayoutDetail'),
    payoutCheckButton: document.querySelector('#payoutCheckButton'),
    goalRingFill: document.querySelector('#goalRingFill'),
    goalPercent: document.querySelector('#goalPercent'),
    goalProgress: document.querySelector('#goalProgress'),
    goalSentence: document.querySelector('#goalSentence'),
    goalMonth: document.querySelector('#goalMonth'),
    goalLink: document.querySelector('#goalLink'),
    forfeitsDetail: document.querySelector('#forfeitsDetail'),
    importStatus: document.querySelector('#importStatus'),
    importStatusHint: document.querySelector('#importStatusHint'),
    scheduleMatchNotice: document.querySelector('#scheduleMatchNotice'),
    scheduleMatchMessage: document.querySelector('#scheduleMatchMessage'),
    duplicateNotice: document.querySelector('#duplicateNotice'),
    duplicateMessage: document.querySelector('#duplicateMessage'),
    openDuplicateButton: document.querySelector('#openDuplicateButton'),
    completeScheduledButton: document.querySelector('#completeScheduledButton'),
    editDialogTitle: document.querySelector('#editDialogTitle'),
    editDialogDescription: document.querySelector('#editDialogDescription'),
    editStatus: document.querySelector('#editStatus'),
    editStatusHint: document.querySelector('#editStatusHint'),
    editBasePayLabel: document.querySelector('#editBasePayLabel'),
    linkedExpensesSection: document.querySelector('#linkedExpensesSection'),
    reviewBlockButton: document.querySelector('#reviewBlockButton'),
    installBanner: document.querySelector('#installBanner'),
    installBannerText: document.querySelector('#installBannerText'),
    shareTip: document.querySelector('#shareTip'),
    installBannerButton: document.querySelector('#installBannerButton'),
    dismissInstallBanner: document.querySelector('#dismissInstallBanner')
};

let selectedFileUrl;
// The read in flight: aborting it, or replacing it with a newer pick, makes its late answer harmless.
let importAbort;
let importTimer;
const IMPORT_TIMEOUT_MS = 60000;
let editingShiftId;
let editSnapshot;
/** The account's vehicle cost method, once the Expenses page has loaded the settings. */
let expenseCostMethod;
/**
 * The block the expense in the form is linked to (one being edited, or a new toll started from Review this block), kept
 * in the form when the Shift list doesn't hold it.
 */
let editingExpenseLink;
/** True while a move back to scheduled is being sent, so going online again can't offer a second press. */
let notWorkedSending = false;
let pendingConfirmAction;
let lastFocusedElement;
let visibleShiftCount = 25;
let currentShifts = [];
let dashboardAbort;
let reportAbort;
let filterState = readFilterState();
let reportGroupBy = new URLSearchParams(window.location.search).get('groupBy') || 'month';
let reportTab = window.flexbuddyReports.parseReportParams(window.location.search).tab;
let currentScreen = 'dashboard';
// The address follows the screen only once startup has read it, so a shared screenshot's id is not overwritten first.
let urlSyncEnabled = false;
let editingExpenseId;
let editMode = 'edit';
let editOriginalStatus;
let importCandidate;
let importWarnings = [];
let importDuplicateEdited = false;
// The single screenshot under review gets its own request ids, so a repeated save is answered with the first one.
let importRequestId;
let importStatusRequestId;
/**
 * Several screenshots picked at once (import-queue.js): {items, current, paused, reading, abort}. `current` is the
 * number of the file in the review form; `reading` is the file being read now, one at a time.
 */
let importBatch;
/** True while an import save is on its way, so Skip, Stop or Review can't move the form out from under it. */
let importSaveInFlight = false;
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
    let response;
    try {
        response = await window.fetch(url, options);
    } catch (error) {
        // Being offline is not an expired session, so a failed request never bounces to the login page.
        if (error?.name !== 'AbortError' && !navigator.onLine) {
            window.flexbuddyPwa?.noteNetworkFailure();
            const offlineError = new Error(t('js.common.offlineTryAgain'));
            offlineError.offline = true;
            throw offlineError;
        }
        throw error;
    }
    window.flexbuddyPwa?.noteResponse(response);
    const responsePath = new URL(response.url, window.location.origin).pathname;
    if (response.status === 401 || response.status === 403 || (response.redirected && responsePath === '/login')) {
        window.location.assign('/login?expired');
        throw new Error(t('js.common.sessionExpired'));
    }
    return plainFailure(response);
}

/**
 * A failed request answered by the host rather than by FlexBuddy, such as Render's 502 page while the server restarts,
 * comes back as a whole HTML page, and callers show a response body as the error message. This swaps it for a short
 * sentence with the same status; every other answer from FlexBuddy itself passes through untouched.
 */
function plainFailure(response) {
    if (response.ok) return response;
    if (!(response.headers.get('Content-Type') || '').includes('text/html')) return response;
    const message = response.status >= 500
        ? t('js.app.unreachable')
        : t('js.app.requestFailed');
    return new Response(message, {status: response.status, headers: {'Content-Type': 'text/plain'}});
}

Object.values(previewFields).forEach(input => input.addEventListener('focus', () => {
    const sourceLine = input.dataset.lineIndex;
    highlightRawLine(sourceLine === '' || sourceLine === undefined ? null : Number(sourceLine));
}));

elements.themeToggleButton.addEventListener('click', toggleTheme);
elements.reportsNavButton.addEventListener('click', () => showReportsScreen());
elements.scheduleNavButton.addEventListener('click', showScheduleScreen);
elements.importStatus.addEventListener('change', applyImportStatusRules);
elements.completeScheduledButton.addEventListener('click', completeScheduledShift);
elements.openDuplicateButton.addEventListener('click', openDuplicateShift);
[elements.station, elements.date, elements.startTime, elements.endTime].forEach(input =>
    input.addEventListener('input', () => {
        if (importDuplicateEdited) return;
        importDuplicateEdited = true;
        applyImportStatusRules();
    }));
elements.editStatus.addEventListener('change', () => applyEditStatusRules(true));
elements.expensesNavButton.addEventListener('click', showExpensesScreen);
// The logo goes Home without a reload; its address still works for a middle-click or with scripts off.
elements.homeLink.addEventListener('click', event => {
    event.preventDefault();
    showDashboard();
});
elements.dashboardNavButton.addEventListener('click', () => showDashboard());
elements.dropZone.addEventListener('click', openFilePicker);
elements.screenshotInput.addEventListener('change', event => pickScreenshots([...event.target.files]));
// In a batch, the card's remove button skips the file under review and Cancel stops the whole import.
elements.removeFileButton.addEventListener('click', () => (importBatch ? skipBatchItem() : resetImport()));
elements.cancelImportButton.addEventListener('click', () => (importBatch ? stopBatch() : resetImport()));
elements.skipImportButton.addEventListener('click', skipBatchItem);
elements.stopQueueButton.addEventListener('click', stopBatch);
elements.resumeQueueButton.addEventListener('click', resumeBatch);
elements.finishQueueButton.addEventListener('click', finishBatch);
elements.importQueueList.addEventListener('click', handleQueueRowAction);
// The batch check compares the form's current values, so it follows every edit of the block's station, day and times.
[elements.station, elements.date, elements.startTime, elements.endTime].forEach(input =>
    input.addEventListener('input', () => {
        if (currentBatchItem()) applyImportStatusRules();
    }));
window.addEventListener('beforeunload', event => {
    if (!importBatch || !window.flexbuddyImportQueue.hasUnsaved(importBatch.items)) return;
    event.preventDefault();
    event.returnValue = '';
});
elements.chooseAgainButton.addEventListener('click', () => {
    resetImport();
    // iOS only opens the picker from inside the tap itself, so nothing may be awaited before this.
    openFilePicker();
});
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
elements.rangeChip.addEventListener('click', openRangeSheet);
elements.rangeDoneButton.addEventListener('click', closeRangeSheet);
elements.rangeSheet.addEventListener('click', event => {
    if (event.target === elements.rangeSheet) closeRangeSheet();
});
[elements.chartsTab, elements.historyTab].forEach(tab => {
    tab.addEventListener('click', () => selectReportTab(tab === elements.historyTab ? 'history' : 'charts'));
    tab.addEventListener('keydown', handleReportTabKeys);
});
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
elements.heatmapMetric.addEventListener('change', loadHeatmap);
elements.payoutCheckButton.addEventListener('click', event => window.flexbuddyPayouts.open(event.currentTarget));
elements.reviewBlockButton.addEventListener('click', reviewEditedBlock);
// The queue is on the phone, so its Pending tags and the offline state of the edit controls follow it.
document.addEventListener('flexbuddy:outbox', refreshPendingTags);
document.addEventListener('flexbuddy:online', () => {
    applyOfflineEditState();
    applyOfflineExpenseState();
});
window.addEventListener('offline', () => {
    applyOfflineEditState();
    applyOfflineExpenseState();
});
// The grid needs width, so it starts open on wider screens and folded on a phone.
elements.heatmapCard.open = window.matchMedia('(min-width: 621px)').matches;
elements.groupButtons.forEach(button => button.addEventListener('click', () => {
    reportGroupBy = button.dataset.group;
    syncFilterControls();
    persistFilterState();
    loadEarningsReport(buildQuery(false));
}));
elements.editForm.addEventListener('submit', saveEditedShift);
[elements.editActualStart, elements.editActualEnd, elements.editStartTime, elements.editEndTime]
    .forEach(input => input.addEventListener('input', updateActualSummary));
// Readings fill in the miles as they are typed; a miles value typed afterwards still wins.
[elements.editOdometerStart, elements.editOdometerEnd].forEach(input => input.addEventListener('input', () => {
    const miles = odometerMiles();
    if (miles !== null && miles >= 0) elements.editMiles.value = miles.toFixed(1);
    updateOdometerSummary();
}));
[elements.editStops, elements.editReturns, elements.editActualStart, elements.editActualEnd,
    elements.editStartTime, elements.editEndTime].forEach(input => input.addEventListener('input', updateRouteSummary));
elements.sameAsScheduledButton.addEventListener('click', () => {
    elements.editActualStart.value = elements.editStartTime.value;
    elements.editActualEnd.value = elements.editEndTime.value;
    updateActualSummary();
});
elements.closeEditButton.addEventListener('click', closeEditModal);
elements.cancelEditButton.addEventListener('click', closeEditModal);
elements.deleteShiftButton.addEventListener('click', showDeleteConfirmation);
elements.duplicateShiftButton.addEventListener('click', () => {
    if (editSnapshot) duplicateShift(editSnapshot, lastFocusedElement);
});
elements.cancelDeleteButton.addEventListener('click', hideDeleteConfirmation);
elements.confirmDeleteButton.addEventListener('click', deleteEditedShift);
elements.notWorkedButton.addEventListener('click', showNotWorkedConfirmation);
elements.cancelNotWorkedButton.addEventListener('click', hideNotWorkedConfirmation);
elements.confirmNotWorkedButton.addEventListener('click', markEditedShiftNotWorked);
elements.trashSection.addEventListener('toggle', () => {
    if (elements.trashSection.open) loadTrash();
});
elements.emptyTrashButton.addEventListener('click', () => openConfirm(
    t('js.app.emptyTrashTitle'),
    t('js.app.emptyTrashMessage'),
    emptyTrash
));
elements.expenseForm.addEventListener('submit', saveExpense);
elements.expenseCategory.addEventListener('change', renderExpenseCategoryHint);
elements.expenseShift.addEventListener('change', renderExpenseCategoryHint);
// On a phone the form comes first; this goes straight to the list and puts keyboard focus on its heading.
document.querySelector('#expenseHistoryJump')?.addEventListener('click', () => {
    document.querySelector('.expense-history-panel').scrollIntoView({behavior: 'smooth', block: 'start'});
    document.querySelector('#expenseHistoryTitle').focus({preventScroll: true});
});
elements.cancelExpenseEdit.addEventListener('click', resetExpenseForm);
elements.expenseDate.addEventListener('change', () => populateExpenseShifts(currentShifts));
[elements.expenseFrom, elements.expenseTo, elements.expenseFilterCategory].forEach(input => input.addEventListener('change', loadExpenses));
elements.expenseQuery.addEventListener('input', () => { clearTimeout(elements.expenseQuery._timer); elements.expenseQuery._timer = setTimeout(loadExpenses, 250); });
elements.expenseTrashSection.addEventListener('toggle', () => { if (elements.expenseTrashSection.open) loadExpenseTrash(); });
elements.emptyExpenseTrashButton.addEventListener('click', () => openConfirm(
    t('js.app.emptyExpenseTrashTitle'),
    t('js.app.emptyExpenseTrashMessage'),
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
        showToast(t('js.app.actionFailed'), error.message || t('js.app.actionNotCompleted'), {alert: true});
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
    if (event.key === 'Escape' && !elements.rangeSheet.classList.contains('is-hidden')) {
        closeRangeSheet();
        return;
    }
    if (event.key === 'Tab' && !elements.confirmModal.classList.contains('is-hidden')) {
        trapFocus(elements.confirmModal, event);
    } else if (event.key === 'Tab' && !elements.editModal.classList.contains('is-hidden')) {
        trapFocus(elements.editModal, event);
    } else if (event.key === 'Tab' && !elements.rangeSheet.classList.contains('is-hidden')) {
        trapFocus(elements.rangeSheet, event);
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

elements.dropZone.addEventListener('drop', event => pickScreenshots([...event.dataTransfer.files]));

/** One file keeps the single-screenshot flow; two to ten start a batch, reviewed one at a time. */
function pickScreenshots(files) {
    if (files.length === 0) return;
    if (refuseNewPick()) {
        elements.screenshotInput.value = '';
        return;
    }
    // A finished batch is cleared first, so its list never sits beside another screenshot's review.
    if (importBatch) resetImport();
    if (files.length === 1) processScreenshot(files[0]);
    else startBatch(files);
}

/**
 * True, with a message, while an import still has files to read or review or a save is on its way: a new pick then
 * would put another screenshot in the form that belongs to the file under review, or drop the batch mid-save.
 */
function refuseNewPick() {
    const busy = importSaveInFlight || (importBatch && !window.flexbuddyImportQueue.isFinished(importBatch.items));
    if (busy) showMessage(elements.uploadError, t('js.importQueue.finishFirst'));
    return Boolean(busy);
}

function openFilePicker() {
    if (refuseNewPick()) return;
    elements.screenshotInput.click();
}

function toggleTheme() {
    const nextTheme = document.documentElement.dataset.theme === 'light' ? 'dark' : 'light';
    document.documentElement.dataset.theme = nextTheme;
    localStorage.setItem('flexbuddy-theme', nextTheme);
    updateThemeToggle(nextTheme);
    window.flexbuddyPwa?.applyThemeColor(nextTheme);
}

function updateThemeToggle(theme) {
    const label = theme === 'light' ? t('js.common.switchToDarkMode') : t('js.common.switchToLightMode');
    elements.themeToggleButton.setAttribute('aria-label', label);
    elements.themeToggleButton.title = label;
}

function showScreen(name, smooth = true) {
    currentScreen = name;
    elements.dashboardScreens.forEach(section => section.classList.toggle('is-hidden', name !== 'dashboard'));
    elements.reportsScreen.classList.toggle('is-hidden', name !== 'reports');
    elements.scheduleScreen.classList.toggle('is-hidden', name !== 'schedule');
    elements.importScreen.classList.toggle('is-hidden', name !== 'import');
    elements.expensesScreen.classList.toggle('is-hidden', name !== 'expenses');
    setActiveNavigation(name);
    persistFilterState();
    window.scrollTo({top: 0, behavior: smooth ? 'smooth' : 'auto'});
}

function showDashboard(smooth = true) {
    showScreen('dashboard', smooth);
    window.flexbuddyHome?.renderToday();
}

function showScheduleScreen(smooth = true) {
    showScreen('schedule', smooth);
    window.flexbuddyStanding?.load();
    // Resolves once the schedule's content has loaded, for callers that need to scroll to something below it.
    return window.flexbuddySchedule.show();
}

function showReportsScreen(smooth = true, tab = reportTab) {
    showScreen('reports', smooth);
    selectReportTab(tab);
}

function showImportScreen(smooth = true) {
    showScreen('import', smooth);
}

function showExpensesScreen(smooth = true) {
    showScreen('expenses', smooth);
    if (!elements.expenseDate.value) elements.expenseDate.value = toIsoDate(new Date());
    loadExpenses();
}

function setActiveNavigation(activeItem) {
    // The import screen belongs to no item: it opens from the + button, so none is highlighted there.
    [['dashboard', elements.dashboardNavButton], ['schedule', elements.scheduleNavButton],
        ['reports', elements.reportsNavButton], ['expenses', elements.expensesNavButton]].forEach(([name, button]) => {
        button.classList.toggle('is-active', activeItem === name);
        setCurrentPage(button, activeItem === name);
    });
}

function setCurrentPage(button, current) {
    if (current) button.setAttribute('aria-current', 'page');
    else button.removeAttribute('aria-current');
}

/** Shows Charts or History on the Reports screen; both stay loaded, so switching never fetches anything. */
function selectReportTab(tab) {
    reportTab = tab === 'history' ? 'history' : 'charts';
    const showHistory = reportTab === 'history';
    [[elements.chartsTab, elements.chartsPanel, !showHistory], [elements.historyTab, elements.historyPanel, showHistory]]
        .forEach(([button, panel, active]) => {
            button.classList.toggle('is-active', active);
            button.setAttribute('aria-selected', String(active));
            button.tabIndex = active ? 0 : -1;
            panel.hidden = !active;
        });
    persistFilterState();
}

/** The WAI-ARIA tabs pattern: the arrow keys, Home and End move between the two tabs and select them. */
function handleReportTabKeys(event) {
    const tabs = [elements.chartsTab, elements.historyTab];
    let next;
    if (event.key === 'ArrowRight' || event.key === 'ArrowLeft') next = tabs[(tabs.indexOf(event.currentTarget) + 1) % tabs.length];
    else if (event.key === 'Home') next = tabs[0];
    else if (event.key === 'End') next = tabs.at(-1);
    if (!next) return;
    event.preventDefault();
    selectReportTab(next === elements.historyTab ? 'history' : 'charts');
    next.focus();
}

function openRangeSheet() {
    elements.rangeSheet.classList.remove('is-hidden');
    document.body.classList.add('modal-open');
    (elements.presetChips.find(chip => chip.classList.contains('is-active')) ?? elements.presetChips[0]).focus();
}

function closeRangeSheet() {
    elements.rangeSheet.classList.add('is-hidden');
    document.body.classList.remove('modal-open');
    elements.rangeChip.focus();
}

async function processScreenshot(file) {
    hideMessage(elements.uploadError);

    if (!['image/png', 'image/jpeg'].includes(file.type)) {
        showMessage(elements.uploadError, t('js.app.choosePngJpeg'));
        return;
    }

    if (file.size > 5 * 1024 * 1024) {
        showMessage(elements.uploadError, t('js.app.screenshotTooBig'));
        return;
    }

    cancelImportRequest();
    showSelectedFile(file);
    setProcessing(true);

    const abort = new AbortController();
    importAbort = abort;
    let timedOut = false;
    importTimer = setTimeout(() => {
        timedOut = true;
        abort.abort();
    }, IMPORT_TIMEOUT_MS);

    try {
        const response = await requestPreview(file, abort.signal);

        if (!response.ok) {
            throw new Error(await response.text() || t('js.app.screenshotNotProcessed'));
        }

        const preview = await response.json();
        if (importAbort !== abort) return;
        importRequestId = crypto.randomUUID();
        importStatusRequestId = crypto.randomUUID();
        populatePreview(preview);
    } catch (error) {
        // A cancel or a newer pick already reset the screen, and this answer must not touch it.
        if (importAbort !== abort && !timedOut) return;
        if (error.name === 'AbortError' && !timedOut) return;
        showMessage(elements.uploadError, timedOut
            ? t('js.app.screenshotTimedOut')
            : error.message || t('js.app.screenshotNotProcessed'));
        elements.emptyPreview.classList.remove('is-hidden');
        elements.previewForm.classList.add('is-hidden');
    } finally {
        if (importAbort === abort) {
            clearTimeout(importTimer);
            importAbort = undefined;
            setProcessing(false);
        }
    }
}

/** Sends one screenshot to be read; nothing is saved. The server reads one screenshot at a time for everyone. */
function requestPreview(file, signal) {
    const formData = new FormData();
    formData.append('screenshot', file);
    return apiFetch('/shifts/import-preview', {method: 'POST', headers: csrfHeaders(), body: formData, signal});
}

function cancelImportRequest() {
    clearTimeout(importTimer);
    const abort = importAbort;
    importAbort = undefined;
    abort?.abort();
}

function showSelectedFile(file) {
    if (selectedFileUrl) URL.revokeObjectURL(selectedFileUrl);
    selectedFileUrl = URL.createObjectURL(file);
    elements.imagePreview.src = selectedFileUrl;
    elements.fileName.textContent = file.name;
    elements.fileSize.textContent = formatFileSize(file.size);
    elements.dropZone.classList.add('is-hidden');
    elements.fileCard.classList.remove('is-hidden');
    elements.fileActions.classList.remove('is-hidden');
}

function populatePreview(preview) {
    const candidate = preview.shifts?.[0];
    if (!candidate) throw new Error(t('js.app.noShiftDetails'));

    setPreviewField('station', candidate.station);
    setPreviewField('date', candidate.date);
    setPreviewField('startTime', candidate.startTime, trimTime);
    setPreviewField('endTime', candidate.endTime, trimTime);
    setPreviewField('basePay', candidate.basePay);
    setPreviewField('tips', candidate.tips, value => value ?? 0);
    importCandidate = candidate;
    importDuplicateEdited = false;
    // The driver says whether they worked the block; the screenshot's suggestion is only a hint.
    elements.importStatus.value = window.flexbuddyImportStatus.INITIAL_STATUS;
    elements.importStatusHint.textContent = t(window.flexbuddyImportStatus.hintKey(candidate));
    renderRawText(preview.lines, preview.rawText);
    renderReadQuality(preview.meanConfidence);

    importWarnings = candidate.warnings ?? [];
    applyImportStatusRules();
    elements.ocrDetails.classList.remove('is-hidden');
    elements.emptyPreview.classList.add('is-hidden');
    elements.previewForm.classList.remove('is-hidden');

    const missingField = Object.entries(previewFields)
            .find(([name]) => candidate[name]?.level === 'MISSING');
    if (missingField) missingField[1].focus();
}

function applyImportStatusRules() {
    const scheduled = elements.importStatus.value === 'SCHEDULED';
    elements.tips.disabled = scheduled;
    elements.miles.disabled = scheduled;
    if (scheduled) {
        elements.tips.value = 0;
        elements.miles.value = '';
    }
    renderImportNotices();
    elements.saveButton.querySelector('span').textContent = saveLabel();
    // A future date is expected for a scheduled block and suspicious for a completed one.
    const warnings = importWarnings.map(warning => warning.code !== 'FUTURE_DATE' ? warning : {
        ...warning,
        severity: scheduled ? 'INFO' : 'WARNING',
        message: scheduled ? t('js.app.scheduledFar') : t('js.app.dateFar')
    });
    renderWarnings(warnings);
    elements.warningNotice.classList.toggle('is-hidden', warnings.length === 0);
}

/**
 * The server's notices for this screenshot, unless another file of this batch already saved the same block: the
 * server read this file before that save, so the batch's own check replaces its answer.
 */
function importNotices() {
    const item = currentBatchItem();
    const inBatch = item && window.flexbuddyImportQueue.savedMatch(importFormBlock(), importBatch.items, item);
    if (inBatch) {
        return inBatch.kind === 'SCHEDULED_MATCH' ? {logged: undefined, schedule: inBatch} : {logged: inBatch, schedule: undefined};
    }
    return window.flexbuddyImportDuplicates.choose(importCandidate, elements.importStatus.value, importDuplicateEdited);
}

/** The block as the review form holds it now. */
function importFormBlock() {
    return {
        station: elements.station.value.trim(),
        date: elements.date.value,
        startTime: elements.startTime.value,
        endTime: elements.endTime.value,
        status: elements.importStatus.value
    };
}

function renderImportNotices() {
    const {logged, schedule} = importNotices();
    elements.duplicateNotice.classList.toggle('is-hidden', !logged);
    elements.duplicateNotice.dataset.shiftId = logged ? logged.shiftId : '';
    elements.duplicateMessage.textContent = logged ? logged.message : '';
    elements.scheduleMatchNotice.classList.toggle('is-hidden', !schedule);
    elements.scheduleMatchNotice.dataset.shiftId = schedule ? schedule.shiftId : '';
    elements.scheduleMatchMessage.textContent = schedule ? schedule.message : '';
}

/** The save button's label: "Add anyway" while the duplicate notice shows, otherwise by status. */
function saveLabel() {
    if (importNotices().logged) return t('js.app.addAnyway');
    return elements.importStatus.value === 'SCHEDULED' ? t('js.common.addScheduledShift') : t('js.common.addShift');
}

async function openDuplicateShift() {
    hideMessage(elements.saveError);
    const shiftId = elements.duplicateNotice.dataset.shiftId;
    if (!shiftId) return;
    elements.openDuplicateButton.disabled = true;
    try {
        const response = await apiFetch(`/shifts/${encodeURIComponent(shiftId)}`);
        if (!response.ok) throw new Error(await response.text() || t('js.app.requestFailed'));
        openEditModal(await response.json(), elements.openDuplicateButton);
    } catch (error) {
        showMessage(elements.saveError, error.message || t('js.app.requestFailed'));
    } finally {
        elements.openDuplicateButton.disabled = window.flexbuddyPwa?.isOffline() ?? false;
    }
}

async function completeScheduledShift() {
    hideMessage(elements.saveError);
    const shiftId = elements.scheduleMatchNotice.dataset.shiftId;
    // Add shift and Complete never both go out for one screenshot: that would count its pay twice.
    if (!shiftId || importSaveInFlight || elements.completeScheduledButton.disabled
        || !elements.previewForm.reportValidity()) return;
    const batchItem = currentBatchItem();
    const pay = {basePay: Number(elements.basePay.value), tips: Number(elements.tips.value)};
    elements.completeScheduledButton.disabled = true;
    importSaveInFlight = true;
    let answered = false;
    try {
        const response = await apiFetch(`/shifts/${encodeURIComponent(shiftId)}/status`, {
            method: 'PATCH',
            headers: retryHeaders(batchItem ? batchItem.statusRequestId : importStatusRequestId,
                {'Content-Type': 'application/json'}),
            body: JSON.stringify({
                status: 'COMPLETED',
                ...pay,
                miles: elements.miles.value === '' ? null : Number(elements.miles.value)
            })
        });
        answered = true;
        if (!response.ok) throw new Error(await response.text() || t('js.app.scheduledNotCompleted'));
        const completed = await response.json();
        // A repeat is answered with the block as the first copy left it; a pay edited since then wasn't applied.
        const replayed = Number(completed.basePay) !== pay.basePay || Number(completed.tips) !== pay.tips;
        if (batchItem) {
            window.flexbuddyImportQueue.noteCompleted(importBatch.items, completed.id);
            recordBatchSave(batchItem, completed, 'completed', replayed);
            return;
        }
        resetImport();
        if (replayed) showToast(t('js.importQueue.savedBeforeTitle'), t('js.importQueue.savedBeforeMessage'));
        else showToast(t('js.app.scheduledCompletedTitle'), t('js.app.scheduledCompletedMessage'));
        await loadStations();
        await loadDashboard();
        showDashboard();
    } catch (error) {
        if (batchItem && !answered) {
            // It may have been completed: the row says so, and no other file offers to complete the block again.
            batchItem.uncertain = true;
            window.flexbuddyImportQueue.noteCompleted(importBatch.items, Number(shiftId));
        }
        showMessage(elements.saveError, error.message || t('js.app.scheduledNotCompleted'));
    } finally {
        importSaveInFlight = false;
        elements.completeScheduledButton.disabled = window.flexbuddyPwa?.isOffline() ?? false;
    }
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
        badge.textContent = field.level === 'MISSING' ? t('js.app.missing') : t('js.app.percentRead', field.confidence);
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
        heading.textContent = severity === 'ERROR' ? t('js.app.needsAttention')
            : severity === 'WARNING' ? t('js.app.pleaseCheck') : t('js.app.forYourInformation');
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
        elements.rawText.textContent = fallbackText || t('js.app.noReadableText');
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

    // A second tap while a save is on its way does nothing; the request id covers a retry after it.
    if (importSaveInFlight || elements.saveButton.disabled || !elements.previewForm.reportValidity()) return;

    const shift = {
        station: elements.station.value.trim(),
        date: elements.date.value,
        startTime: elements.startTime.value,
        endTime: elements.endTime.value,
        basePay: Number(elements.basePay.value),
        tips: Number(elements.tips.value),
        miles: elements.miles.value === '' ? null : Number(elements.miles.value),
        status: elements.importStatus.value
    };
    const batchItem = currentBatchItem();

    setSaving(true);
    importSaveInFlight = true;

    let answered = false;
    try {
        const send = () => apiFetch('/shifts', {
            method: 'POST',
            headers: retryHeaders(batchItem ? batchItem.requestId : importRequestId, {'Content-Type': 'application/json'}),
            body: JSON.stringify(shift)
        });
        let response = await send();
        answered = true;

        // A 409 DUPLICATE is a second copy of this same save racing the first, which landed. Sent once more with the
        // same request id, it is answered with that first block.
        let delivered = !response.ok && await isDuplicate(response);
        if (delivered) {
            const again = await send();
            if (again.ok) {
                response = again;
                delivered = false;
            }
        }
        if (!response.ok && !delivered) {
            const message = await response.text();
            throw new Error(message || t('js.app.saveFailedCheck'));
        }
        const saved = delivered ? undefined : await response.json();
        const replayed = saved && !window.flexbuddyImportQueue.sameAsSent(shift, saved);

        if (batchItem) {
            recordBatchSave(batchItem, saved ?? shift, 'added', replayed);
            return;
        }

        const scheduled = shift.status === 'SCHEDULED';
        resetImport();
        if (replayed) {
            showToast(t('js.importQueue.savedBeforeTitle'), t('js.importQueue.savedBeforeMessage'));
            await loadStations();
            await loadDashboard();
            showDashboard();
            return;
        }
        showToast(scheduled ? t('js.app.shiftScheduled') : t('js.app.shiftAdded'),
            scheduled ? t('js.app.shiftScheduledMessage') : t('js.app.shiftAddedMessage'));
        await loadStations();
        await loadDashboard();
        if (scheduled) showScheduleScreen();
        else showDashboard();
    } catch (error) {
        // With no answer the save may still have landed; a later Skip or Stop says so instead of "Skipped".
        if (batchItem && !answered) batchItem.uncertain = true;
        showMessage(elements.saveError, error.message || t('js.app.saveFailed'));
    } finally {
        importSaveInFlight = false;
        setSaving(false);
    }
}

// Reports and history load with everything else, so every "reload after save" call keeps working unchanged. Loading them
// only once Reports opens would save requests on the dashboard; it is left for later.
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
        loadEarningsReport(buildQuery(false)),
        loadHeatmap()
    ]);
    window.flexbuddySchedule?.refresh();
    window.flexbuddyFinish?.loadMissing();
    window.flexbuddyStanding?.load();
    loadGoals();
    loadPayPeriods();
    loadTaxTile();
    window.flexbuddyHome?.load();
}

/** "Set aside this week": the driver's own percentage of this week's estimated net, once they choose one. */
async function loadTaxTile() {
    let summary;
    try {
        const response = await apiFetch('/tax/summary');
        if (!response.ok) throw new Error();
        summary = await response.json();
    } catch {
        elements.taxWeekAmount.textContent = '—';
        return;
    }
    if (summary.percent == null) {
        elements.taxWeekAmount.textContent = t('js.app.taxNotSetUp');
        elements.taxWeekDetail.innerHTML = `<a class="text-link" href="/account#taxes">${escapeHtml(t('js.app.taxSetUp'))}</a>`;
        return;
    }
    elements.taxWeekAmount.textContent = formatMoney(summary.thisWeekSetAside);
    elements.taxWeekDetail.textContent = t('js.app.taxPercentOfNet', Number(summary.percent));
    elements.taxWeekDetail.title = t('js.app.taxTitle', Number(summary.percent), formatMoney(summary.thisWeekNet),
        formatMoney(summary.remaining));
}

// Which of the server's pay periods each preset shows: the one covering today, and the one before it.
const PAY_PERIOD_PRESETS = {payperiod: 0, lastpayperiod: 1};
let payPeriods;

/**
 * Loads the pay period covering today, the two before it, and the next payout; the server owns the payout calendar.
 * Three periods reach back to the latest payout that has already arrived on any usual schedule, for the tile's
 * check reminder.
 */
async function loadPayPeriods() {
    try {
        const response = await apiFetch('/shifts/pay-periods?count=3');
        if (!response.ok) throw new Error();
        payPeriods = await response.json();
    } catch {
        elements.nextPayoutAmount.textContent = '—';
        elements.nextPayoutDetail.textContent = t('js.app.payPeriodsFailed');
        return payPeriods;
    }
    renderNextPayout(payPeriods.nextPayout);
    renderPayoutCheck(payPeriods.periods.find(period => period.status !== 'UPCOMING'));
    // A pay-period view restored from the address bar follows the calendar into the next period.
    const index = PAY_PERIOD_PRESETS[filterState.preset];
    const period = index === undefined ? null : payPeriods.periods[index];
    if (period && (period.from !== filterState.from || period.to !== filterState.to)) applyPreset(filterState.preset);
    return payPeriods;
}

function renderNextPayout(payout) {
    const scheduled = Number(payout.scheduledPay) > 0 ? ` · ${t('js.app.plusScheduled', formatMoney(payout.scheduledPay))}` : '';
    elements.nextPayoutAmount.textContent = formatMoney(payout.earned);
    elements.nextPayoutDetail.textContent = `${payoutDay(payout.payoutDate)} · ${tn(payout.blocks, 'js.common.blocks')}${scheduled}`;
    elements.nextPayoutDetail.title = t('js.app.payoutRangeTitle', formatDate(payout.from), formatDate(payout.to));
}

/** The tile's link into the payouts sheet, nudging when the latest payout that arrived is unchecked or came up short. */
function renderPayoutCheck(latest) {
    const button = elements.payoutCheckButton;
    const day = latest ? parseLocalDate(latest.payoutDate).toLocaleDateString(appLocale(), {weekday: 'short', month: 'short', day: 'numeric'}) : '';
    button.classList.toggle('is-warning', latest?.status === 'SHORT');
    if (latest?.status === 'UNCHECKED' && latest.blocks > 0) {
        button.textContent = t('js.app.checkPayout', day);
    } else if (latest?.status === 'SHORT') {
        button.textContent = t('js.app.payoutShort', day, formatMoney(Math.abs(Number(latest.difference))));
    } else {
        button.textContent = t('js.app.payoutHistory');
    }
}

function payoutDay(date) {
    const days = Math.round((parseLocalDate(date) - startOfToday()) / 86400000);
    if (days === 0) return t('js.app.today');
    if (days === 1) return t('js.app.tomorrow');
    return parseLocalDate(date).toLocaleDateString(appLocale(), {weekday: 'short', month: 'short', day: 'numeric'});
}

let lastWeekGoalPercent;

/** The week's goal tile: a ring, "$412.50 of $600", and how many usual blocks would close the gap. */
async function loadGoals() {
    let goals;
    try {
        const response = await apiFetch('/shifts/goals');
        if (!response.ok) throw new Error();
        goals = await response.json();
    } catch {
        return;
    }
    const week = goals.week;
    const month = goals.month;
    elements.goalMonth.textContent = month
        ? t('js.app.monthGoal', formatMoney(month.earned), formatMoney(month.goal), Number(month.percent).toFixed(0))
        : '';
    elements.goalLink.textContent = week || month ? t('js.app.changeGoals') : t('js.app.setGoal');
    // A round line cap would draw a dot for an empty ring, so the fill is hidden until there is progress.
    elements.goalRingFill.classList.toggle('is-empty', !week || Number(week.percent) <= 0);
    elements.goalPercent.textContent = week ? `${Math.round(Number(week.percent))}%` : '';
    if (!week) {
        elements.goalCard.dataset.state = 'none';
        elements.goalRingFill.setAttribute('stroke-dasharray', '0 100');
        // Home knows what was earned this week, which reads better than "No goal yet" beside an empty ring.
        elements.goalProgress.textContent = window.flexbuddyHome?.noGoalText() ?? (month ? t('js.app.noWeeklyGoal') : t('js.app.noGoalYet'));
        elements.goalSentence.textContent = month ? t('js.app.monthlyGoalBelow') : t('js.app.setWeeklyTarget');
        return;
    }
    const percent = Number(week.percent);
    const reached = percent >= 100;
    elements.goalCard.dataset.state = reached ? 'reached' : week.onTrack ? 'on-track' : 'behind';
    elements.goalRingFill.setAttribute('stroke-dasharray', `${Math.min(percent, 100)} 100`);
    elements.goalProgress.textContent = t('js.app.earnedOfGoal', formatMoney(week.earned), formatMoney(week.goal));
    elements.goalSentence.textContent = goalSentence(week, goals.basis);
    if (lastWeekGoalPercent !== undefined && lastWeekGoalPercent < 100 && reached) {
        showToast(t('js.app.weeklyGoalReached'), formatMoney(week.earned));
    }
    lastWeekGoalPercent = percent;
}

function goalSentence(week, basis) {
    const amount = basis === 'NET' ? t('js.app.goalAmountNet') : t('js.app.goalAmountPay');
    if (Number(week.percent) >= 100) return t('js.app.goalReached', formatMoney(Number(week.earned) - Number(week.goal)));
    if (Number(week.remaining) === 0) return t('js.app.goalCovered');
    const planned = Number(week.planned) > 0;
    if (!week.blocksToGo || !week.averageBlockMinutes) {
        return planned ? t('js.app.goalToGoPlanned', formatMoney(week.remaining)) : t('js.app.goalToGo', formatMoney(week.remaining));
    }
    const hours = Math.round(week.averageBlockMinutes / 30) / 2;
    const count = week.blocksToGo;
    const words = ['', 'js.common.numberOne', 'js.common.numberTwo', 'js.common.numberThree', 'js.common.numberFour',
        'js.common.numberFive', 'js.common.numberSix', 'js.common.numberSeven', 'js.common.numberEight', 'js.common.numberNine'];
    const number = words[count] ? t(words[count]) : String(count);
    const key = count === 1
        ? (planned ? 'js.app.goalBlocksPlanned.one' : 'js.app.goalBlocks.one')
        : (planned ? 'js.app.goalBlocksPlanned.other' : 'js.app.goalBlocks.other');
    const text = t(key, number, hours, amount, formatMoney(week.goal));
    return text.charAt(0).toUpperCase() + text.slice(1);
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
        const timed = statistics.timedShifts > 0;
        elements.totalShiftsDetail.textContent = flexbuddyReports.rangeLabel(filterState).replace(/ ▾$/, '');
        elements.timeWorkedDetail.textContent = timed
            ? t('js.app.onTheClockPace', formatMinutes(statistics.clockedMinutes), paceText(statistics.averageFinishedEarlyMinutes))
            : t('js.app.acrossEveryShift');
        elements.baseTipsTotal.textContent = `${formatMoney(statistics.totalBasePay)} · ${formatMoney(statistics.totalTips)}`;
        const tipShare = Number(statistics.tipsShareOfEarnings || 0);
        elements.baseShareBar.style.width = `${100 - tipShare}%`;
        elements.tipsShareBar.style.width = `${tipShare}%`;
        elements.tipsShare.textContent = t('js.app.percentFromTips', tipShare.toFixed(1));
        elements.netEarnings.textContent = formatMoney(statistics.netEarnings);
        const missingMiles = window.flexbuddyHome?.netMissingMiles(statistics) ?? 0;
        elements.netHourly.textContent = t(missingMiles ? 'js.app.netHourlyEstMissing' : 'js.app.netHourlyEst',
            formatMoney(statistics.netHourlyRate));
        elements.netMargin.textContent = missingMiles
            ? tn(missingMiles, 'js.app.netMissingMiles')
            : t('js.app.afterDeductions', Number(statistics.netMargin || 0).toFixed(1));
        elements.expenseTotal.textContent = formatMoney(statistics.totalExpenses);
        elements.totalMiles.textContent = t('js.common.miles', Number(statistics.totalMiles || 0).toFixed(1));
        elements.mileageCost.textContent = t('js.app.mileageCost', formatMoney(statistics.mileageCost));
        const lateThisMonth = statistics.lateForfeitedThisMonth ?? 0;
        elements.forfeitsMonth.textContent = (lateThisMonth ? t('js.app.forfeitsWithLate', statistics.forfeitedThisMonth ?? 0, lateThisMonth) : String(statistics.forfeitedThisMonth ?? 0));
        elements.forfeitsDetail.textContent = statistics.needsConfirmation
            ? tn(statistics.needsConfirmation, 'js.home.blocksToConfirm')
            : t('js.app.cancelledInView', statistics.cancelledShifts ?? 0);
    } catch (error) {
        if (error?.name === 'AbortError') return;
        [elements.totalEarnings, elements.totalShifts, elements.totalTime, elements.averagePay,
            elements.averageHourly, elements.totalShiftsDetail, elements.timeWorkedDetail, elements.baseTipsTotal,
            elements.tipsShare, elements.netEarnings, elements.netHourly, elements.netMargin,
            elements.expenseTotal, elements.totalMiles, elements.mileageCost,
            elements.forfeitsMonth, elements.forfeitsDetail].forEach(element => element.textContent = '—');
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
        elements.historyList.innerHTML = `<div class="history-empty">${escapeHtml(t('js.app.shiftsNotLoaded'))}</div>`;
        elements.showMoreButton.classList.add('is-hidden');
        elements.resultsSummary.textContent = t('js.app.resultsUnavailable');
        elements.activeFilterSummary.textContent = t('js.app.shiftsNotLoaded');
        elements.exportCsvButton.classList.add('is-disabled');
        elements.exportCsvButton.setAttribute('aria-disabled', 'true');
        elements.exportCsvButton.title = t('js.app.resultsUnavailableTitle');
    }
}

// Fewer blocks than this make any pattern chance, so the heatmap says so instead of ranking slots.
const HEATMAP_MIN_TOTAL = 10;

/** Best times to work, for the same filters as the rest of the dashboard. */
async function loadHeatmap() {
    const metric = elements.heatmapMetric.value;
    try {
        const response = await apiFetch(`/shifts/reports/heatmap?metric=${metric}&${buildQuery(false)}`);
        if (!response.ok) throw new Error();
        const heatmap = await response.json();
        window.flexbuddyCharts.renderHeatmap(elements.heatmapTable, elements.heatmapLegend, heatmap);
        const best = heatmap.best;
        const days = ['js.charts.dayMonday', 'js.charts.dayTuesday', 'js.charts.dayWednesday', 'js.charts.dayThursday',
            'js.charts.dayFriday', 'js.charts.daySaturday', 'js.charts.daySunday'];
        const label = elements.heatmapMetric.selectedOptions[0].textContent.toLowerCase();
        if (heatmap.totalShifts < HEATMAP_MIN_TOTAL) {
            elements.heatmapSummary.textContent = t('js.app.heatmapNotEnough', heatmap.totalShifts);
        } else if (!best) {
            elements.heatmapSummary.textContent = t('js.app.heatmapNoSlot');
        } else {
            const value = metric === 'SHIFTS' ? tn(best.value, 'js.common.blocks')
                : metric === 'AVERAGE_PAY' ? `${formatMoney(best.value)} ${label}`
                    : `${t('js.common.perHour', formatMoney(best.value))} ${label.replace(t('js.app.perHourSuffix'), '')}`;
            elements.heatmapSummary.textContent = t('js.app.heatmapBest', t(days[best.weekday - 1]), heatmap.bands[best.band], value,
                tn(best.shifts, 'js.common.blocks'));
        }
    } catch {
        elements.heatmapSummary.textContent = t('js.app.heatmapFailed');
        elements.heatmapTable.replaceChildren();
        elements.heatmapLegend.textContent = '';
    }
}

const REPORT_GROUP_KEYS = {
    station: 'js.charts.groupStation', week: 'js.charts.groupWeek', month: 'js.charts.groupMonth', year: 'js.charts.groupYear'
};

async function loadEarningsReport(query) {
    hideMessage(elements.reportError);
    elements.earningsChartTitle.textContent = t('js.app.earningsBy', REPORT_GROUP_KEYS[reportGroupBy] ? t(REPORT_GROUP_KEYS[reportGroupBy]) : reportGroupBy);
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
        showMessage(elements.reportError, error.message || t('js.app.reportFailed'));
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
        empty.innerHTML = `${escapeHtml(t('js.app.noShiftsMatch'))} <button class="text-button" type="button">${escapeHtml(t('js.app.clearFilters'))}</button>`;
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
        const month = date.toLocaleDateString(appLocale(), {month: 'short'});
        const day = date.getDate();
        const weekday = date.toLocaleDateString(appLocale(), {weekday: 'short'});
        const total = shift.earnedPay ?? shift.totalPay ?? (Number(shift.basePay || 0) + Number(shift.tips || 0));
        const worked = !shift.status || shift.status === 'COMPLETED';
        const statusBadge = worked ? ''
            : `<small class="status-badge status-${shift.status.toLowerCase()}">${escapeHtml(shift.status === 'CANCELLED' ? t('js.common.cancelled')
                : shift.lateForfeit ? t('js.common.lateForfeit') : t('js.common.forfeited'))}</small>`;
        const actual = shift.details?.actualMinutes;
        const workSummary = worked && actual != null
            ? t('js.app.workedOf', formatMinutes(actual), formatMinutes(shift.timeWorked), formatMoney(shift.details.actualHourlyRate))
            : worked ? t('js.app.grossLine', formatMinutes(shift.timeWorked), formatMoney(shift.hourlyRate))
            : shift.status === 'CANCELLED' ? t('js.app.cancellationNoHours') : t('js.app.notWorkedNoPay');
        const route = worked ? routeSummary(shift.details?.stops, shift.details?.returns,
            shift.details?.actualMinutes ?? shift.timeWorked) : '';
        const edited = shift.createdAt && shift.updatedAt
                && new Date(shift.updatedAt) - new Date(shift.createdAt) > 60000;
        // The server flags a block whose missing miles leave its vehicle cost out of estimated net.
        const netLine = (shift.miles == null ? '' : `${t('js.common.miles', Number(shift.miles).toFixed(1))} · `)
            + t('js.app.estNetLine', formatMoney(shift.netPay), formatMoney(shift.netHourlyRate))
            + (shift.netMissingMiles ? ` · ${t('js.home.missingMiles')}` : '');

        row.innerHTML = `
            <div class="date-badge"><small>${escapeHtml(month)}</small><strong>${day}</strong></div>
            <div class="shift-main"><strong>${escapeHtml(shift.station)}${edited ? `<small class="edited-tag">${escapeHtml(t('js.app.edited'))}</small>` : ''}${statusBadge}</strong><span>${escapeHtml(t('js.app.weekdayShift', weekday))}</span></div>
            <div class="shift-time"><strong>${formatTime(shift.startTime)} – ${formatTime(shift.endTime)}</strong><span>${escapeHtml(t('js.app.scheduledTime'))}</span></div>
            <div class="shift-pay"><strong>${formatMoney(total)}</strong><span>${workSummary}</span><span class="net-line">${escapeHtml(netLine)}</span>${shift.netMissingMiles ? `<button class="text-button add-miles-button" type="button">${escapeHtml(t('js.home.addMiles'))}</button>` : ''}${route ? `<span>${escapeHtml(route)}</span>` : ''}</div>
            <button class="edit-shift-button" type="button">
                <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m4 20 4.2-1 10.9-10.9a2.1 2.1 0 0 0-3-3L5.2 16 4 20Zm10.5-13.5 3 3"/></svg>
            </button>
        `;

        row.dataset.shiftId = shift.id;
        const editButton = row.querySelector('.edit-shift-button');
        editButton.setAttribute('aria-label', t('js.app.editShiftOn', shift.station, shift.date));
        editButton.addEventListener('click', () => openEditModal(shift, editButton));
        // The line can be cut off on a narrow phone, so its full text is also its tooltip.
        row.querySelector('.net-line').title = netLine;
        const addMilesButton = row.querySelector('.add-miles-button');
        if (addMilesButton) {
            addMilesButton.setAttribute('aria-label', t('js.app.addMilesTo', shift.station, formatDate(shift.date)));
            addMilesButton.addEventListener('click', event => {
                event.stopPropagation();
                openEditModal(shift, addMilesButton, {focusMiles: true});
            });
        }
        elements.historyList.append(window.flexbuddySwipe ? window.flexbuddySwipe.wrap(row, {
            onEdit: () => openEditModal(shift, editButton),
            onDelete: () => deleteShiftFromList(shift),
            onDuplicate: () => duplicateShift(shift, editButton),
            deleteQuestion: t('js.app.deleteQuestion', shift.station, formatDate(shift.date))
        }) : row);
    }
    elements.showMoreButton.classList.toggle('is-hidden', visibleShiftCount >= shifts.length);
    refreshPendingTags();
}

/** Tags the history rows whose finish is queued, and untags the ones that synced. */
function refreshPendingTags() {
    const pending = window.flexbuddyOutbox?.pendingShiftIds() ?? new Set();
    elements.historyList.querySelectorAll('[data-shift-id]').forEach(row => {
        const heading = row.querySelector('.shift-main strong');
        const tag = heading?.querySelector('.pending-tag');
        const queued = pending.has(Number(row.dataset.shiftId));
        if (queued && !tag) {
            const added = document.createElement('small');
            added.className = 'pending-tag';
            added.textContent = t('js.common.pending');
            heading.append(added);
        } else if (!queued && tag) {
            tag.remove();
        }
    });
}

function openEditModal(shift, trigger, options = {}) {
    editMode = shift.id == null ? 'new' : 'edit';
    editingShiftId = shift.id ?? undefined;
    editOriginalStatus = shift.status || 'COMPLETED';
    lastFocusedElement = trigger;
    editSnapshot = {...shift};
    elements.editStation.value = shift.station ?? '';
    elements.editDate.value = shift.date ?? '';
    elements.editStartTime.value = trimTime(shift.startTime);
    elements.editEndTime.value = trimTime(shift.endTime);
    elements.editBasePay.value = shift.basePay ?? '';
    elements.editTips.value = shift.tips ?? 0;
    elements.editMiles.value = shift.miles ?? '';
    elements.editActualStart.value = trimTime(shift.details?.actualStart);
    elements.editActualEnd.value = trimTime(shift.details?.actualEnd);
    elements.editActualTimes.open = Boolean(shift.details?.actualStart);
    updateActualSummary();
    elements.editOdometerStart.value = shift.details?.odometerStart ?? '';
    elements.editOdometerEnd.value = shift.details?.odometerEnd ?? '';
    elements.editOdometer.open = shift.details?.odometerStart != null || shift.details?.odometerEnd != null;
    updateOdometerSummary();
    elements.editStops.value = shift.details?.stops ?? '';
    elements.editPackages.value = shift.details?.packages ?? '';
    elements.editReturns.value = shift.details?.returns ?? '';
    elements.editRoute.open = [shift.details?.stops, shift.details?.packages, shift.details?.returns].some(value => value != null);
    updateRouteSummary();
    elements.editStatus.value = options.status || editOriginalStatus;
    // A block that already happened cannot go back to scheduled; delete it and add it again instead.
    [...elements.editStatus.options].forEach(option => {
        option.disabled = option.value === 'SCHEDULED' && editMode === 'edit' && editOriginalStatus !== 'SCHEDULED';
    });
    const isNew = editMode === 'new';
    elements.editDialogTitle.textContent = isNew ? newShiftCopy(elements.editStatus.value).title : t('js.app.editShift');
    elements.editDialogDescription.textContent = isNew
        ? newShiftCopy(elements.editStatus.value).description
        : t('js.app.updateValues');
    elements.deleteShiftButton.classList.toggle('is-hidden', isNew);
    elements.duplicateShiftButton.classList.toggle('is-hidden', isNew);
    elements.notWorkedButton.classList.toggle('is-hidden',
        !window.flexbuddyNotWorked.canMarkNotWorked(editMode, editOriginalStatus));
    elements.linkedExpensesSection.classList.toggle('is-hidden', isNew);
    // Only a worked block has a checklist: a scheduled one counts for nothing until it is confirmed.
    elements.reviewBlockButton.classList.toggle('is-hidden', isNew || editOriginalStatus !== 'COMPLETED');
    elements.editTimestamps.classList.toggle('is-hidden', isNew);
    if (!isNew) {
        loadLinkedExpenses(shift.id);
        elements.editTimestamps.textContent = timestampSummary(shift);
        elements.editTimestamps.title = t('js.app.createdUpdated', formatTimestamp(shift.createdAt), formatTimestamp(shift.updatedAt));
    }
    applyEditStatusRules(Boolean(options.status) && options.status !== editOriginalStatus);
    applyOfflineEditState();
    hideDeleteConfirmation();
    hideNotWorkedConfirmation();
    hideMessage(elements.editError);
    elements.editModal.classList.remove('is-hidden');
    document.body.classList.add('modal-open');
    if (options.focusPay) {
        elements.editBasePay.focus();
        elements.editBasePay.select();
    } else if (options.focusMiles && !elements.editMiles.disabled) {
        elements.editMiles.focus();
    } else {
        elements.editStation.focus();
    }
}

/** The words for adding a shift, which follow the chosen status: a scheduled block is a plan, anything else happened. */
function newShiftCopy(status) {
    return status === 'SCHEDULED'
        ? {title: t('js.common.addScheduledShift'),
            description: t('js.app.addScheduledDescription'),
            toast: [t('js.app.shiftScheduled'), t('js.app.shiftScheduledMessage')]}
        : {title: t('js.common.addShift'),
            description: t('js.app.addShiftDescription'),
            toast: [t('js.app.shiftAdded'), t('js.app.shiftAddedMessage')]};
}

function applyEditStatusRules(statusChanged = false) {
    const status = elements.editStatus.value;
    if (editMode === 'new') {
        // The title and description follow the status while adding, but a description written for something else,
        // such as a duplicate's "A copy of ...", is left alone.
        elements.editDialogTitle.textContent = newShiftCopy(status).title;
        const standard = ['SCHEDULED', 'COMPLETED'].map(name => newShiftCopy(name).description);
        if (standard.includes(elements.editDialogDescription.textContent)) {
            elements.editDialogDescription.textContent = newShiftCopy(status).description;
        }
    }
    const hints = {
        SCHEDULED: t('js.app.hintScheduled'),
        COMPLETED: t('js.app.hintCompleted'),
        CANCELLED: t('js.app.hintCancelled'),
        FORFEITED: t('js.app.hintForfeited')
    };
    elements.editStatusHint.textContent = hints[status];
    elements.editBasePayLabel.textContent = status === 'SCHEDULED' ? t('js.app.offeredPay')
        : status === 'CANCELLED' ? t('js.app.cancellationPay') : t('js.app.basePay');
    elements.editBasePay.min = status === 'COMPLETED' || status === 'SCHEDULED' ? '0.01' : '0';
    const tipsAllowed = status === 'COMPLETED';
    elements.editTips.disabled = !tipsAllowed;
    if (!tipsAllowed) elements.editTips.value = 0;
    elements.editMiles.disabled = status === 'SCHEDULED';
    if (status === 'SCHEDULED') elements.editMiles.value = '';
    // Only a worked block has actual times; a scheduled one gets its start from the Start block button.
    elements.editActualTimes.classList.toggle('is-hidden', status !== 'COMPLETED');
    elements.editOdometer.classList.toggle('is-hidden', status === 'SCHEDULED');
    elements.editRoute.classList.toggle('is-hidden', status !== 'COMPLETED');
    // The offered amount is never earned by a cancelled or forfeited block unless the driver enters it.
    if (statusChanged && (status === 'CANCELLED' || status === 'FORFEITED')) elements.editBasePay.value = 0;
    elements.saveEditButton.querySelector('span').textContent = saveEditLabel();
}

/** Minutes between two "HH:MM" values, the finish wrapping past midnight, or null when either is missing. */
function minutesBetween(start, end) {
    if (!start || !end) return null;
    const toMinutes = value => value.split(':').map(Number).reduce((hours, minutes) => hours * 60 + minutes);
    const minutes = toMinutes(end) - toMinutes(start);
    return minutes < 0 ? minutes + 1440 : minutes;
}

/** "42 stops · 5.7 min/stop · 1 return", from whichever counts are known; empty when there are no stops or returns. */
function routeSummary(stops, returns, clockedMinutes) {
    const parts = [];
    if (stops) {
        parts.push(tn(stops, 'js.app.stops'));
        if (clockedMinutes) parts.push(t('js.app.minPerStop', (clockedMinutes / stops).toFixed(1)));
    }
    if (returns !== null && returns !== undefined) parts.push(tn(returns, 'js.app.returns'));
    return parts.join(' · ');
}

function updateRouteSummary() {
    const count = input => input.value === '' ? null : Number(input.value);
    const clocked = minutesBetween(elements.editActualStart.value, elements.editActualEnd.value)
        || minutesBetween(elements.editStartTime.value, elements.editEndTime.value);
    elements.editRouteSummary.textContent = routeSummary(count(elements.editStops), count(elements.editReturns), clocked) || t('js.app.optional');
}

function odometerMiles() {
    if (elements.editOdometerStart.value === '' || elements.editOdometerEnd.value === '') return null;
    return Number(elements.editOdometerEnd.value) - Number(elements.editOdometerStart.value);
}

function updateOdometerSummary() {
    const miles = odometerMiles();
    elements.editOdometerSummary.textContent = miles === null ? t('js.app.optional')
        : miles < 0 ? t('js.app.endBelowStart') : t('js.common.miles', miles.toFixed(1));
}

function updateActualSummary() {
    const actual = minutesBetween(elements.editActualStart.value, elements.editActualEnd.value);
    const scheduled = minutesBetween(elements.editStartTime.value, elements.editEndTime.value);
    if (!actual) {
        elements.editActualSummary.textContent = elements.editActualStart.value ? t('js.app.startedNotFinished') : t('js.app.optional');
        return;
    }
    const early = scheduled === null ? 0 : scheduled - actual;
    const pace = early > 0 ? ` · ${t('js.finish.early', formatMinutes(early))}` : early < 0 ? ` · ${t('js.finish.over', formatMinutes(-early))}` : '';
    elements.editActualSummary.textContent = `${t('js.app.onTheClock', formatMinutes(actual))}${pace}`;
}

function saveEditLabel() {
    const status = elements.editStatus.value;
    if (editMode === 'new') return status === 'SCHEDULED' ? t('js.common.addScheduledShift') : t('js.common.addShift');
    if (status === editOriginalStatus) return t('js.app.saveChanges');
    return {COMPLETED: t('js.app.markCompleted'), CANCELLED: t('js.app.markCancelled'), FORFEITED: t('js.app.markForfeited')}[status]
        || t('js.app.saveChanges');
}

/** Leaves the edit dialog for the block's checklist, unless that would drop changes the driver hasn't saved. */
function reviewEditedBlock() {
    if (editHasChanges()) {
        showMessage(elements.editError, t('js.blockReview.saveFirst'));
        return;
    }
    const id = editingShiftId;
    const from = lastFocusedElement;
    lastFocusedElement = undefined;
    closeEditModal();
    window.flexbuddyBlockReview.open(id, from);
}

/** True when any field of the edit dialog differs from the block as it was opened. */
function editHasChanges() {
    const shift = editSnapshot ?? {};
    const details = shift.details ?? {};
    const fields = [
        [elements.editStation, shift.station], [elements.editDate, shift.date],
        [elements.editStartTime, trimTime(shift.startTime)], [elements.editEndTime, trimTime(shift.endTime)],
        [elements.editBasePay, shift.basePay], [elements.editTips, shift.tips ?? 0], [elements.editMiles, shift.miles],
        [elements.editStatus, editOriginalStatus], [elements.editActualStart, trimTime(details.actualStart)],
        [elements.editActualEnd, trimTime(details.actualEnd)], [elements.editOdometerStart, details.odometerStart],
        [elements.editOdometerEnd, details.odometerEnd], [elements.editStops, details.stops],
        [elements.editPackages, details.packages], [elements.editReturns, details.returns]
    ];
    return fields.some(([input, saved]) => !sameFieldValue(input.value, saved));
}

/** "12.50" and 12.5 are the same amount; dates, times and text compare as written. */
function sameFieldValue(value, saved) {
    const typed = String(value ?? '').trim();
    const kept = saved == null ? '' : String(saved).trim();
    if (typed === kept) return true;
    return typed !== '' && kept !== '' && !Number.isNaN(Number(typed)) && !Number.isNaN(Number(kept))
        && Number(typed) === Number(kept);
}

function closeEditModal() {
    elements.editModal.classList.add('is-hidden');
    document.body.classList.remove('modal-open');
    elements.editForm.reset();
    hideMessage(elements.editError);
    editingShiftId = undefined;
    editSnapshot = undefined;
    editMode = 'edit';
    editOriginalStatus = undefined;
    elements.editTips.disabled = false;
    elements.editMiles.disabled = false;

    if (lastFocusedElement?.isConnected) lastFocusedElement.focus();
    lastFocusedElement = undefined;
}

const MARKED_MESSAGES = {
    COMPLETED: 'js.app.markedCompleted', CANCELLED: 'js.app.markedCancelled', FORFEITED: 'js.app.markedForfeited',
    SCHEDULED: 'js.app.markedScheduled'
};

async function saveEditedShift(event) {
    event.preventDefault();
    hideMessage(elements.editError);

    if (!elements.editForm.reportValidity() || (editMode === 'edit' && editingShiftId === undefined)) return;

    const shiftId = editingShiftId;
    const creating = editMode === 'new';
    const statusChanged = !creating && elements.editStatus.value !== editOriginalStatus;
    const shift = {
        station: elements.editStation.value.trim(),
        date: elements.editDate.value,
        startTime: elements.editStartTime.value,
        endTime: elements.editEndTime.value,
        basePay: Number(elements.editBasePay.value),
        tips: Number(elements.editTips.value),
        miles: elements.editMiles.value === '' ? null : Number(elements.editMiles.value),
        status: elements.editStatus.value
    };
    // A block that happened always sends its details so a cleared value is saved. Only a worked block has actual
    // times, and a scheduled one leaves its details to the server.
    if (shift.status !== 'SCHEDULED') {
        const worked = shift.status === 'COMPLETED';
        if (worked && elements.editActualEnd.value && !elements.editActualStart.value) {
            showMessage(elements.editError, t('js.finish.enterStart'));
            return;
        }
        if (odometerMiles() !== null && odometerMiles() < 0) {
            showMessage(elements.editError, t('js.finish.odometerEndBeforeStart'));
            return;
        }
        const reading = input => input.value === '' ? null : Number(input.value);
        if (worked && reading(elements.editReturns) !== null && reading(elements.editPackages) !== null
                && reading(elements.editReturns) > reading(elements.editPackages)) {
            showMessage(elements.editError, t('js.finish.returnsOverPackages'));
            return;
        }
        shift.details = {
            actualStart: worked ? elements.editActualStart.value || null : null,
            actualEnd: worked ? elements.editActualEnd.value || null : null,
            odometerStart: reading(elements.editOdometerStart),
            odometerEnd: reading(elements.editOdometerEnd),
            stops: worked ? reading(elements.editStops) : null,
            packages: worked ? reading(elements.editPackages) : null,
            returns: worked ? reading(elements.editReturns) : null
        };
    }

    setEditSaving(true);

    try {
        let response;
        if (creating) {
            const result = await window.flexbuddyOutbox.submit('shift-create', {
                method: 'POST', url: '/shifts', body: shift,
                summary: {title: ['js.app.queuedAddShift', shift.station],
                    detail: `${formatShortDay(shift.date)} · ${formatTime(shift.startTime)}–${formatTime(shift.endTime)}`}
            });
            if (result.queued) {
                closeEditModal();
                showToast(t('js.finish.savedOfflineTitle'), t('js.app.savedOfflineAdded', shift.station, formatDate(shift.date)));
                return;
            }
            response = result.sent;
        } else {
            response = await apiFetch(`/shifts/${shiftId}`, {
                method: 'PUT',
                headers: retryHeaders(crypto.randomUUID(), {'Content-Type': 'application/json'}),
                body: JSON.stringify(shift)
            });
        }

        if (!response.ok && !(await isDuplicate(response))) {
            const message = await response.text();
            throw new Error(message || t('js.app.updateFailedCheck'));
        }

        const previous = editSnapshot;
        closeEditModal();
        if (creating) {
            showToast(...newShiftCopy(shift.status).toast);
        } else if (statusChanged) {
            showToast(t('js.app.shiftUpdated'), t(MARKED_MESSAGES[shift.status] || 'js.app.shiftUpdatedMessage'));
        } else {
            showToast(t('js.app.shiftUpdated'), t('js.app.shiftUpdatedMessage'), {
                actionLabel: t('js.common.undo'),
                duration: 8000,
                onAction: () => undoEdit(shiftId, previous)
            });
        }
        await loadStations();
        await loadDashboard();
    } catch (error) {
        showMessage(elements.editError, error.message || t('js.app.updateFailed'));
    } finally {
        setEditSaving(false);
        applyOfflineEditState();
    }
}

/** Asks before moving a block marked completed by mistake back to scheduled, naming every saved value it clears. */
function showNotWorkedConfirmation() {
    if (!editSnapshot) return;
    const miles = new Intl.NumberFormat(appLocale(), {maximumFractionDigits: 1});
    const items = window.flexbuddyNotWorked.clearedItems(editSnapshot,
        {money: formatMoney, number: value => miles.format(Number(value))});
    elements.notWorkedCleared.textContent = items.length
        ? t('js.notWorked.clears', items.map(([key, ...args]) => t(key, ...args)).join(', '))
        : t('js.notWorked.nothingElse');
    hideMessage(elements.editError);
    elements.editForm.querySelector('.form-actions').classList.add('is-hidden');
    elements.notWorkedConfirm.classList.remove('is-hidden');
    elements.cancelNotWorkedButton.focus();
}

function hideNotWorkedConfirmation() {
    elements.editForm.querySelector('.form-actions').classList.remove('is-hidden');
    elements.notWorkedConfirm.classList.add('is-hidden');
}

/**
 * Moves the saved block back to scheduled. It names the version the dialog opened, so a block changed elsewhere is
 * refused instead of corrected blind, and Undo puts every cleared value back.
 */
async function markEditedShiftNotWorked() {
    const shiftId = editingShiftId;
    const previous = editSnapshot;
    if (shiftId === undefined || !previous) return;
    hideMessage(elements.editError);
    notWorkedSending = true;
    elements.confirmNotWorkedButton.disabled = true;
    try {
        const response = await apiFetch(`/shifts/${shiftId}/not-worked`, {
            method: 'POST',
            headers: retryHeaders(crypto.randomUUID(), {'Content-Type': 'application/json'}),
            body: JSON.stringify({expectedUpdatedAt: previous.updatedAt})
        });
        if (response.status === 409) {
            hideNotWorkedConfirmation();
            showMessage(elements.editError, t('js.notWorked.changedElsewhere'));
            return;
        }
        if (!response.ok) throw new Error(await response.text() || t('js.notWorked.failed'));
        closeEditModal();
        showToast(t('js.notWorked.doneTitle'), t('js.notWorked.doneMessage', previous.station, formatDate(previous.date)), {
            actionLabel: t('js.common.undo'),
            duration: 8000,
            onAction: () => undoEdit(shiftId, previous)
        });
        await loadStations();
        await loadDashboard();
    } catch (error) {
        showMessage(elements.editError, error.message || t('js.notWorked.failed'));
    } finally {
        notWorkedSending = false;
        elements.confirmNotWorkedButton.disabled = false;
        applyOfflineEditState();
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
        const batch = await deleteShift(id);
        closeEditModal();
        await afterShiftDeleted(batch);
    } catch (error) {
        showMessage(elements.editError, error.message || t('js.app.deleteFailed'));
    } finally {
        elements.confirmDeleteButton.disabled = false;
    }
}

/** Deletes a shift confirmed from a swiped history row. */
async function deleteShiftFromList(shift) {
    try {
        await afterShiftDeleted(await deleteShift(shift.id));
    } catch (error) {
        showToast(t('js.app.notDeleted'), error.message || t('js.app.deleteFailed'), {alert: true});
        await loadDashboard();
    }
}

async function deleteShift(id) {
    const response = await apiFetch(`/shifts/${id}`, {method: 'DELETE', headers: csrfHeaders()});
    if (!response.ok) throw new Error(await response.text() || t('js.app.deleteFailed'));
    return response.headers.get('X-Delete-Batch');
}

async function afterShiftDeleted(batch) {
    showToast(t('js.app.shiftDeleted'), t('js.app.availableInTrash'), {
        actionLabel: t('js.common.undo'),
        duration: 8000,
        alert: true,
        onAction: () => restoreBatch(batch)
    });
    await loadStations();
    await loadDashboard();
}

/** Starts a new scheduled block like this one, on the same weekday in the coming week. */
function duplicateShift(shift, trigger) {
    closeEditModal();
    const source = parseLocalDate(shift.date);
    const date = new Date();
    date.setHours(0, 0, 0, 0);
    do {
        date.setDate(date.getDate() + 1);
    } while (date.getDay() !== source.getDay());
    openEditModal({id: null, station: shift.station, date: toIsoDate(date), startTime: shift.startTime,
        endTime: shift.endTime, basePay: shift.status === 'COMPLETED' ? shift.basePay : '', tips: 0, miles: null,
        status: 'SCHEDULED'},
        trigger, {status: 'SCHEDULED'});
    elements.editDialogDescription.textContent =
        t('js.app.copyOf', shift.station, formatDate(shift.date));
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
            miles: previous.miles,
            // Puts the status back too, for an undo of a block moved back to scheduled.
            status: previous.status,
            details: previous.details && previous.status !== 'SCHEDULED' ? {
                actualStart: previous.status === 'COMPLETED' ? previous.details.actualStart : null,
                actualEnd: previous.status === 'COMPLETED' ? previous.details.actualEnd : null,
                odometerStart: previous.details.odometerStart,
                odometerEnd: previous.details.odometerEnd,
                stops: previous.status === 'COMPLETED' ? previous.details.stops : null,
                packages: previous.status === 'COMPLETED' ? previous.details.packages : null,
                returns: previous.status === 'COMPLETED' ? previous.details.returns : null
            } : undefined
        })
    });
    if (!response.ok) throw new Error(await response.text());
    showToast(t('js.app.editUndone'), t('js.app.editUndoneMessage'));
    await loadDashboard();
}

async function restoreBatch(batch) {
    if (!batch) return;
    const response = await apiFetch(`/shifts/restore-batch/${encodeURIComponent(batch)}`, {
        method: 'POST', headers: csrfHeaders()
    });
    if (!response.ok) throw new Error(await response.text());
    showToast(t('js.app.shiftRestored'), t('js.app.shiftRestoredMessage'));
    await loadStations();
    await loadDashboard();
    if (elements.trashSection.open) await loadTrash();
}

async function loadTrash() {
    elements.trashList.innerHTML = `<p>${escapeHtml(t('js.app.loadingTrash'))}</p>`;
    try {
        const response = await apiFetch('/shifts/trash');
        if (!response.ok) throw new Error(await response.text());
        renderTrash(await response.json());
    } catch (error) {
        elements.trashList.innerHTML = `<p>${escapeHtml(error.message || t('js.app.trashNotLoaded'))}</p>`;
    }
}

function renderTrash(shifts) {
    elements.trashCount.textContent = shifts.length ? `(${shifts.length})` : '';
    elements.emptyTrashButton.classList.toggle('is-hidden', shifts.length === 0);
    elements.trashList.replaceChildren();
    if (!shifts.length) {
        elements.trashList.innerHTML = `<p>${escapeHtml(t('js.app.noTrash'))}</p>`;
        return;
    }
    shifts.forEach(shift => {
        const row = document.createElement('article');
        row.className = 'trash-row';
        row.innerHTML = `<div><strong>${escapeHtml(shift.station)} · ${formatDate(shift.date)}</strong><span>${escapeHtml(t('js.app.deletedAt', formatTimestamp(shift.deletedAt)))}</span></div><div><button class="text-button restore-trash" type="button">${escapeHtml(t('js.common.restore'))}</button><button class="danger-text-button permanent-trash" type="button">${escapeHtml(t('js.common.deletePermanently'))}</button></div>`;
        row.querySelector('.restore-trash').addEventListener('click', () => restoreTrashShift(shift.id));
        row.querySelector('.permanent-trash').addEventListener('click', () => openConfirm(
            t('js.app.deleteShiftPermanentlyTitle'),
            t('js.app.deleteShiftPermanentlyMessage', shift.station, formatDate(shift.date)),
            () => permanentlyDeleteShift(shift.id)
        ));
        elements.trashList.append(row);
    });
}

async function restoreTrashShift(id) {
    const response = await apiFetch(`/shifts/${id}/restore`, {method: 'POST', headers: csrfHeaders()});
    if (!response.ok) throw new Error(await response.text());
    showToast(t('js.app.shiftRestored'), t('js.app.shiftRestoredMessage'));
    await loadTrash();
    await loadStations();
    await loadDashboard();
}

async function permanentlyDeleteShift(id) {
    const response = await apiFetch(`/shifts/trash/${id}`, {method: 'DELETE', headers: csrfHeaders()});
    if (!response.ok) throw new Error(await response.text());
    showToast(t('js.app.permanentlyDeletedTitle'), t('js.app.permanentlyDeletedMessage'), {alert: true});
    await loadTrash();
}

async function emptyTrash() {
    const response = await apiFetch('/shifts/trash', {method: 'DELETE', headers: csrfHeaders()});
    if (!response.ok) throw new Error(await response.text());
    const result = await response.json();
    showToast(t('js.app.trashEmptiedTitle'), t('js.app.trashEmptiedMessage', result.deleted), {alert: true});
    await loadTrash();
}

function openConfirm(title, message, action, acceptLabel = t('js.common.deletePermanently')) {
    pendingConfirmAction = action;
    lastFocusedElement = document.activeElement;
    elements.confirmTitle.textContent = title;
    elements.confirmMessage.textContent = message;
    elements.acceptConfirmButton.textContent = acceptLabel;
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
    cancelImportRequest();
    endBatch();
    clearImportReview();
    importRequestId = undefined;
    importStatusRequestId = undefined;
    elements.screenshotInput.value = '';
    elements.dropZone.classList.remove('is-hidden');
}

/** Empties the file card and the review form, ready for another screenshot. */
function clearImportReview() {
    setProcessing(false);
    if (selectedFileUrl) {
        URL.revokeObjectURL(selectedFileUrl);
        selectedFileUrl = undefined;
    }
    elements.previewForm.reset();
    elements.fileCard.classList.add('is-hidden');
    elements.fileActions.classList.add('is-hidden');
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
    importCandidate = undefined;
    importWarnings = [];
    elements.importStatus.value = window.flexbuddyImportStatus.INITIAL_STATUS;
    elements.importStatusHint.textContent = '';
    elements.tips.disabled = false;
    elements.miles.disabled = false;
    importDuplicateEdited = false;
    elements.scheduleMatchNotice.classList.add('is-hidden');
    elements.duplicateNotice.classList.add('is-hidden');
    setSaving(false);
}

// ===== Several screenshots at once: import-queue.js decides what each row says and which file comes next =====

/** The batch file in the review form, while it is still unsaved. */
function currentBatchItem() {
    if (!importBatch || importBatch.current === undefined) return undefined;
    const item = importBatch.items.find(entry => entry.number === importBatch.current);
    return item?.state === 'ready' ? item : undefined;
}

async function startBatch(files) {
    const queue = window.flexbuddyImportQueue;
    hideMessage(elements.uploadError);
    if (files.length > queue.MAX_FILES) {
        elements.screenshotInput.value = '';
        showMessage(elements.uploadError, t('js.importQueue.tooMany'));
        return;
    }
    resetImport();
    elements.dropZone.classList.add('is-hidden');
    // Files the server would refuse are never hashed or sent.
    const signatures = await Promise.all(files.map(file => (queue.fileProblemKey(file) ? undefined : fileSignature(file))));
    importBatch = {items: queue.createItems(files, signatures, () => crypto.randomUUID()).items, current: undefined, paused: false};
    renderBatch();
    readNextInBatch();
}

/** Identifies a file's bytes, so the same screenshot picked twice is read once. */
async function fileSignature(file) {
    try {
        const digest = await crypto.subtle.digest('SHA-256', await file.arrayBuffer());
        return Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, '0')).join('');
    } catch {
        return `${file.name}|${file.size}|${file.lastModified}`;
    }
}

/**
 * Reads the next waiting file, then the one after, one at a time. A file the server can't read is marked and the next
 * one is read; a busy server, the time limit or a lost connection pauses reading until the driver taps Continue
 * reading. Reading never saves anything.
 */
async function readNextInBatch() {
    const batch = importBatch;
    const queue = window.flexbuddyImportQueue;
    if (!batch || batch.reading || batch.paused) return;
    const item = queue.nextToRead(batch.items);
    if (!item) {
        renderBatch();
        return;
    }
    if (window.flexbuddyPwa?.isOffline()) {
        batch.paused = true;
        renderBatch();
        return;
    }
    item.state = 'reading';
    batch.reading = item;
    const abort = new AbortController();
    batch.abort = abort;
    if (batch.current === undefined) showBatchIdle();
    renderBatch();

    let timedOut = false;
    let status = 0;
    const timer = setTimeout(() => {
        timedOut = true;
        abort.abort();
    }, IMPORT_TIMEOUT_MS);
    try {
        const response = await requestPreview(item.file, abort.signal);
        status = response.status;
        if (!response.ok) throw new Error(await response.text() || t('js.app.screenshotNotProcessed'));
        const preview = await response.json();
        if (!preview.shifts?.[0]) {
            status = 400;
            throw new Error(t('js.app.noShiftDetails'));
        }
        // Stopped, or the whole import cleared, while this answer was on its way.
        if (importBatch !== batch || item.state !== 'reading') return;
        item.preview = preview;
        item.state = 'ready';
    } catch (error) {
        if (importBatch !== batch || item.state !== 'reading') return;
        const failure = queue.readFailure(timedOut ? 0 : status);
        Object.assign(item, {
            state: 'failed',
            retry: failure.retry,
            error: timedOut ? t('js.app.screenshotTimedOut') : error.message || t('js.app.screenshotNotProcessed')
        });
        if (failure.pause) batch.paused = true;
    } finally {
        clearTimeout(timer);
        if (batch.reading === item) {
            batch.reading = undefined;
            batch.abort = undefined;
        }
    }
    if (batch.current === undefined) openNextBatchReview(item.number - 1);
    else renderBatch();
    readNextInBatch();
}

/** Opens the next file read and not yet saved after file `after`, or shows the file being read while none is. */
function openNextBatchReview(after = 0) {
    const batch = importBatch;
    const next = window.flexbuddyImportQueue.nextToReview(batch.items, after);
    batch.current = next?.number;
    if (next) showBatchReview(next);
    else showBatchIdle();
    renderBatch();
}

function showBatchReview(item) {
    clearImportReview();
    showSelectedFile(item.file);
    elements.fileActions.classList.add('is-hidden');
    populatePreview(item.preview);
}

function showBatchIdle() {
    clearImportReview();
    const reading = importBatch?.reading;
    if (!reading) return;
    showSelectedFile(reading.file);
    elements.fileActions.classList.add('is-hidden');
    setProcessing(true);
}

/** Marks a file saved with the block the server answered with, refreshes the totals, and opens the next file. */
function recordBatchSave(item, block, kind, changed = false) {
    item.state = 'saved';
    item.uncertain = false;
    item.saved = {
        shiftId: block.id, status: block.status, station: block.station, date: block.date,
        startTime: trimTime(block.startTime), endTime: trimTime(block.endTime), kind, changed
    };
    if (changed) showToast(t('js.importQueue.savedBeforeTitle'), t('js.importQueue.savedBeforeMessage'));
    loadStations().then(loadDashboard).catch(() => {});
    openNextBatchReview(item.number);
    scrollToReview();
}

/** On a phone the review form is below the list, so moving to the next file brings it into view. */
function scrollToReview() {
    if (!currentBatchItem() || !window.matchMedia('(max-width: 900px)').matches) return;
    elements.previewForm.closest('.preview-panel').scrollIntoView({behavior: 'smooth', block: 'start'});
}

function skipBatchItem() {
    const item = currentBatchItem();
    if (!item || importSaveInFlight) return;
    item.state = 'skipped';
    openNextBatchReview(item.number);
    scrollToReview();
}

function reviewBatchItem(item) {
    if (importSaveInFlight || item.state !== 'ready') return;
    importBatch.current = item.number;
    showBatchReview(item);
    renderBatch();
    scrollToReview();
}

function retryBatchItem(item) {
    if (item.state !== 'failed' || !item.retry) return;
    Object.assign(item, {state: 'waiting', error: undefined, retry: undefined});
    importBatch.paused = false;
    renderBatch();
    readNextInBatch();
}

function resumeBatch() {
    if (!importBatch) return;
    importBatch.paused = false;
    renderBatch();
    readNextInBatch();
}

/** Stops reading and reviewing; the blocks already saved stay saved and listed. */
function stopBatch() {
    const batch = importBatch;
    if (!batch || importSaveInFlight) return;
    window.flexbuddyImportQueue.stop(batch.items);
    const abort = batch.abort;
    Object.assign(batch, {reading: undefined, abort: undefined, current: undefined, paused: false});
    abort?.abort();
    clearImportReview();
    renderBatch();
}

function finishBatch() {
    resetImport();
    showDashboard();
}

/** Forgets the batch; called by resetImport, so every way of starting over also ends a batch. */
function endBatch() {
    if (!importBatch) return;
    const abort = importBatch.abort;
    importBatch = undefined;
    abort?.abort();
    renderBatch();
}

function handleQueueRowAction(event) {
    const button = event.target.closest('button[data-action]');
    const item = button && importBatch?.items.find(entry => entry.number === Number(button.dataset.number));
    if (!item) return;
    if (button.dataset.action === 'review') reviewBatchItem(item);
    else if (button.dataset.action === 'retry') retryBatchItem(item);
    else if (button.dataset.action === 'open') openBatchShift(item.saved.shiftId, button);
}

async function openBatchShift(shiftId, trigger) {
    hideMessage(elements.uploadError);
    try {
        const response = await apiFetch(`/shifts/${encodeURIComponent(shiftId)}`);
        if (!response.ok) throw new Error(await response.text() || t('js.app.requestFailed'));
        openEditModal(await response.json(), trigger);
    } catch (error) {
        showMessage(elements.uploadError, error.message || t('js.app.requestFailed'));
    }
}

const QUEUE_ACTION_TEXT = {review: 'js.importQueue.review', retry: 'js.importQueue.tryAgain', open: 'js.importQueue.open'};
const QUEUE_ACTION_LABEL = {
    review: 'js.importQueue.reviewLabel', retry: 'js.importQueue.tryAgainLabel', open: 'js.importQueue.openLabel'
};

function renderBatch() {
    const batch = importBatch;
    const queue = window.flexbuddyImportQueue;
    elements.importQueue.classList.toggle('is-hidden', !batch);
    elements.resetButton.classList.toggle('is-hidden', Boolean(batch));
    elements.skipImportButton.classList.toggle('is-hidden', !batch);
    if (!batch) {
        elements.importQueueList.replaceChildren();
        elements.importQueueStatus.textContent = '';
        return;
    }
    const finished = queue.isFinished(batch.items);
    elements.importQueueStatus.textContent = queue.headline(batch.items, batch.current, batch.paused);
    elements.resumeQueueButton.classList.toggle('is-hidden', !batch.paused || !queue.nextToRead(batch.items));
    elements.stopQueueButton.classList.toggle('is-hidden', finished);
    elements.finishQueueButton.classList.toggle('is-hidden', !finished);
    // Rows are rebuilt on every change, so a focused row button is found again in the new rows.
    const focused = elements.importQueueList.contains(document.activeElement) ? document.activeElement.dataset : undefined;
    elements.importQueueList.replaceChildren(...batch.items.map(item => queueRow(item, batch.current)));
    if (focused) {
        elements.importQueueList.querySelector(`button[data-action="${focused.action}"][data-number="${focused.number}"]`)
            ?.focus();
    }
}

function queueRow(item, current) {
    const queue = window.flexbuddyImportQueue;
    const row = document.createElement('li');
    row.className = 'import-queue-row';
    row.dataset.state = item.state;
    if (item.state === 'ready' && item.number === current) {
        row.classList.add('is-current');
        row.setAttribute('aria-current', 'step');
    }
    const number = document.createElement('span');
    number.className = 'import-queue-number';
    number.textContent = item.number;
    const file = document.createElement('div');
    file.className = 'import-queue-file';
    const name = document.createElement('strong');
    name.textContent = item.name;
    name.title = item.name;
    const status = document.createElement('small');
    status.textContent = queue.rowText(item, current, formatShortDay);
    file.append(name, status);
    row.append(number, file);
    for (const action of queue.rowActions(item, current)) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'text-button';
        button.dataset.action = action;
        button.dataset.number = item.number;
        button.textContent = t(QUEUE_ACTION_TEXT[action]);
        button.setAttribute('aria-label', t(QUEUE_ACTION_LABEL[action], item.number));
        row.append(button);
    }
    return row;
}

function setProcessing(processing) {
    elements.processingRow.classList.toggle('is-hidden', !processing);
}

function setSaving(saving) {
    elements.saveButton.disabled = saving;
    elements.saveButton.querySelector('span').textContent = saving ? t('js.app.addingShift')
        : saveLabel();
}

/** Editing, deleting and duplicating a saved shift need a connection; adding one does not, because it can be queued. */
function applyOfflineEditState() {
    const blocked = editMode === 'edit' && (window.flexbuddyPwa?.isOffline() ?? false);
    [elements.saveEditButton, elements.deleteShiftButton, elements.duplicateShiftButton, elements.notWorkedButton,
        elements.confirmNotWorkedButton].forEach(button => {
        button.disabled = blocked;
        button.title = blocked ? t('js.pwa.availableWhenOnline') : '';
    });
    if (notWorkedSending) elements.confirmNotWorkedButton.disabled = true;
}

function setEditSaving(saving) {
    elements.saveEditButton.disabled = saving;
    elements.saveEditButton.querySelector('span').textContent = saving ? t('js.app.saving') : saveEditLabel();
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
    return new Intl.DateTimeFormat(appLocale(), {hour: 'numeric', minute: '2-digit'})
        .format(new Date(2000, 0, 1, hours, minutes));
}

function formatMoney(value) {
    return new Intl.NumberFormat(appLocale(), {
        style: 'currency',
        currency: 'USD'
    }).format(Number(value || 0));
}

/** How a timed block's length compared with its schedule, for example "finish 38m early on average". */
function paceText(earlyMinutes) {
    if (earlyMinutes == null || earlyMinutes === 0) return t('js.app.paceOnSchedule');
    return earlyMinutes > 0 ? t('js.app.paceEarly', formatMinutes(earlyMinutes))
        : t('js.app.paceOver', formatMinutes(-earlyMinutes));
}

function formatMinutes(value) {
    const totalMinutes = Number(value || 0);
    const hours = Math.floor(totalMinutes / 60);
    const minutes = totalMinutes % 60;
    return t('js.common.hoursMinutes', hours, minutes);
}

function formatFileSize(bytes) {
    if (bytes < 1024 * 1024) return t('js.app.sizeKb', Math.max(1, Math.round(bytes / 1024)));
    return t('js.app.sizeMb', (bytes / (1024 * 1024)).toFixed(1));
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
    if (!['all', 'week', 'month', 'year', '30days', 'custom', 'payperiod', 'lastpayperiod'].includes(filterState.preset)) {
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
    elements.sortDirectionButton.textContent = filterState.dir === 'asc' ? t('js.app.sortAsc') : t('js.app.sortDesc');
    elements.sortDirectionButton.setAttribute('aria-label', filterState.dir === 'asc' ? t('js.app.sortAscending') : t('js.app.sortDescending'));
    elements.presetChips.forEach(chip => chip.classList.toggle('is-active', chip.dataset.preset === filterState.preset));
    elements.groupButtons.forEach(button => button.classList.toggle('is-active', button.dataset.group === reportGroupBy));
    elements.rangeChip.textContent = flexbuddyReports.rangeLabel(filterState);
}

async function applyPreset(preset) {
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
    } else if (PAY_PERIOD_PRESETS[preset] !== undefined) {
        const period = (payPeriods ?? await loadPayPeriods())?.periods[PAY_PERIOD_PRESETS[preset]];
        if (!period) {
            showToast(t('js.app.payPeriodUnavailable'), t('js.app.payPeriodUnavailableMessage'), {alert: true});
            return;
        }
        from = period.from;
        to = period.to;
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
        showMessage(elements.filterError, t('js.app.startAfterEnd'));
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

/**
 * Keeps the address matching the screen, so a reload comes back to it. Reports also carries its range, sort, grouping and
 * tab; every other screen has a plain address, so saving a shift elsewhere never leaves filter settings in it.
 */
function persistFilterState() {
    if (!urlSyncEnabled) return;
    let query = '';
    if (currentScreen === 'reports') query = flexbuddyReports.reportParams(filterState, reportGroupBy, reportTab).toString();
    else if (currentScreen !== 'dashboard') query = `screen=${currentScreen}`;
    window.history.replaceState({}, '', `${window.location.pathname}${query ? `?${query}` : ''}`);
}

async function loadStations() {
    try {
        const response = await apiFetch('/shifts/stations');
        if (!response.ok) throw new Error();
        const stations = await response.json();
        elements.filterStation.replaceChildren(new Option(t('js.app.allStations'), ''));
        stations.forEach(station => elements.filterStation.add(new Option(station, station)));
        window.flexbuddyEvaluate?.setStations(stations);
        if (filterState.station && !stations.some(station => station.toLowerCase() === filterState.station.toLowerCase())) {
            elements.filterStation.add(new Option(filterState.station, filterState.station));
        }
        elements.filterStation.value = filterState.station;
    } catch {
        elements.filterStation.replaceChildren(new Option(t('js.app.allStations'), ''));
    }
}

function updateResultSummary(shifts) {
    const count = shifts.length;
    elements.resultsSummary.textContent = flexbuddyReports.scopeLine(filterState, shifts, formatDate);
    elements.activeFilterSummary.textContent = flexbuddyReports.summaryLine(shifts);
    elements.exportCsvButton.href = `/shifts/export.csv?${buildQuery()}`;
    elements.exportCsvButton.classList.toggle('is-disabled', count === 0);
    elements.exportCsvButton.setAttribute('aria-disabled', String(count === 0));
    elements.exportCsvButton.title = count === 0
            ? t('js.app.noShiftsMatchActive')
            : t('js.app.exportMatching', count);
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
    window.scrollTo({top: 0, behavior: 'smooth'});
}

/** "Sep 29": a day without its year, for the short lines in the Waiting to sync strip. */
function formatShortDay(value) {
    return parseLocalDate(value).toLocaleDateString(appLocale(), {month: 'short', day: 'numeric'});
}

function formatDate(value) {
    return parseLocalDate(value).toLocaleDateString(appLocale(), {month: 'short', day: 'numeric', year: 'numeric'});
}

function formatTimestamp(value) {
    return value ? new Date(value).toLocaleString(appLocale()) : t('js.app.unknown');
}

function timestampSummary(shift) {
    if (!shift.createdAt) return '';
    const added = new Date(shift.createdAt).toLocaleDateString(appLocale(), {
        month: 'short', day: 'numeric', year: 'numeric'
    });
    const edited = shift.updatedAt && new Date(shift.updatedAt) - new Date(shift.createdAt) > 60000
            ? ` · ${t('js.app.lastEdited', new Date(shift.updatedAt).toLocaleDateString(appLocale(), {month: 'short', day: 'numeric', year: 'numeric'}))}`
            : '';
    return `${t('js.app.added', added)}${edited}`;
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

/**
 * Headers for a save that can be retried: the security token plus a key made once per submit, so a repeat of the
 * same request is recognised by the server instead of being applied twice.
 */
function retryHeaders(requestId, headers = {}) {
    return csrfHeaders({...headers, 'Idempotency-Key': requestId});
}

/** True for the 409 that a repeated create gets when the first copy already landed: the change was delivered. */
async function isDuplicate(response) {
    if (response.status !== 409) return false;
    try {
        return (await response.clone().json()).code === 'DUPLICATE';
    } catch {
        return false;
    }
}

function populateExpenseShifts(shifts) {
    const selected = elements.expenseShift.value;
    elements.expenseShift.innerHTML = `<option value="">${escapeHtml(t('js.app.noLinkedShift'))}</option>`;
    const selectedDate = elements.expenseDate.value;
    shifts.filter(shift => !selectedDate || shift.date === selectedDate || String(shift.id) === selected).forEach(shift => {
        const option = document.createElement('option');
        option.value = shift.id;
        option.textContent = `${formatDate(shift.date)} · ${shift.station}`;
        option.dataset.status = shift.status ?? '';
        elements.expenseShift.append(option);
    });
    keepEditingExpenseLink();
    if ([...elements.expenseShift.options].some(option => option.value === selected)) elements.expenseShift.value = selected;
    renderExpenseCategoryHint();
}

/**
 * The Shift list only holds blocks from History's current results, never deleted ones. An expense being edited keeps
 * its own link as an option, so saving it doesn't quietly unlink it.
 */
function keepEditingExpenseLink() {
    const link = editingExpenseLink;
    if (!link || [...elements.expenseShift.options].some(option => option.value === String(link.id))) return;
    const option = document.createElement('option');
    option.value = link.id;
    option.textContent = link.label;
    option.dataset.status = link.status;
    option.dataset.keptLink = 'true';
    elements.expenseShift.append(option);
}

/** Takes a kept link back out of the Shift list, so it is never offered for another expense. */
function dropKeptExpenseLink() {
    elements.expenseShift.querySelectorAll('option[data-kept-link]').forEach(option => option.remove());
}

const EXPENSE_CATEGORY_KEYS = {
    FUEL: 'js.app.categoryFuel', TOLL: 'js.app.categoryToll', PARKING: 'js.app.categoryParking',
    MAINTENANCE: 'js.app.categoryMaintenance', OTHER: 'js.app.categoryOther'
};

/** A category's name in the driver's language, as the expense list shows it in capitals. */
function categoryName(category) {
    return EXPENSE_CATEGORY_KEYS[category] ? t(EXPENSE_CATEGORY_KEYS[category]) : category;
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
            ? t('js.app.costActual') : t('js.app.costStandard', formatMoney(settings.mileageRate));
        elements.expenseFuelNote.hidden = settings.vehicleCostMethod !== 'STANDARD_MILEAGE';
        expenseCostMethod = settings.vehicleCostMethod;
        renderExpenseCategoryHint();
    } catch (error) {
        elements.expenseList.innerHTML = `<p class="history-empty">${escapeHtml(error.message || t('js.app.expensesNotLoaded'))}</p>`;
    }
}

function renderExpenseSummary(summary) {
    elements.expenseSummaryTotal.textContent = formatMoney(summary.total);
    elements.expenseSummaryCount.textContent = tn(summary.count || 0, 'js.app.entries');
    elements.expenseFuel.textContent = formatMoney(summary.byCategory?.FUEL);
    elements.expenseRoad.textContent = formatMoney(Number(summary.byCategory?.TOLL || 0) + Number(summary.byCategory?.PARKING || 0));
    const categories = ['FUEL', 'TOLL', 'PARKING', 'MAINTENANCE', 'OTHER'];
    const max = Math.max(...categories.map(category => Number(summary.byCategory?.[category] || 0)), 1);
    elements.expenseCategoryBreakdown.replaceChildren(...categories.map(category => {
        const row = document.createElement('div');
        const label = document.createElement('span');
        label.textContent = categoryName(category);
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

/** Where an expense belongs: its block's station, marked when that block is in Recently deleted. */
function expenseStationLabel(expense) {
    if (!expense.station) return '';
    return expense.shiftDeleted ? t('js.app.stationDeletedBlock', expense.station) : expense.station;
}

function renderExpenses(expenses) {
    elements.expenseList.replaceChildren();
    if (!expenses.length) {
        elements.expenseList.innerHTML = `<p class="history-empty">${escapeHtml(t('js.app.noExpensesMatch'))}</p>`;
        return;
    }
    expenses.forEach(expense => {
        const row = document.createElement('article');
        row.className = 'expense-row';
        row.innerHTML = `<div class="expense-category-icon">${escapeHtml(categoryName(expense.category).slice(0, 1).toUpperCase())}</div><div class="expense-main"><strong>${escapeHtml(categoryName(expense.category).toUpperCase())}</strong><span>${formatDate(expense.date)}${expense.station ? ` · ${escapeHtml(expenseStationLabel(expense))}` : ''}</span><small>${escapeHtml(expense.note || t('js.app.noNote'))}</small></div><strong class="expense-amount">${formatMoney(expense.amount)}</strong><div class="expense-row-actions"><button class="text-button edit-expense" type="button">${escapeHtml(t('js.common.edit'))}</button><button class="danger-text-button delete-expense" type="button">${escapeHtml(t('js.common.delete'))}</button></div>`;
        row.querySelector('.edit-expense').addEventListener('click', () => editExpense(expense));
        row.querySelector('.delete-expense').addEventListener('click', () => deleteExpense(expense));
        elements.expenseList.append(window.flexbuddySwipe ? window.flexbuddySwipe.wrap(row, {
            onEdit: () => editExpense(expense),
            onDelete: () => deleteExpense(expense),
            deleteQuestion: t('js.app.deleteExpenseQuestion', formatMoney(expense.amount), categoryName(expense.category).toLowerCase())
        }) : row);
    });
}

function editExpense(expense) {
    editingExpenseId = expense.id;
    elements.expenseFormTitle.textContent = t('js.app.editExpense');
    elements.saveExpenseButton.textContent = t('js.app.saveExpense');
    elements.cancelExpenseEdit.classList.remove('is-hidden');
    elements.expenseDate.value = expense.date;
    elements.expenseCategory.value = expense.category;
    elements.expenseAmount.value = expense.amount;
    dropKeptExpenseLink();
    // A block History knows reads like the other options; a deleted one is named by its station.
    const known = expense.shiftId == null ? undefined : currentShifts.find(shift => shift.id === expense.shiftId);
    editingExpenseLink = expense.shiftId == null ? undefined : {
        id: expense.shiftId,
        label: known ? `${formatDate(known.date)} · ${known.station}` : expenseStationLabel(expense) || t('js.app.linkedShift'),
        status: known?.status ?? ''
    };
    keepEditingExpenseLink();
    elements.expenseShift.value = expense.shiftId ?? '';
    elements.expenseNote.value = expense.note ?? '';
    renderExpenseCategoryHint();
    elements.expenseForm.scrollIntoView({behavior: 'smooth', block: 'center'});
    applyOfflineExpenseState();
}

/** Saving an edited expense needs a connection, because only adding one can be queued. */
function applyOfflineExpenseState() {
    const blocked = editingExpenseId !== undefined && (window.flexbuddyPwa?.isOffline() ?? false);
    elements.saveExpenseButton.disabled = blocked;
    elements.saveExpenseButton.title = blocked ? t('js.pwa.availableWhenOnline') : '';
}

/**
 * The Expenses form ready for a toll or parking on this block: its day, Toll, and the block already linked, kept as an
 * option even when History's current results don't hold it.
 */
function startExpenseForBlock(shift) {
    showExpensesScreen();
    resetExpenseForm();
    elements.expenseDate.value = shift.date;
    elements.expenseCategory.value = 'TOLL';
    editingExpenseLink = {id: shift.id, label: `${formatDate(shift.date)} · ${shift.station}`, status: shift.status ?? ''};
    populateExpenseShifts(currentShifts);
    elements.expenseShift.value = String(shift.id);
    renderExpenseCategoryHint();
    elements.expenseForm.scrollIntoView({behavior: 'smooth', block: 'center'});
    elements.expenseAmount.focus({preventScroll: true});
}

function resetExpenseForm() {
    editingExpenseId = undefined;
    editingExpenseLink = undefined;
    dropKeptExpenseLink();
    elements.expenseForm.reset();
    elements.expenseDate.value = toIsoDate(new Date());
    elements.expenseFormTitle.textContent = t('js.app.logExpense');
    elements.saveExpenseButton.textContent = t('js.app.addExpense');
    elements.cancelExpenseEdit.classList.add('is-hidden');
    hideMessage(elements.expenseError);
    renderExpenseCategoryHint();
    applyOfflineExpenseState();
}

/** What the chosen category does to estimated net, or nothing while the cost method isn't known. */
function expenseEffectText(category) {
    const linkedToScheduled = elements.expenseShift.selectedOptions[0]?.dataset.status === 'SCHEDULED';
    const key = window.flexbuddyExpenseEffect?.effectKey(category, expenseCostMethod, linkedToScheduled);
    return key ? t(key) : '';
}

/** Rewrites the hint only when it changes, so a screen reader doesn't announce the same words again. */
function renderExpenseCategoryHint() {
    const text = expenseEffectText(elements.expenseCategory.value);
    if (elements.expenseCategoryHint.textContent !== text) elements.expenseCategoryHint.textContent = text;
}

async function saveExpense(event) {
    event.preventDefault();
    hideMessage(elements.expenseError);
    if (!elements.expenseForm.reportValidity()) return;
    const body = {date: elements.expenseDate.value, category: elements.expenseCategory.value,
        amount: Number(elements.expenseAmount.value), note: elements.expenseNote.value.trim() || null,
        shiftId: elements.expenseShift.value ? Number(elements.expenseShift.value) : null};
    const id = editingExpenseId;
    const effect = expenseEffectText(body.category) || t('js.expenseEffect.saved');
    elements.saveExpenseButton.disabled = true;
    try {
        let response;
        if (id) {
            response = await apiFetch(`/expenses/${id}`, {method: 'PUT',
                headers: csrfHeaders({'Content-Type': 'application/json'}), body: JSON.stringify(body)});
        } else {
            const label = elements.expenseCategory.selectedOptions[0].textContent;
            const result = await window.flexbuddyOutbox.submit('expense-create', {
                method: 'POST', url: '/expenses', body,
                summary: {title: `${label} · ${formatMoney(body.amount)}`, detail: formatShortDay(body.date)}
            });
            if (result.queued) {
                resetExpenseForm();
                showToast(t('js.finish.savedOfflineTitle'), t('js.app.savedOfflineExpense', label, formatMoney(body.amount)));
                return;
            }
            response = result.sent;
        }
        if (!response.ok && !(await isDuplicate(response))) throw new Error(await response.text());
        resetExpenseForm();
        showToast(id ? t('js.app.expenseUpdated') : t('js.app.expenseAdded'), effect);
        await Promise.all([loadExpenses(), loadDashboard()]);
    } catch (error) { showMessage(elements.expenseError, error.message || t('js.app.expenseNotSaved')); }
    finally { elements.saveExpenseButton.disabled = false; applyOfflineExpenseState(); }
}

async function deleteExpense(expense) {
    const response = await apiFetch(`/expenses/${expense.id}`, {method: 'DELETE', headers: csrfHeaders()});
    if (!response.ok) return showToast(t('js.app.deleteFailedTitle'), await response.text(), {alert: true});
    const batch = response.headers.get('X-Delete-Batch');
    showToast(t('js.app.expenseDeleted'), t('js.app.availableInTrash'), {actionLabel: t('js.common.undo'), duration: 8000,
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
    if (!expenses.length) return elements.expenseTrashList.innerHTML = `<p>${escapeHtml(t('js.app.noExpenseTrash'))}</p>`;
    expenses.forEach(expense => {
        const row = document.createElement('article'); row.className = 'trash-row';
        // The note and station tell similar expenses apart; the note is text, cut to one line with its full text on hover.
        row.innerHTML = `<div><strong>${escapeHtml(categoryName(expense.category).toUpperCase())} · ${formatMoney(expense.amount)}</strong><span>${formatDate(expense.date)}${expense.station ? ` · ${escapeHtml(expenseStationLabel(expense))}` : ''}</span>${expense.note ? `<small class="trash-note">${escapeHtml(expense.note)}</small>` : ''}</div><div><button class="text-button" type="button">${escapeHtml(t('js.common.restore'))}</button><button class="danger-text-button" type="button">${escapeHtml(t('js.common.deletePermanently'))}</button></div>`;
        const note = row.querySelector('.trash-note');
        if (note) note.title = expense.note;
        const [restore, remove] = row.querySelectorAll('button');
        restore.addEventListener('click', async () => { await apiFetch(`/expenses/${expense.id}/restore`, {method:'POST',headers:csrfHeaders()}); await loadExpenseTrash(); await loadExpenses(); await loadDashboard(); });
        remove.addEventListener('click', () => openConfirm(t('js.app.deleteExpensePermanentlyTitle'), t('js.app.deleteExpensePermanentlyMessage'), async () => { await apiFetch(`/expenses/trash/${expense.id}`, {method:'DELETE',headers:csrfHeaders()}); await loadExpenseTrash(); }));
        elements.expenseTrashList.append(row);
    });
}

async function emptyExpenseTrash() {
    const response = await apiFetch('/expenses/trash', {method: 'DELETE', headers: csrfHeaders()});
    if (!response.ok) throw new Error(await response.text() || t('js.app.expenseTrashNotEmptied'));
    await loadExpenseTrash();
    showToast(t('js.app.expenseTrashEmptiedTitle'), t('js.app.expenseTrashEmptiedMessage'));
}

async function loadLinkedExpenses(shiftId) {
    elements.linkedExpensesList.innerHTML = `<small>${escapeHtml(t('js.common.loading'))}</small>`;
    try {
        const response = await apiFetch(`/shifts/${shiftId}/expenses`);
        if (!response.ok) throw new Error();
        const expenses = await response.json();
        elements.linkedExpensesList.innerHTML = expenses.length
            ? expenses.map(expense => `<small>${escapeHtml(categoryName(expense.category).toUpperCase())} · ${formatMoney(expense.amount)} · ${formatDate(expense.date)}</small>`).join('')
            : `<small>${escapeHtml(t('js.app.noLinkedExpenses'))}</small>`;
    } catch { elements.linkedExpensesList.innerHTML = `<small>${escapeHtml(t('js.app.linkedExpensesNotLoaded'))}</small>`; }
}

function openInitialScreen() {
    const params = new URLSearchParams(window.location.search);
    const finish = params.get('finish');
    if (finish) {
        showDashboard(false);
        window.flexbuddyFinish?.openById(finish);
        return;
    }
    const screen = params.get('screen');
    // Filter settings in the address belong to Reports, so an older bookmark such as /?preset=month opens it.
    const hasReportParams = ['preset', 'from', 'to', 'station', 'groupBy'].some(key => params.has(key));
    // ?screen=home is the dashboard under its new name, and the same as no screen at all.
    if (screen === 'reports' || (!screen && hasReportParams)) showReportsScreen(false);
    else if (screen === 'schedule') showScheduleScreen(false);
    else if (screen === 'import') showImportScreen(false);
    else if (screen === 'expenses') showExpensesScreen(false);
    else if (screen === 'evaluate') {
        showDashboard(false);
        window.flexbuddyEvaluate?.open();
    }
    else showDashboard(false);
}

const SHARED_UNAVAILABLE = t('js.app.sharedUnavailable');

/** Right after the emailed code is accepted the address carries ?welcome; say so once and tidy the address. */
function noteRecoveryCodeUse() {
    const params = new URLSearchParams(window.location.search);
    if (!params.has('recovery-used')) return;
    params.delete('recovery-used');
    const query = params.toString();
    window.history.replaceState({}, '', `${window.location.pathname}${query ? `?${query}` : ''}`);
    const left = Number(document.body.dataset.recoveryLeft);
    showToast(t('js.app.recoveryUsed'), Number.isFinite(left) ? t('js.app.recoveryUsedLeft', left) : t('js.app.recoveryUsedMakeNew'));
}

function greetNewDriver() {
    const params = new URLSearchParams(window.location.search);
    if (!params.has('welcome')) return;
    params.delete('welcome');
    const query = params.toString();
    window.history.replaceState({}, '', `${window.location.pathname}${query ? `?${query}` : ''}`);
    showToast(t('js.app.emailConfirmed'), t('js.app.welcome'));
}

/**
 * Opens the screenshots shared from another app, which the service worker left in the share inbox (share-inbox.js):
 * one is reviewed as usual, two to ten start a batch. Without a share in the address, a share made while signed out
 * and still waiting opens now, after the sign-in. Nothing is saved until the driver presses a save button.
 */
async function importSharedScreenshot() {
    const params = new URLSearchParams(window.location.search);
    const id = params.get('shared');
    const inbox = window.flexbuddyShareInbox;
    if (!id) {
        const waiting = await inbox.takeNewest();
        if (!waiting.length) return;
        showImportScreen(false);
        pickScreenshots(waiting);
        showToast(t('js.app.sharedReadyTitle'), t('js.app.sharedReadyMessage'));
        return;
    }
    // Removed first so a reload or the back button never imports the same share twice.
    params.delete('shared');
    const query = params.toString();
    window.history.replaceState({}, '', `${window.location.pathname}${query ? `?${query}` : ''}`);

    if (id === 'too-many') {
        showMessage(elements.uploadError, t('js.app.sharedTooMany'));
        return;
    }
    const files = await inbox.take(id);
    if (files.length) pickScreenshots(files);
    else showMessage(elements.uploadError, SHARED_UNAVAILABLE);
}

/** Saves the browser's time zone when it differs, so "today", Upcoming, and reminders match the driver's clock. */
async function reportTimeZone() {
    const zone = Intl.DateTimeFormat().resolvedOptions().timeZone;
    if (!zone || !navigator.onLine) return;
    try {
        if (sessionStorage.getItem('flexbuddy-zone-reported') === zone) return;
    } catch {
        // Storage can be unavailable; reporting again is harmless.
    }
    try {
        const response = await apiFetch('/account/settings');
        if (!response.ok) return;
        const settings = await response.json();
        if (settings.timeZone !== zone) {
            const saved = await apiFetch('/account/time-zone', {
                method: 'PUT',
                headers: csrfHeaders({'Content-Type': 'application/json'}),
                body: JSON.stringify({timeZone: zone})
            });
            if (!saved.ok) return;
            showToast(t('js.app.timeZoneUpdated'), t('js.app.timeZoneSet', zone));
            await loadDashboard();
        }
        try {
            sessionStorage.setItem('flexbuddy-zone-reported', zone);
        } catch {
            // Ignore unavailable storage.
        }
    } catch {
        // Offline or signed out; try again on the next load.
    }
}

function setupInstallBanner() {
    let visits = 0;
    let dismissed = false;
    try {
        visits = Number(localStorage.getItem('flexbuddy-visits') || 0) + 1;
        localStorage.setItem('flexbuddy-visits', String(visits));
        dismissed = localStorage.getItem('flexbuddy-install-dismissed') === 'true';
    } catch {
        return;
    }
    elements.installBannerButton.addEventListener('click', () => window.flexbuddyPwa.promptInstall());
    elements.dismissInstallBanner.addEventListener('click', () => {
        dismissed = true;
        elements.installBanner.dataset.available = 'false';
        document.dispatchEvent(new CustomEvent('flexbuddy:attention'));
        try {
            localStorage.setItem('flexbuddy-install-dismissed', 'true');
        } catch {
            // Ignore unavailable storage.
        }
    });
    window.flexbuddyPwa?.onInstallChange(state => {
        const available = visits >= 3 && !dismissed && (state === 'prompt' || state === 'ios');
        // Home's Needs attention card lists the prompt and opens this banner; it is only ever offered in a browser.
        elements.installBanner.dataset.available = String(available);
        document.dispatchEvent(new CustomEvent('flexbuddy:attention'));
        elements.installBannerButton.classList.toggle('is-hidden', state !== 'prompt');
        elements.installBannerText.textContent = state === 'ios'
            ? t('js.app.installIos')
            : t('js.app.installOpenFromHome');
    });
}

document.addEventListener('flexbuddy:cache-ready', () => {
    loadStations();
    loadDashboard();
});
updateThemeToggle(document.documentElement.dataset.theme);
// Sharing to FlexBuddy from Photos needs the Android app or the installed web app, so only they mention it.
elements.shareTip.classList.toggle('is-hidden', !/Android/i.test(navigator.userAgent)
    || !window.matchMedia('(display-mode: standalone)').matches);
initializeFilters();
openInitialScreen();
greetNewDriver();
noteRecoveryCodeUse();
importSharedScreenshot();
urlSyncEnabled = true;
loadStations();
loadDashboard();
reportTimeZone();
setupInstallBanner();
