package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.entity.UserRecord;
import com.moodrecipe.backend.entity.RecipeInteraction;
import com.moodrecipe.backend.model.RecordRequest;
import com.moodrecipe.backend.repository.UserRecordRepository;
import com.moodrecipe.backend.repository.RecipeInteractionRepository;
import jakarta.validation.Valid;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/records")
public class RecordController {

    private final UserRecordRepository repository;
    private final RecipeInteractionRepository recipeInteractions;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public RecordController(UserRecordRepository repository, RecipeInteractionRepository recipeInteractions) {
        this.repository = repository;
        this.recipeInteractions = recipeInteractions;
    }

    /** 保存一条记录 */
    @PostMapping
    public ApiResponse<UserRecord> save(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid, @Valid @RequestBody RecordRequest req) {
        UserRecord record = new UserRecord();
        record.setOpenid(openid);
        record.setImageUrl(req.imageUrl());
        record.setDishName(req.dishName());
        record.setMoodTag(req.moodTag());
        record.setNote(req.note());
        record.setRecipeId(req.recipeId() != null ? String.valueOf(req.recipeId()) : null);
        record.setCookingTime(req.cookingTime());
        record.setRecordDate(req.recordDate() != null ? req.recordDate() : LocalDate.now().format(DATE_FMT));
        UserRecord saved = repository.save(record);
        try {
            if (req.recipeId() != null && !req.recipeId().isBlank()) {
                RecipeInteraction interaction = new RecipeInteraction();
                interaction.setOpenid(openid);
                interaction.setRecipeId(Long.valueOf(req.recipeId()));
                interaction.setAction("MADE");
                recipeInteractions.save(interaction);
            }
        } catch (NumberFormatException ignored) {
            // AI 临时菜谱没有持久化 id，仍保留用户的做菜记录。
        }
        return ApiResponse.ok(saved);
    }

    /** 某用户全部记录 */
    @GetMapping
    public ApiResponse<List<UserRecord>> list(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        return ApiResponse.ok(repository.findByOpenidOrderByCreatedAtDesc(openid));
    }

    /** 某用户某月记录 */
    @GetMapping("/month")
    public ApiResponse<List<UserRecord>> byMonth(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid, @RequestParam String month) {
        if (month == null || !month.matches("^\\d{4}-\\d{2}$")) {
            return ApiResponse.error(400, "月份格式应为 YYYY-MM");
        }
        return ApiResponse.ok(repository.findByOpenidAndRecordDateStartingWith(openid, month));
    }

    /** 删除记录 */
    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable Long id, @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        UserRecord record = repository.findById(id).orElse(null);
        if (record == null) return ApiResponse.error(404, "记录不存在");
        if (!openid.equals(record.getOpenid())) return ApiResponse.error(403, "无权删除该记录");
        repository.delete(record);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("deleted", true);
        res.put("id", id);
        return ApiResponse.ok(res);
    }

    /** 综合统计（总记录/天数/心情分布/连续打卡/最常做菜） */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        List<UserRecord> all = repository.findByOpenidOrderByCreatedAtDesc(openid);
        Map<String, Object> res = new LinkedHashMap<>();

        res.put("totalRecords", all.size());
        Set<String> dateSet = all.stream()
            .map(UserRecord::getRecordDate).filter(Objects::nonNull).collect(Collectors.toSet());
        res.put("totalDays", dateSet.size());

        // 心情分布
        Map<String, Long> moodDist = all.stream()
            .filter(r -> r.getMoodTag() != null)
            .collect(Collectors.groupingBy(UserRecord::getMoodTag, Collectors.counting()));
        res.put("moodDistribution", moodDist);

        // 当前连续打卡天数
        res.put("currentStreak", calcCurrentStreak(dateSet));
        // 最长连续打卡
        res.put("longestStreak", calcLongestStreak(dateSet));

        // 最常做菜 Top3
        List<Map<String, Object>> topDishes = all.stream()
            .filter(r -> r.getDishName() != null)
            .collect(Collectors.groupingBy(UserRecord::getDishName, Collectors.counting()))
            .entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(3)
            .map(e -> {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("name", e.getKey());
                d.put("count", e.getValue());
                return d;
            }).collect(Collectors.toList());
        res.put("topDishes", topDishes);

        return ApiResponse.ok(res);
    }

    /** 年度统计 */
    @GetMapping("/year-stats")
    public ApiResponse<Map<String, Object>> yearStats(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid, @RequestParam int year) {
        if (year < 2000 || year > 2100) {
            return ApiResponse.error(400, "年份范围应在 2000-2100 之间");
        }
        String yearPrefix = String.valueOf(year);
        List<UserRecord> yearRecords = repository.findByOpenidOrderByCreatedAtDesc(openid).stream()
            .filter(r -> r.getRecordDate() != null && r.getRecordDate().startsWith(yearPrefix))
            .collect(Collectors.toList());

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("totalRecords", yearRecords.size());
        res.put("totalDays", yearRecords.stream().map(UserRecord::getRecordDate).distinct().count());

        // 月度热力图
        Map<String, Long> monthly = new LinkedHashMap<>();
        for (int m = 1; m <= 12; m++) {
            String mm = String.format("%02d", m);
            long count = yearRecords.stream()
                .filter(r -> r.getRecordDate() != null && r.getRecordDate().startsWith(yearPrefix + "-" + mm))
                .count();
            monthly.put(mm, count);
        }
        res.put("monthlyHeatmap", monthly);

        // 心情分布
        Map<String, Long> moodDist = yearRecords.stream()
            .filter(r -> r.getMoodTag() != null)
            .collect(Collectors.groupingBy(UserRecord::getMoodTag, Collectors.counting()));
        res.put("moodDistribution", moodDist);

        // 最常做菜 Top3
        List<Map<String, Object>> topDishes = yearRecords.stream()
            .filter(r -> r.getDishName() != null)
            .collect(Collectors.groupingBy(UserRecord::getDishName, Collectors.counting()))
            .entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(3)
            .map(e -> {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("name", e.getKey());
                d.put("count", e.getValue());
                return d;
            }).collect(Collectors.toList());
        res.put("topDishes", topDishes);

        return ApiResponse.ok(res);
    }

    private int calcCurrentStreak(Set<String> dateSet) {
        int streak = 0;
        LocalDate today = LocalDate.now();
        while (dateSet.contains(today.format(DATE_FMT))) {
            streak++;
            today = today.minusDays(1);
        }
        return streak;
    }

    private int calcLongestStreak(Set<String> dateSet) {
        if (dateSet.isEmpty()) return 0;
        List<LocalDate> dates = dateSet.stream()
            .map(d -> LocalDate.parse(d, DATE_FMT))
            .sorted()
            .collect(Collectors.toList());
        int longest = 1, current = 1;
        for (int i = 1; i < dates.size(); i++) {
            if (dates.get(i).minusDays(1).equals(dates.get(i - 1))) {
                current++;
                longest = Math.max(longest, current);
            } else {
                current = 1;
            }
        }
        return longest;
    }
}
