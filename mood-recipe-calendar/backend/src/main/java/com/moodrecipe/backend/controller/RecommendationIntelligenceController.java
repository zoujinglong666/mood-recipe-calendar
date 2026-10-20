package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import com.moodrecipe.backend.entity.UserProfileSnapshot;
import com.moodrecipe.backend.model.RecommendationMetrics;
import com.moodrecipe.backend.model.UserProfile;
import com.moodrecipe.backend.repository.UserProfileSnapshotRepository;
import com.moodrecipe.backend.service.FeedbackLearningService;
import com.moodrecipe.backend.service.RecommendationMetricsService;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * [2026-10-20] 个性化与可观测性的对外接口。
 * - GET /api/intelligence/profile        当前用户动态画像（越用越准）
 * - GET /api/intelligence/profile/history 该用户画像演化时间线（回溯）
 * - GET /api/intelligence/metrics        全站推荐效果指标
 * - GET /api/intelligence/metrics/user   当前用户的效果 + 偏好匹配度
 */
@RestController
@RequestMapping("/api/intelligence")
public class RecommendationIntelligenceController {

    private final FeedbackLearningService learning;
    private final RecommendationMetricsService metrics;
    private final UserProfileSnapshotRepository snapshots;

    public RecommendationIntelligenceController(FeedbackLearningService learning,
                                                RecommendationMetricsService metrics,
                                                UserProfileSnapshotRepository snapshots) {
        this.learning = learning;
        this.metrics = metrics;
        this.snapshots = snapshots;
    }

    @GetMapping("/profile")
    public ApiResponse<UserProfile> profile(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        return ApiResponse.ok(learning.getProfile(openid));
    }

    @GetMapping("/profile/history")
    public ApiResponse<List<UserProfileSnapshot>> history(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        return ApiResponse.ok(snapshots.findByOpenidOrderBySnapshotAtDesc(openid, PageRequest.of(0, 50)));
    }

    @GetMapping("/metrics")
    public ApiResponse<RecommendationMetrics> metrics() {
        return ApiResponse.ok(metrics.overview());
    }

    @GetMapping("/metrics/user")
    public ApiResponse<RecommendationMetrics> metricsUser(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        return ApiResponse.ok(metrics.forUser(openid));
    }
}
