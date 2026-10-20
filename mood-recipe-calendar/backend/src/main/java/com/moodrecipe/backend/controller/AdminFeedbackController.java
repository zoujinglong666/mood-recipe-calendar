package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import com.moodrecipe.backend.entity.UserFeedback;
import com.moodrecipe.backend.repository.UserFeedbackRepository;
import com.moodrecipe.backend.service.DailyFeedbackLogService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理员反馈中心 + 反馈日报手动触发（仅管理员）。
 * 反馈日报：默认推昨天，也可传 date=yyyy-MM-dd 补推任意一天。
 * 反馈中心：分页列表（按状态筛选）+ 状态流转（OPEN→PROCESSING→RESOLVED/CLOSED，可重开）。
 */
@RestController
@RequestMapping("/api/admin/feedback")
public class AdminFeedbackController {

    /** 与用户侧 FeedbackController 提交时的四类、前端状态文案保持一致 */
    private static final List<String> STATUSES = List.of("OPEN", "PROCESSING", "RESOLVED", "CLOSED");

    private final DailyFeedbackLogService feedbackLog;
    private final UserFeedbackRepository feedback;
    private final Set<String> adminOpenids;

    public AdminFeedbackController(DailyFeedbackLogService feedbackLog,
                                   UserFeedbackRepository feedback,
                                   @Value("${app.admin-openids:}") String adminOpenidsProp) {
        this.feedbackLog = feedbackLog;
        this.feedback = feedback;
        this.adminOpenids = Arrays.stream(adminOpenidsProp.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private void requireAdmin(String openid) {
        if (!adminOpenids.contains(openid)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无管理员权限");
        }
    }

    @PostMapping("/push")
    public ApiResponse<String> push(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
            @RequestParam(required = false) String date) {
        requireAdmin(openid);
        LocalDate day = date == null || date.isBlank()
                ? LocalDate.now() : LocalDate.parse(date);
        return ApiResponse.ok(feedbackLog.pushForDay(day));
    }

    /** 反馈中心列表：按状态筛选 + 分页 + 各状态计数（供筛选 chips）。 */
    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireAdmin(openid);
        int safeSize = Math.min(50, Math.max(1, size));
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = (status == null || status.isBlank())
                ? feedback.findAllByOrderByCreatedAtDesc(pageable)
                : feedback.findByStatusOrderByCreatedAtDesc(status, pageable);
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("ALL", feedback.count());
        STATUSES.forEach(s -> counts.put(s, feedback.countByStatus(s)));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", result.getContent());
        data.put("total", result.getTotalElements());
        data.put("page", page);
        data.put("size", safeSize);
        data.put("counts", counts);
        return ApiResponse.ok(data);
    }

    /** 状态流转：非管理员 403；非法状态 400；反馈不存在 404。 */
    @PutMapping("/{id}/status")
    public ApiResponse<UserFeedback> updateStatus(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
            @PathVariable Long id,
            @RequestBody StatusRequest body) {
        requireAdmin(openid);
        if (body == null || body.status() == null || !STATUSES.contains(body.status()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非法的反馈状态");
        UserFeedback item = feedback.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "反馈不存在"));
        item.setStatus(body.status());
        return ApiResponse.ok(feedback.save(item));
    }

    public record StatusRequest(String status) {}
}
