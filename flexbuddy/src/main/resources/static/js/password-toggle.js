// Password boxes: adds a show/hide button to every input[data-reveal]. Before a form submits, every box is hidden
// again, so the browser saves a password rather than plain text, and nothing stays visible in the back/forward
// cache. The pure helper runs under `node --test`.
(() => {
    const EYE = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12Z"/><circle cx="12" cy="12" r="3"/></svg>';
    const EYE_OFF = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 3l18 18M10.6 5.1A10.8 10.8 0 0 1 12 5c6.4 0 10 7 10 7a17.6 17.6 0 0 1-3.2 4.1M6.6 6.6C3.8 8.4 2 12 2 12s3.6 7 10 7a9.7 9.7 0 0 0 5.4-1.6M9.9 9.9a3 3 0 0 0 4.2 4.2"/></svg>';

    /** What the button shows and says for a box that is currently visible or hidden. */
    function buttonState(visible) {
        return visible
            ? {type: 'text', label: 'Hide password', pressed: 'true', icon: EYE_OFF}
            : {type: 'password', label: 'Show password', pressed: 'false', icon: EYE};
    }

    function apply(input, button, visible) {
        const state = buttonState(visible);
        input.type = state.type;
        button.setAttribute('aria-label', state.label);
        button.setAttribute('aria-pressed', state.pressed);
        button.title = state.label;
        button.innerHTML = state.icon;
    }

    function enhance(input) {
        const wrap = document.createElement('span');
        wrap.className = 'password-field';
        input.replaceWith(wrap);
        wrap.append(input);
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'password-toggle';
        wrap.append(button);
        apply(input, button, false);
        button.addEventListener('click', () => {
            const end = input.selectionEnd;
            apply(input, button, input.type === 'password');
            input.focus();
            try { input.setSelectionRange(end, end); } catch { /* some types refuse a selection */ }
        });
        input.form?.addEventListener('submit', () => apply(input, button, false));
        window.addEventListener('pagehide', () => apply(input, button, false));
    }

    window.flexbuddyPasswordToggle = {buttonState};
    if (typeof document.querySelectorAll === 'function') {
        document.querySelectorAll('input[data-reveal]').forEach(enhance);
    }
})();
