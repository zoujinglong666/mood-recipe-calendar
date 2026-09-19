# Mood Recipe backend detached launcher (WMI based)
# Parent process is system WMI service, survives any terminal session cleanup.
# Usage: powershell -ExecutionPolicy Bypass -File start-backend-detached.ps1
$ErrorActionPreference = "Stop"
$b = "C:\Users\DELL\DoubaoWork\chats\2026-09-01\new-chat\mood-recipe-calendar\backend"
$java = "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot\bin\java.exe"

# Skip if backend already listening on 8080
$listen = Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue
if ($listen) {
    Write-Host "Port 8080 already in use by PID $($listen.OwningProcess), backend running."
    exit 0
}

# IMPORTANT: cd /d to backend dir FIRST. spring.config.import=optional:file:./.env is relative
# to the working directory; WMI default cwd is C:\Windows\System32, so .env would not load
# (virtual pay / Agnes / COS keys all empty). Local IDE runs work because cwd = backend.
$cmdLine = "cmd /c cd /d `"$b`" && `"$java`" -Xms256m -Xmx768m -jar `"$b\target\backend-0.0.1-SNAPSHOT.jar`" > `"$b\backend_stdout.log`" 2> `"$b\backend_stderr.log`""
$wmi = [WMIClass]"\\.\root\cimv2:Win32_Process"
$ret = $wmi.Create($cmdLine)
if ($ret.ReturnValue -ne 0) {
    throw "WMI create failed ReturnValue=$($ret.ReturnValue)"
}
Write-Host "Backend started (cmd PID=$($ret.ProcessId)), waiting for ready..."
Start-Sleep -Seconds 20
try {
    $r = Invoke-WebRequest -Uri "http://localhost:8080/api/health" -UseBasicParsing -TimeoutSec 8
    Write-Host "HEALTH OK: $($r.Content)"
} catch {
    Write-Host "HEALTH FAIL: $($_.Exception.Message) (see backend_stderr.log)"
}
