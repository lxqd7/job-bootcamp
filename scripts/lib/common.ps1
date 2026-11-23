Set-StrictMode -Version Latest

function Get-ProjectRoot {
    return (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
}

function Write-Info([string]$Message) {
    Write-Host "[INFO] $Message" -ForegroundColor Cyan
}

function Write-Ok([string]$Message) {
    Write-Host "[ OK ] $Message" -ForegroundColor Green
}

function Write-Warn([string]$Message) {
    Write-Host "[WARN] $Message" -ForegroundColor Yellow
}

function Write-Err([string]$Message) {
    Write-Host "[FAIL] $Message" -ForegroundColor Red
}

function Initialize-LocalEnv {
    $root = Get-ProjectRoot
    $localEnv = Join-Path $root 'config\local.env.ps1'
    if (Test-Path $localEnv) {
        . $localEnv
    }
    if (-not $env:DB_HOST) { $env:DB_HOST = '127.0.0.1' }
    if (-not $env:DB_PORT) { $env:DB_PORT = '3306' }
    if (-not $env:DB_NAME) { $env:DB_NAME = 'bc_course' }
    if (-not $env:USER_DB_NAME) { $env:USER_DB_NAME = 'bc_user' }
    if (-not $env:LEARNING_DB_NAME) { $env:LEARNING_DB_NAME = 'bc_learning' }
    if (-not $env:MARKETING_DB_NAME) { $env:MARKETING_DB_NAME = 'bc_marketing' }
    if (-not $env:ASSIGNMENT_DB_NAME) { $env:ASSIGNMENT_DB_NAME = 'bc_assignment' }
    if (-not $env:DB_USERNAME) { $env:DB_USERNAME = 'root' }
    if (-not $env:COURSE_PORT) { $env:COURSE_PORT = '18082' }
    if (-not $env:LEARNING_PORT) { $env:LEARNING_PORT = '18083' }
    if (-not $env:MARKETING_PORT) { $env:MARKETING_PORT = '18087' }
    if (-not $env:ASSIGNMENT_PORT) { $env:ASSIGNMENT_PORT = '18085' }
    if (-not (Test-Path Env:DB_PASSWORD)) { $env:DB_PASSWORD = '' }
}

function Test-TcpPort([string]$ComputerName, [int]$Port, [int]$TimeoutMs = 500) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $async = $client.BeginConnect($ComputerName, $Port, $null, $null)
        if (-not $async.AsyncWaitHandle.WaitOne($TimeoutMs)) { return $false }
        $client.EndConnect($async)
        return $true
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

function Test-PortInUse([int]$Port) {
    return (Test-TcpPort '127.0.0.1' $Port) -or (Test-TcpPort '::1' $Port)
}

function Test-PortListening([int]$Port) {
    $connections = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    if ($connections) { return $true }
    return (Test-TcpPort '127.0.0.1' $Port 1500)
}

function Test-PortFree([int]$Port) {
    return -not (Test-PortInUse $Port)
}

function Wait-HttpOk([string]$Url, [int]$TimeoutSeconds = 60) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 2
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) {
                return $true
            }
        } catch {
        }
        Start-Sleep -Milliseconds 800
    }
    return $false
}