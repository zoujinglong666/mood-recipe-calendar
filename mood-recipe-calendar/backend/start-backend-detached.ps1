# 心情菜谱后端【脱离会话】启动脚本
# 用 WMI (Win32_Process.Create) 启动，父进程是系统 WMI 服务，任何终端会话退出都不会波及后端进程。
# 用法：powershell -ExecutionPolicy Bypass -File start-backend-detached.ps1
$ErrorActionPreference = "Stop"
$b = "C:\Users\DELL\DoubaoWork\chats\2026-09-01\new-chat\mood-recipe-calendar\backend"
$java = "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot\bin\java.exe"
$secret = "94e7e896a38088da4ee0fadc259e621e"

# 若已有后端在跑，直接退出
$listen = Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue
if ($listen) {
    Write-Host "8080 已被 PID $($listen.OwningProcess) 监听，后端已在运行，无需启动。"
    exit 0
}

$cmdLine = "cmd /c set WECHAT_SECRET=$secret&& `"$java`" -Xms256m -Xmx768m -jar `"$b\target\backend-0.0.1-SNAPSHOT.jar`" > `"$b\backend_stdout.log`" 2> `"$b\backend_stderr.log`""
$wmi = [WMIClass]"\\.\root\cimv2:Win32_Process"
$ret = $wmi.Create($cmdLine)
if ($ret.ReturnValue -ne 0) {
    throw "WMI 创建进程失败 ReturnValue=$($ret.ReturnValue)"
}
Write-Host "后端已启动 (cmd PID=$($ret.ProcessId))，等待就绪..."
Start-Sleep -Seconds 20
try {
    $r = Invoke-WebRequest -Uri "http://localhost:8080/api/health" -UseBasicParsing -TimeoutSec 8
    Write-Host "HEALTH OK: $($r.Content)"
} catch {
    Write-Host "HEALTH FAIL: $($_.Exception.Message)（请查看 backend_stderr.log）"
}
