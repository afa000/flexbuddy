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
        const response = await fetch('/account/restore/preview', {method: 'POST', headers: csrfHeaders(), body: form});
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
        const response = await fetch('/account/restore', {
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
        showToast('Restore complete', `${result.inserted} shifts restored · ${result.skipped} skipped`, result.batchId);
        restorePreview.classList.add('is-hidden');
    } catch (error) {
        showError(error.message || 'The backup could not be restored.');
    } finally {
        restoreButton.disabled = false;
        restoreButton.textContent = 'Restore shifts';
    }
});

function renderPreview(preview, filename) {
    restoreToken = preview.token;
    document.querySelector('#previewTotal').textContent = preview.total;
    document.querySelector('#previewNew').textContent = preview.newShifts;
    document.querySelector('#previewExisting').textContent = preview.alreadyPresent;
    document.querySelector('#previewInvalid').textContent = preview.invalid;
    document.querySelector('#previewSource').textContent = `${filename} · ${preview.sameAccount ? 'Same account' : `From ${preview.sourceEmail || 'another account'}`} · ${preview.deletedInBackup} recently deleted`;
    const problems = document.querySelector('#restoreProblems');
    problems.replaceChildren();
    preview.problems.forEach(problem => {
        const item = document.createElement('li');
        item.textContent = `Shift ${problem.index + 1}, ${problem.field}: ${problem.message}`;
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

function showToast(title, message, batchId) {
    const toast = document.querySelector('#accountToast');
    document.querySelector('#accountToastTitle').textContent = title;
    document.querySelector('#accountToastMessage').textContent = message;
    const action = document.querySelector('#accountToastAction');
    action.classList.toggle('is-hidden', !batchId);
    if (batchId) action.onclick = () => undoRestore(batchId);
    toast.classList.remove('is-hidden');
    window.setTimeout(() => toast.classList.add('is-hidden'), batchId ? 10000 : 4500);
}

async function undoRestore(batchId) {
    const response = await fetch(`/account/restore/${encodeURIComponent(batchId)}/undo`, {method: 'POST', headers: csrfHeaders()});
    if (!response.ok) return showError(await response.text());
    const result = await response.json();
    showToast('Restore undone', `${result.restored} original shifts restored`, null);
}
