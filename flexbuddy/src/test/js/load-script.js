'use strict';
// Loads a browser script from static/js into a Node vm context, unchanged.
//
// The cross-realm catch: objects made inside the loaded script (a Date from parseIso, an array, a plain object) come
// from the context's realm. They are not `instanceof Date` in the test file, and assert.deepStrictEqual treats them as
// different from literals made in the test. So compare primitives or ISO strings, or compare
// JSON.parse(JSON.stringify(result)) against the literal.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const STATIC_JS = path.resolve(__dirname, '../../main/resources/static/js');
const MESSAGES = path.resolve(__dirname, '../../main/resources/i18n/messages.properties');

/** Reads the escapes of a .properties value: \n, \t, \\, \uXXXX, or an escaped character. */
function unescapeProperty(value) {
    return value.replace(/\\(u[0-9a-fA-F]{4}|.)/g, (match, escape) => {
        if (escape[0] === 'u' && escape.length === 5) return String.fromCharCode(parseInt(escape.slice(1), 16));
        return {n: '\n', t: '\t', r: '\r', f: '\f'}[escape] ?? escape;
    });
}

/** True when the line ends in an odd number of backslashes, which continues it on the next line. */
function continues(line) {
    const trailing = /\\+$/.exec(line);
    return trailing !== null && trailing[0].length % 2 === 1;
}

/** The English text the scripts show: the js.* entries of the real messages file, parsed the way Java reads it. */
function englishMessages() {
    const messages = {};
    const lines = fs.readFileSync(MESSAGES, 'utf8').split(/\r?\n/);
    for (let i = 0; i < lines.length; i++) {
        let line = lines[i].replace(/^\s+/, '');
        if (!line || line.startsWith('#') || line.startsWith('!')) continue;
        while (continues(line) && i + 1 < lines.length) {
            line = line.slice(0, -1) + lines[++i].replace(/^\s+/, '');
        }
        const separator = line.search(/[=:]/);
        if (separator < 0) continue;
        const key = line.slice(0, separator).trim();
        if (key.startsWith('js.')) messages[key] = unescapeProperty(line.slice(separator + 1).replace(/^\s+/, ''));
    }
    return messages;
}

/**
 * Runs one browser script from static/js in a fresh context and returns that context, whose `window` holds whatever
 * the script exported (for example `context.flexbuddyCalendar`). The stub document answers every query with nothing,
 * so scripts that only touch the page inside their functions load cleanly; scripts that query the page at load time
 * are not loadable here. i18n.js runs first with the real English messages, so t() and tn() work in every test.
 */
function loadScript(name, extra = {}) {
    const document = {
        addEventListener() {}, removeEventListener() {},
        querySelector: () => null, querySelectorAll: () => [], getElementById: () => null,
        documentElement: {dataset: {}}
    };
    const context = {document, navigator: {onLine: true}, console, setTimeout, clearTimeout, ...extra};
    context.window = context;
    context.flexbuddyMessages = englishMessages();
    vm.createContext(context);
    for (const script of ['i18n.js', name]) {
        const file = path.join(STATIC_JS, script);
        vm.runInContext(fs.readFileSync(file, 'utf8'), context, {filename: file});
    }
    return context;
}

module.exports = {loadScript, englishMessages, STATIC_JS};
