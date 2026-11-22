# CleanYourTV Mond Launcher - Local Build Script for Windows
# Builds the modified launcher APK with the Mond Clock (Anurati & Quicksand fonts)

param(
    [switch]$InstallJdk
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host " Building CleanYourTV Mond Launcher APK   " -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

# 1. Check for Java JDK 17
$javaCmd = Get-Command "java.exe" -ErrorAction SilentlyContinue
if (-not $javaCmd -and -not $env:JAVA_HOME) {
    if ($InstallJdk) {
        Write-Host "Installing Microsoft OpenJDK 17 via winget..." -ForegroundColor Yellow
        winget install --id Microsoft.OpenJDK.17 -e --silent --accept-package-agreements --accept-source-agreements
        $env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17"
        $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
    } else {
        Write-Host "Java JDK 17 was not detected on your PC." -ForegroundColor Red
        Write-Host "To install it automatically, run:" -ForegroundColor Yellow
        Write-Host "  powershell -ExecutionPolicy Bypass -File .\build-mond-apk.ps1 -InstallJdk" -ForegroundColor White
        Write-Host "Or install Android Studio / OpenJDK 17 manually." -ForegroundColor Gray
        exit 1
    }
}

# 2. Run Gradle Build
Write-Host "Running Gradle build (assembleRelease)..." -ForegroundColor Green
& .\gradlew.bat assembleRelease --no-daemon

if ($LASTEXITCODE -eq 0) {
    $apk = Get-ChildItem -Path "app\build\outputs\apk\release\*.apk" | Select-Object -First 1
    if ($apk) {
        $dest = Join-Path $PSScriptRoot "cleanyourtv-mond-launcher.apk"
        Copy-Item $apk.FullName $dest -Force
        Write-Host ""
        Write-Host "SUCCESS! Launcher APK built at:" -ForegroundColor Green
        Write-Host "  $dest" -ForegroundColor White
        Write-Host ""
        Write-Host "To install on your TV over ADB, run:" -ForegroundColor Cyan
        Write-Host "  adb install -r cleanyourtv-mond-launcher.apk" -ForegroundColor White
    }
} else {
    Write-Host "Build failed with exit code $LASTEXITCODE." -ForegroundColor Red
}
