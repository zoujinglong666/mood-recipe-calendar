package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.UserAchievement;
import com.moodrecipe.backend.entity.UserRecord;
import com.moodrecipe.backend.entity.RecipeInteraction;
import com.moodrecipe.backend.repository.UserAchievementRepository;
import com.moodrecipe.backend.repository.UserRecordRepository;
import com.moodrecipe.backend.repository.RecipeInteractionRepository;
import com.moodrecipe.backend.config.AppClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 成就自动检测服务：每次用户记录用餐后调用，自动解锁符合条件的成就。
 */
@Service
public class AchievementService {

    private static final Logger log = LoggerFactory.getLogger(AchievementService.class);

    private final UserAchievementRepository achievements;
    private final UserRecordRepository records;
    private final RecipeInteractionRepository interactions;
    private final AchievementCatalog catalog;

    public AchievementService(UserAchievementRepository achievements,
                               UserRecordRepository records,
                               RecipeInteractionRepository interactions,
                               AchievementCatalog catalog) {
        this.achievements = achievements;
        this.records = records;
        this.interactions = interactions;
        this.catalog = catalog;
    }

    /** 检查并解锁所有符合条件的成就，返回新解锁的成就 code 列表。 */
    public List<String> checkAndUnlock(String openid) {
        List<String> newlyUnlocked = new ArrayList<>();
        try {
            List<UserRecord> allRecords = records.findTop30ByOpenidOrderByCreatedAtDesc(openid);
            int totalRecords = (int) records.countByOpenid(openid);

            // 不同菜品数
            Set<String> distinctDishes = allRecords.stream()
                    .map(UserRecord::getDishName)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .collect(Collectors.toSet());
            int distinctCount = distinctDishes.size();

            // 连续记录天数
            int streak = calcStreak(allRecords);

            // 点赞数
            long likeCount = interactions.findByOpenidAndAction(openid, "LIKE").size();

            // 记录数成就
            unlockIf(openid, "first_pick", totalRecords >= 1, newlyUnlocked);
            unlockIf(openid, "record_3", totalRecords >= 3, newlyUnlocked);
            unlockIf(openid, "record_10", totalRecords >= 10, newlyUnlocked);
            unlockIf(openid, "record_30", totalRecords >= 30, newlyUnlocked);
            unlockIf(openid, "record_50", totalRecords >= 50, newlyUnlocked);
            unlockIf(openid, "record_100", totalRecords >= 100, newlyUnlocked);

            // 不同菜品成就
            unlockIf(openid, "distinct_3", distinctCount >= 3, newlyUnlocked);
            unlockIf(openid, "distinct_10", distinctCount >= 10, newlyUnlocked);
            unlockIf(openid, "distinct_30", distinctCount >= 30, newlyUnlocked);
            unlockIf(openid, "distinct_50", distinctCount >= 50, newlyUnlocked);

            // 连续天数成就
            unlockIf(openid, "streak_3", streak >= 3, newlyUnlocked);
            unlockIf(openid, "streak_7", streak >= 7, newlyUnlocked);
            unlockIf(openid, "streak_14", streak >= 14, newlyUnlocked);
            unlockIf(openid, "streak_30", streak >= 30, newlyUnlocked);

            // 点赞成就
            unlockIf(openid, "like_5", likeCount >= 5, newlyUnlocked);
            unlockIf(openid, "like_15", likeCount >= 15, newlyUnlocked);

        } catch (Exception e) {
            log.warn("成就检测失败: {}", e.getMessage());
        }
        return newlyUnlocked;
    }

    /** 获取用户已解锁的成就列表。 */
    public List<AchievementCatalog.Achievement> unlockedList(String openid) {
        Set<String> unlocked = achievements.findByOpenid(openid).stream()
                .map(UserAchievement::getAchievementCode)
                .collect(Collectors.toSet());
        return catalog.all().stream()
                .filter(a -> unlocked.contains(a.code()))
                .toList();
    }

    private void unlockIf(String openid, String code, boolean condition, List<String> newlyUnlocked) {
        if (condition && !achievements.existsByOpenidAndAchievementCode(openid, code)) {
            UserAchievement ua = new UserAchievement();
            ua.setOpenid(openid);
            ua.setAchievementCode(code);
            ua.setUnlockedAt(AppClock.now());
            achievements.save(ua);
            newlyUnlocked.add(code);
            log.info("成就解锁: openid={}, code={}", openid, code);
        }
    }

    private int calcStreak(List<UserRecord> records) {
        if (records.isEmpty()) return 0;
        Set<LocalDate> dates = records.stream()
                .map(UserRecord::getRecordDate)
                .filter(Objects::nonNull)
                .map(LocalDate::parse)
                .collect(Collectors.toSet());
        int streak = 0;
        LocalDate cursor = AppClock.today();
        // 如果今天没记录，从昨天开始算
        if (!dates.contains(cursor)) cursor = cursor.minusDays(1);
        while (dates.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }
}
