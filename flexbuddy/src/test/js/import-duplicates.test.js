const test = require('node:test');
const assert = require('node:assert');
const {loadScript} = require('./load-script');

const choose = loadScript('import-duplicates.js').flexbuddyImportDuplicates.choose;
const logged = {shiftId: 1, kind: 'EXACT', message: 'logged'};
const schedule = {shiftId: 2, kind: 'SCHEDULED_MATCH', message: 'schedule'};

test('an already-logged block wins over the scheduled-block notice', () => {
    const picked = choose({duplicates: [schedule, logged]}, 'COMPLETED', false);
    assert.strictEqual(picked.logged.shiftId, 1);
    assert.strictEqual(picked.schedule, undefined);
});

test('a likely match shows the same notice', () => {
    assert.strictEqual(choose({duplicates: [{shiftId: 3, kind: 'LIKELY'}]}, 'SCHEDULED', false).logged.shiftId, 3);
});

test('a scheduled match only shows for a completed import', () => {
    assert.strictEqual(choose({duplicates: [schedule]}, 'COMPLETED', false).schedule.shiftId, 2);
    assert.strictEqual(choose({duplicates: [schedule]}, 'SCHEDULED', false).schedule, undefined);
});

test('editing the read values drops the already-logged notice but not the scheduled match', () => {
    assert.strictEqual(choose({duplicates: [logged]}, 'COMPLETED', true).logged, undefined);
    assert.strictEqual(choose({duplicates: [logged, schedule]}, 'COMPLETED', true).schedule.shiftId, 2);
});

test('no candidate or no duplicates gives no notice', () => {
    assert.deepStrictEqual(Object.values(choose(undefined, 'COMPLETED', false)), [undefined, undefined]);
    assert.deepStrictEqual(Object.values(choose({duplicates: []}, 'COMPLETED', false)), [undefined, undefined]);
});
