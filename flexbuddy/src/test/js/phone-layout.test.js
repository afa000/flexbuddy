const test = require('node:test');
const assert = require('node:assert');
const {loadScript} = require('./load-script');

const {hidesFixedControls} = loadScript('field-focus.js').flexbuddyFieldFocus;
const {showsUpcomingPanel} = loadScript('schedule.js').flexbuddySchedule;

const input = type => ({tagName: 'INPUT', type});

test('typing or picking in a field hides the fixed navigation and + button', () => {
    for (const type of ['text', 'number', 'date', 'time', 'search', 'email', 'password']) {
        assert.strictEqual(hidesFixedControls(input(type)), true, type);
    }
    assert.strictEqual(hidesFixedControls({tagName: 'SELECT'}), true);
    assert.strictEqual(hidesFixedControls({tagName: 'TEXTAREA'}), true);
    assert.strictEqual(hidesFixedControls({tagName: 'input'}), true, 'an input with no type is a text field');
});

test('buttons, checkboxes and disabled or read-only fields leave them in place', () => {
    for (const type of ['checkbox', 'radio', 'range', 'file', 'submit', 'button']) {
        assert.strictEqual(hidesFixedControls(input(type)), false, type);
    }
    assert.strictEqual(hidesFixedControls({tagName: 'BUTTON'}), false);
    assert.strictEqual(hidesFixedControls({tagName: 'INPUT', type: 'text', disabled: true}), false);
    assert.strictEqual(hidesFixedControls({tagName: 'INPUT', type: 'text', readOnly: true}), false);
    assert.strictEqual(hidesFixedControls(null), false);
});

test('the Next 14 days panel shows only when something is coming', () => {
    assert.strictEqual(showsUpcomingPanel({next: null, days: []}), false);
    assert.strictEqual(showsUpcomingPanel({next: {id: 1}, days: []}), true, 'a block beyond 14 days is still upcoming');
    assert.strictEqual(showsUpcomingPanel({next: null, days: [{date: '2026-11-07'}]}), true);
    assert.strictEqual(showsUpcomingPanel(null), false);
});
