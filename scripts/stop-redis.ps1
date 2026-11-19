[CmdletBinding()]
param()

. (Join-Path $PSScriptRoot 'lib\common.ps1')

$root = Get-ProjectRoot
$dataDir = Join-Path $root '.local-run\redis'
if (-not $env:REDIS_HOME) {
    $env:REDIS_HOME = 'D:\Develop\JAVA\IdeaProjects\tjxt-ai-master\.local-migration\native-windows\redis\Redis-7.4.2-Windows-x64-msys2'
}
$port = if ($env:REDIS_PORT) { [int]$env:REDIS_PORT } else { 6379 }
if (-not (Test-PortListening $port)) {
    Write-Info 'Redis is not listening; nothing to stop.'
    exit 0
}

$cli = Join-Path $env:REDIS_HOME 'redis-cli.exe'
if (Test-Path $cli) {
    Write-Info 'Stopping Redis...'
    & $cli -h 127.0.0.1 -p $port shutdown nosave 2>&1 | Out-Null
} else {
    $pidFile = Join-Path $dataDir 'redis.pid'
    if (Test-Path $pidFile) {
        $serverPid = Get-Content $pidFile -Raw
        taskkill.exe /PID $serverPid /T /F 2>&1 | Out-Null
    }
}

$deadline = (Get-Date).AddSeconds(15)
while ((Get-Date) -lt $deadline -and (Test-PortListening $port)) {
    Start-Sleep -Milliseconds 300
}
if (Test-PortListening $port) {
    Write-Err "Redis is still listening on $port."
    exit 1
}
Remove-Item (Join-Path $dataDir 'redis.pid') -Force -ErrorAction SilentlyContinue
Write-Ok 'Redis stopped.'
exit 0