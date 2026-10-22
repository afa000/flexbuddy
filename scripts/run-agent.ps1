# Runs Antigravity or the local model (OpenCode + Ollama) on one task without opening either app, and saves the run's
# output to .ai/artifacts/<task-id>/. Run it from the working tree the agent should edit, on the task's branch:
#   .\scripts\run-agent.ps1 -Agent local -TaskId FB-003
#   .\scripts\run-agent.ps1 -Agent antigravity -TaskId FB-003 -Prompt "Only do step 1 of the task's handoff plan."
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('antigravity', 'local')]
    [string]$Agent,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9_-]+$')]
    [string]$TaskId,

    # Replaces the default instruction, which is to implement the task with the implement-task skill.
    [string]$Prompt,

    [string]$Model = 'gemma4:12b-32k',

    [int]$TimeoutMinutes = 30
)

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

if ($Agent -eq 'local') {
    # The default agent asks before every edit and command, which an unattended run can't answer. The headless agent
    # in opencode.json may edit files and run only read-only git and node --test. (Environment-variable overrides
    # lose to the project opencode.json, so the rules live there.)
    $exe = "ollama"
    $arguments = @("launch", "opencode", "--model", $Model, "--", "run", "--standalone", "--agent", "headless",
        "--model", "ollama/$Model")
} else {
    # The Antigravity CLI only reads user-wide rules. On Windows it checks each command wrapped in quotes, so plain
    # prefix rules such as command(git status) never match; scripts/antigravity-cli-settings.json uses regexes. Copy,
    # don't rewrite, that file: the CLI ignores a settings file that starts with a byte-order mark.
    $settings = Join-Path $env:USERPROFILE ".gemini\antigravity-cli\settings.json"
    $wanted = Join-Path $PSScriptRoot "antigravity-cli-settings.json"
    if (-not (Test-Path $settings)) {
        Copy-Item $wanted $settings
        Write-Host "Installed the Antigravity CLI permission rules in $settings"
    } elseif ((Get-Content $settings -Raw | ConvertFrom-Json | ConvertTo-Json -Depth 5 -Compress) -ne
            (Get-Content $wanted -Raw | ConvertFrom-Json | ConvertTo-Json -Depth 5 -Compress)) {
        # Compared as JSON: the CLI rewrites the file with & < > escaped, which is the same rules.
        Write-Warning "$settings differs from scripts/antigravity-cli-settings.json; using it as it is."
    }
    $exe = Join-Path $env:USERPROFILE ".gemini\bin\agy.exe"
    $arguments = @("--mode", "accept-edits", "--print-timeout", "${TimeoutMinutes}m", "--output-format", "text", "-p")
}
$arguments += '"' + ($Prompt -replace '"', '\"') + '"'

Write-Host "Running $Agent on $TaskId in $root (log: .ai/artifacts/$TaskId/agent-$Agent-$stamp.log)"
$out = "$log.out"
$err = "$log.err"
$timer = [Diagnostics.Stopwatch]::StartNew()
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
$commands = ""
if ($Agent -eq 'antigravity') {
    # In unattended mode one refused command ends the whole run, so record which commands it ran.
    $brain = Get-ChildItem (Join-Path $env:USERPROFILE ".gemini\antigravity-cli\brain") -Directory |
        Sort-Object LastWriteTime | Select-Object -Last 1
    $transcript = Join-Path $brain.FullName ".system_generated\logs\transcript.jsonl"
    if (Test-Path $transcript) {
        $commands = (Select-String -Path $transcript -Pattern 'CommandLine":"\\"(.*?)\\""' -AllMatches |
            ForEach-Object { $_.Matches } |
            ForEach-Object { "- " + ($_.Groups[1].Value -replace '\\\\\\\\', '\' -replace '\\\\\\"', '"') }) -join "`n"
    }
    if ((Get-Content $err -Raw) -match 'cannot prompt for') {
        Write-Warning "Antigravity stopped at a command its rules don't allow; it is the last one listed in the log."
    }
}
@"
# $Agent on $TaskId, $stamp

Branch: $branch
Elapsed: $elapsed s
$(if ($Agent -eq 'local') { "Model: $Model, context while running: $(if ($context) { $context } else { 'not seen' })" })

## Prompt

$Prompt

## Output

$(Get-Content $out -Raw)

## Tool log

$(Get-Content $err -Raw)
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
