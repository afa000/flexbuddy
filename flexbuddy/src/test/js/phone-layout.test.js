const test = require('node:test');
const assert = require('node:assert');
const {loadScript} = require('./load-script');

const {showsUpcomingPanel} = loadScript('schedule.js').flexbuddySchedule;

test('the Next 14 days panel shows only when something is coming', () => {
    assert.strictEqual(showsUpcomingPanel({next: null, days: []}), false);
    assert.strictEqual(showsUpcomingPanel({next: null, days: [{date: '2026-11-07'}]}), true);
    // The server sets next exactly when days has entries; either one is enough to keep the panel.
    assert.strictEqual(showsUpcomingPanel({next: {id: 1}, days: []}), true);
    assert.strictEqual(showsUpcomingPanel(null), false);
});
