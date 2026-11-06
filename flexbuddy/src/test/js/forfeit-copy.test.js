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
        assert.match(spanish(key), /aplicación de Amazon Flex/, key);
    }
});

test('the late confirmation also opens for a block that is already over, so its Amazon step is conditional', () => {
    assert.match(englishMessages()['js.schedule.lateForfeitMessage'], /if the block is still on your Amazon Flex itinerary/);
    assert.match(spanish('js.schedule.lateForfeitMessage'), /si el bloque sigue en su itinerario de Amazon Flex/);
});

test('the forfeit button records a status, like Mark cancelled', () => {
    const english = englishMessages();
    assert.strictEqual(english['js.schedule.forfeit'], 'Mark forfeited');
    assert.strictEqual(english['js.schedule.forfeitBlock'], 'Mark forfeited');
    assert.strictEqual(english['js.schedule.forfeitThisBlock'], 'Mark this block forfeited?');
    assert.strictEqual(english['js.schedule.forfeitAnyway'], 'Record anyway');
    for (const key of ['js.schedule.forfeit', 'js.schedule.forfeitBlock']) {
        assert.strictEqual(spanish(key), 'Marcar como perdido', key);
    }
    assert.strictEqual(spanish('js.schedule.forfeitAnyway'), 'Registrar de todos modos');
});
