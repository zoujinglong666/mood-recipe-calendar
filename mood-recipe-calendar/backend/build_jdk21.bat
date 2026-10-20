@echo off
chcp 65001 >nul
set "JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot"
set "MAVEN_OPTS=-Dfile.encoding=UTF-8"
cd /d %~dp0
call mvnw.cmd clean package -DskipTests -B > build.log 2>&1
echo DONE_BUILD_EXIT=%errorlevel% >> build.log
