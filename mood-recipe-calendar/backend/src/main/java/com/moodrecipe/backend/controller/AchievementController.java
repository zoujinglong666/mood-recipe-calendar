package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.entity.UserAchievement;
import com.moodrecipe.backend.service.AchievementCatalog;
import com.moodrecipe.backend.service.AchievementService;
import com.moodrecipe.backend.repository.UserAchievementRepository;
import org.springframework.web.bind.annotation.*;

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
    public Map<String, Object> list(@RequestHeader("X-Openid") String openid) {
        List<UserAchievement> unlocked = repo.findByOpenid(openid);
        List<Map<String, Object>> all = catalog.all().stream().map(a -> {
            boolean isUnlocked = unlocked.stream().anyMatch(u -> u.getAchievementCode().equals(a.code()));
            return Map.<String, Object>of(
                "code", a.code(),
                "name", a.name(),
                "desc", a.desc(),
                "emoji", a.emoji(),
                "unlocked", isUnlocked
            );
        }).toList();
        return Map.of(
            "total", catalog.all().size(),
            "unlockedCount", unlocked.size(),
            "achievements", all
        );
    }

    /** 手动触发一次成就检测（记录用餐后自动调用，这里手动触发用）。 */
    @PostMapping("/check")
    public Map<String, Object> check(@RequestHeader("X-Openid") String openid) {
        List<String> newly = achievementService.checkAndUnlock(openid);
        return Map.of("newlyUnlocked", newly, "count", newly.size());
    }
}
