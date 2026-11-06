const test = require('node:test');
const assert = require('node:assert');
const {loadScript} = require('./load-script');

const {hintKey, INITIAL_STATUS} = loadScript('import-status.js').flexbuddyImportStatus;
const read = (date, suggestedStatus) => ({date: {value: date, level: date ? 'HIGH' : 'MISSING'}, suggestedStatus});

test('every screenshot starts with no status chosen, whatever the suggestion', () => {
    assert.strictEqual(INITIAL_STATUS, '');
});

test('a block that has not started is hinted as scheduled', () => {
    assert.strictEqual(hintKey(read('2026-11-20', 'SCHEDULED')), 'js.app.importStatusLooksScheduled');
});

test('a past block asks the driver to confirm they worked it rather than assuming so', () => {
    assert.strictEqual(hintKey(read('2026-10-02', 'COMPLETED')), 'js.app.importStatusLooksPast');
});

test('no date read gets its own hint even though the server suggests completed', () => {
    assert.strictEqual(hintKey(read(null, 'COMPLETED')), 'js.app.importStatusNoDate');
    assert.strictEqual(hintKey({suggestedStatus: 'COMPLETED'}), 'js.app.importStatusNoDate');
});

test('no screenshot read gives no hint', () => {
    assert.strictEqual(hintKey(undefined), undefined);
});

test('the empty choice offers no completion shortcut and shows the already-logged notice', () => {
    const choose = loadScript('import-duplicates.js').flexbuddyImportDuplicates.choose;
    const candidate = {duplicates: [{shiftId: 2, kind: 'SCHEDULED_MATCH'}]};
    assert.strictEqual(choose(candidate, INITIAL_STATUS, false).schedule, undefined);
    assert.strictEqual(choose({duplicates: [{shiftId: 1, kind: 'EXACT'}]}, INITIAL_STATUS, false).logged.shiftId, 1);
});

test('every hint has English text that never says the block was paid', () => {
    const {englishMessages} = require('./load-script');
    const messages = englishMessages();
    for (const candidate of [read('2026-11-20', 'SCHEDULED'), read('2026-10-02', 'COMPLETED'), read(null, 'COMPLETED')]) {
        const text = messages[hintKey(candidate)];
        assert.ok(text, hintKey(candidate));
        assert.doesNotMatch(text, /\bpaid\b/i);
    }
});
