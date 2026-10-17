package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import com.moodrecipe.backend.service.OpsMetricsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 运营观测查询（仅管理员）：留存曲线、单日总览（漏斗/付费转化/K 因子/LLM 成本）。
 * 数据全部由既有事实表实时聚合，不新建表。
 */
@RestController
@RequestMapping("/api/admin/metrics")
public class AdminOpsMetricsController {

    private final OpsMetricsService metrics;
    private final Set<String> adminOpenids;

    public AdminOpsMetricsController(OpsMetricsService metrics,
                                     @Value("${app.admin-openids:}") String adminOpenidsProp) {
        this.metrics = metrics;
        this.adminOpenids = Arrays.stream(adminOpenidsProp.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private void requireAdmin(String openid) {
        if (!adminOpenids.contains(openid)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无管理员权限");
        }
    }

    /** 单日总览：DAU/漏斗/渗透/付费/转化/K 因子/LLM 调用量与估算成本 */
    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
            @RequestParam(required = false) String date) {
        requireAdmin(openid);
        LocalDate day = date == null || date.isBlank()
                ? LocalDate.now() : LocalDate.parse(date);
        return ApiResponse.ok(metrics.overview(day));
    }

    /** 注册 cohort 留存曲线（默认最近 14 天，最多 90）：D1/D3/D7 */
    @GetMapping("/retention")
    public ApiResponse<Object> retention(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
            @RequestParam(defaultValue = "14") int days) {
        requireAdmin(openid);
        return ApiResponse.ok(metrics.retention(days));
    }

    /** 立即把运营播报推送到 WxPusher（异步）；返回推送文本供确认 */
    @PostMapping("/push")
    public ApiResponse<String> pushNow(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
            @RequestParam(required = false) String date) {
        requireAdmin(openid);
        LocalDate day = date == null || date.isBlank()
                ? LocalDate.now() : LocalDate.parse(date);
        return ApiResponse.ok(metrics.pushNow(day));
    }
}
