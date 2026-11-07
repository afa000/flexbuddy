const test = require('node:test');
const assert = require('node:assert');
const {loadScript} = require('./load-script');

const review = loadScript('block-review.js').flexbuddyBlockReview;
const fmt = {
    money: value => `$${Number(value).toFixed(2)}`,
    time: value => String(value).slice(0, 5),
    minutes: value => `${Math.floor(value / 60)}h ${value % 60}m`,
    day: iso => `day ${iso}`
};
const block = (extra = {}) => ({id: 7, status: 'COMPLETED', station: 'VEA7', date: '2026-11-03', startTime: '09:00:00',
    endTime: '13:00:00', miles: null, details: {}, ...extra});
const period = (status, extra = {}) => ({payoutDate: '2026-11-10', from: '2026-11-02', to: '2026-11-08', status,
    difference: null, ...extra});
const states = items => [...items.map(item => `${item.key}:${item.state}`)];

test('a block with nothing recorded: times and costs are optional, miles missing, payout waiting', () => {
    const items = review.rows(block(), {expenses: [], periods: [period('UPCOMING')]}, fmt);
    assert.deepStrictEqual(states(items), ['times:optional', 'miles:missing', 'costs:optional', 'payout:waiting']);
    assert.strictEqual(items[0].text, 'Not recorded · optional');
    assert.strictEqual(items[2].text, 'None linked · optional');
    assert.strictEqual(items[3].text, 'Comes with the day 2026-11-10 payout');
    assert.deepStrictEqual([...items[1].actions], ['addMiles', 'noMiles']);
    assert.strictEqual(review.toCheck(items), 1);
    assert.strictEqual(review.summary(items), '1 to check');
});

test('missing, explicitly zero and driven miles read differently, and only missing needs checking', () => {
    const miles = value => review.rows(block({miles: value}), {expenses: [], periods: []}, fmt)[1];
    assert.deepStrictEqual([miles(null).state, miles(null).text], ['missing', 'Not recorded']);
    assert.deepStrictEqual([miles(0).state, miles(0).text], ['zero', 'No miles driven']);
    assert.deepStrictEqual([miles('0.00').state], ['zero']);
    assert.deepStrictEqual([miles(42).state, miles(42).text], ['done', '42.0 mi']);
    assert.deepStrictEqual([...miles(0).actions], ['change']);
});

test('recorded times show the range and the time on the clock', () => {
    const times = review.rows(block({details: {actualStart: '09:02:00', actualEnd: '12:48:00', actualMinutes: 226}}),
        {expenses: [], periods: []}, fmt)[0];
    assert.strictEqual(times.state, 'done');
    assert.strictEqual(times.text, '09:02 – 12:48 · 3h 46m on the clock');
});

test('only tolls and parking count toward the costs row', () => {
    const expenses = [{category: 'TOLL', amount: 3}, {category: 'PARKING', amount: '1.50'}, {category: 'FUEL', amount: 40}];
    const costs = review.rows(block(), {expenses, periods: []}, fmt)[2];
    assert.strictEqual(costs.state, 'done');
    assert.strictEqual(costs.text, '$4.50 in 2 expenses');
    assert.strictEqual(review.rows(block(), {expenses: [expenses[0]], periods: []}, fmt)[2].text, '$3.00 in 1 expense');
    assert.strictEqual(review.rows(block(), {expenses: null, periods: []}, fmt)[2].text, 'Linked expenses could not be loaded.');
});

test('the payout row follows the payout whose period holds the block, and never calls a block paid by itself', () => {
    const payout = (periods, date = '2026-11-03') => review.rows(block({date}), {expenses: [], periods}, fmt)[3];
    assert.deepStrictEqual([payout([period('UNCHECKED')]).state, payout([period('UNCHECKED')]).text],
        ['attention', 'day 2026-11-10 payout not checked yet']);
    assert.strictEqual(payout([period('MATCHED')]).state, 'done');
    const short = payout([period('SHORT', {difference: '-3.00'})]);
    assert.deepStrictEqual([short.state, short.text], ['attention', 'day 2026-11-10 payout was $3.00 short']);
    assert.strictEqual(payout([period('OVER', {difference: '5'})]).text, 'day 2026-11-10 payout was $5.00 more than the blocks earned');
    const old = payout([period('MATCHED')], '2026-10-01');
    assert.deepStrictEqual([old.state, old.text, [...old.actions]], ['optional', 'Check it in Payout history', ['payout']]);
    // The period's first and last days both belong to it.
    assert.strictEqual(review.periodFor('2026-11-02', [period('MATCHED')]).payoutDate, '2026-11-10');
    assert.strictEqual(review.periodFor('2026-11-08', [period('MATCHED')]).payoutDate, '2026-11-10');
    assert.strictEqual(review.periodFor('2026-11-09', [period('MATCHED')]), undefined);
});

test('everything recorded and the payout matched reads "All checked"', () => {
    const items = review.rows(block({miles: 0, details: {actualStart: '09:00', actualEnd: '13:00', actualMinutes: 240}}),
        {expenses: [{category: 'TOLL', amount: 2}], periods: [period('MATCHED')]}, fmt);
    assert.strictEqual(review.toCheck(items), 0);
    assert.strictEqual(review.summary(items), 'All checked');
    assert.strictEqual(review.summary([{state: 'missing'}, {state: 'attention'}, {state: 'optional'}]), '2 to check');
});

test('Home shows the newest worked block from today or yesterday, never a later-dated one or another kind', () => {
    const shifts = [
        block({id: 1, date: '2026-11-05', startTime: '09:00'}),
        block({id: 2, date: '2026-11-05', startTime: '15:00'}),
        block({id: 3, date: '2026-11-06', status: 'SCHEDULED'}),
        block({id: 4, date: '2026-11-06', status: 'FORFEITED'}),
        block({id: 5, date: '2026-11-03'}),
        block({id: 6, date: '2026-11-30'})
    ];
    assert.strictEqual(review.lastBlock(shifts, '2026-11-05', '2026-11-06').id, 2);
    assert.strictEqual(review.lastBlock(shifts, '2026-11-06', '2026-11-07'), undefined);
    assert.strictEqual(review.lastBlock(null, '2026-11-05', '2026-11-06'), undefined);
});
