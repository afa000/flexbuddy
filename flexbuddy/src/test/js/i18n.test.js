'use strict';
const test = require('node:test');
const assert = require('node:assert');
const {loadScript, englishMessages} = require('./load-script');

test('a key that is not in the messages is shown as the key', () => {
    const {flexbuddyI18n} = loadScript('i18n.js');
    assert.strictEqual(flexbuddyI18n.t('missing.key'), 'missing.key');
});

test('format fills arguments, and a doubled apostrophe is one apostrophe only when arguments are given', () => {
    const {flexbuddyI18n} = loadScript('i18n.js');
    assert.strictEqual(flexbuddyI18n.format("It''s {0}", ['ready']), "It's ready");
    assert.strictEqual(flexbuddyI18n.format("It's", []), "It's");
    assert.strictEqual(flexbuddyI18n.format('{1} before {0}', ['a', 'b']), 'b before a');
    assert.strictEqual(flexbuddyI18n.format('left {2}', ['a']), 'left {2}');
});

test('tn picks the singular for one and the plural for everything else', () => {
    const {tn} = loadScript('i18n.js');
    assert.strictEqual(tn(1, 'js.common.blocks'), '1 block');
    assert.strictEqual(tn(3, 'js.common.blocks'), '3 blocks');
    assert.strictEqual(tn(0, 'js.common.blocks'), '0 blocks');
});

test('appLocale follows the page language with the US region', () => {
    const spanish = loadScript('i18n.js', {document: {documentElement: {lang: 'es', dataset: {}}, getElementById: () => null}});
    assert.strictEqual(spanish.appLocale(), 'es-US');
    assert.strictEqual(new Intl.NumberFormat(spanish.appLocale(), {style: 'currency', currency: 'USD'})
        .format(1234.5), '$1,234.50');
    const british = loadScript('i18n.js', {document: {documentElement: {lang: 'en-GB', dataset: {}}, getElementById: () => null}});
    assert.strictEqual(british.appLocale(), 'en-US');
    assert.strictEqual(loadScript('i18n.js').appLocale(), 'en-US');
});

test('the messages in the page are used when the page carries them', () => {
    const node = {textContent: '{"js.x":"Hola {0}"}'};
    const {t} = loadScript('i18n.js', {document: {documentElement: {dataset: {}}, getElementById: id => (id === 'flexbuddyMessages' ? node : null)}});
    assert.strictEqual(t('js.x', 'Ana'), 'Hola Ana');
});

test('a page whose messages are not valid JSON leaves keys visible instead of breaking', () => {
    const node = {textContent: '{oops'};
    const {t} = loadScript('i18n.js', {document: {documentElement: {dataset: {}}, getElementById: () => node}});
    assert.strictEqual(t('js.x'), 'js.x');
});

test('the English file parses to the same text the server would send', () => {
    const messages = englishMessages();
    assert.strictEqual(messages['js.common.blocks.one'], '{0} block');
    assert.strictEqual(messages['js.common.blocks.other'], '{0} blocks');
});
