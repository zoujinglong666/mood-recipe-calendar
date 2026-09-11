@echo off
chcp 65001 >nul
title 心情菜谱日历 - 后端服务 (自动重启)
cd /d "%~dp0"

echo ========================================
echo   心情菜谱日历 后端服务
echo   端口: 8080
echo   按 Ctrl+C 停止服务
echo ========================================
echo.

:loop
echo [%date% %time%] 启动后端服务...
java -jar target\backend-0.0.1-SNAPSHOT.jar
echo.
echo [%date% %time%] 后端服务已退出，5秒后自动重启...
echo 按 Ctrl+C 可停止自动重启
timeout /t 5 /nobreak >nul
goto loop
