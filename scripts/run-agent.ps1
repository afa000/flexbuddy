# Runs Antigravity or the local model (OpenCode + Ollama) on one task without opening either app, and saves the run's
# output to .ai/artifacts/<task-id>/. Run it from the working tree the agent should edit, on the task's branch:
#   .\scripts\run-agent.ps1 -Agent local -TaskId FB-003
#   .\scripts\run-agent.ps1 -Agent antigravity -TaskId FB-003 -Prompt "Only do step 1 of the task's handoff plan."
#   .\scripts\run-agent.ps1 -Agent antigravity -TaskId FB-003 -Model gemini-4-argon-high
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('antigravity', 'local')]
    [string]$Agent,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9_-]+$')]
    [string]$TaskId,

    # Replaces the default instruction, which is to implement the task with the implement-task skill.
    [string]$Prompt,

    # An Ollama model for -Agent local (default gemma4:12b-32k), or an Antigravity model id from `agy models` for
    # -Agent antigravity, such as gemini-4-argon-high (default: your own agy default).
    [ValidatePattern('^[A-Za-z0-9_.:-]*$')]
    [string]$Model,

    [int]$TimeoutMinutes = 30
)
if ($Agent -eq 'local' -and -not $Model) { $Model = 'gemma4:12b-32k' }

$ErrorActionPreference = "Stop"
$root = (git rev-parse --show-toplevel).Trim() -replace '/', '\'
$logs = Join-Path $root ".ai\artifacts\$TaskId"
New-Item -ItemType Directory -Force -Path $logs | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$log = Join-Path $logs "agent-$Agent-$stamp.log"

$branch = (git -C $root branch --show-current).Trim()
if ($branch -eq "main") {
    throw "Agents never work on main. Switch to ai/$TaskId first."
}
if ($branch -ne "ai/$TaskId") {
    Write-Warning "On branch $branch, not ai/$TaskId."
}
if (-not (Test-Path (Join-Path $root ".ai\tasks\$TaskId.md"))) {
    throw "Task specification not found: .ai/tasks/$TaskId.md"
}

if (-not $Prompt) {
    $checks = if ($Agent -eq 'local') {
        'You cannot run verify.ps1 here; run the JavaScript tests with: node --test flexbuddy/src/test/js/*.test.js'
    } else {
        'Run the tests the task needs.'
    }
    $Prompt = "Use the implement-task skill for $TaskId. The task is in .ai/tasks/$TaskId.md and you are on its " +
        "branch. $checks Write .ai/implementations/$TaskId.md before you stop. Do not commit or push."
}
# Antigravity's October runs spent most of their time searching for facts the spec already gave, and a refused command
# is now skipped rather than fatal, so say both up front.
$Prompt += " The task file already lists the files to read and the facts you need: read those first and search only " +
    "for what it doesn't say. If a command is refused, don't retry it; note it in the handoff report and carry on."
if ($Agent -eq 'antigravity') {
    # The terminal sandbox can read this working tree but not write to it, and has no network. Tests write build
    # output, so they have to leave the sandbox, which the allow rules approve.
    $Prompt += " Terminal commands run in a sandbox that can read this repository but not write to it: make every " +
        "file change with your file-editing tools, not the terminal. Run tests outside the sandbox (BypassSandbox), " +
        "from the repository root and on their own: .\scripts\verify.ps1 -TaskId $TaskId for the full check, or " +
        "node --test flexbuddy/src/test/js/<name>.test.js for one script's tests."
}

if ($Agent -eq 'local') {
    # The default agent asks before every edit and command, which an unattended run can't answer. The headless agent
    # in opencode.json may edit files and run only read-only git and node --test. (Environment-variable overrides
    # lose to the project opencode.json, so the rules live there.)
    $exe = "ollama"
    $arguments = @("launch", "opencode", "--model", $Model, "--", "run", "--standalone", "--agent", "headless",
        "--model", "ollama/$Model")
} else {
    # scripts/antigravity-cli-settings.json applies to this run only (--settings); its allow and deny lists replace
    # the ones in ~/.gemini/antigravity-cli/settings.json. On Windows the CLI checks each command wrapped in quotes,
    # so plain prefix rules such as command(git status) never match and the rules use regexes. The sandbox refuses
    # to start with a relative path in a file rule, so those are made absolute here. WriteAllText writes no
    # byte-order mark, which the CLI won't read.
    $rootForRules = $root -replace '\\', '/'
    $rules = (Get-Content (Join-Path $PSScriptRoot "antigravity-cli-settings.json") -Raw) -replace
        '(read_file|write_file)\((?![A-Za-z]:|/|~)', "`$1($rootForRules/"
    $settings = Join-Path $logs "agent-$Agent-$stamp.settings.json"
    [IO.File]::WriteAllText($settings, $rules)
    $exe = Join-Path $env:USERPROFILE ".gemini\bin\agy.exe"
    $arguments = @("--settings", "`"$settings`"", "--mode", "accept-edits", "--print-timeout", "${TimeoutMinutes}m",
        "--output-format", "json")
    if ($Model) { $arguments += @("--model", $Model) }
    $arguments += "-p"
}
$arguments += '"' + ($Prompt -replace '"', '\"') + '"'

Write-Host "Running $Agent on $TaskId in $root (log: .ai/artifacts/$TaskId/agent-$Agent-$stamp.log)"
$out = "$log.out"
$err = "$log.err"
$timer = [Diagnostics.Stopwatch]::StartNew()
$handoff = Join-Path $root ".ai\implementations\$TaskId.md"
$handoffBefore = if (Test-Path $handoff) { (Get-Item $handoff).LastWriteTimeUtc } else { $null }
$process = Start-Process -FilePath $exe -ArgumentList $arguments -WorkingDirectory $root -NoNewWindow -PassThru `
    -RedirectStandardOutput $out -RedirectStandardError $err
$context = $null
while (-not $process.HasExited -and $timer.Elapsed.TotalMinutes -lt $TimeoutMinutes) {
    Start-Sleep -Seconds 10
    if ($Agent -eq 'local' -and -not $context) {
        $loaded = ollama ps | Select-String ([regex]::Escape($Model))
        if ($loaded) { $context = ($loaded.Line -split '\s{2,}')[4] }
    }
}
if (-not $process.HasExited) {
    $process.Kill()
    Write-Warning "Stopped after $TimeoutMinutes minutes."
}
$elapsed = [int]$timer.Elapsed.TotalSeconds

$changes = (git -C $root status --short) -join "`n"
# Both CLIs write UTF-8; Windows PowerShell would read it as the ANSI code page.
$output = Get-Content $out -Raw -Encoding UTF8
$toolLog = Get-Content $err -Raw -Encoding UTF8
$answerEmpty = [string]::IsNullOrWhiteSpace([string]$output)
$commands = ""
if ($Agent -eq 'antigravity') {
    $result = $null
    try { $result = $output | ConvertFrom-Json } catch { }
    if ($result) {
        $answerEmpty = [string]::IsNullOrWhiteSpace([string]$result.response)
        $output = "Status: $($result.status)`n`n$($result.response)"
        # Record which commands it ran, so a refused or wasted command is easy to find. The conversation id finds
        # this run's transcript even when another run is going at the same time.
        $transcript = Join-Path $env:USERPROFILE `
            ".gemini\antigravity-cli\brain\$($result.conversation_id)\.system_generated\logs\transcript.jsonl"
        if (Test-Path $transcript) {
            # Each argument is itself JSON-encoded, so CommandLine is decoded twice.
            $commands = (Get-Content $transcript -Encoding UTF8 | ForEach-Object { ($_ | ConvertFrom-Json).tool_calls } |
                Where-Object { $_.name -eq 'run_command' } |
                ForEach-Object {
                    $where = if ($_.args.BypassSandbox -eq 'true') { ' (outside the sandbox)' } else { '' }
                    "- " + ($_.args.CommandLine | ConvertFrom-Json) + $where
                }) -join "`n"
        }
    }
    if ($toolLog -match 'cannot prompt for') {
        Write-Warning "Antigravity stopped at a command its rules don't allow; it is the last one listed in the log."
    }
}
# Every tool the agent was refused, from either CLI's notices on stderr (Antigravity soft-denies and carries on).
$refused = (($toolLog -split "`r?`n") | Where-Object { $_ -match '(?i)\b(soft-)?denied\b|cannot prompt for|not allowed' } |
    ForEach-Object { "- " + $_.Trim() }) -join "`n"
$handoffAfter = if (Test-Path $handoff) { (Get-Item $handoff).LastWriteTimeUtc } else { $null }
$handoffNote = if (-not $handoffAfter) { "No handoff report was written." }
    elseif ($handoffAfter -ne $handoffBefore) { "Handoff report written: .ai/implementations/$TaskId.md" }
    else { "The handoff report was not updated during this run." }
if ($answerEmpty) {
    Write-Warning "The agent produced no answer. Check the refused tools and the tool log in the run's log file."
}
if ($handoffAfter -eq $handoffBefore) { Write-Warning $handoffNote }
@"
# $Agent on $TaskId, $stamp

Branch: $branch
Elapsed: $elapsed s
$(if ($Agent -eq 'local') { "Model: $Model, context while running: $(if ($context) { $context } else { 'not seen' })" } else { "Model: $(if ($Model) { $Model } else { 'agy default' })" })

## Prompt

$Prompt

## Output

$output

## Handoff

$handoffNote

## Refused tools

$(if ($refused) { $refused } else { "(none reported)" })

## Tool log

$toolLog
$(if ($commands) { "`n## Commands run`n`n$commands`n" })
## Working tree afterwards

$changes
"@ | Set-Content -Encoding UTF8 $log
Remove-Item $out, $err

Write-Host "Finished in $elapsed s."
if ($Agent -eq 'local' -and $context -and $context -ne '32768') {
    Write-Warning "The model ran with a $context context, not 32768; OpenCode can't use its tools below that."
}
Write-Host "Changed files:"
Write-Host $(if ($changes) { $changes } else { "(none)" })
