process.env.TZ = 'America/Los_Angeles';
const test = require('node:test');
const assert = require('node:assert');
const {loadScript} = require('./load-script');

// setup.js reads the settings summaries from account-sections.js, so the second script is loaded with the first's exports.
const sections = loadScript('account-sections.js').flexbuddyAccountSections;
const setup = loadScript('setup.js', {flexbuddyAccountSections: sections}).flexbuddySetup;

const settings = {
    weeklyGoal: 400, monthlyGoal: null, vehicleCostMethod: 'STANDARD_MILEAGE', mileageRate: 0.7,
    payoutDays: ['TUESDAY', 'FRIDAY'], payoutLagDays: 1, taxSetAsidePercent: null
};
const row = (state, key) => state.rows.find(r => r.key === key);

test('while the settings load, nothing is done and the review rows say Loading', () => {
    const state = setup.setupState({hasBlocks: false, settings: null});

    assert.strictEqual(state.progress, '0 of 2 done');
    assert.strictEqual(state.complete, false);
    assert.strictEqual(row(state, 'costs').detail, 'Loading…');
    assert.strictEqual(row(state, 'payouts').detail, 'Loading…');
    assert.strictEqual(row(state, 'taxes').detail, 'Loading…');
    assert.strictEqual(row(state, 'block').action, 'Add');
});

test('a block and a goal make the driver set up, and the review rows show the current settings', () => {
    const state = setup.setupState({hasBlocks: true, settings});

    assert.strictEqual(state.complete, true);
    assert.strictEqual(state.progress, "You're set up");
    assert.strictEqual(row(state, 'block').done, true);
    assert.strictEqual(row(state, 'block').action, null);
    assert.strictEqual(row(state, 'costs').detail, 'Standard mileage · $0.70/mi');
    assert.strictEqual(row(state, 'payouts').detail, 'Tue & Fri · paid 1 day after');
});

test('one step done reads 1 of 2', () => {
    assert.strictEqual(setup.setupState({hasBlocks: true, settings: {...settings, weeklyGoal: null}}).progress, '1 of 2 done');
    assert.strictEqual(setup.setupState({hasBlocks: false, settings}).progress, '1 of 2 done');
});

test('a monthly goal alone counts as a goal', () => {
    const state = setup.setupState({hasBlocks: false, settings: {...settings, weeklyGoal: null, monthlyGoal: 1600}});

    assert.strictEqual(row(state, 'goal').done, true);
    assert.strictEqual(state.doneCount, 1);
});

test('actual expenses are shown as Actual expenses', () => {
    const state = setup.setupState({hasBlocks: false, settings: {...settings, vehicleCostMethod: 'ACTUAL_EXPENSES'}});

    assert.strictEqual(row(state, 'costs').detail, 'Actual expenses');
});

test('taxes is an estimate row that is never counted', () => {
    const off = setup.setupState({hasBlocks: true, settings});
    assert.strictEqual(row(off, 'taxes').detail, 'Off · an estimate to help you save, not tax advice');
    assert.strictEqual(row(off, 'taxes').action, 'Set up');

    const on = setup.setupState({hasBlocks: true, settings: {...settings, taxSetAsidePercent: 25}});
    assert.strictEqual(row(on, 'taxes').detail, '25% set aside · estimate, not tax advice');
    assert.strictEqual(row(on, 'taxes').action, 'Change');

    // Leaving taxes off still finishes setup, and turning it on adds nothing to the count.
    for (const state of [off, on]) {
        assert.strictEqual(state.total, 2);
        assert.strictEqual(state.doneCount, 2);
        assert.strictEqual(state.complete, true);
    }
    assert.strictEqual(setup.setupState({hasBlocks: false, settings: {...settings, weeklyGoal: null, taxSetAsidePercent: 25}}).doneCount, 0);
});

test('only the first two rows can be done, and the review rows have no done state', () => {
    const state = setup.setupState({hasBlocks: true, settings});

    for (const key of ['costs', 'payouts', 'taxes']) {
        assert.strictEqual(row(state, key).review, true);
        assert.strictEqual(row(state, key).done, undefined);
    }
});
