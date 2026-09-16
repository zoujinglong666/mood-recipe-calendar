@echo off
rem 心情菜谱后端启动脚本（由 Windows 任务计划程序托管，独立于任何终端会话）
set WECHAT_SECRET=94e7e896a38088da4ee0fadc259e621e
"C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot\bin\java.exe" -Xms256m -Xmx768m -jar "C:\Users\DELL\DoubaoWork\chats\2026-09-01\new-chat\mood-recipe-calendar\backend\target\backend-0.0.1-SNAPSHOT.jar" > "C:\Users\DELL\DoubaoWork\chats\2026-09-01\new-chat\mood-recipe-calendar\backend\backend_stdout.log" 2> "C:\Users\DELL\DoubaoWork\chats\2026-09-01\new-chat\mood-recipe-calendar\backend\backend_stderr.log"
