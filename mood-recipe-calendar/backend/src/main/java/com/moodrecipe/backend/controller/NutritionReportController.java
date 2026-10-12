package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import com.moodrecipe.backend.service.NutritionReportService;
import com.moodrecipe.backend.service.UsageQuotaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/** 本月营养月报：会员专享（家庭共享成员同样可看自己的报告）。 */
@RestController
@RequestMapping("/api/nutrition-report")
public class NutritionReportController {
    private final NutritionReportService reports;
    private final UsageQuotaService quotas;

    @Autowired
    public NutritionReportController(NutritionReportService reports, UsageQuotaService quotas) {
        this.reports = reports;
        this.quotas = quotas;
    }

    /** 当月报告；month 参数可选（YYYY-MM），缺省为本月。 */
    @GetMapping("/current")
    public ApiResponse<?> current(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
            @RequestParam(value = "month", required = false) String month) {
        if (!quotas.member(openid))
            return ApiResponse.error(403, "本月营养月报为会员专享，开通后每月一期");
        return ApiResponse.ok(reports.report(openid, month));
    }
}
