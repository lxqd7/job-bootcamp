[CmdletBinding()]
param()

. (Join-Path $PSScriptRoot 'lib\common.ps1')

$root = Get-ProjectRoot
Initialize-LocalEnv
$failures = New-Object System.Collections.Generic.List[string]

function Add-Failure([string]$Message) {
    $script:failures.Add($Message)
    Write-Err $Message
}

Write-Info 'Checking local dependencies (course, user, learning, trade and marketing services plus frontend dev servers).'

try {
    $javaVersion = (& java -version 2>&1 | Out-String)
    if ($javaVersion -match 'version "21\.') {
        Write-Ok 'Java 21 detected.'
    } else {
        Add-Failure "Java 21 is required. Current output: $($javaVersion.Trim())"
    }
} catch {
    Add-Failure 'java was not found on PATH.'
}

$wrapper = Join-Path $root 'backend\mvnw.cmd'
if (Test-Path $wrapper) {
    Write-Ok 'Maven wrapper is present (backend/mvnw.cmd).'
} else {
    Add-Failure 'backend/mvnw.cmd is missing.'
}

$courseJar = Join-Path $root 'backend\bootcamp-course\target\bootcamp-course-0.1.0-SNAPSHOT.jar'
if (Test-Path $courseJar) {
    Write-Ok 'Course service jar is built.'
} else {
    Add-Failure 'Course service jar is missing. Run: cd backend; .\mvnw.cmd -B verify'
}

$userJar = Join-Path $root 'backend\bootcamp-user\target\bootcamp-user-0.1.0-SNAPSHOT.jar'
if (Test-Path $userJar) {
    Write-Ok 'User service jar is built.'
} else {
    Add-Failure 'User service jar is missing. Run: cd backend; .\mvnw.cmd -B verify'
}

$marketingJar = Join-Path $root 'backend\bootcamp-marketing\target\bootcamp-marketing-0.1.0-SNAPSHOT.jar'
if (Test-Path $marketingJar) {
    Write-Ok 'Marketing service jar is built.'
} else {
    Add-Failure 'Marketing service jar is missing. Run: cd backend; .\mvnw.cmd -B verify'
}

$learningJar = Join-Path $root 'backend\bootcamp-learning\target\bootcamp-learning-0.1.0-SNAPSHOT.jar'
if (Test-Path $learningJar) {
    Write-Ok 'Learning service jar is built.'
} else {
    Add-Failure 'Learning service jar is missing. Run: cd backend; .\mvnw.cmd -B verify'
}

$tradeJar = Join-Path $root 'backend\bootcamp-trade\target\bootcamp-trade-0.1.0-SNAPSHOT.jar'
if (Test-Path $tradeJar) {
    Write-Ok 'Trade service jar is built.'
} else {
    Add-Failure 'Trade service jar is missing. Run: cd backend; .\mvnw.cmd -B verify'
}

$gatewayJar = Join-Path $root 'backend\bootcamp-gateway\target\bootcamp-gateway-0.1.0-SNAPSHOT.jar'
if (Test-Path $gatewayJar) {
    Write-Ok 'Gateway jar is built.'
} else {
    Add-Failure 'Gateway jar is missing. Run: cd backend; .\mvnw.cmd -B verify'
}

try {
    $nodeVersion = (& node -v).Trim()
    $nodeMajor = [int](($nodeVersion -replace '^v', '') -split '\.')[0]
    if ($nodeMajor -ge 22) {
        Write-Ok "Node $nodeVersion detected."
    } else {
        Add-Failure "Node 22 or newer is required; found $nodeVersion."
    }
} catch {
    Add-Failure 'node was not found on PATH.'
}

$npm = Get-Command npm.cmd -ErrorAction SilentlyContinue
if ($npm) {
    Write-Ok "npm $(& $npm.Source -v) detected."
} else {
    Add-Failure 'npm.cmd was not found on PATH.'
}

$nodeModules = Join-Path $root 'frontend\node_modules'
if (Test-Path $nodeModules) {
    Write-Ok 'Frontend dependencies are installed.'
} else {
    Add-Failure 'frontend/node_modules is missing. Run: cd frontend; npm install'
}

$localEnv = Join-Path $root 'config\local.env.ps1'
if (Test-Path $localEnv) {
    Write-Ok 'config/local.env.ps1 found.'
} elseif (Test-Path Env:DB_PASSWORD) {
    Write-Warn 'config/local.env.ps1 not found; using database settings from the current shell environment.'
} else {
    Add-Failure 'Database settings are missing. Copy config/examples/local.env.example.ps1 to config/local.env.ps1 and fill DB_PASSWORD, or set DB_PASSWORD in the current shell.'
}

$mysql = Get-Command mysql.exe -ErrorAction SilentlyContinue
if (-not $mysql) {
    Write-Warn 'mysql client not found; skipping the live database check.'
} elseif ([string]::IsNullOrEmpty($env:DB_PASSWORD)) {
    Add-Failure 'DB_PASSWORD is empty; cannot verify the database connection.'
} else {
    $courseDb = $env:DB_NAME
    if (-not $env:USER_DB_NAME) { $env:USER_DB_NAME = 'bc_user' }
    if (-not $env:MARKETING_DB_NAME) { $env:MARKETING_DB_NAME = 'bc_marketing' }
    if (-not $env:LEARNING_DB_NAME) { $env:LEARNING_DB_NAME = 'bc_learning' }
    if (-not $env:TRADE_DB_NAME) { $env:TRADE_DB_NAME = 'bc_trade' }
    $userDb = $env:USER_DB_NAME
    $marketingDb = $env:MARKETING_DB_NAME
    $learningDb = $env:LEARNING_DB_NAME
    $tradeDb = $env:TRADE_DB_NAME
    if (($courseDb -notmatch '^[A-Za-z0-9_]+$') -or ($userDb -notmatch '^[A-Za-z0-9_]+$') -or ($marketingDb -notmatch '^[A-Za-z0-9_]+$') -or ($learningDb -notmatch '^[A-Za-z0-9_]+$') -or ($tradeDb -notmatch '^[A-Za-z0-9_]+$')) {
        Add-Failure 'Database names may only contain letters, digits and underscores.'
    } else {
        $env:MYSQL_PWD = $env:DB_PASSWORD
        $dbCheck = (& $mysql.Source --protocol=tcp --connect-timeout=5 -h $env:DB_HOST -P $env:DB_PORT -u $env:DB_USERNAME -N -B -e "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME IN ('$courseDb','$userDb','$marketingDb','$learningDb','$tradeDb') ORDER BY SCHEMA_NAME" 2>$null)
        $mysqlExit = $LASTEXITCODE
        Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
        if ($mysqlExit -ne 0) {
            Add-Failure "MySQL connection failed (exit code $mysqlExit). Check DB_HOST/DB_PORT/DB_USERNAME/DB_PASSWORD; access denied usually means the password is wrong."
        } else {
            $found = @($dbCheck | ForEach-Object { $_.ToString().Trim() } | Where-Object { $_ })
            $missing = @()
            foreach ($db in @($courseDb, $userDb, $marketingDb, $learningDb, $tradeDb)) {
                if ($found -notcontains $db) { $missing += $db }
            }
            if ($missing.Count -gt 0) {
                foreach ($db in $missing) {
                    Add-Failure "Database '$db' does not exist. Create it as a dedicated project database: CREATE DATABASE $db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
                }
            } else {
                Write-Ok "MySQL $($env:DB_USERNAME)@$($env:DB_HOST):$($env:DB_PORT) reachable; databases $courseDb, $userDb, $marketingDb, $learningDb and $tradeDb exist."
            }
        }
    }
}

$userPort = if ($env:USER_PORT) { [int]$env:USER_PORT } else { 18081 }
$marketingPort = if ($env:MARKETING_PORT) { [int]$env:MARKETING_PORT } else { 18087 }
$learningPort = if ($env:LEARNING_PORT) { [int]$env:LEARNING_PORT } else { 18083 }
$tradePort = if ($env:TRADE_PORT) { [int]$env:TRADE_PORT } else { 18084 }
$gatewayPort = if ($env:GATEWAY_PORT) { [int]$env:GATEWAY_PORT } else { 18080 }
foreach ($port in @($gatewayPort, $userPort, $marketingPort, $learningPort, $tradePort, [int]$env:COURSE_PORT, 15173, 15174)) {
    if (Test-PortFree $port) {
        Write-Ok "Port $port is free."
    } else {
        Add-Failure "Port $port is already in use. Stop that process first; the reference project also uses 18081/18082, so do not run both projects at once."
    }
}

if ($failures.Count -gt 0) {
    Write-Host ''
    Write-Err "$($failures.Count) check(s) failed. Fix the items above and rerun."
    exit 1
}

Write-Host ''
Write-Ok 'All dependency checks passed.'
exit 0