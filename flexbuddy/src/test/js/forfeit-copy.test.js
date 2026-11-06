const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const {englishMessages} = require('./load-script');

// Forfeiting in FlexBuddy only records the status. Both confirmations must say the block still has to be forfeited in
// the Amazon Flex app, in every language the app ships.
const KEYS = ['js.schedule.forfeitMessage', 'js.schedule.lateForfeitMessage'];

/** The raw Spanish value of a key; these messages are single-line, so a line lookup is enough. */
function spanish(key) {
    const file = path.resolve(__dirname, '../../main/resources/i18n/messages_es.properties');
    const line = fs.readFileSync(file, 'utf8').split(/\r?\n/).find(entry => entry.startsWith(`${key}=`));
    return line?.slice(key.length + 1);
}

test('the forfeit confirmations say FlexBuddy only records it and Amazon Flex is where to forfeit', () => {
    const english = englishMessages();
    for (const key of KEYS) {
        assert.match(english[key], /FlexBuddy/, key);
        assert.match(english[key], /Amazon Flex app/, key);
        assert.match(spanish(key), /FlexBuddy/, key);
        assert.match(spanish(key), /app de Amazon Flex/, key);
    }
});

test('the forfeit button records a status, like Mark cancelled', () => {
    const english = englishMessages();
    assert.strictEqual(english['js.schedule.forfeit'], 'Mark forfeited');
    assert.strictEqual(english['js.schedule.forfeitBlock'], 'Mark forfeited');
    assert.strictEqual(spanish('js.schedule.forfeit'), 'Marcar como perdido');
});
