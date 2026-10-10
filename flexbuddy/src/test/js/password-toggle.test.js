'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {loadScript} = require('./load-script');

const toggle = loadScript('password-toggle.js').flexbuddyPasswordToggle;

test('a hidden box offers to show the password', () => {
    const state = toggle.buttonState(false);
    assert.equal(state.type, 'password');
    assert.equal(state.label, 'Show password');
    assert.equal(state.pressed, 'false');
});

test('a visible box offers to hide the password', () => {
    const state = toggle.buttonState(true);
    assert.equal(state.type, 'text');
    assert.equal(state.label, 'Hide password');
    assert.equal(state.pressed, 'true');
});

test('the two states use different icons', () => {
    assert.notEqual(toggle.buttonState(true).icon, toggle.buttonState(false).icon);
});
