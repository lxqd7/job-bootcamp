[CmdletBinding()]
param()

. (Join-Path $PSScriptRoot 'lib\common.ps1')

$root = Get-ProjectRoot
Initialize-LocalEnv
$dataDir = Join-Path $root '.local-run\redis'
New-Item -ItemType Directory -Force -Path $dataDir | Out-Null

if (-not $env:REDIS_HOME) {
    $env:REDIS_HOME = 'D:\Develop\JAVA\IdeaProjects\tjxt-ai-master\.local-migration\native-windows\redis\Redis-7.4.2-Windows-x64-msys2'
}
$server = Join-Path $env:REDIS_HOME 'redis-server.exe'
if (-not (Test-Path $server)) { Write-Err "redis-server not found: $server"; exit 1 }

$port = if ($env:REDIS_PORT) { [int]$env:REDIS_PORT } else { 6379 }
if (Test-PortListening $port) {
    Write-Info "Redis is already listening on $port; reusing it."
    exit 0
}

Write-Info 'Starting Redis...'
$log = Join-Path $dataDir 'redis.log'
$process = Start-Process -FilePath $server -ArgumentList @(
    '--port', "$port",
    '--bind', '127.0.0.1',
    '--protected-mode', 'yes',
    '--dir', $dataDir,
    '--dbfilename', 'dump.rdb',
    '--appendonly', 'no',
    '--logfile', $log
) -WorkingDirectory $dataDir -PassThru -WindowStyle Hidden
Set-Content -Path (Join-Path $dataDir 'redis.pid') -Value $process.Id

$deadline = (Get-Date).AddSeconds(30)
while ((Get-Date) -lt $deadline -and -not (Test-PortListening $port)) {
    Start-Sleep -Milliseconds 300
}
if (-not (Test-PortListening $port)) {
    Write-Err "Redis did not start. See $log."
    exit 1
}
Write-Ok "Redis ready at 127.0.0.1:$port, data $dataDir."
exit 0