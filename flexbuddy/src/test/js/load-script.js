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

/**
 * Runs one browser script from static/js in a fresh context and returns that context, whose `window` holds whatever
 * the script exported (for example `context.flexbuddyCalendar`). The stub document answers every query with nothing,
 * so scripts that only touch the page inside their functions load cleanly; scripts that query the page at load time
 * are not loadable here.
 */
function loadScript(name, extra = {}) {
    const document = {
        addEventListener() {}, removeEventListener() {},
        querySelector: () => null, querySelectorAll: () => [],
        documentElement: {dataset: {}}
    };
    const context = {document, navigator: {onLine: true}, console, setTimeout, clearTimeout, ...extra};
    context.window = context;
    vm.createContext(context);
    const file = path.join(STATIC_JS, name);
    vm.runInContext(fs.readFileSync(file, 'utf8'), context, {filename: file});
    return context;
}

module.exports = {loadScript, STATIC_JS};
