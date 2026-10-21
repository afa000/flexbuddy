'use strict';
const test = require('node:test');
const assert = require('node:assert');
const {loadScript} = require('./load-script');

const SELECTORS = {toast: '#successToast', title: '#toastTitle', message: '#toastMessage', action: '#toastAction',
    countdown: '#toastCountdown'};

/** The few element features toast.js uses, with real class bookkeeping and listeners a test can fire. */
function fakeElement() {
    const classes = new Set();
    const attributes = {};
    const listeners = {};
    return {
        textContent: '', onclick: null, style: {}, offsetWidth: 0,
        classList: {
            add: name => classes.add(name),
            remove: name => classes.delete(name),
            toggle: (name, force) => (force ?? !classes.has(name)) ? classes.add(name) : classes.delete(name),
            contains: name => classes.has(name)
        },
        setAttribute: (name, value) => { attributes[name] = String(value); },
        getAttribute: name => attributes[name] ?? null,
        addEventListener: (type, listener) => (listeners[type] ??= []).push(listener),
        fire: (type, event = {}) => (listeners[type] ?? []).forEach(listener => listener(event)),
        contains: () => false
    };
}

/**
 * A fresh copy of toast.js wired to fake elements and a keyboard. Node's mock timers drive both its setTimeout and its
 * Date.now(), so a test moves time on with t.mock.timers.tick(ms).
 */
function setUp(t) {
    t.mock.timers.enable({apis: ['setTimeout', 'Date']});
    const el = Object.fromEntries(Object.keys(SELECTORS).map(name => [name, fakeElement()]));
    el.toast.classList.add('is-hidden');
    el.toast.contains = node => [el.title, el.message, el.action].includes(node);
    const bySelector = Object.fromEntries(Object.entries(SELECTORS).map(([name, selector]) => [selector, el[name]]));
    const keydown = [];
    const document = {
        querySelector: selector => bySelector[selector] ?? null,
        querySelectorAll: () => [], getElementById: () => null, documentElement: {dataset: {}},
        addEventListener(type, listener) {
            if (type === 'keydown') keydown.push(listener);
        },
        removeEventListener() {}
    };
    const toast = loadScript('toast.js', {document, setTimeout, clearTimeout, Date}).flexbuddyToast;
    const init = () => toast.init(SELECTORS);
    const pressKey = key => keydown.forEach(listener => listener({key}));
    return {toast, el, tick: ms => t.mock.timers.tick(ms), init, pressKey};
}

test('show before init does nothing', t => {
    const {toast, el} = setUp(t);
    toast.show('Shift deleted', 'Your history is up to date.');
    assert.strictEqual(toast.isVisible(), false);
    assert.strictEqual(el.title.textContent, '');
    toast.hide();
});

test('show fills the notice, reveals it and announces it politely', t => {
    const {toast, el, init} = setUp(t);
    init();
    toast.show('Shift deleted', 'Your history is up to date.');
    assert.strictEqual(toast.isVisible(), true);
    assert.strictEqual(el.title.textContent, 'Shift deleted');
    assert.strictEqual(el.message.textContent, 'Your history is up to date.');
    assert.strictEqual(el.toast.classList.contains('is-hidden'), false);
    assert.strictEqual(el.toast.getAttribute('role'), 'status');
    assert.strictEqual(el.countdown.style.animation, 'toast-countdown 3500ms linear forwards');
});

test('an alert notice is announced as an alert', t => {
    const {toast, el, init} = setUp(t);
    init();
    toast.show('Could not save', 'Check your connection.', {alert: true});
    assert.strictEqual(el.toast.getAttribute('role'), 'alert');
});

test('the action button shows only with an action, labelled Undo unless told otherwise', t => {
    const {toast, el, tick, init} = setUp(t);
    init();
    toast.show('Saved', '');
    assert.strictEqual(el.action.classList.contains('is-hidden'), true);
    assert.strictEqual(el.action.onclick, null);

    toast.hide();
    tick(100);
    toast.show('Shift deleted', '', {onAction: () => {}});
    assert.strictEqual(el.action.classList.contains('is-hidden'), false);
    assert.strictEqual(el.action.textContent, 'Undo');

    toast.hide();
    tick(100);
    toast.show('Expense deleted', '', {onAction: () => {}, actionLabel: 'Restore'});
    assert.strictEqual(el.action.textContent, 'Restore');
});

test('a notice hides itself after its duration, 3500 ms by default', t => {
    const {toast, el, tick, init} = setUp(t);
    init();
    toast.show('Saved', '', {duration: 1000});
    tick(999);
    assert.strictEqual(toast.isVisible(), true);
    tick(1);
    assert.strictEqual(toast.isVisible(), false);
    assert.strictEqual(el.toast.classList.contains('is-hidden'), true);

    tick(100);
    toast.show('Saved again', '');
    tick(3499);
    assert.strictEqual(toast.isVisible(), true);
    tick(1);
    assert.strictEqual(toast.isVisible(), false);
});

test('hovering pauses the countdown and leaving resumes it with the time that was left', t => {
    const {toast, el, tick, init} = setUp(t);
    init();
    toast.show('Shift deleted', '', {duration: 1000});
    tick(600);
    el.toast.fire('mouseenter');
    assert.strictEqual(el.countdown.style.animationPlayState, 'paused');
    tick(5000);
    assert.strictEqual(toast.isVisible(), true);

    el.toast.fire('mouseleave');
    assert.strictEqual(el.countdown.style.animationPlayState, 'running');
    tick(399);
    assert.strictEqual(toast.isVisible(), true);
    tick(1);
    assert.strictEqual(toast.isVisible(), false);
});

test('focus inside the notice keeps it paused until focus leaves it', t => {
    const {toast, el, tick, init} = setUp(t);
    init();
    toast.show('Shift deleted', '', {duration: 1000, onAction: () => {}});
    el.toast.fire('focusin');
    el.toast.fire('focusout', {relatedTarget: el.action});
    tick(5000);
    assert.strictEqual(toast.isVisible(), true);

    el.toast.fire('focusout', {relatedTarget: null});
    tick(1000);
    assert.strictEqual(toast.isVisible(), false);
});

test('a second notice waits for the first and appears 100 ms after it hides', t => {
    const {toast, el, tick, init} = setUp(t);
    init();
    toast.show('First', '', {duration: 1000});
    toast.show('Second', '', {duration: 1000});
    assert.strictEqual(el.title.textContent, 'First');

    tick(1000);
    assert.strictEqual(toast.isVisible(), false);
    tick(99);
    assert.strictEqual(el.title.textContent, 'First');
    tick(1);
    assert.strictEqual(toast.isVisible(), true);
    assert.strictEqual(el.title.textContent, 'Second');
});

test('Escape hides the visible notice and other keys leave it', t => {
    const {toast, init, pressKey} = setUp(t);
    init();
    toast.show('Saved', '');
    pressKey('Enter');
    assert.strictEqual(toast.isVisible(), true);
    pressKey('Escape');
    assert.strictEqual(toast.isVisible(), false);
});

test('clicking the action runs it and hides the notice, so the button cannot be clicked again', async t => {
    const {toast, el, init} = setUp(t);
    init();
    let calls = 0;
    toast.show('Shift deleted', '', {onAction: async () => { calls++; }});
    await el.action.onclick();
    assert.strictEqual(calls, 1);
    assert.strictEqual(toast.isVisible(), false);
    // .is-hidden is display: none, which takes the button out of reach.
    assert.strictEqual(el.toast.classList.contains('is-hidden'), true);
});

test('a failed action shows Undo failed as an alert with the reason', async t => {
    const {toast, el, init} = setUp(t);
    init();
    toast.show('Shift deleted', '', {onAction: async () => { throw new Error('You are offline.'); }});
    await el.action.onclick();
    assert.strictEqual(toast.isVisible(), true);
    assert.strictEqual(el.title.textContent, 'Undo failed');
    assert.strictEqual(el.message.textContent, 'You are offline.');
    assert.strictEqual(el.toast.getAttribute('role'), 'alert');
});

test('a failure without a reason falls back to a general message', async t => {
    const {toast, el, init} = setUp(t);
    init();
    toast.show('Shift deleted', '', {onAction: () => Promise.reject(new Error(''))});
    await el.action.onclick();
    assert.strictEqual(el.title.textContent, 'Undo failed');
    assert.strictEqual(el.message.textContent, 'The action could not be undone.');
});

test('a notice that times out leaves no action on the button', t => {
    const {toast, el, tick, init} = setUp(t);
    init();
    toast.show('Shift deleted', '', {onAction: () => {}, duration: 500});
    assert.strictEqual(typeof el.action.onclick, 'function');
    tick(500);
    assert.strictEqual(toast.isVisible(), false);
    assert.strictEqual(el.action.onclick, null);
});

test('a notice closed with hide() leaves no action on the button', t => {
    const {toast, el, init} = setUp(t);
    init();
    toast.show('Shift deleted', '', {onAction: () => {}});
    toast.hide();
    assert.strictEqual(el.action.onclick, null);
});

test('clicking the action clears it from the button, so it runs only once', async t => {
    const {toast, el, init} = setUp(t);
    init();
    let calls = 0;
    let finish;
    toast.show('Shift deleted', '', {onAction: () => new Promise(resolve => { calls++; finish = resolve; })});
    const click = el.action.onclick();
    // Still waiting for the restore: the button must already have no handler.
    assert.strictEqual(el.action.onclick, null);
    finish();
    await click;
    assert.strictEqual(calls, 1);
});
