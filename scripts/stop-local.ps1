[CmdletBinding()]
param()

. (Join-Path $PSScriptRoot 'lib\common.ps1')

$root = Get-ProjectRoot
$pidFile = Join-Path $root '.local-run\pids.json'

if (-not (Test-Path $pidFile)) {
    Write-Info 'No PID file found; this script has nothing it started. Nothing was stopped.'
    exit 0
}

$json = Get-Content $pidFile -Raw | ConvertFrom-Json
$records = @($json | ForEach-Object { $_ })
$refused = $false

foreach ($record in $records) {
    $process = Get-CimInstance Win32_Process -Filter "ProcessId=$($record.pid)" -ErrorAction SilentlyContinue
    if (-not $process) {
        Write-Warn "PID $($record.pid) ($($record.kind)) is already gone."
        continue
    }

    # A PID can be reused; refuse to stop when the command line does not match.
    $expected = switch ($record.kind) {
        'gateway' { 'bootcamp-gateway' }
        'course' { 'bootcamp-course' }
        'user' { 'bootcamp-user' }
        'learning' { 'bootcamp-learning' }
        'trade' { 'bootcamp-trade' }
        'marketing' { 'bootcamp-marketing' }
        'student' { 'dev:student' }
        'admin' { 'dev:admin' }
        default { '' }
    }
    if ($expected -and $process.CommandLine -notlike "*$expected*") {
        Write-Err "PID $($record.pid) no longer looks like '$($record.kind)'. Command line: $($process.CommandLine). Refusing to stop it."
        $refused = $true
        continue
    }

    Write-Info "Stopping $($record.kind) (PID $($record.pid))..."
    & taskkill.exe /PID $record.pid /T /F 2>&1 | Out-Null
    Start-Sleep -Milliseconds 500
}

foreach ($record in $records) {
    $deadline = (Get-Date).AddSeconds(20)
    while ((Get-Date) -lt $deadline -and -not (Test-PortFree ([int]$record.port))) {
        Start-Sleep -Milliseconds 500
    }
    if (-not (Test-PortFree ([int]$record.port))) {
        Write-Warn "Port $($record.port) is still in use after stopping $($record.kind)."
        $refused = $true
    }
}

if ($refused) {
    Write-Err 'Some processes were not stopped; PID file kept for inspection.'
    exit 1
}

Remove-Item $pidFile -Force
Write-Ok 'Local services stopped (only processes started by this script were touched).'
exit 0