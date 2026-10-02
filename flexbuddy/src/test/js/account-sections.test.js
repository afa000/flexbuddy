process.env.TZ = 'America/Los_Angeles';
const test = require('node:test');
const assert = require('node:assert');
const {loadScript} = require('./load-script');

const sections = loadScript('account-sections.js').flexbuddyAccountSections;
const base = {
    vehicleCostMethod: 'STANDARD_MILEAGE', mileageRate: 0.7, weeklyGoal: null, monthlyGoal: null,
    taxSetAsidePercent: null, payoutDays: ['TUESDAY', 'FRIDAY'], payoutLagDays: 1,
    remindBeforeMinutes: 60, remindConfirm: true, remindMiles: false
};

test('sectionForHash maps the old anchors and the section ids', () => {
    assert.strictEqual(sections.sectionForHash('#goals'), 'costs');
    assert.strictEqual(sections.sectionForHash('#taxes'), 'taxes-section');
    assert.strictEqual(sections.sectionForHash('#payouts'), 'payouts-section');
    assert.strictEqual(sections.sectionForHash('#backup'), 'backup');
    assert.strictEqual(sections.sectionForHash('#reminders'), 'reminders');
    assert.strictEqual(sections.sectionForHash('#account'), 'account');
    assert.strictEqual(sections.sectionForHash('#costs'), 'costs');
});

test('sectionForHash gives null for anything else', () => {
    assert.strictEqual(sections.sectionForHash('#nope'), null);
    assert.strictEqual(sections.sectionForHash(''), null);
    assert.strictEqual(sections.sectionForHash(undefined), null);
});

test('costs summary names the method, the rate and the goal', () => {
    assert.strictEqual(sections.summaries({...base, weeklyGoal: 400}, {}).costs, 'Standard mileage · $0.70/mi · Goal $400/wk');
    assert.strictEqual(sections.summaries({...base, vehicleCostMethod: 'ACTUAL_EXPENSES', monthlyGoal: 1600}, {}).costs,
        'Actual expenses · Goal $1,600/mo');
    assert.strictEqual(sections.summaries(base, {}).costs, 'Standard mileage · $0.70/mi');
    // A weekly goal wins when both are set.
    assert.strictEqual(sections.summaries({...base, weeklyGoal: 400, monthlyGoal: 1600}, {}).costs.endsWith('Goal $400/wk'), true);
});

test('taxes summary shows the percentage and the reminders, or Off', () => {
    assert.strictEqual(sections.summaries({...base, taxSetAsidePercent: 25}, {remindTax: true}).taxes, '25% set aside · reminders on');
    assert.strictEqual(sections.summaries({...base, taxSetAsidePercent: 27.5}, {remindTax: false}).taxes, '27.5% set aside');
    assert.strictEqual(sections.summaries({...base, taxSetAsidePercent: null}, {remindTax: true}).taxes, 'Off');
});

test('payouts summary joins the days and counts the lag', () => {
    assert.strictEqual(sections.summaries(base, {}).payouts, 'Tue & Fri · paid 1 day after');
    assert.strictEqual(sections.summaries({...base, payoutLagDays: 2}, {}).payouts, 'Tue & Fri · paid 2 days after');
    assert.strictEqual(sections.summaries({...base, payoutDays: ['FRIDAY', 'MONDAY']}, {}).payouts, 'Mon & Fri · paid 1 day after');
});

test('reminders summary gives the lead time and the two switches', () => {
    assert.strictEqual(sections.summaries(base, {}).reminders, '1 hour before · confirm on · miles off');
    assert.strictEqual(sections.summaries({...base, remindBeforeMinutes: 30}, {}).reminders.startsWith('30 min before'), true);
    assert.strictEqual(sections.summaries({...base, remindBeforeMinutes: 120}, {}).reminders.startsWith('2 hours before'), true);
    assert.strictEqual(sections.summaries({...base, remindBeforeMinutes: 720}, {}).reminders.startsWith('12 hours before'), true);
    assert.strictEqual(sections.summaries({...base, remindBeforeMinutes: null, remindConfirm: false, remindMiles: true}, {}).reminders,
        'No reminder before blocks · confirm off · miles on');
});

test('backup summary shows the local date of the stored instant', () => {
    assert.strictEqual(sections.summaries(base, {lastBackupAt: null}).backup, 'Never backed up');
    assert.strictEqual(sections.summaries(base, {}).backup, 'Never backed up');
    // 02:00 UTC on Sep 15 is still the evening of Sep 14 in Los Angeles.
    assert.strictEqual(sections.summaries(base, {lastBackupAt: '2026-09-15T02:00:00Z'}).backup, 'Last backup: Sep 14');
});

test('account summary is the name and email, and unloaded settings say so', () => {
    const result = sections.summaries(null, {displayName: 'Angel', email: 'angel@example.com'});
    assert.strictEqual(result.account, 'Angel · angel@example.com');
    assert.strictEqual(result.costs, 'Loading…');
    assert.strictEqual(result.backup, 'Never backed up');
});

test('costMethod is the method part of the costs summary on its own', () => {
    assert.strictEqual(sections.costMethod({vehicleCostMethod: 'STANDARD_MILEAGE', mileageRate: 0.7}), 'Standard mileage · $0.70/mi');
    assert.strictEqual(sections.costMethod({vehicleCostMethod: 'ACTUAL_EXPENSES', mileageRate: 0.7}), 'Actual expenses');
    assert.strictEqual(typeof sections.payouts, 'function');
});
