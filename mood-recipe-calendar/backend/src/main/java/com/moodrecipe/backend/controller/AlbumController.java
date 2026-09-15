package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.entity.MonthlyAlbum;
import com.moodrecipe.backend.entity.UserRecord;
import com.moodrecipe.backend.repository.MonthlyAlbumRepository;
import com.moodrecipe.backend.repository.UserRecordRepository;
import com.moodrecipe.backend.service.GuozaiAgent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/albums")
public class AlbumController {

    private final MonthlyAlbumRepository albumRepository;
    private final UserRecordRepository recordRepository;
    private final GuozaiAgent guozaiAgent;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AlbumController(MonthlyAlbumRepository albumRepository, UserRecordRepository recordRepository,
                           GuozaiAgent guozaiAgent) {
        this.albumRepository = albumRepository;
        this.recordRepository = recordRepository;
        this.guozaiAgent = guozaiAgent;
    }

    /**
     * 生成/获取月度画册
     * GET /api/albums/month?openid=xxx&month=2026-09
     */
    @GetMapping("/month")
    public ApiResponse<MonthlyAlbum> getMonthAlbum(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid, @RequestParam String month) {
        if (month == null || !month.matches("\\d{4}-(0[1-9]|1[0-2])")) {
            return ApiResponse.error(400, "month 格式应为 YYYY-MM");
        }
        List<UserRecord> records = recordRepository.findByOpenidAndRecordDateStartingWith(openid, month);
        if (records.isEmpty()) {
            return ApiResponse.error("本月暂无记录，无法生成画册");
        }

        String recordIds = records.stream().map(UserRecord::getId).filter(Objects::nonNull)
            .sorted().map(String::valueOf).collect(Collectors.joining(","));
        Optional<MonthlyAlbum> existing = albumRepository.findByOpenidAndMonth(openid, month);
        if (existing.isPresent() && recordIds.equals(existing.get().getRecordIds())
                && !isLegacySummary(existing.get().getAiSummary())
                && records.stream().map(UserRecord::getUpdatedAt).filter(Objects::nonNull)
                    .noneMatch(updated -> existing.get().getGeneratedAt() == null
                            || updated.isAfter(existing.get().getGeneratedAt()))) {
            return ApiResponse.ok(existing.get());
        }

        MonthlyAlbum album = existing.orElseGet(MonthlyAlbum::new);
        album.setOpenid(openid);
        album.setMonth(month);
        album.setRecordIds(recordIds);
        album.setCoverText("小圆同学的" + month.substring(5) + "月干饭日记");

        // 统计
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalDays", records.stream().map(UserRecord::getRecordDate).distinct().count());
        Map<String, Long> moodDist = records.stream()
            .filter(r -> r.getMoodTag() != null)
            .collect(Collectors.groupingBy(UserRecord::getMoodTag, Collectors.counting()));
        stats.put("moodDistribution", moodDist);
        // 最常做菜 Top3
        Map<String, Long> dishCount = records.stream()
            .filter(r -> r.getDishName() != null && !r.getDishName().isBlank())
            .collect(Collectors.groupingBy(UserRecord::getDishName, Collectors.counting()));
        List<Map<String, Object>> topDishes = dishCount.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(3)
            .map(e -> {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("name", e.getKey());
                d.put("count", e.getValue());
                return d;
            }).collect(Collectors.toList());
        stats.put("topDishes", topDishes);
        stats.put("longestStreak", longestStreak(records));

        try {
            album.setStats(objectMapper.writeValueAsString(stats));
        } catch (Exception e) {
            album.setStats("{}");
        }

        album.setAiSummary(guozaiAgent.monthlyLetter(month, records));
        album.setGeneratedAt(LocalDateTime.now());

        return ApiResponse.ok(albumRepository.save(album));
    }

    private boolean isLegacySummary(String summary) {
        return summary == null || summary.isBlank() || summary.contains("每一道菜都是对自己的温柔");
    }

    private int longestStreak(List<UserRecord> records) {
        List<LocalDate> dates = records.stream().map(UserRecord::getRecordDate)
            .filter(value -> value != null && value.matches("\\d{4}-\\d{2}-\\d{2}"))
            .distinct().sorted().map(LocalDate::parse).toList();
        int longest = 0;
        int current = 0;
        LocalDate previous = null;
        for (LocalDate date : dates) {
            current = previous != null && previous.plusDays(1).equals(date) ? current + 1 : 1;
            longest = Math.max(longest, current);
            previous = date;
        }
        return longest;
    }
}
