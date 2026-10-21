# Runs the Maven and browser-script checks for one task and writes a summary to
# .ai/artifacts/<task-id>/. Run from anywhere: .\scripts\verify.ps1 -TaskId FB-001
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9_-]+$')]
    [string]$TaskId
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$app = Join-Path $root "flexbuddy"
$logs = Join-Path $root ".ai\artifacts\$TaskId"
New-Item -ItemType Directory -Force -Path $logs | Out-Null

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
    $maven = Invoke-Check "Maven verification" ".\mvnw.cmd -B -ntp verify" "maven.log"
    $js = Invoke-Check "JavaScript tests" "node --test src/test/js/*.test.js" "javascript.log"
}
finally {
    Pop-Location
}

$commit = (git -C $root rev-parse --short HEAD).Trim()
$tree = if (git -C $root status --porcelain) { "with uncommitted changes" } else { "clean" }
$postgres = if ($env:FLEXBUDDY_TEST_POSTGRES_URL) {
    "ran in the Maven verification against $env:FLEXBUDDY_TEST_POSTGRES_URL"
} else {
    "skipped locally (FLEXBUDDY_TEST_POSTGRES_* not set); CI runs it on the pull request"
}

@"
# Verification: $TaskId

- Commit: $commit ($tree)
- Run at: $(Get-Date -Format "yyyy-MM-dd HH:mm")
- Maven verification: $maven
- JavaScript tests: $js
- PostgreSQL migration test: $postgres

Logs: .ai/artifacts/$TaskId/
"@ | Set-Content -Encoding UTF8 (Join-Path $logs "summary.md")

if ($maven -ne "PASS" -or $js -ne "PASS") {
    throw "Verification failed. See .ai/artifacts/$TaskId/ for the logs."
}
Write-Host "All executed checks passed. Summary: .ai/artifacts/$TaskId/summary.md"
