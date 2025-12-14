# Building QR Scanner Pro

This guide explains how to build the QR Scanner Pro Android application.

## Prerequisites

The following are already set up in your workspace at `D:\workspace\android\`:
- Android SDK (`android-sdk`)
- JDK 17 (`jdk\jdk-17.0.2`)
- Gradle wrapper (`gradle\wrapper`)
- Platform tools

## Build Methods

### Method 1: Using the Build Script (Recommended)

Simply double-click `build.bat` or run from command prompt:

```cmd
build.bat
```

This script will:
1. Set up all required environment variables
2. Build the debug APK
3. Show the location of the generated APK

### Method 2: Using Android Studio

1. Open Android Studio
2. Select "Open an Existing Project"
3. Navigate to `D:\workspace\android\Projects\scanner`
4. Wait for Gradle sync to complete
5. Click "Build" → "Build Bundle(s) / APK(s)" → "Build APK(s)"

### Method 3: Manual Gradle Build

From the project directory:

```cmd
REM Set environment variables
set JAVA_HOME=D:\workspace\android\jdk\jdk-17.0.2
set ANDROID_HOME=D:\workspace\android\android-sdk

REM Build debug APK
gradlew.bat assembleDebug

REM Build release APK (requires signing configuration)
gradlew.bat assembleRelease
```

## Output Location

After a successful build, find the APK at:
```
app\build\outputs\apk\debug\app-debug.apk
```

## Installing on Device

### Using Gradle

```cmd
gradlew.bat installDebug
```

### Using ADB

```cmd
adb install app\build\outputs\apk\debug\app-debug.apk
```

## Build Variants

- **Debug**: Development build with debugging enabled
  ```cmd
  gradlew.bat assembleDebug
  ```

- **Release**: Optimized production build (requires signing)
  ```cmd
  gradlew.bat assembleRelease
  ```

## Troubleshooting

### "JAVA_HOME is not set"
Run the environment setup script first:
```cmd
D:\workspace\android\setup-android-env.bat
```

### Gradle Sync Failed
1. Ensure internet connection is available (for first build)
2. Check that SDK is properly installed
3. Try: `gradlew.bat clean`

### Build Failed
1. Check error messages in the output
2. Ensure all dependencies can be downloaded
3. Try cleaning: `gradlew.bat clean build`

## Common Gradle Commands

```cmd
gradlew.bat tasks              # List all available tasks
gradlew.bat clean              # Clean build directory
gradlew.bat assembleDebug      # Build debug APK
gradlew.bat installDebug       # Install debug APK on device
gradlew.bat uninstallDebug     # Uninstall from device
gradlew.bat lint               # Run lint checks
gradlew.bat test               # Run unit tests
```

## Project Structure

```
scanner/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/com/scanner/app/
│   │       │   ├── data/          # Data models
│   │       │   ├── scanner/       # Camera & ML Kit integration
│   │       │   ├── ui/            # Compose UI screens
│   │       │   └── MainActivity.kt
│   │       ├── res/               # Resources
│   │       └── AndroidManifest.xml
│   └── build.gradle.kts
├── gradle/
│   └── wrapper/
├── build.gradle.kts
├── settings.gradle.kts
├── local.properties
└── build.bat
```

## Performance Notes

- First build may take 5-10 minutes (downloads dependencies)
- Subsequent builds are much faster (uses Gradle cache)
- Release builds take longer due to optimization and minification
