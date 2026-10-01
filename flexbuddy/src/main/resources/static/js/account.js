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
    let response;
    try {
        response = await window.fetch(url, options);
    } catch (error) {
        if (!navigator.onLine) {
            window.flexbuddyPwa?.noteNetworkFailure();
            throw new Error('You are offline. Try again when your connection returns.');
        }
        throw error;
    }
    window.flexbuddyPwa?.noteResponse(response);
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
    window.flexbuddyPwa?.applyThemeColor(theme);
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

let latestSettings = null;

function renderSettings(settings) {
    latestSettings = settings;
    document.querySelector('#vehicleCostMethod').value = settings.vehicleCostMethod;
    document.querySelector('#accountMileageRate').value = settings.mileageRate;
    document.querySelector('#mileageRateHelp').textContent = `App default: $${Number(settings.defaultMileageRate).toFixed(3)} per mile (${settings.mileageRateYear}). This is an estimate, not tax advice.`;
    renderReminders(settings);
    renderGoals(settings);
    renderPayouts(settings);
    updateSectionSummaries();
}

/** Writes each folded section's one-line summary, so a save shows in its section's row straight away. */
function updateSectionSummaries() {
    const heading = document.querySelector('.account-heading');
    const lines = window.flexbuddyAccountSections.summaries(latestSettings, {
        lastBackupAt: document.querySelector('#backupStatus').dataset.lastBackup,
        displayName: heading.dataset.name, email: heading.dataset.email, remindTax: latestSettings?.remindTax
    });
    document.querySelectorAll('[data-summary]').forEach(span => { span.textContent = lines[span.dataset.summary] ?? ''; });
}

const sectionList = [...document.querySelectorAll('.settings-section')];
const phoneWidth = window.matchMedia('(max-width: 620px)');
let applyingHash = false;

/** Opens the section a link names, such as /account#goals, and brings the named card into view. */
function openSectionFromHash() {
    const id = window.flexbuddyAccountSections.sectionForHash(window.location.hash);
    const section = id && document.getElementById(id);
    if (!section) return;
    applyingHash = true;
    // The toggle events from this change arrive later, so they are ignored for a moment.
    setTimeout(() => { applyingHash = false; }, 100);
    if (phoneWidth.matches) sectionList.forEach(other => { if (other !== section) other.open = false; });
    section.open = true;
    const target = document.getElementById(window.location.hash.slice(1)) ?? section;
    target.scrollIntoView({block: 'start'});
}

// On a phone one section is open at a time, so the page stays short; on a wider screen several can be.
sectionList.forEach(section => section.addEventListener('toggle', () => {
    if (!section.open || applyingHash || !phoneWidth.matches) return;
    sectionList.forEach(other => { if (other !== section) other.open = false; });
}));
window.addEventListener('hashchange', openSectionFromHash);

const payoutForm = document.querySelector('#payoutSettingsForm');

function renderPayouts(settings) {
    const days = new Set(settings.payoutDays || ['TUESDAY', 'FRIDAY']);
    payoutForm.querySelectorAll('.payout-days input').forEach(box => box.checked = days.has(box.value));
    document.querySelector('#payoutLagDays').value = settings.payoutLagDays ?? 1;
}

payoutForm.addEventListener('submit', async event => {
    event.preventDefault();
    const error = document.querySelector('#payoutError');
    error.classList.add('is-hidden');
    const days = [...payoutForm.querySelectorAll('.payout-days input:checked')].map(box => box.value);
    if (!days.length) {
        error.textContent = 'Choose at least one payout day.';
        error.classList.remove('is-hidden');
        return;
    }
    const button = document.querySelector('#savePayoutsButton');
    button.disabled = true;
    try {
        const response = await apiFetch('/account/payouts', {
            method: 'PUT',
            headers: csrfHeaders({'Content-Type': 'application/json'}),
            body: JSON.stringify({payoutDays: days, payoutLagDays: Number(document.querySelector('#payoutLagDays').value)})
        });
        if (!response.ok) throw await responseError(response, 'Choose at least one payout day and a lag from 0 to 14 days.');
        renderSettings(await response.json());
        showToast('Payout schedule saved', 'Pay periods and the Next payout tile use the new schedule.');
    } catch (exception) {
        error.textContent = exception.message || 'The payout schedule could not be saved.';
        error.classList.remove('is-hidden');
    } finally {
        button.disabled = window.flexbuddyPwa?.isOffline() ?? false;
    }
});

const goalForm = document.querySelector('#goalSettingsForm');

function renderGoals(settings) {
    document.querySelector('#weeklyGoal').value = settings.weeklyGoal ?? '';
    document.querySelector('#monthlyGoal').value = settings.monthlyGoal ?? '';
    const basis = goalForm.querySelector(`input[name="goalBasis"][value="${settings.goalBasis || 'GROSS'}"]`);
    if (basis) basis.checked = true;
}

goalForm.addEventListener('submit', async event => {
    event.preventDefault();
    const error = document.querySelector('#goalError');
    error.classList.add('is-hidden');
    const button = document.querySelector('#saveGoalsButton');
    const amount = id => {
        const value = document.querySelector(id).value;
        return value === '' ? null : Number(value);
    };
    button.disabled = true;
    try {
        const response = await apiFetch('/account/goals', {
            method: 'PUT',
            headers: csrfHeaders({'Content-Type': 'application/json'}),
            body: JSON.stringify({
                weeklyGoal: amount('#weeklyGoal'),
                monthlyGoal: amount('#monthlyGoal'),
                goalBasis: goalForm.querySelector('input[name="goalBasis"]:checked').value
            })
        });
        if (!response.ok) throw await responseError(response, 'Goals must be more than zero, or left empty to turn them off.');
        renderSettings(await response.json());
        showToast('Goals saved', 'Your dashboard shows the new targets.');
    } catch (exception) {
        error.textContent = exception.message || 'Goals could not be saved.';
        error.classList.remove('is-hidden');
    } finally {
        button.disabled = window.flexbuddyPwa?.isOffline() ?? false;
    }
});

updateSectionSummaries();
openSectionFromHash();
loadSettings();


const reminderForm = document.querySelector('#reminderSettingsForm');
const timeZoneSelect = document.querySelector('#timeZoneSelect');
const calendarFeedUrl = document.querySelector('#calendarFeedUrl');
const pushToggleButton = document.querySelector('#pushToggleButton');
const pushStatus = document.querySelector('#pushStatus');
let pushConfig = {configured: false};

/**
 * Signing out, on this device or everywhere, ends this device's session, so unsent changes are confirmed first. The
 * unsent changes and the cached figures are then cleared, so the next person to sign in on this phone sees neither.
 */
async function guardedSignOut(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const waiting = window.flexbuddyOutbox?.count() ?? 0;
    if (waiting > 0 && !window.confirm(`${waiting} ${waiting === 1 ? "change hasn't" : "changes haven't"} synced. Signing out discards ${waiting === 1 ? 'it' : 'them'}. Sign out anyway?`)) {
        return;
    }
    try {
        await window.flexbuddyOutbox?.clear();
        await window.flexbuddyPwa?.clearUserData();
    } finally {
        form.submit();
    }
}
['#accountSignOutForm', '#signOutEverywhereForm'].forEach(selector => {
    document.querySelector(selector)?.addEventListener('submit', guardedSignOut);
});

function renderReminders(settings) {
    fillTimeZones(settings.timeZone);
    document.querySelector('#remindBefore').value = settings.remindBeforeMinutes ?? '';
    document.querySelector('#remindConfirm').checked = Boolean(settings.remindConfirm);
    document.querySelector('#remindMiles').checked = Boolean(settings.remindMiles);
    document.querySelector('#remindTax').checked = Boolean(settings.remindTax);
    document.querySelector('#forfeitCutoff').value = settings.forfeitCutoffMinutes ?? 45;
    const hasFeed = Boolean(settings.calendarFeedPath);
    calendarFeedUrl.value = hasFeed ? new URL(settings.calendarFeedPath, window.location.origin).href : '';
    document.querySelector('#copyFeedButton').disabled = !hasFeed;
    document.querySelector('#regenerateFeedButton').textContent = hasFeed ? 'Regenerate link' : 'Create calendar link';
}

function fillTimeZones(selected) {
    const browserZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
    const supported = typeof Intl.supportedValuesOf === 'function' ? Intl.supportedValuesOf('timeZone') : [];
    const zones = [...new Set([selected, browserZone, ...supported].filter(Boolean))].sort();
    if (timeZoneSelect.options.length !== zones.length) {
        timeZoneSelect.replaceChildren(...zones.map(zone => new Option(zone.replaceAll('_', ' '), zone)));
    }
    timeZoneSelect.value = selected || browserZone;
}

async function responseError(response, fallback) {
    const text = await response.text();
    return new Error(text && !text.trim().startsWith('{') ? text : fallback);
}

reminderForm.addEventListener('submit', async event => {
    event.preventDefault();
    const error = document.querySelector('#reminderError');
    error.classList.add('is-hidden');
    const button = document.querySelector('#saveRemindersButton');
    const lead = document.querySelector('#remindBefore').value;
    button.disabled = true;
    try {
        const response = await apiFetch('/account/reminders', {
            method: 'PUT',
            headers: csrfHeaders({'Content-Type': 'application/json'}),
            body: JSON.stringify({
                timeZone: timeZoneSelect.value,
                remindBeforeMinutes: lead === '' ? null : Number(lead),
                remindConfirm: document.querySelector('#remindConfirm').checked,
                remindMiles: document.querySelector('#remindMiles').checked,
                forfeitCutoffMinutes: Number(document.querySelector('#forfeitCutoff').value)
            })
        });
        if (!response.ok) throw await responseError(response, 'Choose a valid time zone, reminder time, and forfeit cutoff (0 to 720 minutes).');
        renderSettings(await response.json());
        showToast('Reminders saved', lead === ''
            ? 'Push reminders are off. Calendar alarms use a 1 hour lead time.'
            : 'Your calendar feed and push reminders use the new lead time.');
    } catch (exception) {
        error.textContent = exception.message || 'Reminder settings could not be saved.';
        error.classList.remove('is-hidden');
    } finally {
        button.disabled = window.flexbuddyPwa?.isOffline() ?? false;
    }
});

document.querySelector('#copyFeedButton').addEventListener('click', async () => {
    if (!calendarFeedUrl.value) return;
    try {
        await navigator.clipboard.writeText(calendarFeedUrl.value);
        showToast('Link copied', 'Add it to Google Calendar or iOS Calendar as a subscribed calendar.');
    } catch {
        calendarFeedUrl.select();
        showToast('Copy the selected link', 'Your browser did not allow automatic copying.');
    }
});

document.querySelector('#regenerateFeedButton').addEventListener('click', async event => {
    const button = event.currentTarget;
    const replacing = Boolean(calendarFeedUrl.value);
    if (replacing && !window.confirm('Regenerate the calendar link? Calendars subscribed to the current link will stop updating.')) return;
    button.disabled = true;
    try {
        const response = await apiFetch('/account/calendar-token', {method: 'POST', headers: csrfHeaders()});
        if (!response.ok) throw await responseError(response, 'The calendar link could not be created.');
        renderSettings(await response.json());
        showToast(replacing ? 'Link regenerated' : 'Calendar link created',
            replacing ? 'The old link no longer works.' : 'Copy it into your calendar app.');
    } catch (exception) {
        showToast('Calendar link failed', exception.message || 'The calendar link could not be created.', {alert: true});
    } finally {
        button.disabled = window.flexbuddyPwa?.isOffline() ?? false;
    }
});

function pushSupported() {
    return window.isSecureContext && 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window;
}

async function initPush() {
    if (!pushSupported()) {
        pushStatus.textContent = window.flexbuddyPwa?.isIos() && !window.flexbuddyPwa.isStandalone()
            ? 'Install FlexBuddy to your home screen first, then turn on push reminders here.'
            : 'This browser cannot receive push reminders. The calendar link works everywhere.';
        return;
    }
    try {
        const response = await apiFetch('/push/public-key');
        if (response.ok) pushConfig = await response.json();
    } catch {
        pushConfig = {configured: false};
    }
    if (!pushConfig.configured) {
        pushStatus.textContent = 'Push reminders are not configured on this server.';
        return;
    }
    await renderPushState();
}

async function renderPushState() {
    const registration = await navigator.serviceWorker.ready;
    const subscription = await registration.pushManager.getSubscription();
    pushToggleButton.disabled = window.flexbuddyPwa?.isOffline() ?? false;
    pushToggleButton.textContent = subscription ? 'Turn off push on this device' : 'Turn on push on this device';
    pushStatus.textContent = subscription
        ? 'Push reminders are on for this device.'
        : Notification.permission === 'denied'
            ? 'Notifications are blocked for this site in your browser settings.'
            : 'Push reminders are off for this device.';
}

pushToggleButton.addEventListener('click', async () => {
    pushToggleButton.disabled = true;
    try {
        const registration = await navigator.serviceWorker.ready;
        const existing = await registration.pushManager.getSubscription();
        if (existing) {
            await apiFetch('/push/subscriptions', {
                method: 'DELETE',
                headers: csrfHeaders({'Content-Type': 'application/json'}),
                body: JSON.stringify({endpoint: existing.endpoint})
            });
            await existing.unsubscribe();
            showToast('Push reminders off', 'This device will no longer get push reminders.');
        } else {
            if (await Notification.requestPermission() !== 'granted') {
                throw new Error('Allow notifications for FlexBuddy to turn on push reminders.');
            }
            const subscription = await registration.pushManager.subscribe({
                userVisibleOnly: true,
                applicationServerKey: base64UrlToBytes(pushConfig.publicKey)
            });
            const response = await apiFetch('/push/subscriptions', {
                method: 'POST',
                headers: csrfHeaders({'Content-Type': 'application/json'}),
                body: JSON.stringify(subscription.toJSON())
            });
            if (!response.ok) {
                await subscription.unsubscribe();
                throw await responseError(response, 'Push reminders could not be turned on.');
            }
            showToast('Push reminders on', document.querySelector('#remindBefore').value
                ? 'You will get a reminder before each scheduled block.'
                : 'Choose a reminder time above and save to get reminders before each block.');
        }
    } catch (exception) {
        showToast('Push reminders', exception.message || 'Push reminders could not be changed.', {alert: true});
    } finally {
        await renderPushState();
    }
});

function base64UrlToBytes(value) {
    const base64 = (value + '='.repeat((4 - value.length % 4) % 4)).replace(/-/g, '+').replace(/_/g, '/');
    return Uint8Array.from(atob(base64), character => character.charCodeAt(0));
}

document.querySelector('#installAppButton').addEventListener('click', () => window.flexbuddyPwa?.promptInstall());
window.flexbuddyPwa?.onInstallChange(state => {
    document.querySelector('#installSection').classList.toggle('is-hidden', state === 'installed' || state === 'unavailable');
    document.querySelector('#installAppButton').classList.toggle('is-hidden', state !== 'prompt');
    document.querySelector('#installHelp').textContent = state === 'ios'
        ? 'In Safari, tap the Share button, then Add to Home Screen.'
        : 'Open FlexBuddy from your home screen, full screen, with your last synced data available offline.';
});

initPush();
