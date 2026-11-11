// Claude Code PreToolUse guards for FlexBuddy (FB-027). They back up two AGENTS.md rules mechanically:
//   node guard.mjs release    - refuses a git push to main, git push --all/--mirror and gh pr merge (they deploy)
//   node guard.mjs migration  - refuses an edit to a Flyway migration that git already tracks (it has run in production)
// A refusal exits with code 2 and a one-line reason on stderr. Anything unreadable exits 0, so a broken hook never
// blocks ordinary work. Nothing here prints the command or file contents beyond the refused part.

import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const PROTECTED_BRANCH = 'main';
const PREFIX_TOKENS = new Set(['&', 'call', 'command', 'env', 'sudo', 'exec']);
const PUSH_OPTIONS_WITH_VALUE = new Set(['-o', '--push-option', '--repo', '--receive-pack', '--exec']);
const GIT_OPTIONS_WITH_VALUE = new Set(['-C', '-c', '--git-dir', '--work-tree', '--namespace']);

/** Splits a shell command into the commands chained by &&, ||, ;, | and newlines. */
export function splitCommands(command) {
    return String(command).split(/\|\||&&|[;|\r\n]/).map((part) => part.trim()).filter(Boolean);
}

/** Splits one command into words, dropping the quotes around quoted words. */
export function tokenize(segment) {
    return (segment.match(/"[^"]*"|'[^']*'|\S+/g) || []).map((word) => word.replace(/^(["'])(.*)\1$/, '$2'));
}

function programName(token) {
    return token.replace(/\\/g, '/').split('/').pop().toLowerCase().replace(/\.exe$/, '');
}

/** Drops leading words such as PowerShell's & or VAR=value that don't change which program runs. */
function stripPrefix(tokens) {
    let i = 0;
    while (i < tokens.length && (PREFIX_TOKENS.has(tokens[i].toLowerCase()) || /^[A-Za-z_][A-Za-z0-9_]*=/.test(tokens[i]))) {
        i++;
    }
    return tokens.slice(i);
}

/** Whether a push refspec writes to the protected branch: main, HEAD:main, x:main, +main, refs/heads/main, :main. */
export function targetsProtected(refspec, currentBranch) {
    const spec = refspec.replace(/^\+/, '');
    const destination = spec.includes(':') ? spec.slice(spec.lastIndexOf(':') + 1) : spec;
    if (destination === PROTECTED_BRANCH || destination === `refs/heads/${PROTECTED_BRANCH}`) return true;
    return !spec.includes(':') && spec === 'HEAD' && currentBranch === PROTECTED_BRANCH;
}

/**
 * Returns the reason a command must be refused, or null. currentBranch(gitDir) gives the branch checked out where the
 * push would run; it is only asked for when the push names no refspec.
 */
export function releaseRefusal(command, currentBranch) {
    for (const segment of splitCommands(command)) {
        const tokens = stripPrefix(tokenize(segment));
        if (tokens.length === 0) continue;
        const program = programName(tokens[0]);

        if (program === 'gh' && tokens[1] === 'pr' && tokens[2] === 'merge') {
            return 'gh pr merge deploys production. The owner merges pull requests themselves.';
        }
        if (program !== 'git') continue;

        let i = 1;
        let gitDir = null;
        while (i < tokens.length && tokens[i].startsWith('-')) {
            const option = tokens[i];
            if (GIT_OPTIONS_WITH_VALUE.has(option)) {
                if (option === '-C') gitDir = tokens[i + 1] ?? null;
                i += 2;
            } else {
                i += 1;
            }
        }
        if (tokens[i] !== 'push') continue;

        const positional = [];
        for (let j = i + 1; j < tokens.length; j++) {
            const word = tokens[j];
            if (word === '--all' || word === '--mirror') {
                return `git push ${word} would push ${PROTECTED_BRANCH}, which deploys production. Push one feature branch, or ask the owner.`;
            }
            if (PUSH_OPTIONS_WITH_VALUE.has(word)) {
                j++;
            } else if (!word.startsWith('-')) {
                positional.push(word);
            }
        }

        const refspecs = positional.slice(1);
        const branch = () => currentBranch(gitDir);
        if (refspecs.length === 0) {
            if (branch() === PROTECTED_BRANCH) {
                return `git push from ${PROTECTED_BRANCH} deploys production. The owner pushes ${PROTECTED_BRANCH} themselves.`;
            }
            continue;
        }
        if (refspecs.some((refspec) => targetsProtected(refspec, refspec === 'HEAD' ? branch() : null))) {
            return `Pushing to ${PROTECTED_BRANCH} deploys production. The owner pushes ${PROTECTED_BRANCH} themselves.`;
        }
    }
    return null;
}

/** Whether a path is a Flyway migration file (db/migration/V<n>__*.sql). */
export function isMigrationPath(path) {
    return /(^|\/)db\/migration\/V\d+__[^/]*\.sql$/i.test(String(path).replace(/\\/g, '/'));
}

/** Returns the reason an edit must be refused, or null. isTracked(path) says whether git already tracks the file. */
export function migrationRefusal(path, isTracked) {
    if (!path || !isMigrationPath(path) || !isTracked(path)) return null;
    return 'This migration is already committed and has run in production. Never edit it; add the next V<n>__*.sql instead.';
}

function gitCurrentBranch(cwd) {
    try {
        return execFileSync('git', ['rev-parse', '--abbrev-ref', 'HEAD'], { cwd, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim();
    } catch {
        return null;
    }
}

function gitTracks(path) {
    const directory = dirname(path);
    if (!existsSync(directory)) return false;
    try {
        execFileSync('git', ['ls-files', '--error-unmatch', '--', path], { cwd: directory, stdio: 'ignore' });
        return true;
    } catch {
        return false;
    }
}

function main(mode) {
    let input;
    try {
        input = JSON.parse(readFileSync(0, 'utf8'));
    } catch {
        return 0;
    }
    const toolInput = input?.tool_input ?? {};
    const cwd = input?.cwd || process.cwd();
    let reason = null;
    if (mode === 'release') {
        reason = releaseRefusal(toolInput.command ?? '', (gitDir) => gitCurrentBranch(gitDir ? resolve(cwd, gitDir) : cwd));
    } else if (mode === 'migration') {
        reason = migrationRefusal(toolInput.file_path ?? toolInput.notebook_path, gitTracks);
    }
    if (!reason) return 0;
    process.stderr.write(`Refused by FlexBuddy's Claude Code hook: ${reason}\n`);
    return 2;
}

const runDirectly = process.argv[1]
    && resolve(fileURLToPath(import.meta.url)).toLowerCase() === resolve(process.argv[1]).toLowerCase();
if (runDirectly) {
    process.exitCode = main(process.argv[2]);
}
