# Runs the Maven and browser-script checks for one task and writes a summary to
# .ai/artifacts/<task-id>/. Run from anywhere: .\scripts\verify.ps1 -TaskId FB-001
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9_-]+$')]
    [string]$TaskId,
    [switch]$ReusePassing
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$app = Join-Path $root "flexbuddy"
$logs = Join-Path $root ".ai\artifacts\$TaskId"
$summaryPath = Join-Path $logs "summary.md"
New-Item -ItemType Directory -Force -Path $logs | Out-Null

function Read-Git {
    $result = git -C $root @args
    if ($LASTEXITCODE -ne 0) { throw "git $args failed." }
    $result
}

$commit = (Read-Git rev-parse HEAD).Trim()
$startedClean = -not (Read-Git status --porcelain --untracked-files=normal)
$runPostgres = $env:FLEXBUDDY_TEST_POSTGRES_URL -cmatch '^jdbc:postgresql://.+$'
$postgresRan = "- PostgreSQL migration test: enabled in the Maven verification"
$postgresSkipped = "- PostgreSQL migration test: skipped locally. CI runs it on the pull request."
if ($ReusePassing -and $startedClean -and (Test-Path -LiteralPath $summaryPath)) {
    $previous = Get-Content -LiteralPath $summaryPath -Encoding UTF8
    $postgresMatches = ($previous -ccontains $postgresRan) -or
        (-not $runPostgres -and ($previous -ccontains $postgresSkipped))
    if (($previous -ccontains "- Commit: $commit (clean)") -and
        ($previous -ccontains "- Maven verification: PASS") -and
        ($previous -ccontains "- JavaScript tests: PASS") -and $postgresMatches) {
        Write-Host "Reusing passing verification for $commit. Summary: .ai/artifacts/$TaskId/summary.md"
        return
    }
}

"Verification started for $commit. Results are pending." |
    Set-Content -Encoding UTF8 -LiteralPath $summaryPath

# Windows PowerShell 5.1 turns each stderr line of a native command into an error, and "Stop"
# would end the script at Maven's first warning. cmd merges stderr into stdout first, so only
# the exit code decides the result.
function Invoke-Check([string]$Name, [string]$Command, [string]$Log) {
    Write-Host "Running $Name..."
    cmd /c "$Command 2>&1" | Tee-Object -Variable output | Out-Host
    $exit = $LASTEXITCODE
    $output | Set-Content -Encoding UTF8 (Join-Path $logs $Log)
    if ($exit -eq 0) { "PASS" } else { "FAIL (exit $exit)" }
}

Push-Location $app
try {
    $maven = Invoke-Check "Maven verification" ".\mvnw.cmd -B -ntp -f reactor/pom.xml verify" "maven.log"
    $js = Invoke-Check "JavaScript tests" "node --test src/test/js/*.test.js" "javascript.log"
}
finally {
    Pop-Location
}

$finishedCommit = (Read-Git rev-parse HEAD).Trim()
$finishedClean = -not (Read-Git status --porcelain --untracked-files=normal)
$tree = if ($finishedCommit -ne $commit) {
    "HEAD changed during verification"
} elseif ($startedClean -and $finishedClean) {
    "clean"
} else {
    "with uncommitted changes"
}
$postgres = if ($runPostgres) { $postgresRan } else { $postgresSkipped }

@"
# Verification: $TaskId

- Commit: $commit ($tree)
- Run at: $(Get-Date -Format "yyyy-MM-dd HH:mm")
- Maven verification: $maven
- JavaScript tests: $js
$postgres

Logs: .ai/artifacts/$TaskId/
"@ | Set-Content -Encoding UTF8 -LiteralPath $summaryPath

if ($maven -ne "PASS" -or $js -ne "PASS") {
    throw "Verification failed. See .ai/artifacts/$TaskId/ for the logs."
}
if ($finishedCommit -ne $commit -or ($startedClean -and -not $finishedClean)) {
    throw "The commit or working tree changed during verification. Rerun the checks on the final commit."
}
Write-Host "All executed checks passed. Summary: .ai/artifacts/$TaskId/summary.md"
