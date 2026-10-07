package com.moodrecipe.backend.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
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

    public GlobalExceptionHandler(WxPusherNotifier wxPusherNotifier) {
        this.wxPusherNotifier = wxPusherNotifier;
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

    /** 兜底：所有未捕获异常 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAll(Exception e, HttpServletRequest request) {
        // 不向前端泄露堆栈，但用日志框架完整记录（带请求路径），方便线上定位；不再用 printStackTrace 丢到 stderr 难检索
        log.error("[uncaught-500] {} {} -> {}: {}", request.getMethod(), request.getRequestURI(), e.getClass().getName(), e.getMessage(), e);
        // 关键服务器错误推送到微信，便于第一时间感知（异步、失败不影响主流程）
        wxPusherNotifier.send(String.format("[锅仔后端 500 告警] %s %s%n%s: %s",
                request.getMethod(), request.getRequestURI(), e.getClass().getSimpleName(), e.getMessage()));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error(500, "服务器内部错误，请稍后重试"));
    }
}
