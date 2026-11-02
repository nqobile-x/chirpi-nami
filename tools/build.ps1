param([string]$Task = 'assembleDebug')

$root = Split-Path $PSScriptRoot -Parent
# Uses JAVA_HOME if set; otherwise falls back to an installed Temurin JDK 21.
if (-not $env:JAVA_HOME) {
    $jdk = Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
}
$log = Join-Path $root 'build-log.txt'

$mutex = New-Object System.Threading.Mutex($false, 'Global\ChirpyNamiGradleBuild')
$hasLock = $false
try {
    $hasLock = $mutex.WaitOne(3000)
    Push-Location $root
    & .\gradlew.bat $Task --console=plain --no-daemon 2>&1 | Out-File -FilePath $log -Encoding utf8
    $code = $LASTEXITCODE
} finally {
    Pop-Location
    if ($hasLock) { $mutex.ReleaseMutex() }
}

$raw = [IO.File]::ReadAllText($log)
$errors = ($raw -split "\r?\n") | Where-Object { $_ -match '^e: |: error: |error: |^\s+symbol:|What went wrong|FAILED' } | Select-Object -Unique
if ($code -eq 0) { "BUILD SUCCESSFUL ($Task)" } else { "BUILD FAILED ($Task)"; $errors | Select-Object -First 60 }
exit $code
