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
import java.util.stream.Collectors;

@Service
public class CompanionMessageService {
    private final UserRecordRepository records;
    private final UserFoodPreferenceRepository preferences;
    private final OperationalEventRepository events;
    private final AiRecipeService ai;

    public CompanionMessageService(UserRecordRepository records, UserFoodPreferenceRepository preferences,
                                   OperationalEventRepository events, AiRecipeService ai) {
        this.records = records;
        this.preferences = preferences;
        this.events = events;
        this.ai = ai;
    }

    public Message create(String openid, int requestedHour) {
        int hour = Math.max(0, Math.min(23, requestedHour));
        String period = period(hour);
        rememberOpen(openid, period);

        List<UserRecord> recent = records.findTop30ByOpenidOrderByCreatedAtDesc(openid);
        UserFoodPreference preference = preferences.findByOpenid(openid).orElse(null);
        String usualPeriod = dominantOpenPeriod(openid);
        String topDish = mostCommon(recent, UserRecord::getDishName);
        String topMood = mostCommon(recent, UserRecord::getMoodTag);
        int streak = currentStreak(recent);
        boolean recordedToday = recent.stream().anyMatch(r -> LocalDate.now().toString().equals(r.getRecordDate()));
        String favorite = firstValue(preference == null ? "" : preference.getFavoriteDishes());
        String favoriteCuisine = firstValue(preference == null ? "" : preference.getFavoriteCuisines());
        if (favorite.isBlank()) favorite = topDish;

        String greeting = greeting(period);
        List<String> candidates = fallbackCandidates(period, usualPeriod, favorite, favoriteCuisine, topMood, streak, recordedToday, preference, recent.isEmpty());
        String fallback = candidates.get(Math.floorMod(Objects.hash(openid, LocalDate.now(), period), candidates.size()));
        String insight = insight(usualPeriod, favorite, favoriteCuisine, streak, preference, recent.isEmpty());
        String context = "当前时段=" + period + "；常打开时段=" + usualPeriod + "；最近常做菜=" + safe(topDish)
                + "；偏爱=" + safe(favorite) + "；偏爱菜系=" + safe(favoriteCuisine) + "；常见心情=" + safe(topMood) + "；连续记录=" + streak
                + "天；今天已记录=" + recordedToday + "；明确口味=" + preferenceSummary(preference);
        String message = ai.companionMessage(context).orElse(fallback);
        return new Message(greeting, message, insight, recordedToday ? "看看今天的食光" : "告诉我现在的心情");
    }

    private void rememberOpen(String openid, String period) {
        String eventType = "HOME_OPEN_" + period;
        LocalDateTime start = LocalDate.now().atStartOfDay();
        if (events.existsByOpenidAndEventTypeAndCreatedAtBetween(openid, eventType, start, start.plusDays(1))) return;
        OperationalEvent event = new OperationalEvent();
        event.setEventType(eventType); event.setSeverity("INFO"); event.setOpenid(openid);
        events.save(event);
    }

    private String dominantOpenPeriod(String openid) {
        return events.findTop60ByOpenidAndEventTypeStartingWithOrderByCreatedAtDesc(openid, "HOME_OPEN_").stream()
                .collect(Collectors.groupingBy(e -> e.getEventType().substring("HOME_OPEN_".length()), Collectors.counting()))
                .entrySet().stream().filter(entry -> entry.getValue() >= 3).max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("还在了解");
    }

    static String period(int hour) {
        if (hour >= 5 && hour < 11) return "早晨";
        if (hour < 15) return "午间";
        if (hour < 18) return "下午";
        if (hour < 22) return "晚间";
        return "深夜";
    }

    private String greeting(String period) {
        return switch (period) {
            case "早晨" -> "早上好，先把自己照顾好";
            case "午间" -> "到饭点了，别让肚子等太久";
            case "下午" -> "下午好，想想今晚吃什么";
            case "晚间" -> "晚上好，今天辛苦啦";
            default -> "夜深了，吃点轻松温暖的";
        };
    }

    private List<String> fallbackCandidates(String period, String usualPeriod, String favorite, String favoriteCuisine, String mood, int streak,
                                             boolean today, UserFoodPreference preference, boolean newUser) {
        List<String> result = new ArrayList<>();
        if (newUser) result.add("先告诉我几样爱吃的，往后的每一顿我都会更懂你。 ");
        if (!favoriteCuisine.isBlank()) result.add("你喜欢的“" + favoriteCuisine + "”我记在心里了，今天也往这个方向替你挑。 ");
        if (!favorite.isBlank()) result.add("你常惦记的“" + favorite + "”我记得，今天也挑一道合口味的。 ");
        if (!mood.isBlank()) result.add("最近“" + mood + "”出现得多，今天给自己留一顿舒服的饭吧。 ");
        if (streak >= 2) result.add("已经连续好好吃饭 " + streak + " 天了，这份认真很值得被记住。 ");
        if (today) result.add("今天这顿已经被我收好了，晚些时候也别忘了喝水休息。 ");
        if (!"还在了解".equals(usualPeriod)) result.add("你常在" + usualPeriod + "来找我，这个饭点我会提前替你多想一步。 ");
        if (preference != null && Boolean.FALSE.equals(preference.getEatCilantro())) result.add("不放香菜这件事我一直记得，放心把今天这顿交给我。 ");
        result.add(switch (period) {
            case "早晨" -> "早饭不用复杂，热乎、顺口，就能给今天一个温柔的开始。 ";
            case "午间" -> "忙归忙，午饭还是要认真吃，锅仔帮你挑一道省心的。 ";
            case "下午" -> "现在想好晚饭，等饿的时候就不用匆忙做决定了。 ";
            case "晚间" -> "今天发生了很多事，先用一顿合胃口的饭把自己接住。 ";
            default -> "这么晚还没休息，就选点清淡省事的，吃完早点睡。 ";
        });
        return result;
    }

    private String insight(String usualPeriod, String favorite, String favoriteCuisine, int streak, UserFoodPreference preference, boolean newUser) {
        if (newUser) return "从第一顿开始认识你";
        if (streak >= 2) return "记得你已连续记录 " + streak + " 天";
        if (!favoriteCuisine.isBlank()) return "记得你喜欢 " + favoriteCuisine;
        if (!favorite.isBlank()) return "记得你喜欢 " + favorite;
        if (preference != null && preference.isOnboardingCompleted()) return "你的口味和忌口都收好了";
        if (!"还在了解".equals(usualPeriod)) return "发现你常在" + usualPeriod + "来看看";
        return "每次选择，都让我更懂你";
    }

    private String preferenceSummary(UserFoodPreference p) {
        if (p == null) return "未设置";
        return "标签" + safe(p.getFavoriteTags()) + "，菜系" + safe(p.getFavoriteCuisines())
                + "，辣度" + safe(p.getSpiceLevel()) + "，葱" + p.getEatScallion() + "，香菜" + p.getEatCilantro();
    }

    private int currentStreak(List<UserRecord> recent) {
        Set<String> days = recent.stream().map(UserRecord::getRecordDate).filter(Objects::nonNull).collect(Collectors.toSet());
        int streak = 0; LocalDate day = LocalDate.now();
        while (days.contains(day.toString())) { streak++; day = day.minusDays(1); }
        return streak;
    }

    private String mostCommon(List<UserRecord> items, java.util.function.Function<UserRecord, String> getter) {
        return items.stream().map(getter).filter(v -> v != null && !v.isBlank())
                .collect(Collectors.groupingBy(v -> v, Collectors.counting())).entrySet().stream()
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("");
    }

    private String firstValue(String csv) {
        if (csv == null || csv.isBlank()) return "";
        return csv.split("[,，、]")[0].trim();
    }

    private String safe(String value) { return value == null || value.isBlank() ? "暂无" : value; }

    public record Message(String greeting, String message, String insight, String actionText) { }
}
