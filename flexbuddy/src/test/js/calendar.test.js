process.env.TZ = 'America/Los_Angeles';
'use strict';
// The date helpers the schedule relies on, in a US time zone where the local day and the UTC day often differ.
// TZ is set on the first line, before anything else runs; node --test runs each file in its own process.
const test = require('node:test');
const assert = require('node:assert/strict');
const {loadScript} = require('./load-script');

const cal = loadScript('calendar.js').flexbuddyCalendar;

test('toIso uses the local calendar date', () => {
    // 23:30 in Los Angeles on Sep 29 is already Sep 30 in UTC.
    assert.equal(cal.toIso(new Date(2026, 8, 29, 23, 30)), '2026-09-29');
});

test('parseIso gives local midnight', () => {
    const date = cal.parseIso('2026-09-29');
    assert.deepEqual([date.getFullYear(), date.getMonth(), date.getDate(), date.getHours()], [2026, 8, 29, 0]);
});

test('addDays across the daylight-saving changes', () => {
    assert.equal(cal.addDays('2026-03-08', 1), '2026-03-09'); // spring forward
    assert.equal(cal.addDays('2026-11-01', 1), '2026-11-02'); // fall back
    assert.equal(cal.addDays('2026-11-02', -1), '2026-11-01');
});

test('addDays across months and years', () => {
    assert.equal(cal.addDays('2026-12-31', 1), '2027-01-01');
    assert.equal(cal.addDays('2026-03-01', -1), '2026-02-28');
    assert.equal(cal.addDays('2028-03-01', -1), '2028-02-29');
});

test('addMonths clamps to the last day of a shorter month', () => {
    assert.equal(cal.addMonths('2026-01-31', 1), '2026-02-28');
    assert.equal(cal.addMonths('2028-01-31', 1), '2028-02-29');
    assert.equal(cal.addMonths('2026-03-31', -1), '2026-02-28');
    assert.equal(cal.addMonths('2026-10-15', 3), '2027-01-15');
});

test('weekStart is the Monday of the ISO week', () => {
    assert.equal(cal.weekStart('2026-09-30'), '2026-09-28'); // a Wednesday
    assert.equal(cal.weekStart('2026-10-04'), '2026-09-28'); // a Sunday
    assert.equal(cal.weekStart('2026-09-28'), '2026-09-28'); // a Monday
});

test('weekStart crosses a year boundary', () => {
    assert.equal(cal.weekStart('2027-01-01'), '2026-12-28'); // a Friday
});
