@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo ========================================
echo   心情菜谱日历 - 后端启动脚本
echo ========================================
echo.

REM 检查 Java
if not exist "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot\bin\java.exe" (
    echo [错误] 未找到 Java 21，请检查安装路径
    pause
    exit /b 1
)

REM 检查 jar 包
if not exist "target\backend-0.0.1-SNAPSHOT.jar" (
    echo [提示] 未找到 jar 包，正在打包...
    call mvnw.cmd package -DskipTests -q
    if errorlevel 1 (
        echo [错误] 打包失败
        pause
        exit /b 1
    )
)

REM 检查端口是否被占用
netstat -ano | findstr ":8080" | findstr "LISTENING" >nul
if %errorlevel%==0 (
    echo [提示] 端口 8080 已被占用，正在关闭旧进程...
    for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8080" ^| findstr "LISTENING"') do (
        taskkill /F /PID %%a >nul 2>&1
    )
    timeout /t 2 /nobreak >nul
)

REM 设置环境变量
set DB_PASSWORD=12345678
set WECHAT_APPID=wx4da25d0ce1a4938c
set WECHAT_SECRET=cb738ee73061756874a44461066cbc10

REM 启动后端
echo [启动] 正在启动后端服务...
echo [日志] 标准输出: backend.log
echo [日志] 错误输出: backend-error.log
echo.

start "心情菜谱日历-后端" /min "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot\bin\java.exe" -jar target\backend-0.0.1-SNAPSHOT.jar

echo [完成] 后端已启动，端口 8080
echo [提示] 关闭此窗口不会停止后端，如需停止请在任务管理器结束 java.exe
echo.
timeout /t 5 /nobreak >nul
