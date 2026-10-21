# Builds .ai/reviews/<task-id>.md from the task, the implementation report, the verification
# summary and the branch's commits. The packet doubles as the pull request description:
#   gh pr create --body-file .ai/reviews/<task-id>.md
# Commit the work and run verify.ps1 first.
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9_-]+$')]
    [string]$TaskId
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

function Invoke-Git {
    $result = git @args
    if ($LASTEXITCODE -ne 0) { throw "git $args failed." }
    $result
}

Push-Location $root
try {
    if (Invoke-Git status --porcelain) {
        throw "Commit or stash changes first: uncommitted edits would be missing from the packet."
    }
    $branch = (Invoke-Git branch --show-current).Trim()
    if ($branch -eq "main") {
        throw "Review packets need a feature branch."
    }

    $task = ".ai/tasks/$TaskId.md"
    $implementation = ".ai/implementations/$TaskId.md"
    $verification = ".ai/artifacts/$TaskId/summary.md"
    if (-not (Test-Path $task)) {
        throw "Task specification not found: $task"
    }

    $taskText = Get-Content $task -Raw -Encoding UTF8
    $implementationText = if (Test-Path $implementation) {
        Get-Content $implementation -Raw -Encoding UTF8
    } else { "Implementation report missing." }

    $head = (Invoke-Git rev-parse --short HEAD).Trim()
    $verificationText = if (Test-Path $verification) {
        $summary = Get-Content $verification -Raw -Encoding UTF8
        if ($summary -notmatch "Commit: $head \(clean\)") {
            $warning = "**Warning: this verification did not run on the current commit ($head)."
            $summary = "$warning Run verify.ps1 again.**`n`n$summary"
        }
        $summary
    } else { "Verification has not been recorded." }

    $commits = (Invoke-Git log --oneline "origin/main..HEAD") -join "`n"
    $diffStats = (Invoke-Git diff --stat "origin/main...HEAD") -join "`n"

    $outputDir = ".ai/reviews"
    New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
    $packet = @"
# FlexBuddy review: $TaskId

Branch: ``$branch`` at ``$head``

## Task

$taskText

## Implementation report

$implementationText

## Verification

$verificationText

## Commits

``````
$commits
``````

## Diff statistics

``````
$diffStats
``````

## Review instructions

Review the actual diff against the task. Prioritize correctness, security, per-user data
isolation, database and backup integrity, earnings regressions, and needless complexity.
"@
    $output = Join-Path $outputDir "$TaskId.md"
    $packet | Set-Content -Encoding UTF8 $output
    Write-Host "Created review packet: $output"
    Write-Host "Check it for secrets before using it as the pull request description."
}
finally {
    Pop-Location
}
