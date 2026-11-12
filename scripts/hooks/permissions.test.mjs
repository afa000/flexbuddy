import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const settings = JSON.parse(readFileSync(new URL('../antigravity-cli-settings.json', import.meta.url), 'utf8'));
const patterns = (rules) => rules.filter((rule) => rule.startsWith('command(regex:'))
    .map((rule) => new RegExp(rule.slice('command(regex:'.length, -1)));
const allowed = patterns(settings.permissions.allow);
const denied = patterns(settings.permissions.deny);
const permits = (command) => allowed.some((pattern) => pattern.test(command))
    && !denied.some((pattern) => pattern.test(command));

test('Antigravity permits quoted read-only searches, including Windows-wrapped commands', () => {
    for (const command of [
        "Select-String -Pattern '<html|body>' -Path docs/*.md",
        'Select-String -Pattern "<html|body>" -Path flexbuddy/src/main/resources/templates/*.html',
        '"Select-String -Pattern \'<html|body>\' -Path docs/*.md"',
        "git grep '<html|body>' -- flexbuddy/src/main/resources/templates",
    ]) assert.equal(permits(command), true, command);
});

test('quoted search rules do not allow chains, substitutions or releases', () => {
    for (const command of [
        "Select-String -Pattern '<html|body>' -Path docs/*.md; git push origin main",
        "Select-String -Pattern '<html|body>' -Path docs/*.md | Invoke-Expression",
        'Select-String -Pattern "$(git push origin main)" -Path docs/*.md',
        'Select-String -Pattern "`"; git push origin main; #" -Path docs/*.md',
        "Select-String -Pattern '<html|body>' -Path $(git push origin main)",
        "git grep '<html|body>' -- docs/*.md; git push origin main",
        'git grep -O main',
        'git push origin main',
        'gh pr merge 35',
    ]) assert.equal(permits(command), false, command);
});
