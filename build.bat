@echo off
REM ─────────────────────────────────────────────────────────────────────
REM  SoliGeram — Windows build script
REM  Double-click or run in CMD/PowerShell
REM ─────────────────────────────────────────────────────────────────────

echo [SoliGeram] Build script starting...
echo Target: Telegram 13.0.2 ^| LSPosed module
echo.

REM Check Java
java -version >nul 2>&1
IF ERRORLEVEL 1 (
    echo [ERROR] Java not found!
    echo Install JDK 17 from: https://adoptium.net/
    pause
    exit /b 1
)
echo [OK] Java found

REM Check ANDROID_HOME
IF "%ANDROID_HOME%"=="" (
    IF "%ANDROID_SDK_ROOT%"=="" (
        echo [WARN] ANDROID_HOME not set
        echo Set it in System Environment Variables
        echo Example: ANDROID_HOME=C:\Users\YourName\AppData\Local\Android\Sdk
        pause
        exit /b 1
    )
)
echo [OK] Android SDK: %ANDROID_HOME%%ANDROID_SDK_ROOT%

REM Build
echo.
echo [*] Building debug APK...
call gradlew.bat assembleDebug

IF EXIST "app\build\outputs\apk\debug\app-debug.apk" (
    echo.
    echo [SUCCESS] APK built!
    echo Location: app\build\outputs\apk\debug\app-debug.apk
    echo.
    echo Install: adb install app\build\outputs\apk\debug\app-debug.apk
) ELSE (
    echo [ERROR] Build failed. Check output above.
)
pause
