param(
    [string]$Profile = "dev"
)

$backendDir = Join-Path $PSScriptRoot "backend"
$logFile = Join-Path $PSScriptRoot "backend\logs\spring-boot.log"

# Ensure logs directory exists
$logDir = Join-Path $PSScriptRoot "backend\logs"
if (-not (Test-Path $logDir)) {
    New-Item -ItemType Directory -Path $logDir -Force | Out-Null
}

Write-Host "Starting Spring Boot backend (profile: $Profile)..." -ForegroundColor Cyan
Write-Host "Logs: $logFile" -ForegroundColor Gray

# Kill any existing Java process on port 8080
$existing = netstat -ano | Select-String ":8080 "
if ($existing) {
    $parts = $existing -split '\s+'
    $pid = $parts[$parts.Count - 1]
    Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2
    Write-Host "Killed existing process on port 8080 (PID: $pid)" -ForegroundColor Yellow
}

# Start backend in background - no blocking
$env:MAVEN_OPTS = "-Xmx512m"
$job = Start-Job -Name "PalmistryBackend" -ScriptBlock {
    param($dir, $logFile, $profile)
    Set-Location $dir
    $env:MAVEN_OPTS = "-Xmx512m"
    $output = mvn spring-boot:run -Dspring-boot.run.profiles=$profile 2>&1
    # Write output
    $output | Out-File -FilePath $logFile -Encoding utf8
} -ArgumentList $backendDir, $logFile, $Profile

Write-Host "Backend starting in background (Job: $($job.Name), ID: $($job.Id))" -ForegroundColor Green
Write-Host "Access: http://localhost:8080" -ForegroundColor Green
