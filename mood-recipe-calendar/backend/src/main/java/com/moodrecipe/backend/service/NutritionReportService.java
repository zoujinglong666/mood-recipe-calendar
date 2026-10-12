package com.moodrecipe.backend.service;

import com.moodrecipe.backend.config.AppClock;
import com.moodrecipe.backend.entity.UserRecord;
import com.moodrecipe.backend.repository.UserRecordRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 本月营养月报：把当月记录、心情和最常做的菜，写成锅仔的一封信。
 * 数据只来自用户自己的 user_records；家庭共享的成员看自己的记录。
 */
@Service
public class NutritionReportService {
    private final UserRecordRepository records;

    public NutritionReportService(UserRecordRepository records) {
        this.records = records;
    }

    public ReportView report(String openid, String month) {
        YearMonth ym = parseMonth(month);
        List<UserRecord> monthRecords = records.findByOpenidAndRecordDateStartingWith(openid, ym.toString());
        TreeSet<LocalDate> dates = new TreeSet<>();
        List<Integer> cookingTimes = new ArrayList<>();
        Map<String, Long> dishCount = new LinkedHashMap<>();
        Map<String, Long> moodCount = new LinkedHashMap<>();
        for (UserRecord r : monthRecords) {
            LocalDate d = parseDate(r.getRecordDate());
            if (d != null) dates.add(d);
            if (r.getCookingTime() != null && r.getCookingTime() > 0) cookingTimes.add(r.getCookingTime());
            if (r.getDishName() != null && !r.getDishName().isBlank())
                dishCount.merge(r.getDishName().trim(), 1L, Long::sum);
            if (r.getMoodTag() != null && !r.getMoodTag().isBlank())
                moodCount.merge(r.getMoodTag().trim(), 1L, Long::sum);
        }
        List<DishCount> topDishes = topOf(dishCount, 5).stream()
                .map(e -> new DishCount(e.getKey(), e.getValue().intValue())).toList();
        List<MoodCount> topMoods = topOf(moodCount, 3).stream()
                .map(e -> new MoodCount(e.getKey(), e.getValue().intValue())).toList();
        int avgCookingTime = cookingTimes.isEmpty() ? 0
                : (int) Math.round(cookingTimes.stream().mapToInt(Integer::intValue).average().orElse(0));
        int longestStreak = longestStreak(dates);

        return new ReportView(ym.toString(), dates.size(), monthRecords.size(), dishCount.size(),
                avgCookingTime, longestStreak, topDishes, topMoods,
                letterTitle(ym), letterParagraphs(ym, monthRecords.size(), dates.size(), topDishes, topMoods,
                        avgCookingTime, longestStreak));
    }

    private YearMonth parseMonth(String month) {
        if (month != null && !month.isBlank()) {
            try { return YearMonth.parse(month.trim()); }
            catch (DateTimeParseException ignored) { /* 落到当前月 */ }
        }
        return YearMonth.from(AppClock.today());
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return LocalDate.parse(raw.trim().substring(0, Math.min(10, raw.trim().length()))); }
        catch (DateTimeParseException e) { return null; }
    }

    private List<Map.Entry<String, Long>> topOf(Map<String, Long> count, int limit) {
        return count.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue).reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(limit).toList();
    }

    private int longestStreak(TreeSet<LocalDate> dates) {
        int best = 0;
        int run = 0;
        LocalDate prev = null;
        for (LocalDate d : dates) {
            run = (prev != null && d.minusDays(1).equals(prev)) ? run + 1 : 1;
            best = Math.max(best, run);
            prev = d;
        }
        return best;
    }

    private String letterTitle(YearMonth ym) {
        return String.format("锅仔的 %d 月来信", ym.getMonthValue());
    }

    private List<String> letterParagraphs(YearMonth ym, int meals, int days, List<DishCount> topDishes,
                                          List<MoodCount> topMoods, int avgCookingTime, int longestStreak) {
        List<String> paragraphs = new ArrayList<>();
        if (meals == 0) {
            // 空数据专用分支：不能用有数据时的模板（会写出"记了 0 顿饭"这种自相矛盾的信）
            paragraphs.add(String.format(
                    "%d 月这么快就过去啦。这个月你还没在锅仔这里留下记录，锅仔不催你——哪天想记了，拍张照、写句话，就是一封新信的开始。",
                    ym.getMonthValue()));
            paragraphs.add("下个月，锅仔继续陪你好好吃饭。");
            return paragraphs;
        }
        paragraphs.add(String.format(
                "%d 月过得怎么样，我不知道；但 %d 月你认真记下了 %d 顿饭，跨过 %d 天。%s",
                ym.getMonthValue() == 1 ? 12 : ym.getMonthValue() - 1, ym.getMonthValue(), meals, days,
                longestStreak >= 3
                        ? String.format("其中连续记了 %d 天，这股劲儿锅仔都看见了。", longestStreak)
                        : "零零散散也是认真，锅仔都收好了。"));
        if (!topDishes.isEmpty()) {
            DishCount top = topDishes.get(0);
            String comment = top.count() >= 5
                    ? "看来它是你的安心菜，怎么做都吃不腻。"
                    : "常做的菜最养人，锅仔记下了。";
            paragraphs.add(String.format("这个月你最常做的是「%s」，做了 %d 次。%s",
                    top.name(), top.count(), comment));
        }
        if (!topMoods.isEmpty()) {
            MoodCount mood = topMoods.get(0);
            paragraphs.add(String.format("「%s」是你这个月最常写下的心情，出现了 %d 次。%s",
                    mood.tag(), mood.count(), moodLine(mood.tag())));
        }
        if (avgCookingTime > 0) {
            paragraphs.add(String.format(
                    "这个月你平均花 %d 分钟做一顿饭。%s下个月，锅仔继续陪你好好吃饭。",
                    avgCookingTime, avgCookingTime <= 20 ? "快手也吃得踏实，" : "愿意花时间做饭的人，生活亏待不了你。"));
        }
        else {
            paragraphs.add("下个月，锅仔继续陪你好好吃饭。");
        }
        return paragraphs;
    }

    private String moodLine(String tag) {
        return switch (tag) {
            case "开心" -> "开心的时候，值得一口好的。";
            case "疲惫" -> "累了还愿意给自己做饭，这很了不起。";
            case "难过" -> "难过的日子，是热饭一口口把你接住的。";
            case "焦虑" -> "焦虑的时候，厨房里的火候最让人安心。";
            case "想家" -> "想家的时候就做家常菜，锅仔陪你。";
            case "嘴馋" -> "会馋的人，生活有滋味。";
            case "满足" -> "满足的一餐又一餐，就是把日子过好了。";
            default -> "每一种心情，都值得被认真对待。";
        };
    }

    public record DishCount(String name, int count) {}

    public record MoodCount(String tag, int count) {}

    public record ReportView(String month, int days, int meals, int distinctDishes, int avgCookingTime,
                             int longestStreak, List<DishCount> topDishes, List<MoodCount> topMoods,
                             String letterTitle, List<String> letterParagraphs) {}
}
