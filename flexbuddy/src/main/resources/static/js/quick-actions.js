// Quick-action button: one round button on every screen of the main page that opens a menu of the four things a
// driver does most, which are importing a screenshot, adding a shift, adding an expense, and starting the next block.
// Loaded before app.js and uses its shared helpers (elements, showImportScreen, openFilePicker, showExpensesScreen,
// resetExpenseForm, openEditModal, toIsoDate, formatTime, editingExpenseId) and the schedule's at click time.
(() => {
    const button = document.querySelector('#quickActionButton');
    const sheet = document.querySelector('#quickActionsSheet');
    if (!button || !sheet) return;
    const rows = [...sheet.querySelectorAll('[role="menuitem"]')];
    const startRow = sheet.querySelector('#qaStartBlock');
    let startShift = null;

    /** Rows that can take focus: a disabled row is skipped by arrow keys as well as by Tab. */
    const enabledRows = () => rows.filter(row => !row.disabled);
    const isOpen = () => !sheet.classList.contains('is-hidden');

    function open(focus = 'first') {
        refreshStartRow();
        const onSchedule = !elements.scheduleScreen.classList.contains('is-hidden');
        sheet.querySelector('#qaAddShiftHint').textContent = onSchedule ? 'A scheduled block' : 'A block you worked';
        sheet.classList.remove('is-hidden');
        document.body.classList.add('modal-open');
        button.setAttribute('aria-expanded', 'true');
        const targets = enabledRows();
        (focus === 'last' ? targets.at(-1) : targets[0])?.focus();
    }

    /** Closes the menu. The modal class goes first because the hidden button cannot take focus back until it is gone. */
    function close({restoreFocus = true} = {}) {
        if (!isOpen()) return;
        sheet.classList.add('is-hidden');
        document.body.classList.remove('modal-open');
        button.setAttribute('aria-expanded', 'false');
        if (restoreFocus) button.focus();
    }

    /** Re-checks the start window on every open, so nothing has to poll. */
    function refreshStartRow() {
        startShift = window.flexbuddySchedule?.startable() ?? null;
        const offline = window.flexbuddyPwa?.isOffline() ?? false;
        sheet.querySelector('#qaStartLabel').textContent = startShift ? 'Start block' : 'No block to start';
        sheet.querySelector('#qaStartHint').textContent = startShift
            ? `${startShift.station} · ${formatTime(startShift.startTime)}–${formatTime(startShift.endTime)}`
            : 'Blocks can start 2 hours before their start time';
        startRow.disabled = !startShift || offline;
        rows.filter(row => row !== startRow).forEach(row => row.disabled = offline);
    }

    button.addEventListener('click', () => (isOpen() ? close() : open()));
    button.addEventListener('keydown', event => {
        if (event.key === 'ArrowUp' && !isOpen()) {
            event.preventDefault();
            open('last');
        }
    });
    sheet.addEventListener('click', event => {
        if (event.target === sheet) close();
    });
    sheet.addEventListener('keydown', event => {
        const targets = enabledRows();
        const index = targets.indexOf(document.activeElement);
        if (event.key === 'Escape') {
            event.stopPropagation();
            close();
        } else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
            event.preventDefault();
            if (!targets.length) return;
            const step = event.key === 'ArrowDown' ? 1 : -1;
            targets[(index + step + targets.length) % targets.length].focus();
        } else if (event.key === 'Home' || event.key === 'End') {
            event.preventDefault();
            (event.key === 'Home' ? targets[0] : targets.at(-1))?.focus();
        } else if (event.key === 'Tab') {
            // Leaving the menu by Tab closes it, as menus do; focus is not pulled back to the button.
            close({restoreFocus: false});
        }
    });

    sheet.querySelector('#qaImport').addEventListener('click', () => {
        close({restoreFocus: false});
        // The file picker only opens from a user gesture, so nothing may be awaited before it.
        showImportScreen(false);
        openFilePicker();
    });

    sheet.querySelector('#qaAddShift').addEventListener('click', () => {
        close({restoreFocus: false});
        if (!elements.scheduleScreen.classList.contains('is-hidden')) {
            window.flexbuddySchedule.add(button);
            return;
        }
        openEditModal({id: null, station: '', date: toIsoDate(new Date()), startTime: '', endTime: '', basePay: '',
            tips: 0, miles: null, status: 'COMPLETED'}, button, {status: 'COMPLETED'});
    });

    sheet.querySelector('#qaAddExpense').addEventListener('click', () => {
        close({restoreFocus: false});
        // An expense being edited is kept, not thrown away by opening the form again.
        if (editingExpenseId === undefined) resetExpenseForm();
        showExpensesScreen(false);
        elements.expenseAmount.focus();
        elements.expenseForm.scrollIntoView({block: 'center'});
    });

    startRow.addEventListener('click', () => {
        const shift = startShift;
        close({restoreFocus: false});
        if (shift) window.flexbuddySchedule.start(shift);
    });

    // On a phone the button steps aside while the driver types, so it never sits over a Save button or the field
    // itself. A mouse pointer keeps it in place, so tabbing through the filters does not make it jump.
    const touch = window.matchMedia('(pointer: coarse)');
    const typing = () => {
        const field = document.activeElement;
        return Boolean(field?.matches?.('input, select, textarea')) && !sheet.contains(field);
    };
    const keyboardOpen = () => window.visualViewport && window.innerHeight - window.visualViewport.height > 150;
    const updateSuppressed = () => document.body.classList.toggle('fab-suppressed',
        touch.matches && (typing() || Boolean(keyboardOpen())));
    document.addEventListener('focusin', updateSuppressed);
    document.addEventListener('focusout', () => requestAnimationFrame(updateSuppressed));
    window.visualViewport?.addEventListener('resize', updateSuppressed);

    window.flexbuddyQuickActions = {open, close};
})();
