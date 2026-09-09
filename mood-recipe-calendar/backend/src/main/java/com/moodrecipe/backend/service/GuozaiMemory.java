package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.OperationalEvent;
import com.moodrecipe.backend.entity.UserFoodPreference;
import com.moodrecipe.backend.entity.UserRecord;
import com.moodrecipe.backend.repository.OperationalEventRepository;
import com.moodrecipe.backend.repository.UserFoodPreferenceRepository;
import com.moodrecipe.backend.repository.UserRecordRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 锅仔记忆层。
 * 聚合用户的口味偏好、用餐历史、情绪趋势、连续记录、行为习惯，
 * 生成统一的记忆快照和锅仔主动分析文本。
 */
@Service
public class GuozaiMemory {

    private final UserRecordRepository records;
    private final UserFoodPreferenceRepository preferences;
    private final OperationalEventRepository events;

    public GuozaiMemory(UserRecordRepository records, UserFoodPreferenceRepository preferences,
                        OperationalEventRepository events) {
        this.records = records;
        this.preferences = preferences;
        this.events = events;
    }

    /** 统一记忆快照。 */
    public record MemorySnapshot(
            String period,
            String usualPeriod,
            String topDish,
            String topMood,
            int streak,
            boolean recordedToday,
            String favorite,
            String favoriteCuisine,
            UserFoodPreference preference,
            boolean isNewUser,
            MoodTrend moodTrend
    ) { }

    /** 情绪趋势分析结果。 */
    public record MoodTrend(
            Map<String, Long> last7Days,
            Map<String, Long> last30Days,
            String dominantMood,
            String recentShift,
            List<String> moodDishPairs
    ) { }

    /**
     * 聚合用户记忆快照。
     */
    public MemorySnapshot snapshot(String openid, int hour) {
        String period = period(hour);
        List<UserRecord> recent = records.findByOpenidOrderByCreatedAtDesc(openid);
        UserFoodPreference preference = preferences.findByOpenid(openid).orElse(null);
        String usualPeriod = dominantOpenPeriod(openid);
        String topDish = mostCommon(recent, UserRecord::getDishName);
        String topMood = mostCommon(recent, UserRecord::getMoodTag);
        int streak = currentStreak(recent);
        boolean recordedToday = recent.stream()
                .anyMatch(r -> LocalDate.now().toString().equals(r.getRecordDate()));
        String favorite = firstValue(preference == null ? "" : preference.getFavoriteDishes());
        String favoriteCuisine = firstValue(preference == null ? "" : preference.getFavoriteCuisines());
        if (favorite.isBlank()) favorite = topDish;

        MoodTrend moodTrend = analyzeMoodTrend(recent);

        boolean isNewUser = recent.isEmpty() && (preference == null || !preference.isOnboardingCompleted());
        return new MemorySnapshot(period, usualPeriod, topDish, topMood, streak, recordedToday,
                favorite, favoriteCuisine, preference, isNewUser, moodTrend);
    }

    /**
     * 情绪趋势分析——基于最近记录，分析心情分布、变化趋势和心情-菜品关联。
     */
    public MoodTrend analyzeMoodTrend(List<UserRecord> recent) {
        LocalDate now = LocalDate.now();
        List<UserRecord> last7 = recordsBetween(recent, now.minusDays(6), now);
        List<UserRecord> last30 = recordsBetween(recent, now.minusDays(29), now);

        Map<String, Long> last7Days = moodDistribution(last7);
        Map<String, Long> last30Days = moodDistribution(last30);

        String dominantMood = last30Days.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("还在了解");

        // 最近3天 vs 之前的情绪变化
        String recentShift = detectMoodShift(recent, now);

        // 心情-菜品关联（每种心情最常搭配的菜）
        List<String> moodDishPairs = moodDishAssociations(last30);

        return new MoodTrend(last7Days, last30Days, dominantMood, recentShift, moodDishPairs);
    }

    /**
     * 生成锅仔主动分析文本——这是寄语的核心输入。
     * 不是简单的数据拼接，而是提炼出有洞察的分析结论。
     */
    public String buildAnalysis(MemorySnapshot snap) {
        List<String> points = new ArrayList<>();

        // 情绪趋势洞察
        if (!snap.moodTrend().last7Days().isEmpty()) {
            String dominant = snap.moodTrend().dominantMood();
            long count = snap.moodTrend().last7Days().getOrDefault(dominant, 0L);
            if (!"还在了解".equals(dominant) && count >= 2) {
                points.add("最近7天里「" + dominant + "」出现了" + count + "次，是主导心情");
            }
        }
        if (!snap.moodTrend().recentShift().isBlank()) {
            points.add(snap.moodTrend().recentShift());
        }

        // 用餐习惯洞察
        if (!snap.topDish().isBlank()) {
            points.add("最近常做的菜是「" + snap.topDish() + "」");
        }
        if (!snap.favoriteCuisine().isBlank()) {
            points.add("偏爱" + snap.favoriteCuisine);
        }
        if (snap.streak() >= 2) {
            points.add("已经连续好好吃饭" + snap.streak() + "天");
        }
        if (snap.recordedToday()) {
            points.add("今天已经记录过一餐");
        } else {
            points.add("今天还没记录，现在是" + snap.period());
        }

        // 口味偏好洞察
        if (snap.preference() != null) {
            if (Boolean.FALSE.equals(snap.preference().getEatCilantro())) {
                points.add("不吃香菜");
            }
            if (Boolean.FALSE.equals(snap.preference().getEatScallion())) {
                points.add("不吃葱");
            }
            if ("NONE".equals(snap.preference().getSpiceLevel())) {
                points.add("完全不吃辣");
            }
        }

        // 心情-菜品关联
        if (!snap.moodTrend().moodDishPairs().isEmpty()) {
            points.add(snap.moodTrend().moodDishPairs().get(0));
        }

        if (points.isEmpty()) {
            points.add("新用户，还在了解中");
        }

        return String.join("；", points);
    }

    // ===== 内部方法 =====

    private Map<String, Long> moodDistribution(List<UserRecord> list) {
        return list.stream()
                .map(UserRecord::getMoodTag)
                .filter(Objects::nonNull)
                .filter(m -> !m.isBlank())
                .collect(Collectors.groupingBy(m -> m, Collectors.counting()));
    }

    private String detectMoodShift(List<UserRecord> recent, LocalDate now) {
        List<UserRecord> last3 = recordsBetween(recent, now.minusDays(2), now);
        List<UserRecord> before = recordsBetween(recent, now.minusDays(9), now.minusDays(3));
        if (last3.isEmpty() || before.isEmpty()) return "";

        String last3Mood = mostCommon(last3, UserRecord::getMoodTag);
        String beforeMood = mostCommon(before, UserRecord::getMoodTag);
        if (last3Mood.isBlank() || beforeMood.isBlank()) return "";
        if (last3Mood.equals(beforeMood)) return "";
        return "最近3天心情从「" + beforeMood + "」转向「" + last3Mood + "」";
    }

    private List<String> moodDishAssociations(List<UserRecord> list) {
        Map<String, List<UserRecord>> byMood = list.stream()
                .filter(r -> r.getMoodTag() != null && !r.getMoodTag().isBlank())
                .collect(Collectors.groupingBy(UserRecord::getMoodTag));
        List<String> pairs = new ArrayList<>();
        for (Map.Entry<String, List<UserRecord>> entry : byMood.entrySet()) {
            String dish = mostCommon(entry.getValue(), UserRecord::getDishName);
            if (!dish.isBlank()) {
                pairs.add("心情「" + entry.getKey() + "」时最常做「" + dish + "」");
            }
        }
        return pairs.stream().limit(2).toList();
    }

    public void rememberHomeOpen(String openid, int hour) {
        String period = period(hour);
        String eventType = "HOME_OPEN_" + period;
        LocalDateTime start = LocalDate.now().atStartOfDay();
        if (events.existsByOpenidAndEventTypeAndCreatedAtBetween(openid, eventType, start, start.plusDays(1))) return;
        OperationalEvent event = new OperationalEvent();
        event.setEventType(eventType);
        event.setSeverity("INFO");
        event.setOpenid(openid);
        events.save(event);
    }

    private String dominantOpenPeriod(String openid) {
        return events.findTop60ByOpenidAndEventTypeStartingWithOrderByCreatedAtDesc(openid, "HOME_OPEN_").stream()
                .collect(Collectors.groupingBy(e -> e.getEventType().substring("HOME_OPEN_".length()), Collectors.counting()))
                .entrySet().stream().filter(entry -> entry.getValue() >= 3)
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("还在了解");
    }

    static String period(int hour) {
        hour = Math.max(0, Math.min(hour, 23));
        if (hour >= 5 && hour < 11) return "早晨";
        if (hour < 15) return "午间";
        if (hour < 18) return "下午";
        if (hour < 22) return "晚间";
        return "深夜";
    }

    private int currentStreak(List<UserRecord> recent) {
        Set<String> days = recent.stream()
                .map(UserRecord::getRecordDate)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        int streak = 0;
        LocalDate day = LocalDate.now();
        while (days.contains(day.toString())) {
            streak++;
            day = day.minusDays(1);
        }
        return streak;
    }

    private String mostCommon(List<UserRecord> items, Function<UserRecord, String> getter) {
        return items.stream().map(getter)
                .filter(v -> v != null && !v.isBlank())
                .collect(Collectors.groupingBy(v -> v, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("");
    }

    private String firstValue(String csv) {
        if (csv == null || csv.isBlank()) return "";
        return csv.split("[,，、]")[0].trim();
    }

    private List<UserRecord> recordsBetween(List<UserRecord> records, LocalDate start, LocalDate end) {
        return records.stream().filter(record -> parseDate(record.getRecordDate())
                .map(date -> !date.isBefore(start) && !date.isAfter(end))
                .orElse(false)).toList();
    }

    private Optional<LocalDate> parseDate(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        try {
            return Optional.of(LocalDate.parse(value));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }
}
