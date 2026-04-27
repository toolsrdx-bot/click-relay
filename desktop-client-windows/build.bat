@echo off
REM Build a single Gorilla.exe using PyInstaller

echo === Gorilla — Windows EXE Builder ===
echo.

where python >nul 2>nul
if errorlevel 1 (
    echo Python not found. Install from https://www.python.org/downloads/
    pause
    exit /b 1
)

echo Installing build dependencies...
python -m pip install --upgrade pip
python -m pip install pyinstaller websockets

echo.
echo Building Gorilla.exe (this takes 1-2 minutes)...
echo.

python -m PyInstaller ^
    --onefile ^
    --noconsole ^
    --name "Gorilla" ^
    --clean ^
    --hidden-import websockets.legacy ^
    --hidden-import websockets.legacy.client ^
    client.py

if errorlevel 1 (
    echo.
    echo Build failed.
    pause
    exit /b 1
)

echo.
echo ============================================
echo  Build complete.
echo  Your .exe is at: dist\Gorilla.exe
echo ============================================
echo.
pause
