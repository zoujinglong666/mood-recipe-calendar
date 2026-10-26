package com.moodrecipe.backend.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.CannotAcquireLockException;

import org.apache.tomcat.util.http.InvalidParameterException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.moodrecipe.backend.service.WxPusherNotifier;

/**
 * 全局异常处理器：统一错误响应格式，避免堆栈信息泄露给前端。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final WxPusherNotifier wxPusherNotifier;
    /** [2026-10-26] 当日 500 计数（P2-9 每日健康摘要用），推告警的同时累加。 */
    private final com.moodrecipe.backend.common.HealthMetrics healthMetrics;

    public GlobalExceptionHandler(WxPusherNotifier wxPusherNotifier,
                                  com.moodrecipe.backend.common.HealthMetrics healthMetrics) {
        this.wxPusherNotifier = wxPusherNotifier;
        this.healthMetrics = healthMetrics;
    }

    /** 参数校验失败（@Valid / @Validated） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
            .map(err -> err.getField() + ": " + err.getDefaultMessage())
            .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(ApiResponse.error(400, "参数校验失败: " + msg));
    }

    /** 约束违反（@Validated 在方法参数上） */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
            .map(v -> v.getPropertyPath() + ": " + v.getMessage())
            .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(ApiResponse.error(400, "参数校验失败: " + msg));
    }

    /** 缺少必填请求参数 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest().body(ApiResponse.error(400, "缺少必填参数: " + e.getParameterName()));
    }

    /** 请求体解析失败（JSON 格式错误） */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(ApiResponse.error(400, "请求体格式错误，请检查 JSON 格式"));
    }

    /** 参数类型不匹配 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(ApiResponse.error(400,
            "参数 " + e.getName() + " 类型错误，期望 " + (e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "正确类型")));
    }

    /** 文件上传超限 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUpload(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(ApiResponse.error(413, "文件大小超过限制（最大 10MB）"));
    }

    /** multipart 请求缺失文件字段（如 POST /api/upload/image 未携带 file 表单项） */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingPart(MissingServletRequestPartException e) {
        return ResponseEntity.badRequest()
            .body(ApiResponse.error(400, "缺少上传文件字段: " + e.getRequestPartName()));
    }

    /** multipart 请求本身解析失败（非 multipart 请求、boundary 缺失、请求体损坏等） */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipart(MultipartException e) {
        return ResponseEntity.badRequest()
            .body(ApiResponse.error(400, "文件上传格式错误，请重新选择图片后上传"));
    }

    /** 非法参数（业务层主动抛出）；详情仅落日志，不回显给客户端，避免「用户/订单不存在」被枚举探测。 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArg(IllegalArgumentException e) {
        log.warn("IllegalArgumentException: {}", e.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.error(400, "请求参数不合法，请检查输入"));
    }

    /**
     * 预期内的 HTTP 状态异常（如权限守卫抛的 403 FORBIDDEN）：按异常自带的状态码原样返回，
     * 仅记 warn 日志、不推送 500 告警。否则会被下方 catch-all 兜底当成 500 误报——
     * 例如非管理员访问 /admin 接口被鉴权拒绝（403），本是正常业务分支，却会触发「锅仔后端 500 告警」。
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatus(ResponseStatusException e, HttpServletRequest request) {
        int status = e.getStatusCode().value();
        log.warn("[expected-status] {} {} -> {}: {}", request.getMethod(), request.getRequestURI(), status, e.getReason());
        return ResponseEntity.status(e.getStatusCode()).body(ApiResponse.error(status, e.getReason()));
    }

    /**
     * 请求方法不被支持（如客户端用 GET 打了一个只接受 POST 的接口）：本应返回 405，
     * 但若被兜底 catch-all 当成 500 推送，会产生「锅仔后端 500 告警」误报。这里按 405 原样返回、只记 warn、不推送。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        log.warn("[method-not-allowed] {} {} -> 405，支持的方法: {}", request.getMethod(), request.getRequestURI(), e.getSupportedMethods());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(ApiResponse.error(405, "请求方法不被支持"));
    }

    /**
     * 请求参数解码失败（如 URL 里出现非法百分号编码 / 多字节截断，Tomcat 抛 InvalidParameterException）。
     * 这是客户端发了畸形请求（探测/爬虫/旧包乱码），不是服务器故障，应返回 400 且不推送 500 告警。
     */
    @ExceptionHandler(InvalidParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidParameter(InvalidParameterException e, HttpServletRequest request) {
        log.warn("[bad-request] {} {} -> 400 参数解码失败: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.error(400, "请求参数包含无法解码的字符"));
    }

    /**
     * 响应写出失败：绝大多数情况是客户端在长耗时请求（如周菜单同步生成）期间已断开连接
     * （Broken pipe / Connection reset / Tomcat ClientAbortException）。这是客户端超时或切走导致的，
     * 不是服务器错误，不应作为 500 告警。仅当根因确为客户端断开时按 debug 记日志、不推送；
     * 其余真实写不出（如序列化错误）仍走 500 流程。
     */
    @ExceptionHandler(HttpMessageNotWritableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotWritable(HttpMessageNotWritableException e, HttpServletRequest request) {
        if (isClientAbort(e.getCause())) {
            log.debug("[client-abort] {} {} 客户端已断开，响应写出失败（非服务器错误，不告警）: {}",
                    request.getMethod(), request.getRequestURI(), rootMessage(e.getCause()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        log.error("[write-500] {} {} -> {}: {}", request.getMethod(), request.getRequestURI(), e.getClass().getName(), e.getMessage(), e);
        healthMetrics.incrementServerErrors();
        wxPusherNotifier.send(String.format("[锅仔后端 500 告警] %s %s%n%s: %s",
                request.getMethod(), request.getRequestURI(), e.getClass().getSimpleName(), e.getMessage()));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error(500, "服务器内部错误，请稍后重试"));
    }

    /** 判断异常根因是否为客户端主动断开连接（Broken pipe / Connection reset / ClientAbortException）。 */
    private static boolean isClientAbort(Throwable cause) {
        Throwable t = cause;
        while (t != null) {
            if (t instanceof IOException
                    && (t.getClass().getName().equals("org.apache.catalina.connector.ClientAbortException")
                        || messageContains(t, "Broken pipe", "Connection reset", "Connection aborted", "ClientAbort"))) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    private static String rootMessage(Throwable cause) {
        Throwable t = cause;
        while (t != null && t.getCause() != null) t = t.getCause();
        return t == null ? "" : t.getMessage();
    }

    private static boolean messageContains(Throwable t, String... keywords) {
        String m = t.getMessage();
        if (m == null) return false;
        for (String kw : keywords) if (m.contains(kw)) return true;
        return false;
    }

    /**
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
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAll(Exception e, HttpServletRequest request) {
        // 不向前端泄露堆栈，但用日志框架完整记录（带请求路径），方便线上定位；不再用 printStackTrace 丢到 stderr 难检索
        log.error("[uncaught-500] {} {} -> {}: {}", request.getMethod(), request.getRequestURI(), e.getClass().getName(), e.getMessage(), e);
        // 关键服务器错误推送到微信，便于第一时间感知（异步、失败不影响主流程）。
        // 但若异常本质属于「客户端错误」（如框架层面的方法不允许 / 4xx 状态异常），只是正常业务分支被错误兜底，
        // 不应推送 500 告警造成误报——此类已在上方显式处理器里给出正确状态码，这里再兜底一层防漏网。
        if (!isClientError(e)) {
            healthMetrics.incrementServerErrors();
            wxPusherNotifier.send(String.format("[锅仔后端 500 告警] %s %s%n%s: %s",
                    request.getMethod(), request.getRequestURI(), e.getClass().getSimpleName(), e.getMessage()));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error(500, "服务器内部错误，请稍后重试"));
    }

    /** 判断异常是否本质属于「客户端错误」：框架层面已规定状态码的 4xx（方法不允许、参数解码失败、响应状态异常等），不应触发 500 告警。 */
    private static boolean isClientError(Exception e) {
        if (e instanceof HttpRequestMethodNotSupportedException) return true;
        if (e instanceof InvalidParameterException) return true;
        if (e instanceof ResponseStatusException rs && rs.getStatusCode().is4xxClientError()) return true;
        return false;
    }
}
