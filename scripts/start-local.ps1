[CmdletBinding()]
param([switch]$SkipCheck, [switch]$SkipFrontend)

. (Join-Path $PSScriptRoot 'lib\common.ps1')

$root = Get-ProjectRoot
Initialize-LocalEnv
Push-Location $root
try {
    if (-not $SkipCheck) {
        & (Join-Path $PSScriptRoot 'check-deps.ps1')
        if ($LASTEXITCODE -ne 0) {
            throw 'Dependency check failed; startup aborted.'
        }
    }

    $runDir = Join-Path $root '.local-run'
    $logDir = Join-Path $runDir 'logs'
    New-Item -ItemType Directory -Force -Path $logDir | Out-Null
    $pidFile = Join-Path $runDir 'pids.json'

    # PID 文件是本脚本唯一的停止依据；只清理由自己启动且仍存活的进程，不按端口杀进程。
    if (Test-Path $pidFile) {
        $existingJson = Get-Content $pidFile -Raw | ConvertFrom-Json
        $existing = @($existingJson | ForEach-Object { $_ })
        $alive = @($existing | Where-Object { Get-Process -Id $_.pid -ErrorAction SilentlyContinue })
        if ($alive.Count -gt 0) {
            throw "Services from a previous run are still alive (PIDs: $($alive.pid -join ', ')). Run scripts/stop-local.ps1 first."
        }
        Remove-Item $pidFile -Force
    }

    $java = (Get-Command java).Source
    $courseJar = Join-Path $root 'backend\bootcamp-course\target\bootcamp-course-0.1.0-SNAPSHOT.jar'
    $userJar = Join-Path $root 'backend\bootcamp-user\target\bootcamp-user-0.1.0-SNAPSHOT.jar'
    $learningJar = Join-Path $root 'backend\bootcamp-learning\target\bootcamp-learning-0.1.0-SNAPSHOT.jar'
    $tradeJar = Join-Path $root 'backend\bootcamp-trade\target\bootcamp-trade-0.1.0-SNAPSHOT.jar'
    $marketingJar = Join-Path $root 'backend\bootcamp-marketing\target\bootcamp-marketing-0.1.0-SNAPSHOT.jar'
    $gatewayJar = Join-Path $root 'backend\bootcamp-gateway\target\bootcamp-gateway-0.1.0-SNAPSHOT.jar'
    $cmd = (Get-Command cmd.exe).Source
    $frontendDir = Join-Path $root 'frontend'

    Write-Info 'Starting gateway, course service, user service, learning service, trade service, marketing service and the two frontend dev servers...'
    $gateway = Start-Process -FilePath $java -ArgumentList @('-jar', $gatewayJar) `
        -WorkingDirectory $root `
        -RedirectStandardOutput (Join-Path $logDir 'gateway.out.log') `
        -RedirectStandardError (Join-Path $logDir 'gateway.err.log') `
        -PassThru -WindowStyle Hidden

    $course = Start-Process -FilePath $java -ArgumentList @('-jar', $courseJar) `
        -WorkingDirectory $root `
        -RedirectStandardOutput (Join-Path $logDir 'course.out.log') `
        -RedirectStandardError (Join-Path $logDir 'course.err.log') `
        -PassThru -WindowStyle Hidden

    $user = Start-Process -FilePath $java -ArgumentList @('-jar', $userJar) `
        -WorkingDirectory $root `
        -RedirectStandardOutput (Join-Path $logDir 'user.out.log') `
        -RedirectStandardError (Join-Path $logDir 'user.err.log') `
        -PassThru -WindowStyle Hidden

    $marketing = Start-Process -FilePath $java -ArgumentList @('-jar', $marketingJar) `
        -WorkingDirectory $root `
        -RedirectStandardOutput (Join-Path $logDir 'marketing.out.log') `
        -RedirectStandardError (Join-Path $logDir 'marketing.err.log') `
        -PassThru -WindowStyle Hidden

    $learning = Start-Process -FilePath $java -ArgumentList @('-jar', $learningJar) `
        -WorkingDirectory $root `
        -RedirectStandardOutput (Join-Path $logDir 'learning.out.log') `
        -RedirectStandardError (Join-Path $logDir 'learning.err.log') `
        -PassThru -WindowStyle Hidden

    $trade = Start-Process -FilePath $java -ArgumentList @('-jar', $tradeJar) `
        -WorkingDirectory $root `
        -RedirectStandardOutput (Join-Path $logDir 'trade.out.log') `
        -RedirectStandardError (Join-Path $logDir 'trade.err.log') `
        -PassThru -WindowStyle Hidden

    $student = $null
    $admin = $null
    if (-not $SkipFrontend) {
        $student = Start-Process -FilePath $cmd -ArgumentList @('/c', 'npm', 'run', 'dev:student') `
            -WorkingDirectory $frontendDir `
            -RedirectStandardOutput (Join-Path $logDir 'student.out.log') `
            -RedirectStandardError (Join-Path $logDir 'student.err.log') `
            -PassThru -WindowStyle Hidden

        $admin = Start-Process -FilePath $cmd -ArgumentList @('/c', 'npm', 'run', 'dev:admin') `
            -WorkingDirectory $frontendDir `
            -RedirectStandardOutput (Join-Path $logDir 'admin.out.log') `
            -RedirectStandardError (Join-Path $logDir 'admin.err.log') `
            -PassThru -WindowStyle Hidden
    }

    $userPort = if ($env:USER_PORT) { [int]$env:USER_PORT } else { 18081 }
    $marketingPort = if ($env:MARKETING_PORT) { [int]$env:MARKETING_PORT } else { 18087 }
    $learningPort = if ($env:LEARNING_PORT) { [int]$env:LEARNING_PORT } else { 18083 }
    $tradePort = if ($env:TRADE_PORT) { [int]$env:TRADE_PORT } else { 18084 }
    $gatewayPort = if ($env:GATEWAY_PORT) { [int]$env:GATEWAY_PORT } else { 18080 }
    $records = @(
        [pscustomobject]@{ kind = 'gateway'; pid = $gateway.Id; port = $gatewayPort; url = "http://127.0.0.1:$gatewayPort/actuator/health" },
        [pscustomobject]@{ kind = 'course'; pid = $course.Id; port = [int]$env:COURSE_PORT; url = "http://127.0.0.1:$($env:COURSE_PORT)/actuator/health" },
        [pscustomobject]@{ kind = 'user'; pid = $user.Id; port = $userPort; url = "http://127.0.0.1:$userPort/actuator/health" },
        [pscustomobject]@{ kind = 'learning'; pid = $learning.Id; port = $learningPort; url = "http://127.0.0.1:$learningPort/actuator/health" },
        [pscustomobject]@{ kind = 'trade'; pid = $trade.Id; port = $tradePort; url = "http://127.0.0.1:$tradePort/actuator/health" },
        [pscustomobject]@{ kind = 'marketing'; pid = $marketing.Id; port = $marketingPort; url = "http://127.0.0.1:$marketingPort/actuator/health" }
    )
    if (-not $SkipFrontend) {
        $records += [pscustomobject]@{ kind = 'student'; pid = $student.Id; port = 15173; url = 'http://localhost:15173/' }
        $records += [pscustomobject]@{ kind = 'admin'; pid = $admin.Id; port = 15174; url = 'http://localhost:15174/' }
    }
    $records | ConvertTo-Json -Depth 3 | Set-Content -Path $pidFile -Encoding UTF8

    Write-Info 'Waiting for services to become ready...'
    $failed = @()
    foreach ($record in $records) {
        if (Wait-HttpOk $record.url 60) {
            Write-Ok "$($record.kind) is ready at $($record.url)"
        } else {
            $failed += $record.kind
        }
    }

    if ($failed.Count -gt 0) {
        Write-Err "Startup incomplete: $($failed -join ', '). See logs in .local-run/logs."
        & (Join-Path $PSScriptRoot 'stop-local.ps1')
        exit 1
    }

    Write-Host ''
    Write-Ok "Gateway        : http://127.0.0.1:$gatewayPort/actuator/health"
    Write-Ok "Course service : http://127.0.0.1:$($env:COURSE_PORT)/actuator/health"
    Write-Ok "User service   : http://127.0.0.1:$userPort/actuator/health"
    Write-Ok "Learning       : http://127.0.0.1:$learningPort/actuator/health"
    Write-Ok "Trade          : http://127.0.0.1:$tradePort/actuator/health"
    Write-Ok "Marketing      : http://127.0.0.1:$marketingPort/actuator/health"
    if (-not $SkipFrontend) {
        Write-Ok 'Student app    : http://localhost:15173/'
        Write-Ok 'Admin app      : http://localhost:15174/'
    }
    Write-Info 'PIDs are recorded in .local-run/pids.json; logs are in .local-run/logs.'
    Write-Info 'Stop with: powershell -ExecutionPolicy Bypass -File scripts/stop-local.ps1'
    exit 0
} catch {
    Write-Err $_.Exception.Message
    exit 1
} finally {
    Pop-Location
}