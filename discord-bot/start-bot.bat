@echo off
echo ================================
echo  Grand Flip Out Discord Bot
echo ================================
echo.

REM Check for Python
python --version >nul 2>&1
if errorlevel 1 (
    python3 --version >nul 2>&1
    if errorlevel 1 (
        echo ERROR: Python not found! Install Python 3.8+ from python.org
        pause
        exit /b 1
    )
    set PYTHON=python3
) else (
    set PYTHON=python
)

REM Install dependencies
echo Installing dependencies...
%PYTHON% -m pip install -r requirements.txt --quiet
echo.

REM Check .env
if not exist .env (
    echo ERROR: No .env file found!
    echo Create a .env file with your DISCORD_TOKEN
    pause
    exit /b 1
)

echo Starting Grand Flip Out bot...
echo Press Ctrl+C to stop
echo.
%PYTHON% bot.py
pause
