const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
const backupInput = document.querySelector('#backupInput');
const backupPicker = document.querySelector('#backupPicker');
const restorePreview = document.querySelector('#restorePreview');
const restoreError = document.querySelector('#restoreError');
const restoreButton = document.querySelector('#restoreButton');
const replaceAckRow = document.querySelector('#replaceAckRow');
const replaceAck = document.querySelector('#replaceAck');
let restoreToken;
const settingsForm = document.querySelector('#expenseSettingsForm');

window.flexbuddyToast.init({
    toast: '#accountToast',
    title: '#accountToastTitle',
    message: '#accountToastMessage',
    action: '#accountToastAction'
});

async function apiFetch(url, options) {
    const response = await window.fetch(url, options);
    const responsePath = new URL(response.url, window.location.origin).pathname;
    if (response.status === 401 || response.status === 403 || (response.redirected && responsePath === '/login')) {
        window.location.assign('/login?expired');
        throw new Error('Your session expired. Sign in again.');
    }
    return response;
}

const themeToggleButton = document.querySelector('#themeToggleButton');
themeToggleButton.addEventListener('click', () => {
    const theme = document.documentElement.dataset.theme === 'light' ? 'dark' : 'light';
    document.documentElement.dataset.theme = theme;
    localStorage.setItem('flexbuddy-theme', theme);
    updateThemeLabel(theme);
});
updateThemeLabel(document.documentElement.dataset.theme);

const lastBackup = document.querySelector('#backupStatus').dataset.lastBackup;
if (lastBackup) {
    document.querySelector('#backupStatus').textContent = `Last backup: ${new Date(lastBackup).toLocaleString()}`;
}

backupPicker.addEventListener('click', () => backupInput.click());
backupInput.addEventListener('change', async () => {
    const file = backupInput.files[0];
    if (!file) return;
    hideError();
    backupPicker.querySelector('strong').textContent = 'Reading backup…';
    const form = new FormData();
    form.append('backup', file);
    try {
        const response = await apiFetch('/account/restore/preview', {method: 'POST', headers: csrfHeaders(), body: form});
        if (!response.ok) throw new Error(await response.text());
        renderPreview(await response.json(), file.name);
    } catch (error) {
        showError(error.message || 'The backup could not be read.');
        restorePreview.classList.add('is-hidden');
    } finally {
        backupPicker.querySelector('strong').textContent = 'Choose another backup';
    }
});

document.querySelectorAll('input[name="restoreMode"]').forEach(input => input.addEventListener('change', () => {
    const replacing = selectedMode() === 'REPLACE';
    replaceAckRow.classList.toggle('is-hidden', !replacing);
    if (!replacing) replaceAck.checked = false;
}));

restoreButton.addEventListener('click', async () => {
    hideError();
    if (selectedMode() === 'REPLACE' && !replaceAck.checked) {
        showError('Confirm that your current shifts will move to Recently deleted.');
        replaceAck.focus();
        return;
    }
    restoreButton.disabled = true;
    restoreButton.textContent = 'Restoring…';
    try {
        const response = await apiFetch('/account/restore', {
            method: 'POST',
            headers: csrfHeaders({'Content-Type': 'application/json'}),
            body: JSON.stringify({
                token: restoreToken,
                mode: selectedMode(),
                includeDeleted: document.querySelector('#includeDeleted').checked,
                acknowledgeReplace: replaceAck.checked
            })
        });
        if (!response.ok) throw new Error(await response.text());
        const result = await response.json();
        showToast('Restore complete', `${result.inserted} shifts and ${result.expensesInserted || 0} expenses restored · ${result.skipped + (result.expensesSkipped || 0)} skipped`,
            result.batchId ? {duration: 10000, onAction: () => undoRestore(result.batchId)} : {});
        restorePreview.classList.add('is-hidden');
    } catch (error) {
        showError(error.message || 'The backup could not be restored.');
    } finally {
        restoreButton.disabled = false;
        restoreButton.textContent = 'Restore account data';
    }
});

function renderPreview(preview, filename) {
    restoreToken = preview.token;
    document.querySelector('#previewTotal').textContent = preview.total;
    document.querySelector('#previewNew').textContent = preview.newShifts;
    document.querySelector('#previewNewNote').textContent = preview.newDeletedShifts
        ? `+${preview.newDeletedShifts} if recently deleted are included`
        : '';
    document.querySelector('#previewExisting').textContent = preview.alreadyPresent;
    document.querySelector('#previewTrashed').textContent = preview.inRecentlyDeleted;
    document.querySelector('#previewDuplicates').textContent = preview.duplicateInBackup;
    document.querySelector('#previewInvalid').textContent = preview.invalid;
    document.querySelector('#previewExpenses').textContent = preview.totalExpenses || 0;
    document.querySelector('#previewExpenseNote').textContent = preview.duplicateExpenses ? `${preview.duplicateExpenses} duplicates` : `${preview.newExpenses || 0} new`;
    document.querySelector('#previewSource').textContent = `${filename} · ${preview.sameAccount ? 'Same account' : `From ${preview.sourceEmail || 'another account'}`} · ${preview.deletedInBackup} recently deleted`;
    const problems = document.querySelector('#restoreProblems');
    problems.replaceChildren();
    preview.problems.forEach(problem => {
        const item = document.createElement('li');
        item.textContent = `Entry ${problem.index + 1}, ${problem.field}: ${problem.message}`;
        problems.append(item);
    });
    restorePreview.classList.remove('is-hidden');
}

function selectedMode() { return document.querySelector('input[name="restoreMode"]:checked').value; }
function csrfHeaders(extra = {}) { return csrfToken && csrfHeader ? {...extra, [csrfHeader]: csrfToken} : extra; }
function showError(message) { restoreError.textContent = message; restoreError.classList.remove('is-hidden'); }
function hideError() { restoreError.textContent = ''; restoreError.classList.add('is-hidden'); }
function updateThemeLabel(theme) {
    const label = theme === 'light' ? 'Switch to dark mode' : 'Switch to light mode';
    themeToggleButton.setAttribute('aria-label', label);
    themeToggleButton.title = label;
}

function showToast(title, message, options = {}) {
    window.flexbuddyToast.show(title, message, {duration: 4500, ...options});
}

async function undoRestore(batchId) {
    const response = await apiFetch(`/account/restore/${encodeURIComponent(batchId)}/undo`, {method: 'POST', headers: csrfHeaders()});
    if (!response.ok) return showError(await response.text());
    const result = await response.json();
    showToast('Restore undone', `${result.restored} shifts and ${result.expensesRestored || 0} expenses restored`);
}

settingsForm.addEventListener('submit', async event => {
    event.preventDefault();
    const error = document.querySelector('#settingsError');
    error.classList.add('is-hidden');
    const button = document.querySelector('#saveSettingsButton');
    button.disabled = true;
    try {
        const response = await apiFetch('/account/settings', {method: 'PUT', headers: csrfHeaders({'Content-Type':'application/json'}),
            body: JSON.stringify({vehicleCostMethod: document.querySelector('#vehicleCostMethod').value,
                mileageRate: Number(document.querySelector('#accountMileageRate').value)})});
        if (!response.ok) throw new Error(await response.text());
        const settings = await response.json();
        renderSettings(settings);
        showToast('Settings saved', 'Net earnings have been recalculated.');
    } catch (exception) {
        error.textContent = exception.message || 'Settings could not be saved.';
        error.classList.remove('is-hidden');
    } finally { button.disabled = false; }
});

document.querySelector('#resetMileageRateButton').addEventListener('click', async () => {
    const error = document.querySelector('#settingsError');
    error.classList.add('is-hidden');
    try {
        const response = await apiFetch('/account/settings', {
            method: 'PUT',
            headers: csrfHeaders({'Content-Type': 'application/json'}),
            body: JSON.stringify({
                vehicleCostMethod: document.querySelector('#vehicleCostMethod').value,
                mileageRate: null
            })
        });
        if (!response.ok) throw new Error(await response.text());
        renderSettings(await response.json());
        showToast('Default rate restored', 'Net earnings now use the app default mileage rate.');
    } catch (exception) {
        error.textContent = exception.message || 'The default rate could not be restored.';
        error.classList.remove('is-hidden');
    }
});

async function loadSettings() {
    const response = await apiFetch('/account/settings');
    if (response.ok) renderSettings(await response.json());
}

function renderSettings(settings) {
    document.querySelector('#vehicleCostMethod').value = settings.vehicleCostMethod;
    document.querySelector('#accountMileageRate').value = settings.mileageRate;
    document.querySelector('#mileageRateHelp').textContent = `App default: $${Number(settings.defaultMileageRate).toFixed(3)} per mile (${settings.mileageRateYear}). This is an estimate, not tax advice.`;
}

loadSettings();
