process.env.TZ = 'America/Los_Angeles';
const test = require('node:test');
const assert = require('node:assert');
const {loadScript} = require('./load-script');

const feedback = loadScript('feedback.js', {URL, URLSearchParams}).flexbuddyFeedback;

test('feedbackMailto addresses the support mailbox with the subject', () => {
    const link = feedback.feedbackMailto({buildId: 'abc123', screen: 'Reports', platform: 'Android', installed: true, date: '2026-10-02'});

    assert.ok(link.startsWith('mailto:flexbuddysupport@gmail.com?subject=FlexBuddy%20feedback&body='));
});

test('feedbackMailto fills in the version, screen, device and date, and never a plus sign', () => {
    const link = feedback.feedbackMailto({buildId: 'abc123', screen: 'Reports', platform: 'Android', installed: true, date: '2026-10-02'});
    const body = decodeURIComponent(link.split('&body=')[1]);

    assert.ok(body.includes('App version: abc123'));
    assert.ok(body.includes('Screen: Reports'));
    assert.ok(body.includes('Device: Android · installed app'));
    assert.ok(body.includes('Date: 2026-10-02'));
    assert.ok(body.startsWith('What happened, or what would help?'));
    assert.ok(!link.includes('+'), 'a plus would show literally in some mail apps');
    assert.ok(link.includes('%20'));
});

test('a browser, not an installed app, says so', () => {
    const link = feedback.feedbackMailto({buildId: 'x', screen: 'Home', platform: 'Windows', installed: false, date: '2026-10-02'});

    assert.ok(decodeURIComponent(link).includes('Device: Windows · browser'));
});

test('localIsoDate is the local day, even late on a US evening', () => {
    // 23:30 in Los Angeles on Oct 2 is already Oct 3 in UTC.
    assert.strictEqual(feedback.localIsoDate(new Date(2026, 9, 2, 23, 30)), '2026-10-02');
    assert.strictEqual(feedback.localIsoDate(new Date(2026, 0, 5, 0, 5)), '2026-01-05');
});

test('screenLabel names the screen from the address', () => {
    assert.strictEqual(feedback.screenLabel('/', '?screen=dashboard'), 'Home');
    assert.strictEqual(feedback.screenLabel('/', '?screen=home'), 'Home');
    assert.strictEqual(feedback.screenLabel('/', ''), 'Home');
    assert.strictEqual(feedback.screenLabel('/', '?screen=reports&tab=history'), 'Reports');
    assert.strictEqual(feedback.screenLabel('/', '?screen=schedule'), 'Schedule');
    assert.strictEqual(feedback.screenLabel('/', '?screen=import'), 'Import');
    assert.strictEqual(feedback.screenLabel('/', '?screen=expenses'), 'Expenses');
    assert.strictEqual(feedback.screenLabel('/account', ''), 'Account');
    assert.strictEqual(feedback.screenLabel('/', '?screen=zzz'), 'Other');
});

test('platformLabel recognises the common user agents', () => {
    const android = 'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36';
    const iphone = 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1';
    const ipad = 'Mozilla/5.0 (iPad; CPU OS 17_5 like Mac OS X) AppleWebKit/605.1.15';
    const windows = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36';
    const mac = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15';

    assert.strictEqual(feedback.platformLabel(android), 'Android');
    assert.strictEqual(feedback.platformLabel(iphone), 'iPhone');
    assert.strictEqual(feedback.platformLabel(ipad), 'iPad');
    assert.strictEqual(feedback.platformLabel(windows), 'Windows');
    assert.strictEqual(feedback.platformLabel(mac), 'Mac');
    assert.strictEqual(feedback.platformLabel('curl/8'), 'Other');
    assert.strictEqual(feedback.platformLabel(undefined), 'Other');
});
