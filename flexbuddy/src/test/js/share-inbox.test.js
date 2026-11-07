const test = require('node:test');
const assert = require('node:assert');
const {loadScript} = require('./load-script');

const ORIGIN = 'http://localhost:8080';
const NOW = 1_800_000_000_000;
const MINUTE = 60 * 1000;

/** Cache Storage with one cache, as much of it as share-inbox.js uses. */
function fakeCaches() {
    const entries = new Map();
    const cache = {
        async keys() {
            return [...entries.keys()].map(url => new Request(url));
        },
        async match(request) {
            return entries.get(typeof request === 'string' ? new URL(request, ORIGIN).href : request.url)?.clone();
        },
        async delete(request) {
            return entries.delete(typeof request === 'string' ? new URL(request, ORIGIN).href : request.url);
        },
        async put(path, response) {
            entries.set(new URL(path, ORIGIN).href, response);
        }
    };
    return {entries, cache, caches: {open: async () => cache}};
}

/** A share as sw.js keeps it: one entry per file, or the single key an older sw.js used when `index` is null. */
async function share(cache, id, names, sharedAt, {single = false} = {}) {
    for (const [index, name] of names.entries()) {
        const path = single ? `/share-inbox/${id}` : `/share-inbox/${id}/${index}`;
        await cache.put(path, new Response(new Blob([name], {type: 'image/png'}), {
            headers: {'Content-Type': 'image/png', 'X-File-Name': encodeURIComponent(name), 'X-Shared-At': String(sharedAt)}
        }));
    }
}

function load(store) {
    return loadScript('share-inbox.js', {caches: store.caches, URL, Request, Response, Blob, File}).flexbuddyShareInbox;
}

const A = '11111111-1111-4111-8111-111111111111';
const B = '22222222-2222-4222-8222-222222222222';

test('every file of a share comes back in the order shared, once', async () => {
    const store = fakeCaches();
    await share(store.cache, A, ['week 1.png', 'week 2.png', 'week 3.png'], NOW - MINUTE);
    const inbox = load(store);

    const files = await inbox.take(A, NOW);
    assert.deepStrictEqual([...files.map(file => file.name)], ['week 1.png', 'week 2.png', 'week 3.png']);
    assert.strictEqual(files[0].type, 'image/png');
    assert.strictEqual(await files[1].text(), 'week 2.png');
    assert.strictEqual((await inbox.take(A, NOW)).length, 0);
    assert.strictEqual(store.entries.size, 0);
});

test('a share kept by the older service worker under one key is still read', async () => {
    const store = fakeCaches();
    await share(store.cache, A, ['old.png'], NOW - MINUTE, {single: true});
    assert.deepStrictEqual([...(await load(store).take(A, NOW)).map(file => file.name)], ['old.png']);
});

test('a share older than ten minutes is deleted, not opened', async () => {
    const store = fakeCaches();
    await share(store.cache, A, ['stale.png'], NOW - 11 * MINUTE);
    await share(store.cache, B, ['fresh.png'], NOW - 2 * MINUTE);
    const inbox = load(store);

    assert.strictEqual((await inbox.take(A, NOW)).length, 0);
    assert.deepStrictEqual([...store.entries.keys()], [`${ORIGIN}/share-inbox/${B}/0`]);
    assert.strictEqual(await inbox.hasWaiting(NOW), true);
    assert.strictEqual(await inbox.hasWaiting(NOW + 9 * MINUTE), false);
    assert.strictEqual(store.entries.size, 0);
});

test('after sign-in the newest waiting share opens, and only it', async () => {
    const store = fakeCaches();
    await share(store.cache, A, ['earlier.png'], NOW - 5 * MINUTE);
    await share(store.cache, B, ['latest 1.png', 'latest 2.png'], NOW - MINUTE);
    const inbox = load(store);

    assert.deepStrictEqual([...(await inbox.takeNewest(NOW)).map(file => file.name)], ['latest 1.png', 'latest 2.png']);
    assert.deepStrictEqual([...(await inbox.takeNewest(NOW)).map(file => file.name)], ['earlier.png']);
    assert.strictEqual((await inbox.takeNewest(NOW)).length, 0);
});

test('an id that is not a share id, or no Cache Storage at all, opens nothing', async () => {
    const store = fakeCaches();
    await share(store.cache, A, ['a.png'], NOW);
    const inbox = load(store);
    assert.strictEqual((await inbox.take('unavailable', NOW)).length, 0);
    assert.strictEqual((await inbox.take('../share-inbox', NOW)).length, 0);
    assert.strictEqual(store.entries.size, 1);

    const without = loadScript('share-inbox.js', {URL}).flexbuddyShareInbox;
    assert.strictEqual((await without.takeNewest(NOW)).length, 0);
    assert.strictEqual(await without.hasWaiting(NOW), false);
});

test('anything else in the inbox cache is cleared away', async () => {
    const store = fakeCaches();
    await store.cache.put('/share-inbox/not-a-share', new Response('x', {headers: {'X-Shared-At': String(NOW)}}));
    await share(store.cache, A, ['a.png'], NOW);
    assert.strictEqual(await load(store).hasWaiting(NOW), true);
    assert.deepStrictEqual([...store.entries.keys()], [`${ORIGIN}/share-inbox/${A}/0`]);
});
