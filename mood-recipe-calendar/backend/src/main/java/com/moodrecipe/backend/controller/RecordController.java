package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.entity.UserRecord;
import com.moodrecipe.backend.model.RecordRequest;
import com.moodrecipe.backend.repository.UserRecordRepository;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/records")
public class RecordController {

    private final UserRecordRepository repository;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public RecordController(UserRecordRepository repository) {
        this.repository = repository;
    }

    /** 保存一条记录 */
    @PostMapping
    public ApiResponse<UserRecord> save(@Valid @RequestBody RecordRequest req) {
        UserRecord record = new UserRecord();
        record.setOpenid(req.openid());
        record.setImageUrl(req.imageUrl());
        record.setDishName(req.dishName());
        record.setMoodTag(req.moodTag());
        record.setNote(req.note());
        record.setRecipeId(req.recipeId() != null ? String.valueOf(req.recipeId()) : null);
        record.setCookingTime(req.cookingTime());
        record.setRecordDate(req.recordDate() != null ? req.recordDate() : LocalDate.now().format(DATE_FMT));
        return ApiResponse.ok(repository.save(record));
    }

    /** 某用户全部记录 */
    @GetMapping
    public ApiResponse<List<UserRecord>> list(@RequestParam String openid) {
        return ApiResponse.ok(repository.findByOpenidOrderByCreatedAtDesc(openid));
    }

    /** 某用户某月记录 */
    @GetMapping("/month")
    public ApiResponse<List<UserRecord>> byMonth(@RequestParam String openid, @RequestParam String month) {
        return ApiResponse.ok(repository.findByOpenidAndRecordDateStartingWith(openid, month));
    }

    /** 删除记录 */
    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable Long id) {
        repository.deleteById(id);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("deleted", true);
        res.put("id", id);
        return ApiResponse.ok(res);
    }

    /** 综合统计（总记录/天数/心情分布/连续打卡/最常做菜） */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats(@RequestParam String openid) {
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
    public ApiResponse<Map<String, Object>> yearStats(@RequestParam String openid, @RequestParam int year) {
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
