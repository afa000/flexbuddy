// PreToolUse guards for FlexBuddy (FB-027), shared by Claude Code (.claude/settings.json) and Codex (.codex/hooks.json).
// They back up AGENTS.md rules mechanically:
//   node guard.mjs release    - refuses a git push to main, git push --all/--mirror and gh pr merge (they deploy)
//   node guard.mjs migration  - refuses an edit to a Flyway migration that git already tracks (it has run in
//                               production), from Claude Code's file tools or a Codex apply_patch
//   node guard.mjs catalog    - refuses reading a whole message catalog; grep it for the keys instead
// A refusal exits with code 2 and a one-line reason on stderr. Anything unreadable exits 0, so a broken hook never
// blocks ordinary work. Nothing here prints the command or file contents beyond the refused part.

import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const PROTECTED_BRANCH = 'main';
const PREFIX_TOKENS = new Set(['&', 'call', 'command', 'env', 'sudo', 'exec']);
const PUSH_OPTIONS_WITH_VALUE = new Set(['-o', '--push-option', '--receive-pack', '--exec']);
const GIT_OPTIONS_WITH_VALUE = new Set(['-C', '-c', '--git-dir', '--work-tree', '--namespace']);

/** Finds executable $(...) expressions, respecting quotes, escapes and nested parentheses. */
function commandSubstitutions(text, body = false) {
    const commands = [];
    let quote = null;
    for (let i = 0; i < text.length; i++) {
        const char = text[i];
        if (quote !== "'" && (char === '\\' || char === '`')) {
            i++;
            continue;
        }
        if (!body && (char === '"' || char === "'")) {
            if (!quote) quote = char;
            else if (quote === char) quote = null;
            continue;
        }
        if (quote === "'" || text.slice(i, i + 2) !== '$(') continue;
        const start = i + 2;
        let depth = 1;
        let innerQuote = null;
        let end = start;
        for (; end < text.length; end++) {
            const inner = text[end];
            if (innerQuote !== "'" && (inner === '\\' || inner === '`')) {
                end++;
                continue;
            }
            if (inner === '"' || inner === "'") {
                if (!innerQuote) innerQuote = inner;
                else if (innerQuote === inner) innerQuote = null;
            }
            if (innerQuote) continue;
            if (inner === '(') depth++;
            if (inner === ')' && --depth === 0) break;
        }
        if (depth === 0) {
            commands.push(text.slice(start, end));
            i = end;
        }
    }
    return commands;
}

/** Drops literal heredoc/here-string text, retaining commands in expanding bodies. */
export function stripHeredocs(command) {
    const kept = [];
    let until = null;
    let expanding = false;
    let body = [];
    for (const line of String(command).split(/\r?\n/)) {
        if (until) {
            if (until.test(line)) {
                if (expanding) kept.push(...commandSubstitutions(body.join('\n'), true));
                body = [];
                until = null;
            } else {
                body.push(line);
            }
            continue;
        }
        kept.push(line);
        // Only a << outside quotes starts a heredoc; quoted text such as "a << b" must not hide the lines after it.
        const unquoted = line.replace(/(?<!<<-?\s*)(["'])(?:(?!\1).)*\1/g, '""');
        const heredoc = unquoted.match(/<<-?\s*(['"]?)([A-Za-z_][A-Za-z0-9_]*)\1/);
        if (heredoc) {
            until = new RegExp(`^\\s*${heredoc[2]}\\s*$`);
            expanding = !heredoc[1];
        } else if (/@(['"])\s*$/.test(line)) {
            until = new RegExp(`^${line.trim().slice(-1)}@`);
            expanding = line.trim().endsWith('"');
            kept[kept.length - 1] = line.replace(/@(['"])\s*$/, '');
        }
    }
    if (until && expanding) kept.push(...commandSubstitutions(body.join('\n'), true));
    return kept.join('\n');
}

/**
 * Splits a shell command into the commands chained by &&, ||, ;, | and newlines, outside quotes only, so quoted text
 * that mentions a push is never read as one. Commands inside $( ) are returned as well, since the shell runs them.
 */
export function splitCommands(command) {
    const text = stripHeredocs(command);
    const parts = [];
    let current = '';
    let quote = null;
    for (let i = 0; i < text.length; i++) {
        const char = text[i];
        // Bash backslash and PowerShell backtick continuations join one logical command, including CRLF input.
        if (quote !== "'" && (char === '\\' || char === '`')) {
            const newline = text.slice(i + 1).match(/^\r?\n/);
            if (newline) {
                i += newline[0].length;
                continue;
            }
        }
        if (quote) {
            if (char === '\\' && quote === '"' && i + 1 < text.length) {
                current += char + text[++i];
                continue;
            }
            if (char === quote) quote = null;
            current += char;
            continue;
        }
        if (char === '\\' && i + 1 < text.length && text[i + 1] !== '\n') {
            current += char + text[++i];
            continue;
        }
        if (char === '"' || char === "'") {
            quote = char;
            current += char;
            continue;
        }
        const pair = text.slice(i, i + 2);
        if (pair === '&&' || pair === '||') {
            parts.push(current);
            current = '';
            i++;
            continue;
        }
        if (char === ';' || char === '|' || char === '\n' || char === '\r') {
            parts.push(current);
            current = '';
            continue;
        }
        current += char;
    }
    parts.push(current);
    const substitutions = commandSubstitutions(text).flatMap((command) => splitCommands(command));
    return [...parts, ...substitutions].map((part) => part.trim()).filter(Boolean);
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
    // A Codex patch is file content, not commands; a doc line that mentions git push must not trip the guard.
    if (/^\*\*\* Begin Patch/m.test(String(command))) return null;
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
        let repoOption = false;
        for (let j = i + 1; j < tokens.length; j++) {
            const word = tokens[j];
            if (word === '--all' || word === '--mirror') {
                return `git push ${word} would push ${PROTECTED_BRANCH}, which deploys production. Push one feature branch, or ask the owner.`;
            }
            if (word === '--repo') {
                // --repo <remote> names the remote, so every positional argument after it is a refspec.
                repoOption = true;
                j++;
            } else if (word.startsWith('--repo=')) {
                repoOption = true;
            } else if (PUSH_OPTIONS_WITH_VALUE.has(word)) {
                j++;
            } else if (!word.startsWith('-')) {
                positional.push(word);
            }
        }

        const refspecs = repoOption ? positional : positional.slice(1);
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

/** The files a Codex apply_patch updates or deletes ("*** Update File: <path>", "*** Delete File: <path>"). */
export function patchedPaths(patch) {
    const text = Array.isArray(patch) ? patch.join('\n') : String(patch ?? '');
    return [...text.matchAll(/^\*\*\* (?:Update|Delete) File: (.+?)\s*$/gm)].map((match) => match[1]);
}

const CATALOG_READ_LIMIT = 200;

/** Whether a path is one of the message catalogs (i18n/messages*.properties). */
export function isCatalogPath(path) {
    return /(^|\/)i18n\/messages[^/]*\.properties$/i.test(String(path).replace(/\\/g, '/'));
}

/** Returns the reason a Read must be refused, or null: a catalog read needs a limit of CATALOG_READ_LIMIT or fewer. */
export function catalogRefusal(toolInput) {
    if (!toolInput || !isCatalogPath(toolInput.file_path ?? '')) return null;
    const limit = Number(toolInput.limit);
    if (Number.isFinite(limit) && limit > 0 && limit <= CATALOG_READ_LIMIT) return null;
    return `The message catalogs are about 100 KB. Grep for the keys you need, or Read with a limit of ${CATALOG_READ_LIMIT} lines or fewer.`;
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

/**
 * Runs one guard on the hook payload from stdin and sets the exit code. Exported for the Codex launcher in
 * .codex/hooks.json, which finds the repository root with git and imports this file, so the hook works from any folder
 * and in bash, cmd and PowerShell alike.
 */
export function runHook(mode) {
    process.exitCode = main(mode);
}

function main(mode) {
    let input;
    try {
        // Windows PowerShell can pipe text with a UTF-8 byte-order mark, which JSON.parse rejects.
        input = JSON.parse(readFileSync(0, 'utf8').replace(/^﻿/, ''));
    } catch {
        return 0;
    }
    const toolInput = input?.tool_input ?? {};
    const cwd = input?.cwd || process.cwd();
    let reason = null;
    if (mode === 'release') {
        reason = releaseRefusal(toolInput.command ?? '', (gitDir) => gitCurrentBranch(gitDir ? resolve(cwd, gitDir) : cwd));
    } else if (mode === 'migration') {
        const paths = input?.tool_name === 'apply_patch' || /^\*\*\* Begin Patch/m.test(String(toolInput.command ?? ''))
            ? patchedPaths(toolInput.command ?? toolInput.patch).map((path) => resolve(cwd, path))
            : [toolInput.file_path ?? toolInput.notebook_path];
        reason = paths.map((path) => migrationRefusal(path, gitTracks)).find(Boolean) ?? null;
    } else if (mode === 'catalog') {
        reason = catalogRefusal(toolInput);
    }
    if (!reason) return 0;
    process.stderr.write(`Refused by FlexBuddy's agent guard (scripts/hooks/guard.mjs): ${reason}\n`);
    return 2;
}

const runDirectly = process.argv[1]
    && resolve(fileURLToPath(import.meta.url)).toLowerCase() === resolve(process.argv[1]).toLowerCase();
if (runDirectly) {
    process.exitCode = main(process.argv[2]);
}
