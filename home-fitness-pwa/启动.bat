@echo off
setlocal EnableDelayedExpansion
chcp 65001 >nul 2>&1
cd /d "%~dp0"
title 居家健身陪练系统

set PORT=8080

echo.
echo   ============================================
echo     居家健身动作纠正与语音陪练系统 (PWA)
echo   ============================================
echo.

:: 检查 Python
where python >nul 2>&1
if !errorlevel! neq 0 (
    echo   [错误] 未找到 Python，请先安装 Python 3
    echo   下载地址: https://www.python.org/downloads/
    echo.
    pause
    exit /b 1
)

:: 检查端口是否被占用
netstat -ano 2>nul | findstr ":!PORT! " >nul 2>&1
if !errorlevel! equ 0 (
    echo   [!] 端口 !PORT! 已被占用，切换到 8081
    set PORT=8081
)

echo   正在启动本地服务器...
echo   地址: http://localhost:!PORT!
echo.
echo   按 Ctrl+C 可停止服务器
echo.

:: 后台启动服务器，等待后打开浏览器
start /b python -m http.server !PORT!
timeout /t 2 /nobreak >nul
start "" "http://localhost:!PORT!"

:: 等待用户关闭
echo   浏览器已打开，按任意键停止服务器...
pause >nul
taskkill /f /im python.exe >nul 2>&1
