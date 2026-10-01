package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.config.AppClock;
import com.moodrecipe.backend.entity.UserRecord;
import com.moodrecipe.backend.model.LearningReceipt;
import com.moodrecipe.backend.model.RecordRequest;
import com.moodrecipe.backend.model.RecordSaveResponse;
import com.moodrecipe.backend.repository.UserRecordRepository;
import com.moodrecipe.backend.service.RecordLearningService;
import com.moodrecipe.backend.service.WechatContentSafetyService;
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
    private final RecordLearningService learning;
    private final WechatContentSafetyService contentSafety;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String DEFAULT_DISH_IMAGE = "/static/dish_tomato_beef.png";

    public RecordController(UserRecordRepository repository, RecordLearningService learning,
                            WechatContentSafetyService contentSafety) {
        this.repository = repository;
        this.learning = learning;
        this.contentSafety = contentSafety;
    }

    /** 保存一条记录 */
    @PostMapping
    public ApiResponse<RecordSaveResponse> save(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
                                                 @Valid @RequestBody RecordRequest req) {
        if (!contentSafety.allowsText(openid, req.dishName(), req.moodTag(), req.note()))
            return ApiResponse.error(400, "文字未通过安全检查");
        List<String> imageUrls = imageUrls(req);
        if (imageUrls.size() > 9) return ApiResponse.error(400, "一条记录最多添加 9 张照片");
        if (imageUrls.isEmpty()) {
            return ApiResponse.error(400, "请先添加自己拍摄的菜品照片");
        }
        String recordDate = req.recordDate() == null ? AppClock.today().format(DATE_FMT) : req.recordDate();
        try {
            LocalDate.parse(recordDate, DATE_FMT);
        } catch (RuntimeException ignored) {
            return ApiResponse.error(400, "日期格式应为有效的 YYYY-MM-DD");
        }
        if (req.clientRequestId() != null && !req.clientRequestId().isBlank()) {
            var existing = repository.findByOpenidAndClientRequestId(openid, req.clientRequestId());
            if (existing.isPresent()) {
                return ApiResponse.ok(new RecordSaveResponse(existing.get(), LearningReceipt.savedOnly()));
            }
        }
        UserRecord record = new UserRecord();
        record.setOpenid(openid);
        record.setImageUrl(imageUrls.isEmpty() ? DEFAULT_DISH_IMAGE : imageUrls.get(0));
        record.setImageUrls(imageUrls);
        record.setDishName(req.dishName());
        record.setMoodTag(req.moodTag());
        record.setNote(req.note());
        record.setRecipeId(req.recipeId());
        record.setExposureId(req.exposureId());
        record.setClientRequestId(req.clientRequestId());
        record.setCookingTime(req.cookingTime());
        record.setRecordDate(recordDate);
        UserRecord saved = repository.save(record);
        LearningReceipt receipt;
        try {
            receipt = learning.learn(openid, req.recipeId(), req.exposureId(),
                    Boolean.TRUE.equals(req.liked()), Boolean.TRUE.equals(req.tooHard()),
                    Boolean.TRUE.equals(req.leftover()));
        } catch (RuntimeException ignored) {
            receipt = LearningReceipt.learningUnavailable();
        }
        return ApiResponse.ok(new RecordSaveResponse(saved, receipt));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserRecord> update(@PathVariable Long id,
                                          @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
                                          @Valid @RequestBody RecordRequest req) {
        UserRecord record = repository.findById(id).orElse(null);
        if (record == null) return ApiResponse.error(404, "记录不存在");
        if (!openid.equals(record.getOpenid())) return ApiResponse.error(403, "无权编辑该记录");
        List<String> imageUrls = imageUrls(req);
        if (imageUrls.isEmpty() || imageUrls.size() > 9) return ApiResponse.error(400, "请保留 1 到 9 张照片");
        if (!contentSafety.allowsText(openid, req.dishName(), req.moodTag(), req.note())) return ApiResponse.error(400, "文字未通过安全检查");
        record.setImageUrls(imageUrls); record.setImageUrl(imageUrls.get(0)); record.setDishName(req.dishName());
        record.setMoodTag(req.moodTag()); record.setNote(req.note()); record.setCookingTime(req.cookingTime());
        if (req.exposureId() != null && !req.exposureId().isBlank()) record.setExposureId(req.exposureId());
        if (req.recordDate() != null) record.setRecordDate(req.recordDate());
        return ApiResponse.ok(repository.save(record));
    }

    private List<String> imageUrls(RecordRequest req) {
        List<String> values = req.imageUrls() == null ? List.of() : req.imageUrls();
        List<String> result = values.stream().filter(value -> value != null && !value.isBlank()).distinct().toList();
        if (!result.isEmpty()) return result;
        return req.imageUrl() == null || req.imageUrl().isBlank() ? List.of() : List.of(req.imageUrl());
    }

    /** 某用户全部记录 */
    @GetMapping
    public ApiResponse<List<UserRecord>> list(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        return ApiResponse.ok(repository.findByOpenidOrderByCreatedAtDesc(openid));
    }

    /** 获取当前用户的一条记录，用于详情与编辑。 */
    @GetMapping("/{id}")
    public ApiResponse<UserRecord> getById(@PathVariable Long id,
                                           @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        UserRecord record = repository.findById(id).orElse(null);
        if (record == null) return ApiResponse.error(404, "记录不存在");
        if (!openid.equals(record.getOpenid())) return ApiResponse.error(403, "无权查看该记录");
        return ApiResponse.ok(record);
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
        Set<String> dateSet = yearRecords.stream()
            .map(UserRecord::getRecordDate).filter(Objects::nonNull).collect(Collectors.toSet());
        res.put("totalDays", dateSet.size());
        res.put("currentStreak", calcCurrentStreak(dateSet));
        res.put("longestStreak", calcLongestStreak(dateSet));

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
        LocalDate today = AppClock.today();
        while (dateSet.contains(today.format(DATE_FMT))) {
            streak++;
            today = today.minusDays(1);
        }
        return streak;
    }

    private int calcLongestStreak(Set<String> dateSet) {
        if (dateSet.isEmpty()) return 0;
        List<LocalDate> dates = dateSet.stream()
            .map(this::safeDate)
            .flatMap(Optional::stream)
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

    private Optional<LocalDate> safeDate(String value) {
        try {
            return Optional.of(LocalDate.parse(value, DATE_FMT));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }
}
