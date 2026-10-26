# -*- coding: utf-8 -*-
"""Add CannotAcquireLockException handler to GlobalExceptionHandler (503, no alert)."""
import io, sys

P = r"C:\Users\DELL\DoubaoWork\chats\2026-09-01\new-chat\mood-recipe-calendar\backend\src\main\java\com\moodrecipe\backend\common\GlobalExceptionHandler.java"

with io.open(P, "r", encoding="utf-8") as f:
    s = f.read()

# 1. add import
if "import org.springframework.dao.CannotAcquireLockException;" not in s:
    s = s.replace(
        "import org.springframework.web.server.ResponseStatusException;",
        "import org.springframework.web.server.ResponseStatusException;\nimport org.springframework.dao.CannotAcquireLockException;"
    )

# 2. add handler before the catch-all @ExceptionHandler(Exception.class)
anchor = """    /** 兜底：所有未捕获异常 */
    @ExceptionHandler(Exception.class)"""
handler = """    /**
     * 数据库锁竞争（行锁等待超时 / 死锁，CannotAcquireLockException）：
     * 并发尖峰下的瞬时故障，业务侧（如配额扣减）已整体回滚、无副作用。
     * 转 503「系统繁忙」并抑制 500 告警——否则每次并发补图/签到尖峰都会推一条
     * 「锅仔后端 500 告警」，且 UnexpectedRollbackException 同样源于此路径。
     */
    @ExceptionHandler(CannotAcquireLockException.class)
    public ResponseEntity<ApiResponse<Void>> handleLockContention(CannotAcquireLockException e, HttpServletRequest request) {
        log.warn("[lock-contention] {} {} -> 503: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ApiResponse.error(503, "系统繁忙，请稍后重试"));
    }

    /** 兜底：所有未捕获异常 */
    @ExceptionHandler(Exception.class)"""
if anchor not in s:
    print("ERROR: catch-all anchor not found")
    sys.exit(1)
s = s.replace(anchor, handler)

with io.open(P, "w", encoding="utf-8", newline="\n") as f:
    f.write(s)
print("OK: GlobalExceptionHandler lock handler added")
