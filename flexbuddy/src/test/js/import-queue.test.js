const test = require('node:test');
const assert = require('node:assert');
const {loadScript} = require('./load-script');

const queue = loadScript('import-queue.js').flexbuddyImportQueue;

const png = (name, size = 1000) => ({name, size, type: 'image/png'});
let ids = 0;
const makeId = () => `id-${++ids}`;
const shortDate = date => `day ${date}`;

/** A batch as the screen would make it: files and their byte signatures. */
function batch(files, signatures = files.map(file => file.name)) {
    return queue.createItems(files, signatures, makeId).items;
}

function saved(item, block) {
    item.state = 'saved';
    item.saved = {shiftId: 40 + item.number, kind: 'added', ...block};
}

const vea7 = {station: 'VEA7', date: '2026-11-03', startTime: '09:00', endTime: '13:00'};

test('mixed files: a bad file gets its reason and the others are still read in order', () => {
    const items = batch([png('a.png'), {name: 'notes.pdf', size: 10, type: 'application/pdf'},
        png('huge.png', 6 * 1024 * 1024), png('b.png')]);

    assert.deepStrictEqual([...items.map(item => item.state)], ['waiting', 'invalid', 'invalid', 'waiting']);
    assert.strictEqual(queue.rowText(items[1], undefined, shortDate), 'Choose a PNG or JPEG screenshot.');
    assert.strictEqual(queue.rowText(items[2], undefined, shortDate), 'The screenshot must be 5 MB or smaller.');
    assert.strictEqual(queue.nextToRead(items).name, 'a.png');
    items[0].state = 'ready';
    assert.strictEqual(queue.nextToRead(items).name, 'b.png');
});

test('more than ten files start nothing', () => {
    const files = Array.from({length: 11}, (_, index) => png(`${index}.png`));
    const result = queue.createItems(files, files.map(file => file.name), makeId);
    assert.strictEqual(result.items, undefined);
    assert.strictEqual(result.problemKey, 'js.importQueue.tooMany');
    assert.strictEqual(queue.createItems(files.slice(0, 10), [], makeId).items.length, 10);
});

test('the same screenshot picked twice is read once and says which one it repeats', () => {
    const items = batch([png('a.png'), png('b.png'), png('a copy.png')], ['hash-a', 'hash-b', 'hash-a']);
    assert.strictEqual(items[2].state, 'repeat');
    assert.strictEqual(items[2].repeatOf, 1);
    assert.strictEqual(queue.rowText(items[2], undefined, shortDate), 'Same screenshot as #1');
    items[0].state = 'ready';
    items[1].state = 'ready';
    assert.strictEqual(queue.nextToRead(items), undefined);
});

test('every file has its own request ids, fixed for the whole batch', () => {
    const items = batch([png('a.png'), png('b.png')]);
    const all = items.flatMap(item => [item.requestId, item.statusRequestId]);
    assert.strictEqual(new Set(all).size, 4);
});

test('a save interrupted and retried is one request: a server keyed by request id keeps one block', () => {
    const [item] = batch([png('a.png'), png('b.png')]);
    const server = new Map();
    const create = (key, body) => {
        if (!server.has(key)) server.set(key, {id: server.size + 1, ...body});
        return server.get(key);
    };
    // The first answer is lost; the driver taps Add again, which sends the same item's id.
    create(item.requestId, {basePay: 80});
    const answer = create(item.requestId, {basePay: 95});
    assert.strictEqual(server.size, 1);
    assert.strictEqual(answer.basePay, 80);
    assert.strictEqual(queue.sameAsSent({...vea7, status: 'COMPLETED', basePay: 95, tips: 0, miles: null},
        {...vea7, status: 'COMPLETED', basePay: 80, tips: 0, miles: null}), false);
});

test('the answer for a block holding what was sent counts as the same, server time format included', () => {
    const sent = {...vea7, station: 'VEA7 ', status: 'COMPLETED', basePay: 80, tips: 4.5, miles: null};
    const shift = {...vea7, station: 'vea7', startTime: '09:00:00', endTime: '13:00:00', status: 'COMPLETED',
        basePay: '80.00', tips: 4.5, miles: null};
    assert.strictEqual(queue.sameAsSent(sent, shift), true);
    assert.strictEqual(queue.sameAsSent({...sent, miles: 12}, shift), false);
});

test('reviewing goes to the next read file after the current one, and wraps round', () => {
    const items = batch([png('a.png'), png('b.png'), png('c.png'), png('d.png')]);
    items[0].state = 'ready';
    items[2].state = 'ready';
    items[3].state = 'reading';
    assert.strictEqual(queue.nextToReview(items).number, 1);
    assert.strictEqual(queue.nextToReview(items, 1).number, 3);
    assert.strictEqual(queue.nextToReview(items, 3).number, 1);
    items[0].state = 'saved';
    items[2].state = 'skipped';
    assert.strictEqual(queue.nextToReview(items, 3), undefined);
});

test('a busy server or no answer pauses reading and can be tried again; a refused screenshot moves on', () => {
    assert.deepStrictEqual({...queue.readFailure(503)}, {retry: true, pause: true});
    assert.deepStrictEqual({...queue.readFailure(0)}, {retry: true, pause: true});
    assert.deepStrictEqual({...queue.readFailure(400)}, {retry: false, pause: false});
    assert.deepStrictEqual({...queue.readFailure(500)}, {retry: true, pause: false});

    const items = batch([png('a.png'), png('b.png')]);
    Object.assign(items[0], {state: 'failed', retry: true, error: 'The reader is busy. Try again in a moment.'});
    assert.strictEqual(queue.rowText(items[0], undefined, shortDate), 'The reader is busy. Try again in a moment.');
    assert.deepStrictEqual([...queue.rowActions(items[0], undefined)], ['retry']);
    assert.deepStrictEqual([...queue.rowActions({...items[0], retry: false}, undefined)], []);
    assert.strictEqual(queue.headline(items, undefined, true), 'Reading paused. Continue when you\'re ready.');
});

test('a screenshot read before another file saved the same block is caught by the batch check', () => {
    // Screenshot 2 was read while screenshot 1 was still under review, so its server notices know nothing of it.
    const items = batch([png('a.png'), png('b.png')]);
    saved(items[0], {...vea7, status: 'COMPLETED'});
    items[1].state = 'ready';
    items[1].preview = {shifts: [{duplicates: []}]};

    const match = queue.savedMatch({...vea7, status: 'COMPLETED'}, items, items[1]);
    assert.strictEqual(match.kind, 'EXACT');
    assert.strictEqual(match.shiftId, 41);
    assert.strictEqual(match.message, 'Screenshot 1 in this import already added this block.');
});

test('the pay screenshot of a block another file added as scheduled offers to complete it', () => {
    const items = batch([png('accepted.png'), png('earnings.png')]);
    saved(items[0], {...vea7, status: 'SCHEDULED'});
    items[1].state = 'ready';

    const completing = queue.savedMatch({...vea7, startTime: '09:10', status: 'COMPLETED'}, items, items[1]);
    assert.strictEqual(completing.kind, 'SCHEDULED_MATCH');
    assert.strictEqual(completing.message, 'Screenshot 1 in this import added this block as scheduled.');
    // Marked scheduled again, it is only "already added".
    assert.strictEqual(queue.savedMatch({...vea7, status: 'SCHEDULED'}, items, items[1]).kind, 'EXACT');

    queue.noteCompleted(items, 41);
    assert.strictEqual(queue.savedMatch({...vea7, status: 'COMPLETED'}, items, items[1]).kind, 'EXACT');
    // The row still says what that screenshot saved.
    assert.strictEqual(queue.rowText(items[0], 2, shortDate), 'Added as scheduled · VEA7 · day 2026-11-03');
});

test('the batch check uses the server rule: 30 minutes of overlap, overnight blocks, station containment', () => {
    const items = batch([png('a.png'), png('b.png')]);
    saved(items[0], {station: 'VEA7', date: '2026-11-03', startTime: '22:00', endTime: '02:00', status: 'COMPLETED'});
    const current = items[1];
    const check = values => queue.savedMatch({status: 'COMPLETED', ...values}, items, current);

    assert.ok(check({station: 'VEA7 Bakersfield', date: '2026-11-04', startTime: '01:00', endTime: '04:00'}));
    assert.strictEqual(check({station: 'VEA7', date: '2026-11-04', startTime: '01:40', endTime: '04:00'}), undefined);
    assert.strictEqual(check({station: 'DCK6', date: '2026-11-03', startTime: '22:00', endTime: '02:00'}), undefined);
    assert.ok(check({station: '', date: '2026-11-03', startTime: '22:00', endTime: '02:00'}));
    assert.strictEqual(check({station: 'VEA7', date: '', startTime: '22:00', endTime: '02:00'}), undefined);
    // A file is never its own duplicate.
    assert.strictEqual(queue.savedMatch({...items[0].saved}, items, items[0]), undefined);
});

test('stopping keeps the saved files and ends every other one; partial success says what saved', () => {
    const items = batch([png('a.png'), png('b.png'), png('c.png'), png('d.png'), {name: 'x.gif', size: 1, type: 'image/gif'}]);
    saved(items[0], {...vea7, status: 'COMPLETED'});
    items[1].state = 'ready';
    items[2].state = 'reading';
    assert.strictEqual(queue.hasUnsaved(items), true);

    queue.stop(items);
    assert.deepStrictEqual([...items.map(item => item.state)], ['saved', 'stopped', 'stopped', 'stopped', 'invalid']);
    assert.strictEqual(queue.hasUnsaved(items), false);
    assert.strictEqual(queue.isFinished(items), true);
    assert.strictEqual(queue.headline(items, undefined, false), 'Added 1 of 5 screenshots.');
    assert.strictEqual(queue.rowText(items[0], undefined, shortDate), 'Added as completed · VEA7 · day 2026-11-03');
    assert.deepStrictEqual([...queue.rowActions(items[0], undefined)], ['open']);
    assert.strictEqual(queue.rowText(items[1], undefined, shortDate), 'Not imported');
});

test('a file whose save got no answer is not called skipped', () => {
    const [item] = batch([png('a.png'), png('b.png')]);
    Object.assign(item, {state: 'skipped', uncertain: true});
    assert.match(queue.rowText(item, undefined, shortDate), /^Not confirmed: it may have been added/);
});

test('rows and headline follow the file under review', () => {
    const items = batch([png('a.png'), png('b.png'), png('c.png')]);
    items[0].state = 'ready';
    items[1].state = 'ready';
    items[2].state = 'reading';
    assert.strictEqual(queue.headline(items, 1, false), 'Screenshot 1 of 3');
    assert.strictEqual(queue.rowText(items[0], 1, shortDate), 'Reviewing now');
    assert.strictEqual(queue.rowText(items[1], 1, shortDate), 'Ready to review');
    assert.strictEqual(queue.rowText(items[2], 1, shortDate), 'Reading…');
    assert.deepStrictEqual([...queue.rowActions(items[0], 1)], []);
    assert.deepStrictEqual([...queue.rowActions(items[1], 1)], ['review']);
    assert.strictEqual(queue.headline(items, undefined, false), 'Screenshot 3 of 3');

    saved(items[1], {...vea7, status: 'SCHEDULED', changed: true});
    assert.strictEqual(queue.rowText(items[1], 1, shortDate),
        'Added as scheduled · VEA7 · day 2026-11-03. It was saved before the connection dropped, so later changes weren\'t applied.');
    saved(items[0], {...vea7, status: 'COMPLETED', kind: 'completed'});
    assert.strictEqual(queue.rowText(items[0], 1, shortDate), 'Completed the scheduled block · VEA7 · day 2026-11-03');
});
