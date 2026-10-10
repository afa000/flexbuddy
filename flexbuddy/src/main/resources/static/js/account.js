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
            throw new Error(t('js.common.offlineTryAgain'));
        }
        throw error;
    }
    window.flexbuddyPwa?.noteResponse(response);
    const responsePath = new URL(response.url, window.location.origin).pathname;
    if (response.status === 401 || response.status === 403 || (response.redirected && responsePath === '/login')) {
        window.location.assign('/login?expired');
        throw new Error(t('js.common.sessionExpired'));
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
    document.querySelector('#backupStatus').textContent = t('js.account.lastBackup', new Date(lastBackup).toLocaleString(appLocale()));
}

backupPicker.addEventListener('click', () => backupInput.click());
backupInput.addEventListener('change', async () => {
    const file = backupInput.files[0];
    if (!file) return;
    hideError();
    backupPicker.querySelector('strong').textContent = t('js.account.readingBackup');
    const form = new FormData();
    form.append('backup', file);
    try {
        const response = await apiFetch('/account/restore/preview', {method: 'POST', headers: csrfHeaders(), body: form});
        if (!response.ok) throw new Error(await response.text());
        renderPreview(await response.json(), file.name);
    } catch (error) {
        showError(error.message || t('js.account.backupUnreadable'));
        restorePreview.classList.add('is-hidden');
    } finally {
        backupPicker.querySelector('strong').textContent = t('js.account.chooseAnotherBackup');
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
        showError(t('js.account.confirmReplace'));
        replaceAck.focus();
        return;
    }
    restoreButton.disabled = true;
    restoreButton.textContent = t('js.account.restoring');
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
        showToast(t('js.account.restoreComplete'), t('js.account.restoreCompleteMessage', result.inserted, result.expensesInserted || 0, result.skipped + (result.expensesSkipped || 0)),
            result.batchId ? {duration: 10000, onAction: () => undoRestore(result.batchId)} : {});
        restorePreview.classList.add('is-hidden');
    } catch (error) {
        showError(error.message || t('js.account.backupNotRestored'));
    } finally {
        restoreButton.disabled = false;
        restoreButton.textContent = t('js.account.restoreAccountData');
    }
});

function renderPreview(preview, filename) {
    restoreToken = preview.token;
    document.querySelector('#previewTotal').textContent = preview.total;
    document.querySelector('#previewNew').textContent = preview.newShifts;
    document.querySelector('#previewNewNote').textContent = preview.newDeletedShifts
        ? t('js.account.newIfDeletedIncluded', preview.newDeletedShifts)
        : '';
    document.querySelector('#previewExisting').textContent = preview.alreadyPresent;
    document.querySelector('#previewTrashed').textContent = preview.inRecentlyDeleted;
    document.querySelector('#previewDuplicates').textContent = preview.duplicateInBackup;
    document.querySelector('#previewInvalid').textContent = preview.invalid;
    document.querySelector('#previewExpenses').textContent = preview.totalExpenses || 0;
    document.querySelector('#previewExpenseNote').textContent = preview.duplicateExpenses ? t('js.account.duplicateExpenses', preview.duplicateExpenses) : t('js.account.newExpenses', preview.newExpenses || 0);
    document.querySelector('#previewSource').textContent = t('js.account.previewSource', filename,
        preview.sameAccount ? t('js.account.sameAccount') : t('js.account.fromAccount', preview.sourceEmail || t('js.account.anotherAccount')),
        preview.deletedInBackup);
    const problems = document.querySelector('#restoreProblems');
    problems.replaceChildren();
    preview.problems.forEach(problem => {
        const item = document.createElement('li');
        item.textContent = t('js.account.problemEntry', problem.index + 1, problem.field, problem.message);
        problems.append(item);
    });
    restorePreview.classList.remove('is-hidden');
}

function selectedMode() { return document.querySelector('input[name="restoreMode"]:checked').value; }
function csrfHeaders(extra = {}) { return csrfToken && csrfHeader ? {...extra, [csrfHeader]: csrfToken} : extra; }
function showError(message) { restoreError.textContent = message; restoreError.classList.remove('is-hidden'); }
function hideError() { restoreError.textContent = ''; restoreError.classList.add('is-hidden'); }
function updateThemeLabel(theme) {
    const label = theme === 'light' ? t('js.common.switchToDarkMode') : t('js.common.switchToLightMode');
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
    showToast(t('js.account.restoreUndone'), t('js.account.restoreUndoneMessage', result.restored, result.expensesRestored || 0));
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
        showToast(t('js.account.settingsSaved'), t('js.account.netRecalculated'));
    } catch (exception) {
        error.textContent = exception.message || t('js.account.settingsNotSaved');
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
        showToast(t('js.account.defaultRateRestored'), t('js.account.defaultRateMessage'));
    } catch (exception) {
        error.textContent = exception.message || t('js.account.defaultRateFailed');
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
    document.querySelector('#mileageRateHelp').textContent = t('js.account.mileageRateHelp', Number(settings.defaultMileageRate).toFixed(3), settings.mileageRateYear);
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
        error.textContent = t('js.account.choosePayoutDay');
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
        if (!response.ok) throw await responseError(response, t('js.account.payoutInvalid'));
        renderSettings(await response.json());
        showToast(t('js.account.payoutSaved'), t('js.account.payoutSavedMessage'));
    } catch (exception) {
        error.textContent = exception.message || t('js.account.payoutNotSaved');
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
        if (!response.ok) throw await responseError(response, t('js.account.goalsInvalid'));
        renderSettings(await response.json());
        showToast(t('js.account.goalsSaved'), t('js.account.goalsSavedMessage'));
    } catch (exception) {
        error.textContent = exception.message || t('js.account.goalsNotSaved');
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
    if (waiting > 0 && !window.confirm(tn(waiting, 'js.account.signOutUnsynced'))) {
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
    document.querySelector('#askMissingMiles').checked = settings.askMissingMiles !== false;
    document.querySelector('#remindMiles').checked = Boolean(settings.remindMiles);
    document.querySelector('#remindTax').checked = Boolean(settings.remindTax);
    document.querySelector('#forfeitCutoff').value = settings.forfeitCutoffMinutes ?? 45;
    const hasFeed = Boolean(settings.calendarFeedPath);
    calendarFeedUrl.value = hasFeed ? new URL(settings.calendarFeedPath, window.location.origin).href : '';
    document.querySelector('#copyFeedButton').disabled = !hasFeed;
    document.querySelector('#regenerateFeedButton').textContent = hasFeed ? t('js.account.regenerateLink') : t('js.account.createCalendarLink');
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
                askMissingMiles: document.querySelector('#askMissingMiles').checked,
                remindMiles: document.querySelector('#remindMiles').checked,
                forfeitCutoffMinutes: Number(document.querySelector('#forfeitCutoff').value)
            })
        });
        if (!response.ok) throw await responseError(response, t('js.account.remindersInvalid'));
        renderSettings(await response.json());
        showToast(t('js.account.remindersSaved'), lead === ''
            ? t('js.account.remindersSavedPushOff')
            : t('js.account.remindersSavedLeadTime'));
    } catch (exception) {
        error.textContent = exception.message || t('js.account.remindersNotSaved');
        error.classList.remove('is-hidden');
    } finally {
        button.disabled = window.flexbuddyPwa?.isOffline() ?? false;
    }
});

document.querySelector('#copyFeedButton').addEventListener('click', async () => {
    if (!calendarFeedUrl.value) return;
    try {
        await navigator.clipboard.writeText(calendarFeedUrl.value);
        showToast(t('js.account.linkCopied'), t('js.account.linkCopiedMessage'));
    } catch {
        calendarFeedUrl.select();
        showToast(t('js.account.copySelected'), t('js.account.copyBlocked'));
    }
});

document.querySelector('#regenerateFeedButton').addEventListener('click', async event => {
    const button = event.currentTarget;
    const replacing = Boolean(calendarFeedUrl.value);
    if (replacing && !window.confirm(t('js.account.confirmRegenerate'))) return;
    button.disabled = true;
    try {
        const response = await apiFetch('/account/calendar-token', {method: 'POST', headers: csrfHeaders()});
        if (!response.ok) throw await responseError(response, t('js.account.linkNotCreated'));
        renderSettings(await response.json());
        showToast(replacing ? t('js.account.linkRegenerated') : t('js.account.linkCreated'),
            replacing ? t('js.account.oldLinkDead') : t('js.account.copyIntoCalendar'));
    } catch (exception) {
        showToast(t('js.account.linkFailed'), exception.message || t('js.account.linkNotCreated'), {alert: true});
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
            ? t('js.account.pushInstallFirst')
            : t('js.account.pushUnsupported');
        return;
    }
    try {
        const response = await apiFetch('/push/public-key');
        if (response.ok) pushConfig = await response.json();
    } catch {
        pushConfig = {configured: false};
    }
    if (!pushConfig.configured) {
        pushStatus.textContent = t('js.account.pushNotConfigured');
        return;
    }
    await renderPushState();
}

async function renderPushState() {
    const registration = await navigator.serviceWorker.ready;
    const subscription = await registration.pushManager.getSubscription();
    pushToggleButton.disabled = window.flexbuddyPwa?.isOffline() ?? false;
    pushToggleButton.textContent = subscription ? t('js.account.pushTurnOff') : t('js.account.pushTurnOn');
    pushStatus.textContent = subscription
        ? t('js.account.pushIsOn')
        : Notification.permission === 'denied'
            ? t('js.account.pushBlocked')
            : t('js.account.pushIsOff');
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
            showToast(t('js.account.pushOffTitle'), t('js.account.pushOffMessage'));
        } else {
            if (await Notification.requestPermission() !== 'granted') {
                throw new Error(t('js.account.pushAllow'));
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
                throw await responseError(response, t('js.account.pushNotTurnedOn'));
            }
            showToast(t('js.account.pushOnTitle'), document.querySelector('#remindBefore').value
                ? t('js.account.pushOnMessage')
                : t('js.account.pushOnChooseTime'));
        }
    } catch (exception) {
        showToast(t('js.account.pushTitle'), exception.message || t('js.account.pushNotChanged'), {alert: true});
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
        ? t('js.account.installIos')
        : t('js.account.installHelp');
});

initPush();
