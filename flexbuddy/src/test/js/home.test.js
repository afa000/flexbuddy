process.env.TZ = 'America/Los_Angeles';
const test = require('node:test');
const assert = require('node:assert');
const {loadScript} = require('./load-script');

const calendar = loadScript('calendar.js').flexbuddyCalendar;
const home = loadScript('home.js', {flexbuddyCalendar: calendar}).flexbuddyHome;

test('weekRange is the Monday-to-Sunday week that holds the day', () => {
    const week = home.weekRange('2026-09-30');
    assert.deepStrictEqual([week.from, week.to, week.label], ['2026-09-28', '2026-10-04', 'Mon Sep 28 – Sun Oct 4']);
    // A Sunday belongs to the week that is ending, not the one starting.
    assert.deepStrictEqual([home.weekRange('2026-10-04').from, home.weekRange('2026-10-04').to], ['2026-09-28', '2026-10-04']);
    assert.strictEqual(home.weekRange('2026-09-28').from, '2026-09-28');
});

test('lastSevenRange is today and the six days before it', () => {
    const range = home.lastSevenRange('2026-10-01');
    assert.deepStrictEqual([range.from, range.to], ['2026-09-25', '2026-10-01']);
    // Across the daylight-saving change on Nov 1 the range still starts seven calendar days back.
    assert.strictEqual(home.lastSevenRange('2026-11-03').from, '2026-10-28');
    assert.strictEqual(home.lastSevenRange('2026-01-03').from, '2025-12-28');
});

// Arrays made inside the loaded script are from another realm, so they are copied before a deep comparison.
const keys = rows => Array.from(rows, row => row.key);

test('attentionRows lists only what applies, in order', () => {
    assert.deepStrictEqual(keys(home.attentionRows({})), []);
    const rows = home.attentionRows({needsConfirmation: 1, missingMiles: 3, backupDue: true});
    assert.deepStrictEqual(keys(rows), ['confirm', 'miles', 'backup']);
    assert.strictEqual(rows[0].text, '1 block to confirm');
    assert.strictEqual(rows[1].text, '3 blocks have no miles');
    assert.strictEqual(home.attentionRows({needsConfirmation: 2})[0].text, '2 blocks to confirm');
    assert.strictEqual(home.attentionRows({missingMiles: 1})[0].text, '1 block has no miles');
});

test('attentionRows puts the install prompt last', () => {
    const rows = home.attentionRows({needsConfirmation: 1, backupDue: true, installable: true});
    assert.deepStrictEqual(keys(rows), ['confirm', 'backup', 'install']);
    assert.deepStrictEqual(keys(home.attentionRows({installable: true})), ['install']);
});

test('recentBlocks skips scheduled blocks, keeps the order and returns at most three', () => {
    const shifts = [
        {id: 1, status: 'SCHEDULED'}, {id: 2, status: 'COMPLETED'}, {id: 3, status: 'FORFEITED'},
        {id: 4, status: 'SCHEDULED'}, {id: 5, status: 'CANCELLED'}, {id: 6, status: 'COMPLETED'}
    ];
    assert.deepStrictEqual(Array.from(home.recentBlocks(shifts), shift => shift.id), [2, 3, 5]);
    assert.deepStrictEqual(Array.from(home.recentBlocks(shifts, 2), shift => shift.id), [2, 3]);
    assert.strictEqual(home.recentBlocks([{id: 1, status: 'SCHEDULED'}]).length, 0);
});
