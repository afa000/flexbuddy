'use strict';
const test = require('node:test');
const assert = require('node:assert');
const {loadScript} = require('./load-script');

/** The few element features toast.js uses, with real class and attribute bookkeeping. */
function fakeElement() {
    const classes = new Set();
    const attributes = {};
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
        addEventListener() {},
        contains: () => false
    };
}

/** Timers that only fire when the test moves the clock on, in the order they are due. */
function fakeClock() {
    let now = 0;
    let nextId = 1;
    const timers = new Map();
    return {
        setTimeout(callback, delay) {
            timers.set(nextId, {callback, at: now + delay});
            return nextId++;
        },
        clearTimeout(id) {
            timers.delete(id);
        },
        tick(ms) {
            const until = now + ms;
            for (;;) {
                const due = [...timers].filter(([, timer]) => timer.at <= until).sort((a, b) => a[1].at - b[1].at)[0];
                if (!due) break;
                timers.delete(due[0]);
                now = due[1].at;
                due[1].callback();
            }
            now = until;
        }
    };
}

/** A fresh copy of toast.js wired to fake elements, a fake clock and a keyboard. */
function setUp() {
    const clock = fakeClock();
    const el = {toast: fakeElement(), title: fakeElement(), message: fakeElement(), action: fakeElement(),
        countdown: fakeElement()};
    el.toast.classList.add('is-hidden');
    const bySelector = {'#successToast': el.toast, '#toastTitle': el.title, '#toastMessage': el.message,
        '#toastAction': el.action, '#toastCountdown': el.countdown};
    const keydown = [];
    const document = {
        querySelector: selector => bySelector[selector] ?? null,
        querySelectorAll: () => [], getElementById: () => null, documentElement: {dataset: {}},
        addEventListener(type, listener) {
            if (type === 'keydown') keydown.push(listener);
        },
        removeEventListener() {}
    };
    const toast = loadScript('toast.js', {document, setTimeout: clock.setTimeout, clearTimeout: clock.clearTimeout})
        .flexbuddyToast;
    const init = () => toast.init({toast: '#successToast', title: '#toastTitle', message: '#toastMessage',
        action: '#toastAction', countdown: '#toastCountdown'});
    const pressKey = key => keydown.forEach(listener => listener({key}));
    return {toast, el, clock, init, pressKey};
}

test('show before init does nothing', () => {
    const {toast, el} = setUp();
    toast.show('Shift deleted', 'Your history is up to date.');
    assert.strictEqual(toast.isVisible(), false);
    assert.strictEqual(el.title.textContent, '');
    assert.strictEqual(el.toast.classList.contains('is-hidden'), true);
    toast.hide();
});

test('show fills the notice, reveals it and announces it politely', () => {
    const {toast, el, init} = setUp();
    init();
    toast.show('Shift deleted', 'Your history is up to date.');
    assert.strictEqual(toast.isVisible(), true);
    assert.strictEqual(el.title.textContent, 'Shift deleted');
    assert.strictEqual(el.message.textContent, 'Your history is up to date.');
    assert.strictEqual(el.toast.classList.contains('is-hidden'), false);
    assert.strictEqual(el.toast.getAttribute('role'), 'status');
    assert.strictEqual(el.countdown.style.animation, 'toast-countdown 3500ms linear forwards');
});

test('an alert notice is announced as an alert', () => {
    const {toast, el, init} = setUp();
    init();
    toast.show('Could not save', 'Check your connection.', {alert: true});
    assert.strictEqual(el.toast.getAttribute('role'), 'alert');
});

test('the action button shows only with an action, labelled Undo unless told otherwise', () => {
    const {toast, el, clock, init} = setUp();
    init();
    toast.show('Saved', '');
    assert.strictEqual(el.action.classList.contains('is-hidden'), true);
    assert.strictEqual(el.action.onclick, null);

    toast.hide();
    clock.tick(100);
    toast.show('Shift deleted', '', {onAction: () => {}});
    assert.strictEqual(el.action.classList.contains('is-hidden'), false);
    assert.strictEqual(el.action.textContent, 'Undo');

    toast.hide();
    clock.tick(100);
    toast.show('Expense deleted', '', {onAction: () => {}, actionLabel: 'Restore'});
    assert.strictEqual(el.action.textContent, 'Restore');
});

test('a notice hides itself after its duration, 3500 ms by default', () => {
    const {toast, el, clock, init} = setUp();
    init();
    toast.show('Saved', '', {duration: 1000});
    clock.tick(999);
    assert.strictEqual(toast.isVisible(), true);
    clock.tick(1);
    assert.strictEqual(toast.isVisible(), false);
    assert.strictEqual(el.toast.classList.contains('is-hidden'), true);

    clock.tick(100);
    toast.show('Saved again', '');
    clock.tick(3499);
    assert.strictEqual(toast.isVisible(), true);
    clock.tick(1);
    assert.strictEqual(toast.isVisible(), false);
});

test('a second notice waits for the first and appears 100 ms after it hides', () => {
    const {toast, el, clock, init} = setUp();
    init();
    toast.show('First', '', {duration: 1000});
    toast.show('Second', '', {duration: 1000});
    assert.strictEqual(el.title.textContent, 'First');

    clock.tick(1000);
    assert.strictEqual(toast.isVisible(), false);
    clock.tick(99);
    assert.strictEqual(el.title.textContent, 'First');
    clock.tick(1);
    assert.strictEqual(toast.isVisible(), true);
    assert.strictEqual(el.title.textContent, 'Second');
});

test('Escape hides the visible notice and other keys leave it', () => {
    const {toast, init, pressKey} = setUp();
    init();
    toast.show('Saved', '');
    pressKey('Enter');
    assert.strictEqual(toast.isVisible(), true);
    pressKey('Escape');
    assert.strictEqual(toast.isVisible(), false);
});

test('the action runs once and hides the notice', async () => {
    const {toast, el, init} = setUp();
    init();
    let calls = 0;
    toast.show('Shift deleted', '', {onAction: async () => { calls++; }});
    await el.action.onclick();
    assert.strictEqual(calls, 1);
    assert.strictEqual(toast.isVisible(), false);
});

test('a failed action shows Undo failed as an alert with the reason', async () => {
    const {toast, el, init} = setUp();
    init();
    toast.show('Shift deleted', '', {onAction: async () => { throw new Error('You are offline.'); }});
    await el.action.onclick();
    assert.strictEqual(toast.isVisible(), true);
    assert.strictEqual(el.title.textContent, 'Undo failed');
    assert.strictEqual(el.message.textContent, 'You are offline.');
    assert.strictEqual(el.toast.getAttribute('role'), 'alert');
});
