// On a phone the bottom navigation and the round + button stay fixed over the page. While the driver types in a field
// they would sit over it, its message or the keyboard's action row, so they step aside until the field loses focus.
// The CSS that hides them only applies to the phone layout.
(() => {
    const TEXT_TYPES = new Set(['text', 'number', 'date', 'time', 'datetime-local', 'month', 'week', 'search', 'email',
        'tel', 'url', 'password']);

    /** True for a field the driver types into or picks from: text-like inputs, selects and text areas. */
    function hidesFixedControls(element) {
        if (!element || element.disabled || element.readOnly) return false;
        const tag = String(element.tagName || '').toUpperCase();
        if (tag === 'TEXTAREA' || tag === 'SELECT') return true;
        return tag === 'INPUT' && TEXT_TYPES.has(String(element.type || 'text').toLowerCase());
    }

    function update(element) {
        document.body?.classList.toggle('field-focus', hidesFixedControls(element));
    }

    if (typeof document.addEventListener === 'function') {
        document.addEventListener('focusin', event => update(event.target));
        // Focus moving between two fields passes through the body, so the check waits for it to land.
        document.addEventListener('focusout', () => setTimeout(() => update(document.activeElement), 0));
    }

    window.flexbuddyFieldFocus = {hidesFixedControls};
})();
