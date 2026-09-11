package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import com.moodrecipe.backend.service.WeeklyMealPlanService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/weekly-plans")
public class WeeklyMealPlanController {
    private final WeeklyMealPlanService plans;
    public WeeklyMealPlanController(WeeklyMealPlanService plans) { this.plans = plans; }
    @GetMapping("/current") public ApiResponse<?> current(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) { return plans.current(openid).map(ApiResponse::ok).orElseGet(() -> ApiResponse.error(404, "还没有本周计划")); }
    @GetMapping("/history") public ApiResponse<?> history(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) { return ApiResponse.ok(plans.history(openid)); }
    @GetMapping("/{id}") public ApiResponse<?> get(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid, @PathVariable Long id) { return plans.get(openid, id).map(ApiResponse::ok).orElseGet(() -> ApiResponse.error(404, "计划不存在")); }
    @PostMapping("/generate") public ApiResponse<?> generate(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid, @RequestBody WeeklyMealPlanService.GenerateRequest request) { return request == null || request.people() < 1 ? ApiResponse.error(400, "请填写用餐人数") : ApiResponse.ok(plans.generate(openid, request)); }
    @PostMapping("/{id}/favorite") public ApiResponse<?> favorite(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid, @PathVariable Long id) { return plans.toggleFavorite(openid, id).map(ApiResponse::ok).orElseGet(() -> ApiResponse.error(404, "计划不存在")); }
    @PostMapping("/{id}/days/{index}/cover") public ApiResponse<?> cover(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid, @PathVariable Long id, @PathVariable int index) { return plans.ensureCover(openid, id, index).map(ApiResponse::ok).orElseGet(() -> ApiResponse.error(404, "计划不存在")); }
    @PostMapping("/{id}/days/{index}/replace") public ApiResponse<?> replace(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid, @PathVariable Long id, @PathVariable int index) { return plans.replaceDay(openid, id, index).map(ApiResponse::ok).orElseGet(() -> ApiResponse.error(404, "计划不存在")); }
    @PostMapping("/{id}/shopping/{name}") public ApiResponse<?> toggle(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid, @PathVariable Long id, @PathVariable String name) { return plans.toggleShopping(openid, id, name).map(ApiResponse::ok).orElseGet(() -> ApiResponse.error(404, "购物项不存在")); }
}
