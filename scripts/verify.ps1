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
$mavenEvidence = "- Maven test evidence: default Surefire selection ran in both reactor modules"
$postgresRan = "- PostgreSQL migration test: PASS"
$postgresSkipped = "- PostgreSQL migration test: skipped locally. CI runs it on the pull request."

# Reject direct test overrides while retaining unrelated JVM options, such as a test agent.
$mavenOptions = @($env:MAVEN_ARGS, $env:MAVEN_OPTS, $env:JDK_JAVA_OPTIONS, $env:JAVA_TOOL_OPTIONS, $env:_JAVA_OPTIONS)
foreach ($config in @(".mvn\maven.config", ".mvn\jvm.config")) {
    $path = Join-Path $app $config
    if (Test-Path -LiteralPath $path) {
        $mavenOptions += Get-Content -LiteralPath $path -Raw
    }
}
$testSelection = '(?i)(?:^|\s|["''])-D\s*["'']?(?:test|skipTests|maven\.test\.skip(?:\.exec)?|maven\.test\.failure\.ignore|groups|excludedGroups|surefire\.(?:includes|excludes)(?:File)?|surefire\.(?:include|exclude)JUnit5Engines|junit\.jupiter\.conditions\.deactivate)(?=[\s="''#]|$)'
if (($mavenOptions -join "`n") -match $testSelection) {
    throw "Remove Maven test selection, skip, or failure-ignore options before running full verification."
}

if ($ReusePassing -and $startedClean -and (Test-Path -LiteralPath $summaryPath)) {
    $previous = Get-Content -LiteralPath $summaryPath -Encoding UTF8
    $postgresMatches = ($previous -ccontains $postgresRan) -or
        (-not $runPostgres -and ($previous -ccontains $postgresSkipped))
    if (($previous -ccontains "- Commit: $commit (clean)") -and
        ($previous -ccontains "- Maven verification: PASS") -and
        ($previous -ccontains $mavenEvidence) -and
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
    Set-Content -Encoding UTF8 -LiteralPath (Join-Path $logs $Log) -Value ($output -join [Environment]::NewLine)
    if ($exit -eq 0) { "PASS" } else { "FAIL (exit $exit)" }
}

# Pin Surefire's default scanning rules above settings-profile properties. Keep the
# per-test selector empty so JUnit can discover nested tests through their outer class.
$emptySelection = Join-Path $logs "surefire-empty-selection.txt"
Set-Content -Encoding Ascii -LiteralPath $emptySelection -Value "" -NoNewline
$mavenFlags = @(
    '-B', '-ntp', '-Dstyle.color=never'
    '-Dtest='
    '-Dsurefire.includes=**/Test*.java,**/*Test.java,**/*Tests.java,**/*TestCase.java'
    '-Dsurefire.excludes=**/*$*'
    ('"-Dsurefire.includesFile={0}"' -f $emptySelection)
    ('"-Dsurefire.excludesFile={0}"' -f $emptySelection)
    '-Dgroups=', '-DexcludedGroups='
    '-Dsurefire.includeJUnit5Engines=', '-Dsurefire.excludeJUnit5Engines='
    '-Djunit.jupiter.conditions.deactivate='
    '-DskipTests=false', '-Dmaven.test.skip=false', '-Dmaven.test.skip.exec=false'
    '-Dmaven.test.failure.ignore=false'
) -join ' '

Push-Location $app
try {
    $maven = Invoke-Check "Maven verification" ".\mvnw.cmd $mavenFlags -f reactor/pom.xml verify" "maven.log"
    $js = Invoke-Check "JavaScript tests" "node --test src/test/js/*.test.js" "javascript.log"
}
finally {
    Pop-Location
}

# Read only the current invocation's log. Reports in target may be left over from another run.
$module = $null
$testedModules = @{}
$migrationPassed = $false
$mavenCompleted = $false
$counts = 'Tests run: (?<total>\d+), Failures: 0, Errors: 0, Skipped: (?<skipped>\d+)'
foreach ($line in (Get-Content -LiteralPath (Join-Path $logs "maven.log") -Encoding UTF8)) {
    if ($line -match '^\[INFO\] --- surefire:[^ ]+:test .* @ (?<module>\S+) ---\s*$') {
        $module = $Matches.module
    } elseif ($line -match "^\[INFO\] $counts\s*$" -and $module) {
        $testedModules[$module] = [int]$Matches.total -gt [int]$Matches.skipped
        $module = $null
    } elseif ($line -match "^\[INFO\] $counts, .* -- in com\.angel\.flexbuddy\.migration\.PostgresMigrationTest\s*$") {
        $migrationPassed = [int]$Matches.total -gt 0 -and [int]$Matches.skipped -eq 0
    } elseif ($line -match '^\[INFO\] BUILD SUCCESS\s*$') {
        $mavenCompleted = $true
    }
}
$bothModulesRan = $testedModules['flexbuddy-import-core'] -and $testedModules['flexbuddy']
if ($maven -eq "PASS" -and -not $mavenCompleted) {
    $maven = "FAIL (missing Maven build success)"
} elseif ($maven -eq "PASS" -and -not $bothModulesRan) {
    $maven = "FAIL (missing passing test results for one or both reactor modules)"
} elseif ($maven -eq "PASS" -and $runPostgres -and -not $migrationPassed) {
    $maven = "FAIL (configured PostgreSQL migration test did not pass)"
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
$evidence = if ($bothModulesRan) { $mavenEvidence } else { "- Maven test evidence: incomplete" }
$postgres = if ($migrationPassed) {
    $postgresRan
} elseif ($runPostgres) {
    "- PostgreSQL migration test: no passing result recorded"
} else {
    $postgresSkipped
}

@"
# Verification: $TaskId

- Commit: $commit ($tree)
- Run at: $(Get-Date -Format "yyyy-MM-dd HH:mm")
- Maven verification: $maven
$evidence
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
