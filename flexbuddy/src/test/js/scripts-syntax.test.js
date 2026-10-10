'use strict';
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const {STATIC_JS} = require('./load-script');

test('every browser script parses, including scripts loaded only when a dialog opens', () => {
    for (const name of fs.readdirSync(STATIC_JS).filter(name => name.endsWith('.js'))) {
        new vm.Script(fs.readFileSync(path.join(STATIC_JS, name), 'utf8'), {filename: name});
    }
});
