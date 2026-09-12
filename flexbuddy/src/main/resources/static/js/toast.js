(() => {
    let elements;
    let state;
    const queue = [];

    function init(selectors) {
        elements = {
            toast: document.querySelector(selectors.toast),
            title: document.querySelector(selectors.title),
            message: document.querySelector(selectors.message),
            action: document.querySelector(selectors.action),
            countdown: selectors.countdown ? document.querySelector(selectors.countdown) : null
        };
        if (!elements.toast) return;
        elements.toast.addEventListener('mouseenter', pause);
        elements.toast.addEventListener('mouseleave', resume);
        elements.toast.addEventListener('focusin', pause);
        elements.toast.addEventListener('focusout', event => {
            if (!elements.toast.contains(event.relatedTarget)) resume();
        });
        document.addEventListener('keydown', event => {
            if (event.key === 'Escape' && state) hide();
        });
    }

    function show(title, message, options = {}) {
        if (!elements?.toast) return;
        queue.push({title, message, duration: options.duration || 3500, ...options});
        if (!state) displayNext();
    }

    function displayNext() {
        const next = queue.shift();
        if (!next) return;
        state = {...next, remaining: next.duration, started: Date.now()};
        elements.title.textContent = next.title;
        elements.message.textContent = next.message;
        elements.toast.setAttribute('role', next.alert ? 'alert' : 'status');
        elements.action.textContent = next.actionLabel || 'Undo';
        elements.action.classList.toggle('is-hidden', !next.onAction);
        elements.action.onclick = next.onAction ? async () => {
            const action = next.onAction;
            hide();
            try {
                await action();
            } catch (error) {
                show('Undo failed', error.message || 'The action could not be undone.', {alert: true});
            }
        } : null;
        if (elements.countdown) {
            elements.countdown.style.animation = 'none';
            void elements.countdown.offsetWidth;
            elements.countdown.style.animation = `toast-countdown ${next.duration}ms linear forwards`;
        }
        elements.toast.classList.remove('is-hidden');
        schedule();
    }

    function schedule() {
        if (!state) return;
        state.started = Date.now();
        state.timer = window.setTimeout(hide, state.remaining);
        if (elements.countdown) elements.countdown.style.animationPlayState = 'running';
    }

    function pause() {
        if (!state?.timer) return;
        window.clearTimeout(state.timer);
        state.timer = null;
        state.remaining -= Date.now() - state.started;
        if (elements.countdown) elements.countdown.style.animationPlayState = 'paused';
    }

    function resume() {
        if (state && !state.timer) schedule();
    }

    function hide() {
        if (!state) return;
        window.clearTimeout(state.timer);
        elements.toast.classList.add('is-hidden');
        state = undefined;
        window.setTimeout(displayNext, 100);
    }

    window.flexbuddyToast = {init, show, hide, isVisible: () => Boolean(state)};
})();
