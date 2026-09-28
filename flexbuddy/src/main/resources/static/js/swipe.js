// Swipe actions for list rows, for touch only: swiping left reveals Edit and Delete, a long swipe asks to delete
// straight away, and swiping right reveals Duplicate where a row offers it. Every action is also reachable without
// swiping, from the row's own buttons or the edit dialog. Tapping a row anywhere opens it for editing.
(() => {
    const START_DISTANCE = 12;
    const MAX_DRIFT = 8;
    const FULL_SWIPE = 0.6;
    const SCREEN_EDGE = 16;
    const ACTION_WIDTH = 88;
    let openWrapper = null;

    /**
     * Wraps a row so it can slide over its actions. Options: onEdit, onDelete, onDuplicate (optional), and
     * deleteQuestion, the text shown when asking to confirm a delete. Returns the wrapper to put in the list.
     */
    function wrap(row, options) {
        const wrapper = document.createElement('div');
        wrapper.className = 'swipe-row';
        const behind = document.createElement('div');
        behind.className = 'swipe-actions';
        behind.innerHTML = `
            ${options.onDuplicate ? '<button class="swipe-action swipe-duplicate" type="button" tabindex="-1">Duplicate</button>' : ''}
            <span class="swipe-spacer"></span>
            <button class="swipe-action swipe-edit" type="button" tabindex="-1">Edit</button>
            <button class="swipe-action swipe-delete" type="button" tabindex="-1">Delete</button>`;
        const confirm = document.createElement('div');
        confirm.className = 'swipe-confirm';
        confirm.hidden = true;
        confirm.innerHTML = `<span></span>
            <button class="secondary-button compact-button swipe-cancel" type="button">Cancel</button>
            <button class="danger-button swipe-confirm-delete" type="button">Delete</button>`;
        confirm.querySelector('span').textContent = options.deleteQuestion;
        wrapper.append(behind, row, confirm);
        row.classList.add('swipe-surface');

        behind.querySelector('.swipe-duplicate')?.addEventListener('click', () => {
            close(wrapper);
            options.onDuplicate();
        });
        behind.querySelector('.swipe-edit').addEventListener('click', () => {
            close(wrapper);
            options.onEdit();
        });
        behind.querySelector('.swipe-delete').addEventListener('click', () => askToDelete(wrapper));
        confirm.querySelector('.swipe-cancel').addEventListener('click', () => close(wrapper));
        confirm.querySelector('.swipe-confirm-delete').addEventListener('click', event => {
            event.currentTarget.disabled = true;
            options.onDelete();
        });

        // Tapping the row anywhere but its own controls opens it, unless it is open or text is being selected.
        row.addEventListener('click', event => {
            if (event.target.closest('button, a, input, select, textarea')) return;
            if (wrapper.dataset.justSwiped || window.getSelection()?.toString()) return;
            if (offset(wrapper) !== 0) {
                close(wrapper);
                return;
            }
            options.onEdit();
        });

        attachGesture(wrapper, row, Boolean(options.onDuplicate));
        return wrapper;
    }

    function attachGesture(wrapper, row, canDuplicate) {
        let start = null;
        let swiping = false;
        let base = 0;

        row.addEventListener('pointerdown', event => {
            if (event.pointerType === 'mouse' || !event.isPrimary) return;
            // A swipe that begins at the screen edge belongs to the browser's back gesture.
            if (event.clientX < SCREEN_EDGE || event.clientX > window.innerWidth - SCREEN_EDGE) return;
            start = {x: event.clientX, y: event.clientY, id: event.pointerId};
            swiping = false;
            base = offset(wrapper);
        });

        row.addEventListener('pointermove', event => {
            if (!start || event.pointerId !== start.id) return;
            const dx = event.clientX - start.x;
            const dy = event.clientY - start.y;
            if (!swiping) {
                // Vertical movement first means the page is scrolling, so the row lets go.
                if (Math.abs(dy) > MAX_DRIFT) {
                    start = null;
                    return;
                }
                if (Math.abs(dx) < START_DISTANCE) return;
                swiping = true;
                if (openWrapper && openWrapper !== wrapper) close(openWrapper);
                row.setPointerCapture(event.pointerId);
                wrapper.classList.add('is-dragging');
            }
            const width = row.offsetWidth;
            const limit = canDuplicate ? ACTION_WIDTH : 0;
            setOffset(wrapper, Math.max(-width, Math.min(limit, base + dx)));
        });

        const finish = event => {
            if (!start || event.pointerId !== start.id) return;
            start = null;
            if (!swiping) return;
            swiping = false;
            wrapper.classList.remove('is-dragging');
            markSwiped(wrapper);
            const width = row.offsetWidth;
            const moved = offset(wrapper);
            if (moved < -width * FULL_SWIPE) askToDelete(wrapper);
            else if (moved < -ACTION_WIDTH) open(wrapper, -ACTION_WIDTH * 2);
            else if (canDuplicate && moved > ACTION_WIDTH / 2) open(wrapper, ACTION_WIDTH);
            else close(wrapper);
        };
        row.addEventListener('pointerup', finish);
        row.addEventListener('pointercancel', finish);
    }

    function askToDelete(wrapper) {
        setOffset(wrapper, 0);
        wrapper.classList.add('is-confirming');
        const confirm = wrapper.querySelector('.swipe-confirm');
        confirm.hidden = false;
        confirm.querySelector('.swipe-cancel').focus({preventScroll: true});
        openWrapper = wrapper;
    }

    function open(wrapper, to) {
        if (openWrapper && openWrapper !== wrapper) close(openWrapper);
        setOffset(wrapper, to);
        wrapper.classList.add('is-open');
        wrapper.querySelectorAll('.swipe-action').forEach(button => button.tabIndex = 0);
        openWrapper = wrapper;
    }

    function close(wrapper) {
        setOffset(wrapper, 0);
        wrapper.classList.remove('is-open', 'is-confirming');
        wrapper.querySelector('.swipe-confirm').hidden = true;
        wrapper.querySelectorAll('.swipe-action').forEach(button => button.tabIndex = -1);
        if (openWrapper === wrapper) openWrapper = null;
    }

    function offset(wrapper) {
        return Number(wrapper.dataset.offset || 0);
    }

    function setOffset(wrapper, value) {
        wrapper.dataset.offset = String(value);
        wrapper.querySelector('.swipe-surface').style.transform = value ? `translateX(${value}px)` : '';
    }

    // The click that ends a swipe must not also count as a tap on the row.
    function markSwiped(wrapper) {
        wrapper.dataset.justSwiped = 'true';
        window.setTimeout(() => delete wrapper.dataset.justSwiped, 350);
    }

    // Touching anywhere outside the open row only closes it; the tap that does so is not passed on. Only one row is
    // open at a time.
    let swallowClick = false;
    document.addEventListener('pointerdown', event => {
        if (!openWrapper || openWrapper.contains(event.target)) return;
        close(openWrapper);
        swallowClick = true;
        window.setTimeout(() => swallowClick = false, 500);
    }, true);
    document.addEventListener('click', event => {
        if (!swallowClick) return;
        swallowClick = false;
        event.preventDefault();
        event.stopPropagation();
    }, true);
    document.addEventListener('keydown', event => {
        if (event.key === 'Escape' && openWrapper) close(openWrapper);
    });

    window.flexbuddySwipe = {wrap};
})();
