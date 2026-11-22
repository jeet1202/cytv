# Automation helper to push CleanYourTV Mond Launcher to GitHub
param(
    [string]$RepoUrl
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host " Push CleanYourTV Mond Launcher to GitHub " -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

# 1. Check if Git is installed
$gitCmd = Get-Command "git.exe" -ErrorAction SilentlyContinue
if (-not $gitCmd) {
    Write-Host "Git is not installed on your PC. Installing Git via winget..." -ForegroundColor Yellow
    winget install --id Git.Git -e --silent --accept-package-agreements --accept-source-agreements
    $env:PATH = "C:\Program Files\Git\cmd;$env:PATH"
    $gitCmd = Get-Command "git.exe" -ErrorAction SilentlyContinue
    if (-not $gitCmd) {
        Write-Host "Please restart PowerShell after Git installation and re-run this script." -ForegroundColor Red
        exit 1
    }
}

if (-not $RepoUrl) {
    Write-Host "Please enter your GitHub repository URL (e.g. https://github.com/username/cleanyourtv-mond.git):" -ForegroundColor Yellow
    $RepoUrl = Read-Host "GitHub Repo URL"
}

if (-not $RepoUrl) {
    Write-Host "No repository URL provided. Aborting." -ForegroundColor Red
    exit 1
}

Write-Host "Initializing Git repository..." -ForegroundColor Green
if (-not (Test-Path ".git")) {
    & git init
}

& git add .
& git commit -m "CleanYourTV with aesthetic Mond Clock"
& git branch -M main

# Check if remote already exists
$existingRemote = & git remote get-url origin 2>$null
if ($existingRemote) {
    & git remote set-url origin $RepoUrl
} else {
    & git remote add origin $RepoUrl
}

Write-Host "Pushing to GitHub ($RepoUrl)..." -ForegroundColor Green
Write-Host "A browser window may open to sign in to your GitHub account." -ForegroundColor Gray
& git push -u origin main --force

Write-Host ""
Write-Host "SUCCESS! Your repository is uploaded." -ForegroundColor Green
Write-Host "Now go to your GitHub repository and click the 'Actions' tab to get your compiled APK!" -ForegroundColor Cyan
