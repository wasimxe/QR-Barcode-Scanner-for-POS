# QR Scanner Pro - PowerShell Build Script

Write-Host "============================================"
Write-Host "QR Scanner Pro - Build Script"
Write-Host "============================================"
Write-Host ""

# Set environment variables
$env:JAVA_HOME = "D:\workspace\android\jdk\jdk-17.0.2"
$env:ANDROID_HOME = "D:\workspace\android\android-sdk"
$env:ANDROID_SDK_ROOT = "D:\workspace\android\android-sdk"
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\tools;$env:PATH"

Write-Host "Environment configured:"
Write-Host "  JAVA_HOME: $env:JAVA_HOME"
Write-Host "  ANDROID_HOME: $env:ANDROID_HOME"
Write-Host ""

Write-Host "Starting build..."
Write-Host ""

# Navigate to project directory
Set-Location "D:\workspace\android\Projects\scanner"

# Build the project
& .\gradlew.bat assembleDebug

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "============================================"
    Write-Host "Build completed successfully!"
    Write-Host "APK location: app\build\outputs\apk\debug\app-debug.apk"
    Write-Host "============================================"
} else {
    Write-Host ""
    Write-Host "============================================"
    Write-Host "Build failed with error code: $LASTEXITCODE"
    Write-Host "============================================"
    exit $LASTEXITCODE
}
