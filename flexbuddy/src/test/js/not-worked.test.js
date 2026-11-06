const test = require('node:test');
const assert = require('node:assert');
const {loadScript, englishMessages} = require('./load-script');

const {clearedItems, canMarkNotWorked} = loadScript('not-worked.js').flexbuddyNotWorked;
const format = {money: value => `$${Number(value).toFixed(2)}`, number: value => String(value)};
// Arrays made inside the loaded script come from another realm, so compare them as JSON.
const cleared = shift => JSON.parse(JSON.stringify(clearedItems(shift, format)));

test('a block with everything a worked block holds lists each saved value in order', () => {
    const shift = {
        tips: 12, miles: 38,
        details: {odometerStart: 1000, odometerEnd: 1038, actualStart: '09:05', actualEnd: '12:41', stops: 30,
            packages: 42, returns: 1}
    };
    assert.deepStrictEqual(cleared(shift), [
        ['js.notWorked.tips', '$12.00'],
        ['js.notWorked.miles', '38'],
        ['js.notWorked.odometer'],
        ['js.notWorked.actualTimes'],
        ['js.notWorked.route']
    ]);
});

test('a block with only its pay lists nothing, so the dialog says nothing else changes', () => {
    assert.deepStrictEqual(cleared({tips: 0, miles: null, details: {}}), []);
    assert.deepStrictEqual(cleared({tips: '0.00'}), []);
});

test('zero tips change nothing, but zero recorded miles are cleared and so are listed', () => {
    assert.deepStrictEqual(cleared({tips: 0, miles: 0}), [['js.notWorked.miles', '0']]);
});

test('one odometer reading, a start time alone or one route count is enough to be listed', () => {
    assert.deepStrictEqual(cleared({details: {odometerEnd: 1038}}), [['js.notWorked.odometer']]);
    assert.deepStrictEqual(cleared({details: {actualStart: '09:05'}}), [['js.notWorked.actualTimes']]);
    assert.deepStrictEqual(cleared({details: {returns: 0}}), [['js.notWorked.route']]);
});

test('only a saved completed block offers the correction', () => {
    assert.strictEqual(canMarkNotWorked('edit', 'COMPLETED'), true);
    for (const status of ['SCHEDULED', 'CANCELLED', 'FORFEITED']) assert.strictEqual(canMarkNotWorked('edit', status), false);
    assert.strictEqual(canMarkNotWorked('new', 'COMPLETED'), false);
});

test('every message the list can show has English text', () => {
    const messages = englishMessages();
    for (const key of ['js.notWorked.clears', 'js.notWorked.nothingElse', 'js.notWorked.tips', 'js.notWorked.miles',
        'js.notWorked.odometer', 'js.notWorked.actualTimes', 'js.notWorked.route']) {
        assert.ok(messages[key], key);
    }
});
