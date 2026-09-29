'use strict';
// The rule as a test: a script must not take a date from toISOString, which is UTC and gives tomorrow's date on a US
// evening. A line that counts UTC days on purpose can say so with the marker "// utc-day".
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {STATIC_JS} = require('./load-script');

const FORBIDDEN = /toISOString\(\)\s*\.(slice\(0,\s*10\)|split\(['"]T['"]\))/;
const MARKER = '// utc-day';

function scripts() {
    return fs.readdirSync(STATIC_JS).filter(name => name.endsWith('.js'));
}

test('the scan finds the scripts', () => {
    // A broken path would otherwise pass silently by scanning nothing.
    assert.ok(scripts().length >= 10, `expected to scan at least 10 scripts in ${STATIC_JS}, found ${scripts().length}`);
});

test('no script derives a date from toISOString', () => {
    const offences = [];
    for (const name of scripts()) {
        fs.readFileSync(path.join(STATIC_JS, name), 'utf8').split(/\r?\n/).forEach((line, index) => {
            if (FORBIDDEN.test(line) && !line.includes(MARKER)) offences.push(`${name}:${index + 1}: ${line.trim()}`);
        });
    }
    assert.deepEqual(offences, [], "Use the device's local date (toIsoDate / flexbuddyCalendar.toIso) instead of "
        + 'toISOString for a date. Offending lines:\n' + offences.join('\n'));
});
