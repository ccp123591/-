@echo off
setlocal EnableDelayedExpansion
chcp 65001 >nul 2>&1
title FitCoach Fullstack - 一键启动

echo.
echo   ============================================
echo     FitCoach AI 居家健身陪练 · 全栈启动脚本
echo   ============================================
echo.

:: 启动后端
echo [1/2] 启动后端 Spring Boot ...
cd backend
start "FitCoach Backend" cmd /k "mvn spring-boot:run"
cd ..

timeout /t 3 /nobreak >nul

:: 启动前端
echo [2/2] 启动前端 Vite ...
cd frontend
if not exist node_modules (
    echo   首次启动需安装依赖 ...
    call npm install
)
start "FitCoach Frontend" cmd /k "npm run dev"
cd ..

timeout /t 5 /nobreak >nul
start "" "http://localhost:5173"

echo.
echo   后端  http://localhost:8080/swagger-ui.html
echo   前端  http://localhost:5173
echo.
echo   关闭对应窗口即可停止服务
echo.
pause
