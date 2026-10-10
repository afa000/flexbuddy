'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {loadScript} = require('./load-script');

test('changing language clears cached content and preserves unsent edits; sign-out still clears edits', async () => {
    const removed = [];
    let clearedOutbox = 0;
    const context = loadScript('pwa.js', {
        addEventListener() {},
        caches: {
            keys: async () => ['flexbuddy-pages-v1', 'flexbuddy-data-v1', 'flexbuddy-static-v1'],
            delete: async key => { removed.push(key); }
        },
        flexbuddyOutbox: {clear: async () => { clearedOutbox++; }}
    });
    await context.flexbuddyPwa.clearUserData({preserveOutbox: true});
    assert.deepEqual(removed, ['flexbuddy-pages-v1', 'flexbuddy-data-v1']);
    assert.equal(clearedOutbox, 0);
    await context.flexbuddyPwa.clearUserData();
    assert.equal(clearedOutbox, 1);
});
