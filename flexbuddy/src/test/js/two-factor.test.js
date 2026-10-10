'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {loadScript} = require('./load-script');

const twoFactor = loadScript('two-factor.js').flexbuddyTwoFactor;

test('the recovery file lists every code with the day it was made', () => {
    const text = twoFactor.recoveryFileText(['AAAAA-BBBBB', 'CCCCC-DDDDD'], '2026-10-10');

    assert.match(text, /AAAAA-BBBBB/);
    assert.match(text, /CCCCC-DDDDD/);
    assert.match(text, /Made 2026-10-10/);
    assert.doesNotMatch(text, /T00:00/);
});

test('the date comes from the device calendar and not from UTC', () => {
    // 11:30 pm on the 9th, local time: a UTC conversion could call it the 10th.
    const lateEvening = new Date(2026, 9, 9, 23, 30);

    assert.equal(twoFactor.localIsoDate(lateEvening), '2026-10-09');
});
