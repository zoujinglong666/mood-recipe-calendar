package com.moodrecipe.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 为所有响应（含错误响应）补充安全响应头，缩小 XSS / 点击劫持 / 信息泄露面。
 * 放在限流过滤器之前（@Order 更小），确保被限流直接返回的 429 也带这些头。
 */
@Component
@Order(-110)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false; // 错误响应同样补安全头
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpServletResponse res = (HttpServletResponse) response;
        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setHeader("X-Frame-Options", "DENY");
        res.setHeader("Referrer-Policy", "no-referrer");
        res.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=()");
        res.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        // API 只返回 JSON，无 HTML 渲染需求：极简 CSP 兜底，万一未来误挂页面也能挡 XSS
        res.setHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
        chain.doFilter(request, response);
    }
}
