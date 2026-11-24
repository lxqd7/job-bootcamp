[CmdletBinding()]
param(
    [switch]$Confirm,
    [switch]$DryRun
)

. (Join-Path $PSScriptRoot 'lib\common.ps1')

$root = Get-ProjectRoot
Initialize-LocalEnv

$databases = @(
    $env:DB_NAME,
    $env:USER_DB_NAME,
    $env:LEARNING_DB_NAME,
    $env:TRADE_DB_NAME,
    $env:MARKETING_DB_NAME,
    $env:ASSIGNMENT_DB_NAME,
    $env:INTERACTION_DB_NAME
)

foreach ($name in $databases) {
    if ($name -notmatch '^bc_[a-z0-9_]+$') {
        Write-Err "Refusing unsafe demo database name: '$name'"
        exit 1
    }
}
if (($databases | Select-Object -Unique).Count -ne $databases.Count) {
    Write-Err 'Database names must be distinct.'
    exit 1
}

$statements = @()
foreach ($name in $databases) {
    $statements += "DROP DATABASE IF EXISTS ``$name``;"
    $statements += "CREATE DATABASE ``$name`` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
}
$sql = $statements -join "`n"

if ($DryRun) {
    Write-Info 'Dry run only. The following SQL would be executed:'
    Write-Host $sql
    Write-Info 'Nothing was dropped.'
    exit 0
}

if (-not $Confirm) {
    Write-Err 'This command deletes all local demo databases. Re-run with -Confirm after stopping services.'
    exit 1
}

$running = @(18080, 18081, 18082, 18083, 18084, 18085, 18086, 18087, 15173, 15174) |
    Where-Object { Test-PortInUse $_ }
if ($running.Count -gt 0) {
    Write-Err "Local services are still running on ports: $($running -join ', '). Run scripts/stop-local.ps1 first."
    exit 1
}

$mysql = if ($env:MYSQL_BIN) { $env:MYSQL_BIN } else { (Get-Command mysql -ErrorAction SilentlyContinue).Source }
if (-not $mysql) {
    Write-Err 'mysql client not found. Set MYSQL_BIN to mysql.exe.'
    exit 1
}

$previousPassword = $env:MYSQL_PWD
try {
    $env:MYSQL_PWD = $env:DB_PASSWORD
    & $mysql --default-character-set=utf8mb4 -h $env:DB_HOST -P $env:DB_PORT -u $env:DB_USERNAME -e $sql
    if ($LASTEXITCODE -ne 0) {
        Write-Err 'Database reset failed.'
        exit 1
    }
} finally {
    $env:MYSQL_PWD = $previousPassword
}

Write-Ok "Demo databases reset: $($databases -join ', ')"
Write-Info 'Start Rabbit/Redis and local services, then run: node scripts/seed-demo.mjs'
Write-Info 'For a fully clean demo, also stop RabbitMQ and remove .local-run/rabbitmq before restarting (local only).'
exit 0