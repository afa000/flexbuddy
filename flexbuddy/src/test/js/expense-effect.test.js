const test = require('node:test');
const assert = require('node:assert');
const {loadScript, englishMessages} = require('./load-script');

const {effectKey} = loadScript('expense-effect.js').flexbuddyExpenseEffect;

// Same table as NetEarningsCalculatorTest.eachCategoryLowersNetOnlyWhereTheExpenseMessageSaysSo.
const LOWERS_NET = {
    STANDARD_MILEAGE: {FUEL: false, TOLL: true, PARKING: true, MAINTENANCE: false, OTHER: true},
    ACTUAL_EXPENSES: {FUEL: true, TOLL: true, PARKING: true, MAINTENANCE: true, OTHER: true}
};

for (const [method, categories] of Object.entries(LOWERS_NET)) {
    for (const [category, lowers] of Object.entries(categories)) {
        test(`${category} under ${method} ${lowers ? 'lowers net' : 'is covered by the mileage rate'}`, () => {
            assert.strictEqual(effectKey(category, method),
                lowers ? 'js.expenseEffect.lowersNet' : 'js.expenseEffect.coveredByMileage');
        });
    }
}

test('an expense linked to a block still scheduled counts only once the block has happened', () => {
    for (const category of ['TOLL', 'PARKING', 'OTHER']) {
        assert.strictEqual(effectKey(category, 'STANDARD_MILEAGE', true), 'js.expenseEffect.afterBlock');
    }
    for (const category of ['FUEL', 'TOLL', 'PARKING', 'MAINTENANCE', 'OTHER']) {
        assert.strictEqual(effectKey(category, 'ACTUAL_EXPENSES', true), 'js.expenseEffect.afterBlock');
    }
    // The mileage rate covers fuel and maintenance whatever block they are linked to.
    assert.strictEqual(effectKey('FUEL', 'STANDARD_MILEAGE', true), 'js.expenseEffect.coveredByMileage');
    assert.strictEqual(effectKey('MAINTENANCE', 'STANDARD_MILEAGE', true), 'js.expenseEffect.coveredByMileage');
});

test('nothing is claimed before the cost method or the category is known', () => {
    assert.strictEqual(effectKey('FUEL', undefined), undefined);
    assert.strictEqual(effectKey('TOLL', null), undefined);
    assert.strictEqual(effectKey('', 'STANDARD_MILEAGE'), undefined);
    assert.strictEqual(effectKey('SNACKS', 'ACTUAL_EXPENSES'), undefined);
});

test('every effect message has English text, and the covered one never says net drops', () => {
    const messages = englishMessages();
    for (const key of ['js.expenseEffect.coveredByMileage', 'js.expenseEffect.lowersNet', 'js.expenseEffect.afterBlock',
        'js.expenseEffect.saved']) {
        assert.ok(messages[key], key);
    }
    assert.doesNotMatch(messages['js.expenseEffect.coveredByMileage'], /recalculated|lowers your/i);
});
