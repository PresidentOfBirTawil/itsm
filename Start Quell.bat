@echo off
rem ---------------------------------------------------------------------------
rem  Double-click this file to start Quell from the source code.
rem  It finds Java 25 or downloads a private copy into this folder (nothing is
rem  installed on the computer), builds Quell the first time, and opens your browser.
rem  Keep the window open while you use Quell; close it to stop Quell.
rem ---------------------------------------------------------------------------
setlocal
cd /d "%~dp0"
title Quell
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0packaging\start-windows.ps1"
if errorlevel 1 (
    echo.
    echo Quell could not start. The messages above say why.
    pause
)
