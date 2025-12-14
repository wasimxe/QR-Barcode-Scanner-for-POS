@echo off
REM QR Scanner Pro Build Script
REM Sets up environment and builds the project

echo ============================================
echo QR Scanner Pro - Build Script
echo ============================================
echo.

REM Set environment variables
set JAVA_HOME=D:\workspace\android\jdk\jdk-17.0.2
set ANDROID_HOME=D:\workspace\android\android-sdk
set ANDROID_SDK_ROOT=D:\workspace\android\android-sdk

REM Add to PATH
set PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\platform-tools;%ANDROID_HOME%\tools;%PATH%

echo Environment configured:
echo   JAVA_HOME: %JAVA_HOME%
echo   ANDROID_HOME: %ANDROID_HOME%
echo.

echo Starting build...
echo.

REM Build the project
call gradlew.bat assembleDebug

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ============================================
    echo Build completed successfully!
    echo APK location: app\build\outputs\apk\debug\app-debug.apk
    echo ============================================
) else (
    echo.
    echo ============================================
    echo Build failed with error code: %ERRORLEVEL%
    echo ============================================
)

pause
