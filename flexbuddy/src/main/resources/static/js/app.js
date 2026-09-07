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
    historyList: document.querySelector('#historyList'),
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
        await loadDashboard();
    } catch (error) {
        showMessage(elements.saveError, error.message || 'The shift could not be saved.');
    } finally {
        setSaving(false);
    }
}

async function loadDashboard() {
    await Promise.all([loadStatistics(), loadShifts()]);
}

async function loadStatistics() {
    try {
        const response = await fetch('/shifts/statistics');
        if (!response.ok) throw new Error();
        const statistics = await response.json();

        elements.totalEarnings.textContent = formatMoney(statistics.totalEarnings);
        elements.totalShifts.textContent = statistics.totalShifts ?? 0;
        elements.totalTime.textContent = formatMinutes(statistics.totalTimeWorked);
        elements.averagePay.textContent = formatMoney(statistics.averagePayPerShift);
    } catch {
        elements.totalEarnings.textContent = '—';
        elements.totalShifts.textContent = '—';
        elements.totalTime.textContent = '—';
        elements.averagePay.textContent = '—';
    }
}

async function loadShifts() {
    try {
        const response = await fetch('/shifts');
        if (!response.ok) throw new Error();
        const shifts = await response.json();
        renderShifts(shifts);
    } catch {
        elements.historyList.innerHTML = '<div class="history-empty">Shifts could not be loaded.</div>';
    }
}

function renderShifts(shifts) {
    elements.historyList.replaceChildren();

    if (!shifts.length) {
        elements.historyList.innerHTML = '<div class="history-empty">No shifts yet. Import your first screenshot above.</div>';
        return;
    }

    const sortedShifts = [...shifts]
        .sort((a, b) => `${b.date}T${b.startTime}`.localeCompare(`${a.date}T${a.startTime}`))
        .slice(0, 8);

    for (const shift of sortedShifts) {
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
            <div class="shift-pay"><strong>${formatMoney(total)}</strong><span>${formatMoney(shift.basePay)} base · ${formatMoney(shift.tips)} tips</span></div>
            <button class="edit-shift-button" type="button">
                <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m4 20 4.2-1 10.9-10.9a2.1 2.1 0 0 0-3-3L5.2 16 4 20Zm10.5-13.5 3 3"/></svg>
            </button>
        `;

        const editButton = row.querySelector('.edit-shift-button');
        editButton.setAttribute('aria-label', `Edit ${shift.station} shift on ${shift.date}`);
        editButton.addEventListener('click', () => openEditModal(shift, editButton));
        elements.historyList.append(row);
    }
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

function csrfHeaders(headers = {}) {
    if (csrfToken && csrfHeader) {
        headers[csrfHeader] = csrfToken;
    }
    return headers;
}

updateThemeToggle(document.documentElement.dataset.theme);
loadDashboard();
