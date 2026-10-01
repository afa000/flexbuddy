process.env.TZ = 'America/Los_Angeles';
const test = require('node:test');
const assert = require('node:assert');
const {loadScript} = require('./load-script');

const reports = loadScript('reports.js', {URLSearchParams}).flexbuddyReports;
const base = {preset: 'all', from: '', to: '', station: '', sort: 'date', dir: 'desc'};

test('rangeLabel names each preset and ends with the caret', () => {
    const expected = {
        all: 'All time', week: 'This week', month: 'This month', year: 'This year', '30days': 'Last 30 days',
        payperiod: 'This pay period', lastpayperiod: 'Last pay period'
    };
    for (const [preset, label] of Object.entries(expected)) {
        assert.strictEqual(reports.rangeLabel({...base, preset}), `${label} ▾`);
    }
});

test('rangeLabel shows custom dates, with either end open', () => {
    assert.strictEqual(reports.rangeLabel({...base, preset: 'custom', from: '2026-09-01', to: '2026-09-15'}), 'Sep 1 – Sep 15 ▾');
    assert.strictEqual(reports.rangeLabel({...base, preset: 'custom', from: '2026-09-01'}), 'From Sep 1 ▾');
    assert.strictEqual(reports.rangeLabel({...base, preset: 'custom', to: '2026-09-15'}), 'Until Sep 15 ▾');
    assert.strictEqual(reports.rangeLabel({...base, preset: 'custom'}), 'Custom ▾');
});

test('rangeLabel reads dates as written, never shifted by the time zone', () => {
    assert.strictEqual(reports.rangeLabel({...base, preset: 'custom', from: '2026-10-01', to: '2026-10-31'}), 'Oct 1 – Oct 31 ▾');
});

test('reportParams always names the screen and adds the tab only for History', () => {
    assert.strictEqual(reports.reportParams(base, 'month', 'charts').get('screen'), 'reports');
    assert.strictEqual(reports.reportParams(base, 'month', 'charts').has('tab'), false);
    assert.strictEqual(reports.reportParams(base, 'month', 'history').get('tab'), 'history');
    assert.strictEqual(reports.reportParams(base, 'month', 'history').get('screen'), 'reports');
});

test('reportParams keeps the range, sort and grouping', () => {
    const state = {preset: 'custom', from: '2026-09-01', to: '2026-09-15', station: 'VEA7', sort: 'totalPay', dir: 'asc'};
    const params = reports.reportParams(state, 'station', 'charts');
    assert.deepStrictEqual(Object.fromEntries(params), {
        screen: 'reports', from: '2026-09-01', to: '2026-09-15', station: 'VEA7', sort: 'totalPay', dir: 'asc',
        preset: 'custom', groupBy: 'station'
    });
});

test('reportParams leaves out an empty range and station', () => {
    const params = reports.reportParams(base, 'month', 'charts');
    assert.deepStrictEqual([params.has('from'), params.has('to'), params.has('station')], [false, false, false]);
});

test('parseReportParams finds the History tab and defaults to Charts', () => {
    assert.strictEqual(reports.parseReportParams('?screen=reports&tab=history').tab, 'history');
    assert.strictEqual(reports.parseReportParams('?screen=reports').tab, 'charts');
    assert.strictEqual(reports.parseReportParams('?tab=other').tab, 'charts');
    assert.strictEqual(reports.parseReportParams('').tab, 'charts');
});

test('summaryLine counts the blocks and names the most-worked stations', () => {
    const shifts = [{station: 'VEA7'}, {station: 'DLV2'}, {station: 'VEA7'}, {station: 'DAX5'}];
    assert.strictEqual(reports.summaryLine(shifts), '4 blocks · VEA7, DAX5, DLV2');
    assert.strictEqual(reports.summaryLine([{station: 'VEA7'}]), '1 block · VEA7');
    assert.strictEqual(reports.summaryLine([]), 'No blocks in this range');
});

test('summaryLine folds a long station list', () => {
    const shifts = ['A1', 'B2', 'C3', 'D4', 'E5'].map(station => ({station}));
    assert.strictEqual(reports.summaryLine(shifts), '5 blocks · A1, B2, C3 +2 more');
});
