'use strict';
// The outbox's decisions, which live in pure functions so they can be tested without a page, IndexedDB or a network.
// Results are compared as primitives or through JSON, because objects made inside the loaded script come from another
// realm (see load-script.js).
const test = require('node:test');
const assert = require('node:assert/strict');
const {loadScript} = require('./load-script');

const outbox = loadScript('outbox.js').flexbuddyOutbox;
const plain = value => JSON.parse(JSON.stringify(value));

test('classify: a 2xx deletes the item', () => {
    for (const status of [200, 201, 204]) assert.equal(outbox.classify(status, false, null), 'delete');
});

test('classify: a 409 is a duplicate that already landed, or a conflict, or a refusal', () => {
    assert.equal(outbox.classify(409, false, {code: 'DUPLICATE'}), 'delete');
    assert.equal(outbox.classify(409, false, {code: 'CONFLICT', current: {id: 5}}), 'conflict');
    assert.equal(outbox.classify(409, false, null), 'failed');
});

test('classify: the first 403 asks for a fresh token, a login redirect or 401 means signed out', () => {
    assert.equal(outbox.classify(403, false, null), 'retry-token');
    assert.equal(outbox.classify(200, true, null), 'signed-out');
    assert.equal(outbox.classify(302, true, null), 'signed-out');
    assert.equal(outbox.classify(401, false, null), 'signed-out');
});

test('classify: a 400 or 404 is a change the server will not take', () => {
    assert.equal(outbox.classify(400, false, null), 'failed');
    assert.equal(outbox.classify(404, false, null), 'failed');
});

test('classify: a server error or no answer at all stops the drain to try again later', () => {
    for (const status of [500, 502, 503]) assert.equal(outbox.classify(status, false, null), 'stop');
    assert.equal(outbox.classify(0, false, null), 'stop'); // a network error or a timeout
    assert.equal(outbox.classify(429, false, null), 'stop');
});

test('nextDelay backs off 30, 60, 120, then 300 seconds for good', () => {
    assert.deepEqual([0, 1, 2, 3, 4, 10].map(outbox.nextDelay), [30000, 60000, 120000, 300000, 300000, 300000]);
});

test('canQueue allows up to 50 items', () => {
    assert.equal(outbox.canQueue(0), true);
    assert.equal(outbox.canQueue(49), true);
    assert.equal(outbox.canQueue(50), false);
});

test('conflictRows lists only the fields the queued change sets, in the sheet order', () => {
    const rows = plain(outbox.conflictRows(
        {status: 'COMPLETED', miles: 42.5, details: {actualStart: '09:00', odometerEnd: 1242.5}},
        {status: 'COMPLETED', miles: 42.5, details: {actualStart: '09:00:00', odometerEnd: 1242.5, stops: 12}}));
    assert.deepEqual(rows.map(row => row.label), ['Status', 'Started', 'Odometer end', 'Miles']);
});

test('conflictRows flags the values that differ', () => {
    const rows = plain(outbox.conflictRows(
        {status: 'COMPLETED', details: {actualStart: '09:00', actualEnd: '13:00'}, miles: 42.5},
        {status: 'COMPLETED', details: {actualStart: '09:00:00', actualEnd: '13:30:00'}, miles: 40}));
    const differs = Object.fromEntries(rows.map(row => [row.label, row.differs]));
    assert.deepEqual(differs, {Status: false, Started: false, Finished: true, Miles: true});
    assert.equal(rows.find(row => row.label === 'Finished').mine, '13:00');
    assert.equal(rows.find(row => row.label === 'Finished').saved, '13:30');
});

test('conflictRows counts nothing against a value as different, and a blank as nothing', () => {
    const rows = plain(outbox.conflictRows(
        {details: {odometerStart: null, odometerEnd: 1242.5, stops: '', returns: 2}},
        {details: {odometerStart: 1200, odometerEnd: null, stops: null, returns: 2}}));
    const byLabel = Object.fromEntries(rows.map(row => [row.label, row]));
    assert.equal(byLabel['Odometer start'].differs, true);
    assert.equal(byLabel['Odometer end'].differs, true);
    assert.equal(byLabel.Stops.differs, false); // '' and null are both nothing
    assert.equal(byLabel.Returns.differs, false);
});

test('conflictRows compares times at minute precision', () => {
    const [row] = plain(outbox.conflictRows({details: {actualStart: '09:00'}}, {details: {actualStart: '09:00:00'}}));
    assert.equal(row.differs, false);
    const [other] = plain(outbox.conflictRows({details: {actualStart: '09:00'}}, {details: {actualStart: '09:01:00'}}));
    assert.equal(other.differs, true);
});
