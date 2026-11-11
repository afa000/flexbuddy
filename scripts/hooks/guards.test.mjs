// Tests for the FlexBuddy agent guards. Run from the repository root: node --test "scripts/hooks/*.test.mjs"
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

import {
    catalogRefusal, isCatalogPath, isMigrationPath, migrationRefusal, patchedPaths, releaseRefusal, splitCommands,
    targetsProtected,
} from './guard.mjs';

const onBranch = (name) => () => name;
const refused = (command, branch = 'ai/FB-027') => releaseRefusal(command, onBranch(branch)) !== null;

test('refuses pushes whose refspec targets main', () => {
    for (const command of [
        'git push origin main',
        'git push origin HEAD:main',
        'git push origin ai/FB-027:main',
        'git push origin +main',
        'git push --force origin main',
        'git push -f origin refs/heads/main',
        'git push origin :main',
        'git push --delete origin main',
        'git push --dry-run origin HEAD:main',
        'git -C flexbuddy push origin main',
        '& git push origin main',
        'git.exe push origin main',
    ]) {
        assert.equal(refused(command), true, command);
    }
});

test('refuses --all and --mirror', () => {
    assert.equal(refused('git push --all origin'), true);
    assert.equal(refused('git push --mirror origin'), true);
});

test('refuses a push with no refspec, or of HEAD, while main is checked out', () => {
    assert.equal(refused('git push', 'main'), true);
    assert.equal(refused('git push origin', 'main'), true);
    assert.equal(refused('git push origin HEAD', 'main'), true);
});

test('refuses gh pr merge', () => {
    assert.equal(refused('gh pr merge 34 --squash'), true);
    assert.equal(refused('gh.exe pr merge'), true);
});

test('finds a refused command anywhere in a chain', () => {
    assert.equal(refused('git add . && git commit -m "x" && git push origin main'), true);
    assert.equal(refused('git status; git push origin main'), true);
    assert.equal(refused('git fetch\ngit push origin main'), true);
    assert.equal(refused('FOO=1 git push origin main'), true);
});

test('allows feature-branch pushes and ordinary commands', () => {
    for (const command of [
        'git push -u origin ai/FB-027',
        'git push origin docs/native-app-plan',
        'git push origin HEAD',
        'git push',
        'git push origin main-notes',
        'git push origin ai/FB-027:ai/FB-027',
        'git push -o ci.skip origin ai/FB-027',
        'git status',
        'git log origin/main..HEAD',
        'git fetch origin main',
        'git switch main',
        'git merge origin/main',
        'echo "git push origin main"',
        'gh pr view 34',
        'gh pr create --base main --title "x"',
        'npm run push',
        '',
    ]) {
        assert.equal(refused(command), false, command);
    }
});

test('only asks for the current branch when the push needs it', () => {
    let asked = 0;
    releaseRefusal('git push -u origin ai/FB-027', () => { asked++; return 'main'; });
    assert.equal(asked, 0);
});

test('targetsProtected reads the destination side of a refspec', () => {
    assert.equal(targetsProtected('main:other', null), false);
    assert.equal(targetsProtected('other:main', null), true);
    assert.equal(targetsProtected('HEAD', 'main'), true);
    assert.equal(targetsProtected('HEAD', 'ai/FB-027'), false);
});

test('splitCommands splits on chains and newlines', () => {
    assert.deepEqual(splitCommands('a && b || c; d | e\nf'), ['a', 'b', 'c', 'd', 'e', 'f']);
});

test('recognises migration paths on Windows and POSIX', () => {
    assert.equal(isMigrationPath('C:\\repo\\flexbuddy\\src\\main\\resources\\db\\migration\\V27__language.sql'), true);
    assert.equal(isMigrationPath('flexbuddy/src/main/resources/db/migration/V1__init.sql'), true);
    assert.equal(isMigrationPath('flexbuddy/src/main/resources/db/migration/README.md'), false);
    assert.equal(isMigrationPath('flexbuddy/src/main/resources/application.properties'), false);
    assert.equal(isMigrationPath('docs/V1__notes.sql'), false);
});

test('refuses edits to tracked migrations only', () => {
    const path = 'flexbuddy/src/main/resources/db/migration/V27__language.sql';
    assert.notEqual(migrationRefusal(path, () => true), null);
    assert.equal(migrationRefusal(path, () => false), null);
    assert.equal(migrationRefusal('docs/agent-workflow.md', () => true), null);
    assert.equal(migrationRefusal(undefined, () => true), null);
});

const script = fileURLToPath(new URL('./guard.mjs', import.meta.url));
const run = (mode, input) => spawnSync(process.execPath, [script, mode], { input, encoding: 'utf8' });

test('the script exits 2 with a reason when it refuses, and 0 otherwise', () => {
    const refusedRun = run('release', JSON.stringify({ tool_input: { command: 'git push origin HEAD:main' } }));
    assert.equal(refusedRun.status, 2);
    assert.match(refusedRun.stderr, /deploys production/);

    const allowedRun = run('release', JSON.stringify({ tool_input: { command: 'git status' } }));
    assert.equal(allowedRun.status, 0);
    assert.equal(allowedRun.stderr, '');
});

test('the script fails open on unreadable input', () => {
    assert.equal(run('release', 'not json').status, 0);
    assert.equal(run('migration', '').status, 0);
    assert.equal(run('unknown-mode', '{}').status, 0);
});

const patch = (...lines) => ['*** Begin Patch', ...lines, '*** End Patch'].join('\n');

test('reads the files a Codex patch updates or deletes', () => {
    assert.deepEqual(patchedPaths(patch(
        '*** Update File: flexbuddy/src/main/resources/db/migration/V27__language.sql',
        '@@',
        '-old',
        '+new',
        '*** Add File: flexbuddy/src/main/resources/db/migration/V29__app_tokens.sql',
        '+create table x();',
        '*** Delete File: docs/old.md',
    )), ['flexbuddy/src/main/resources/db/migration/V27__language.sql', 'docs/old.md']);
    assert.deepEqual(patchedPaths(['apply_patch', patch('*** Update File: a.md')]), ['a.md']);
    assert.deepEqual(patchedPaths(undefined), []);
});

test('a Codex patch is never read as shell commands', () => {
    assert.equal(refused(patch('*** Update File: docs/a.md', '+git push origin main')), false);
});

test('refuses whole reads of a message catalog', () => {
    const english = String.raw`C:\repo\flexbuddy\src\main\resources\i18n\messages.properties`;
    const spanish = 'flexbuddy/src/main/resources/i18n/messages_es.properties';
    assert.equal(isCatalogPath(english), true);
    assert.equal(isCatalogPath(spanish), true);
    assert.equal(isCatalogPath('flexbuddy/src/main/resources/application.properties'), false);
    assert.notEqual(catalogRefusal({ file_path: english }), null);
    assert.notEqual(catalogRefusal({ file_path: spanish, offset: 10, limit: 2000 }), null);
    assert.equal(catalogRefusal({ file_path: spanish, offset: 400, limit: 60 }), null);
    assert.equal(catalogRefusal({ file_path: spanish, limit: 200 }), null);
    assert.equal(catalogRefusal({ file_path: 'AGENTS.md' }), null);
    assert.equal(catalogRefusal(undefined), null);
});

test('the script refuses a Codex patch to a tracked migration and allows a new one', () => {
    const root = fileURLToPath(new URL('../..', import.meta.url));
    const tracked = run('migration', JSON.stringify({
        tool_name: 'apply_patch', cwd: root,
        tool_input: { command: patch('*** Update File: flexbuddy/src/main/resources/db/migration/V27__language.sql') },
    }));
    assert.equal(tracked.status, 2);
    const added = run('migration', JSON.stringify({
        tool_name: 'apply_patch', cwd: root,
        tool_input: { command: patch('*** Add File: flexbuddy/src/main/resources/db/migration/V99__new.sql', '+x') },
    }));
    assert.equal(added.status, 0);
});

test('the script refuses a whole catalog read', () => {
    const result = run('catalog', JSON.stringify({ tool_name: 'Read',
        tool_input: { file_path: 'flexbuddy/src/main/resources/i18n/messages.properties' } }));
    assert.equal(result.status, 2);
    assert.match(result.stderr, /Grep for the keys/);
});
