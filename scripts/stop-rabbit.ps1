[CmdletBinding()]
param()

. (Join-Path $PSScriptRoot 'lib\common.ps1')

$root = Get-ProjectRoot
$rabbitBase = Join-Path $root '.local-run\rabbitmq'
if (-not $env:RABBITMQ_HOME) {
    $env:RABBITMQ_HOME = 'D:\Develop\JAVA\IdeaProjects\tjxt-ai-master\.local-migration\native-windows\rabbitmq\rabbitmq_server-3.13.7'
}
if (-not $env:ERLANG_HOME) {
    $env:ERLANG_HOME = 'D:\Develop\JAVA\IdeaProjects\tjxt-ai-master\.local-migration\native-windows\erlang-portable'
}
if (-not $env:RABBITMQ_BASE) { $env:RABBITMQ_BASE = $rabbitBase }
if (-not $env:RABBITMQ_NODENAME) { $env:RABBITMQ_NODENAME = 'rabbit@localhost' }
$ctl = Join-Path $env:RABBITMQ_HOME 'sbin\rabbitmqctl.bat'

if (-not (Test-PortListening 5672)) {
    Write-Info 'RabbitMQ is not listening; nothing to stop.'
    exit 0
}
if (-not (Test-Path $ctl)) { Write-Err "rabbitmqctl not found: $ctl"; exit 1 }

Write-Info 'Stopping RabbitMQ...'
& $ctl stop 2>&1 | Out-Null
$deadline = (Get-Date).AddSeconds(30)
while ((Get-Date) -lt $deadline -and (Test-PortListening 5672)) {
    Start-Sleep -Milliseconds 500
}
if (Test-PortListening 5672) {
    Write-Err 'RabbitMQ is still listening on 5672.'
    exit 1
}
Remove-Item (Join-Path $rabbitBase 'server.pid') -Force -ErrorAction SilentlyContinue
Write-Ok 'RabbitMQ stopped.'
exit 0