`<#
.SYNOPSIS
Android Emulator Testing Environment Health Check

.DESCRIPTION
Verifies that all required tools and dependencies are properly installed
and configured for Android emulator testing.

.EXAMPLE
.\emu_health_check.ps1 -Help
#>

param (
    [switch]$Help
)

$ErrorActionPreference = "Stop"

if ($Help) {
    Write-Host "Android Emulator Testing - Environment Health Check`n"
    Write-Host "Verifies that your environment is properly configured for Android emulator testing.`n"
    Write-Host "Usage: .\emu_health_check.ps1 [options]`n"
    Write-Host "Options:"
    Write-Host "  -Help    Show this help message`n"
    Write-Host "This script checks for:"
    Write-Host "  - Android SDK availability (ANDROID_HOME)"
    Write-Host "  - ADB (Android Debug Bridge) installation"
    Write-Host "  - Emulator executable availability"
    Write-Host "  - Java Development Kit (JDK)"
    Write-Host "  - Connected Android devices/emulators"
    Write-Host "  - Python 3 installation (for scripts)`n"
    Write-Host "Exit codes:"
    Write-Host "  0 - All checks passed"
    Write-Host "  1 - One or more checks failed (see output for details)"
    exit 0
}

Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host "  Android Emulator Testing - Environment Health Check" -ForegroundColor Cyan
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━`n" -ForegroundColor Cyan

$ChecksPassed = 0
$ChecksFailed = 0

function Check-Passed {
    param([string]$Message)
    Write-Host "✓ $Message" -ForegroundColor Green
    $script:ChecksPassed++
}

function Check-Failed {
    param([string]$Message)
    Write-Host "✗ $Message" -ForegroundColor Red
    $script:ChecksFailed++
}

function Check-Warning {
    param([string]$Message)
    Write-Host "⚠ $Message" -ForegroundColor Yellow
}

# Check 1: ANDROID_HOME
Write-Host "[1/6] " -ForegroundColor Cyan -NoNewline
Write-Host "Checking ANDROID_HOME..."

$envAndroidHome = $env:ANDROID_HOME

if ([string]::IsNullOrWhiteSpace($envAndroidHome)) {
    # Try to guess standard locations
    $userProfile = $env:USERPROFILE
    if ($null -eq $userProfile) {
        $userProfile = $env:HOME
    }
    
    $commonPath = Join-Path $userProfile 'AppData\Local\Android\Sdk'
    $macPath = Join-Path $userProfile 'Library\Android\sdk'
    
    if (Test-Path $commonPath) {
        $env:ANDROID_HOME = $commonPath
        Check-Warning "ANDROID_HOME not set, but found valid SDK at $commonPath"
        Write-Host "       Exporting for this session."
    } elseif (Test-Path $macPath) {
        $env:ANDROID_HOME = $macPath
        Check-Warning "ANDROID_HOME not set, but found valid SDK at $macPath"
        Write-Host "       Exporting for this session."
    } else {
        Check-Failed "ANDROID_HOME environment variable not set"
        Write-Host "       Please set ANDROID_HOME to your Android SDK location."
    }
} else {
    Check-Passed "ANDROID_HOME is set to $envAndroidHome"
}
Write-Host ""

# Check 2: ADB
Write-Host "[2/6] " -ForegroundColor Cyan -NoNewline
Write-Host "Checking ADB (Android Debug Bridge)..."

if (Get-Command adb -ErrorAction SilentlyContinue) {
    try {
        $adbVersion = (adb --version | Select-Object -First 1)
        Check-Passed "ADB is installed ($adbVersion)"
        $adbPath = (Get-Command adb).Source
        Write-Host "       Path: $adbPath"
    } catch {
        Check-Failed "ADB command found but failed to run."
    }
} else {
    if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_HOME)) {
        $platformToolsPath = Join-Path $env:ANDROID_HOME 'platform-tools'
        if ((Test-Path (Join-Path $platformToolsPath 'adb.exe')) -or (Test-Path (Join-Path $platformToolsPath 'adb'))) {
            $env:PATH += ";$platformToolsPath"
            Check-Warning "ADB found in SDK but not in PATH. Adding it temporarily."
            Check-Passed "ADB is installed"
        } else {
            Check-Failed "ADB command not found"
            Write-Host "       Ensure platform-tools is in your PATH."
        }
    } else {
        Check-Failed "ADB command not found"
        Write-Host "       Ensure platform-tools is in your PATH."
    }
}
Write-Host ""

# Check 3: Emulator
Write-Host "[3/6] " -ForegroundColor Cyan -NoNewline
Write-Host "Checking Android Emulator..."

if (Get-Command emulator -ErrorAction SilentlyContinue) {
    try {
        $emuVersion = (emulator -version | Select-Object -First 1)
        Check-Passed "Emulator is installed ($emuVersion)"
    } catch {
        Check-Failed "Emulator command found but failed to run."
    }
} else {
    if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_HOME)) {
        $emulatorPath = Join-Path $env:ANDROID_HOME 'emulator'
        if ((Test-Path (Join-Path $emulatorPath 'emulator.exe')) -or (Test-Path (Join-Path $emulatorPath 'emulator'))) {
            $env:PATH += ";$emulatorPath"
            Check-Warning "Emulator found in SDK but not in PATH. Adding it temporarily."
            Check-Passed "Emulator is installed"
        } else {
            Check-Failed "Emulator command not found"
            Write-Host "       Ensure emulator is in your PATH."
        }
    } else {
        Check-Failed "Emulator command not found"
        Write-Host "       Ensure emulator is in your PATH."
    }
}
Write-Host ""

# Check 4: Java
Write-Host "[4/6] " -ForegroundColor Cyan -NoNewline
Write-Host "Checking Java..."

if (Get-Command java -ErrorAction SilentlyContinue) {
    try {
        $javaVersion = (& java -version 2>&1 | Select-Object -First 1)
        Check-Passed "Java is installed ($javaVersion)"
    } catch {
         Check-Failed "Java command found but failed to run."
    }
} else {
    Check-Failed "Java not found"
    Write-Host "       A JDK is required for Android development."
}
Write-Host ""

# Check 5: Python 3
Write-Host "[5/6] " -ForegroundColor Cyan -NoNewline
Write-Host "Checking Python 3..."

if (Get-Command python3 -ErrorAction SilentlyContinue) {
    try {
        $pythonVersion = (python3 --version | Select-Object -First 1)
        Check-Passed "Python 3 is installed ($pythonVersion)"
    } catch {
        Check-Failed "Python 3 command found but failed to run."
    }
} elseif (Get-Command python -ErrorAction SilentlyContinue) {
    try {
        $pythonVersion = (python --version | Select-Object -First 1)
        if ($pythonVersion -match "Python 3") {
            Check-Passed "Python 3 is installed ($pythonVersion)"
        } else {
            Check-Failed "Python 3 not found, found $pythonVersion instead"
            Write-Host "       Required for skill scripts."
        }
    } catch {
        Check-Failed "Python command found but failed to run."
    }
} else {
    Check-Failed "Python 3 not found"
    Write-Host "       Required for skill scripts."
}
Write-Host ""

# Check 6: Connected Devices
Write-Host "[6/6] " -ForegroundColor Cyan -NoNewline
Write-Host "Checking connected devices..."

if (Get-Command adb -ErrorAction SilentlyContinue) {
    $devices = (adb devices | Select-String -Pattern "device$")
    $deviceCount = if ($null -ne $devices) { @($devices).Count } else { 0 }
    
    if ($deviceCount -gt 0) {
        Check-Passed "Found $deviceCount connected device(s)"
        Write-Host ""
        Write-Host "       Connected devices:"
        foreach ($device in $devices) {
            $line = $device.ToString().Trim()
            Write-Host "       - $line"
        }
    } else {
        Check-Warning "No devices connected or emulators booted"
        Write-Host "       Boot an emulator to begin testing."
        Write-Host "       Use 'emulator -list-avds' to see available AVDs."
    }
} else {
    Check-Failed "Cannot check devices (adb not found)"
}
Write-Host ""


# Summary
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host "  Summary" -ForegroundColor Cyan
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━`n" -ForegroundColor Cyan

Write-Host "Checks passed: " -NoNewline
Write-Host $ChecksPassed -ForegroundColor Green

if ($ChecksFailed -gt 0) {
    Write-Host "Checks failed: " -NoNewline
    Write-Host $ChecksFailed -ForegroundColor Red
    Write-Host ""
    Write-Host "Action required: " -ForegroundColor Yellow -NoNewline
    Write-Host "Fix the failed checks above before testing"
    exit 1
} else {
    Write-Host ""
    Write-Host "✓ Environment is ready for Android emulator testing" -ForegroundColor Green
    exit 0
}
