import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

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
