[CmdletBinding()]
param()

. (Join-Path $PSScriptRoot 'lib\common.ps1')

$root = Get-ProjectRoot
Initialize-LocalEnv
$rabbitBase = Join-Path $root '.local-run\rabbitmq'
New-Item -ItemType Directory -Force -Path $rabbitBase | Out-Null

if (-not $env:ERLANG_HOME) {
    $env:ERLANG_HOME = 'D:\Develop\JAVA\IdeaProjects\tjxt-ai-master\.local-migration\native-windows\erlang-portable'
}
if (-not $env:RABBITMQ_HOME) {
    $env:RABBITMQ_HOME = 'D:\Develop\JAVA\IdeaProjects\tjxt-ai-master\.local-migration\native-windows\rabbitmq\rabbitmq_server-3.13.7'
}
if (-not $env:RABBITMQ_NODENAME) { $env:RABBITMQ_NODENAME = 'rabbit@localhost' }
if (-not $env:RABBITMQ_NODE_PORT) { $env:RABBITMQ_NODE_PORT = '5672' }
if (-not $env:RABBITMQ_DIST_PORT) { $env:RABBITMQ_DIST_PORT = '25672' }
$env:RABBITMQ_BASE = $rabbitBase

$server = Join-Path $env:RABBITMQ_HOME 'sbin\rabbitmq-server.bat'
$ctl = Join-Path $env:RABBITMQ_HOME 'sbin\rabbitmqctl.bat'
if (-not (Test-Path $server)) { Write-Err "RabbitMQ server not found: $server"; exit 1 }
if (-not (Test-Path (Join-Path $env:ERLANG_HOME 'bin\erl.exe'))) {
    Write-Err "Erlang not found: $env:ERLANG_HOME"; exit 1
}

if (Test-PortListening ([int]$env:RABBITMQ_NODE_PORT)) {
    Write-Info 'RabbitMQ is already listening; reusing it.'
} else {
    Write-Info 'Starting RabbitMQ...'
    $cmd = (Get-Command cmd.exe).Source
    $process = Start-Process -FilePath $cmd -ArgumentList @('/c', $server) `
        -WorkingDirectory $root `
        -RedirectStandardOutput (Join-Path $rabbitBase 'server.log') `
        -RedirectStandardError (Join-Path $rabbitBase 'server.err.log') `
        -PassThru -WindowStyle Hidden
    Set-Content -Path (Join-Path $rabbitBase 'server.pid') -Value $process.Id
    $deadline = (Get-Date).AddSeconds(90)
    while ((Get-Date) -lt $deadline -and -not (Test-PortListening ([int]$env:RABBITMQ_NODE_PORT))) {
        Start-Sleep -Milliseconds 800
    }
    if (-not (Test-PortListening ([int]$env:RABBITMQ_NODE_PORT))) {
        Write-Err 'RabbitMQ did not start. See .local-run/rabbitmq/server.log.'
        exit 1
    }
}

$vhost = if ($env:RABBIT_VHOST) { $env:RABBIT_VHOST } else { '/job-bootcamp-dev' }
& $ctl add_vhost $vhost 2>&1 | Out-Null
& $ctl set_permissions -p $vhost guest '.*' '.*' '.*' 2>&1 | Out-Null
Write-Ok "RabbitMQ ready at 127.0.0.1:$($env:RABBITMQ_NODE_PORT), vhost $vhost (guest/guest)."
exit 0