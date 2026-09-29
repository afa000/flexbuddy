process.env.TZ = 'Pacific/Kiritimati';
'use strict';
// The same helpers in UTC+14, a day ahead of Los Angeles and ahead of UTC itself, so a helper that leaned on UTC or on
// one particular zone would fail in one of the two files. TZ is per process, which is why this is its own file.
const test = require('node:test');
const assert = require('node:assert/strict');
const {loadScript} = require('./load-script');

const cal = loadScript('calendar.js').flexbuddyCalendar;

test('toIso uses the local calendar date far east of UTC', () => {
    // 00:30 on Sep 29 in Kiritimati is still Sep 28 in UTC.
    assert.equal(cal.toIso(new Date(2026, 8, 29, 0, 30)), '2026-09-29');
});

test('addDays across month and year ends', () => {
    assert.equal(cal.addDays('2026-03-08', 1), '2026-03-09');
    assert.equal(cal.addDays('2026-12-31', 1), '2027-01-01');
    assert.equal(cal.addDays('2028-03-01', -1), '2028-02-29');
});

test('weekStart is the Monday of the ISO week', () => {
    assert.equal(cal.weekStart('2026-09-30'), '2026-09-28');
    assert.equal(cal.weekStart('2026-10-04'), '2026-09-28');
    assert.equal(cal.weekStart('2026-09-28'), '2026-09-28');
});
