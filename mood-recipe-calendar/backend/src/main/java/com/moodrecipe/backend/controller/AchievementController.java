package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import com.moodrecipe.backend.entity.UserAchievement;
import com.moodrecipe.backend.repository.UserAchievementRepository;
import com.moodrecipe.backend.service.AchievementCatalog;
import com.moodrecipe.backend.service.AchievementService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/achievements")
public class AchievementController {

    private final AchievementService achievementService;
    private final AchievementCatalog catalog;
    private final UserAchievementRepository repo;

    public AchievementController(AchievementService achievementService,
                                  AchievementCatalog catalog,
                                  UserAchievementRepository repo) {
        this.achievementService = achievementService;
        this.catalog = catalog;
        this.repo = repo;
    }

    /** 获取全部成就（含已解锁状态）。 */
    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        List<UserAchievement> unlocked = repo.findByOpenid(openid);
        List<Map<String, Object>> all = catalog.all().stream().map(a -> {
            boolean isUnlocked = unlocked.stream().anyMatch(u -> u.getAchievementCode().equals(a.code()));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", a.code());
            item.put("name", a.name());
            item.put("desc", a.desc());
            item.put("emoji", a.emoji());
            item.put("unlocked", isUnlocked);
            return item;
        }).toList();
        return ApiResponse.ok(Map.of(
            "total", catalog.all().size(),
            "unlockedCount", unlocked.size(),
            "achievements", all
        ));
    }

    /** 手动触发一次成就检测（记录用餐后自动调用，这里供下拉/进入页面时兜底刷新）。 */
    @PostMapping("/check")
    public ApiResponse<Map<String, Object>> check(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        List<String> newly = achievementService.checkAndUnlock(openid);
        return ApiResponse.ok(Map.of("newlyUnlocked", newly, "count", newly.size()));
    }
}
