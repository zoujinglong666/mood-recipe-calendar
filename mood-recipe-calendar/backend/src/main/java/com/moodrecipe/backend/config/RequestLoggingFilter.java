package com.moodrecipe.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * 记录每一个 HTTP 请求：方法、URI（含 query）、响应状态码、耗时、客户端 IP、User-Agent。
 * 使用独立 logger（com.moodrecipe.backend.request）输出单行，便于落盘到 logs/request.log 后 grep 排查。
 *
 * 说明：默认不记录请求/响应 body，避免把 session_key、密码、微信 secret 等敏感信息写进日志。
 * 如需排查具体接口入参，可后续开启 body 记录并加掩码。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("com.moodrecipe.backend.request");

    @Value("${request-log.enabled:true}")
    private boolean enabled;

    @Value("${request-log.exclude:/api/health}")
    private String exclude;

    private volatile List<String> excludeList;

    private List<String> excludeList() {
        if (excludeList == null) {
            excludeList = Arrays.stream(exclude.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .toList();
        }
        return excludeList;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled) {
            return true;
        }
        String uri = request.getRequestURI();
        return excludeList().stream().anyMatch(uri::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        String clientIp = resolveClientIp(request);
        String method = request.getMethod();
        String uri = buildUri(request);
        String ua = request.getHeader("User-Agent");
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            int status = response.getStatus();
            log.info("method={} uri={} status={} duration={}ms ip={} ua={}",
                    method, uri, status, durationMs, clientIp, ua);
        }
    }

    private String buildUri(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        return (query == null || query.isEmpty()) ? uri : uri + "?" + query;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
