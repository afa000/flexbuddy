'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {loadScript} = require('./load-script');

// The stub window has no addEventListener, so only the pure helpers load, which is what is tested here.
const location = {href: 'https://flexbuddy.onrender.com/', origin: 'https://flexbuddy.onrender.com', search: '', pathname: '/'};
const errors = loadScript('errors.js', {location, URL}).flexbuddyErrors;

const base = {
    message: "TypeError: Cannot read properties of null (reading 'x')",
    filename: 'https://flexbuddy.onrender.com/js/app.js',
    line: 10, online: true, sent: 0, seen: new Set()
};

test('a TypeError in one of our own scripts is reported', () => {
    assert.equal(errors.shouldReport(base), true);
});

test('shouldReport ignores what says nothing or is not ours', () => {
    assert.equal(errors.shouldReport({...base, message: 'Script error.'}), false);
    assert.equal(errors.shouldReport({...base, message: ''}), false);
    assert.equal(errors.shouldReport({...base, message: 'ResizeObserver loop completed with undelivered notifications.'}), false);
    assert.equal(errors.shouldReport({...base, filename: 'https://evil.example/js/app.js'}), false);
    assert.equal(errors.shouldReport({...base, filename: 'https://flexbuddy.onrender.com/other/app.js'}), false);
    assert.equal(errors.shouldReport({...base, filename: undefined}), false);
});

test('shouldReport stays quiet offline, after three reports, and for a repeat', () => {
    assert.equal(errors.shouldReport({...base, online: false}), false);
    assert.equal(errors.shouldReport({...base, sent: 3}), false);
    assert.equal(errors.shouldReport({...base, sent: 2}), true);
    const seen = new Set([`${base.message}|${base.filename}|10`]);
    assert.equal(errors.shouldReport({...base, seen}), false);
    assert.equal(errors.shouldReport({...base, seen, line: 11}), true);
});

test('shouldReport skips offline fetch failures and aborted requests', () => {
    const failedFetch = new TypeError('Failed to fetch');
    assert.equal(errors.shouldReport({...base, message: 'Failed to fetch', reason: failedFetch}), false);
    assert.equal(errors.shouldReport({...base, reason: new TypeError('NetworkError when attempting to fetch resource.')}), false);
    assert.equal(errors.shouldReport({...base, reason: new TypeError('Load failed')}), false);
    const aborted = new Error('The user aborted a request.');
    aborted.name = 'AbortError';
    assert.equal(errors.shouldReport({...base, reason: aborted}), false);
    // A TypeError that is not about the network is still ours to report.
    assert.equal(errors.shouldReport({...base, reason: new TypeError("x is not a function")}), true);
});

test('buildReport cuts the message to 300 characters and reduces the filename to its path', () => {
    const report = errors.buildReport({message: 'x'.repeat(500), filename: 'https://flexbuddy.onrender.com/js/app.js?v=123',
        lineno: 7, colno: 3, screen: 'home', buildId: '20261002.1'});

    assert.equal(report.message.length, 300);
    assert.equal(report.source, '/js/app.js');
    assert.equal(report.line, 7);
    assert.equal(report.column, 3);
    assert.equal(report.screen, 'home');
    assert.equal(report.buildId, '20261002.1');
});
