@echo off
echo Gorilla - Windows setup
echo.

where python >nul 2>nul
if errorlevel 1 (
    echo Python not found. Install from https://www.python.org/downloads/
    echo During install, check "Add Python to PATH".
    pause
    exit /b 1
)

echo Python found:
python --version
echo.

echo Installing websockets...
python -m pip install --user websockets

echo.
echo Done. Run the client with:
echo   python client.py
echo.
pause
