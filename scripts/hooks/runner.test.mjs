import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

test('runner accepts namespaced local models and preserves model defaults without launching an agent', {
    skip: process.platform !== 'win32' && 'The runner requires Windows PowerShell',
}, () => {
    const script = [
        "$ErrorActionPreference = 'Stop'",
        "$source = Get-Content scripts/run-agent.ps1 -Raw",
        // Run parameter binding and defaults only; exclude filesystem writes and agent startup.
        "$end = $source.IndexOf('$ErrorActionPreference')",
        "$probe = [scriptblock]::Create($source.Substring(0, $end) + 'Write-Output $Model')",
        "foreach ($model in @('gemma4:12b-32k', 'example/model:latest', 'hf.co/example/model:Q4_K_M')) {",
        "    if ((& $probe -Agent local -TaskId FB-030 -Model $model) -ne $model) { throw 'Local model changed' }",
        "}",
        "if ((& $probe -Agent local -TaskId FB-030) -ne 'gemma4:12b-32k') { throw 'Local default changed' }",
        "if ((& $probe -Agent antigravity -TaskId FB-030 -Model gemini-4-argon-high) -ne 'gemini-4-argon-high') { throw 'Antigravity model changed' }",
        "if ((& $probe -Agent antigravity -TaskId FB-030)) { throw 'Antigravity default changed' }",
        "foreach ($model in @('bad model', 'bad;model', ('bad' + [char]34 + 'model'))) {",
        "    $rejected = $false",
        "    try { & $probe -Agent local -TaskId FB-030 -Model $model | Out-Null }",
        "    catch { $rejected = $true }",
        "    if (-not $rejected) { throw 'Unsafe model accepted' }",
        "}",
    ].join('\n');
    const result = spawnSync('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command', script], {
        cwd: fileURLToPath(new URL('../..', import.meta.url)), encoding: 'utf8',
    });
    assert.equal(result.status, 0, result.stderr || result.stdout);
});

test('runner reports empty answers, refusals and missing handoffs without launching an agent', {
    skip: process.platform !== 'win32' && 'The runner requires Windows PowerShell',
}, () => {
    // Evaluate the real result-processing block with fixtures; never launch an agent or spend quota.
    const script = [
        "$ErrorActionPreference = 'Stop'",
        "$source = Get-Content scripts/run-agent.ps1 -Raw",
        "$start = $source.IndexOf('$answerEmpty =')",
        "$end = $source.IndexOf('@' + [char]34, $start)",
        "$block = [scriptblock]::Create($source.Substring($start, $end - $start))",
        "function Write-Warning([string]$Message) { $script:notices += $Message }",
        "$Agent = 'antigravity'",
        "$TaskId = 'FB-027'",
        "$handoff = '.ai/artifacts/FB-027/nonexistent-handoff-test.md'",
        "$handoffBefore = $null",
        "$toolLog = 'tool soft-denied: edit blocked' + [Environment]::NewLine + 'tool not allowed: shell blocked'",
        "foreach ($response in @('', 'An answer')) {",
        "    $notices = @()",
        "    $output = @{ status = 'SUCCESS'; response = $response; conversation_id = 'FB-027-test-no-transcript' } | ConvertTo-Json",
        "    . $block",
        "    $emptyNotices = @($notices | Where-Object { $_ -like '*produced no answer*' })",
        "    if ($emptyNotices.Count -ne [int]($response -eq '')) { throw 'Incorrect empty-answer warning' }",
        "    if ($refused -notlike '*soft-denied*' -or $refused -notlike '*not allowed*') { throw 'Missing refusal log' }",
        "    if ($handoffNote -ne 'No handoff report was written.') { throw 'Missing handoff was not detected' }",
        "}",
        "$Agent = 'local'",
        "$notices = @()",
        "$output = ' '",
        "$handoff = 'scripts/run-agent.ps1'",
        "$handoffBefore = (Get-Item $handoff).LastWriteTimeUtc",
        ". $block",
        "if ($handoffNote -notlike '*not updated*') { throw 'Unchanged handoff was not detected' }",
        "if (@($notices | Where-Object { $_ -like '*produced no answer*' }).Count -ne 1) { throw 'Blank local answer was not detected' }",
    ].join('\n');
    const result = spawnSync('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command', script], {
        cwd: fileURLToPath(new URL('../..', import.meta.url)), encoding: 'utf8',
    });
    assert.equal(result.status, 0, result.stderr || result.stdout);
});
