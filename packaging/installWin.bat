@echo off
REM Rotlin one-click install for Windows. Double-click me.
setlocal EnableDelayedExpansion

where java >nul 2>nul
if errorlevel 1 (
  echo.
  echo [X] Java not found.
  echo     Install a JDK 21+ first: https://adoptium.net/temurin/releases/?version=21
  echo     Pick the Windows x64 .msi, then run this again.
  echo.
  pause
  exit /b 1
)

set "BIN=%~dp0bin"

REM Read the current *user* PATH only (not system), so we don't bloat it.
set "USERPATH="
for /f "tokens=2,*" %%A in ('reg query HKCU\Environment /v PATH 2^>nul') do set "USERPATH=%%B"

echo %USERPATH% | find /i "%BIN%" >nul
if not errorlevel 1 (
  echo [OK] Rotlin already on your PATH.
) else (
  if defined USERPATH (
    setx PATH "%USERPATH%;%BIN%" >nul
  ) else (
    setx PATH "%BIN%" >nul
  )
  echo [OK] Added Rotlin to your PATH.
)

echo.
echo Done. CLOSE this window, open a NEW terminal, then run:
echo.
echo     rotlin cook examples\hello.rot
echo.
pause
