package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.entity.MonthlyAlbum;
import com.moodrecipe.backend.entity.UserRecord;
import com.moodrecipe.backend.repository.MonthlyAlbumRepository;
import com.moodrecipe.backend.repository.UserRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/albums")
public class AlbumController {

    private final MonthlyAlbumRepository albumRepository;
    private final UserRecordRepository recordRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AlbumController(MonthlyAlbumRepository albumRepository, UserRecordRepository recordRepository) {
        this.albumRepository = albumRepository;
        this.recordRepository = recordRepository;
    }

    /**
     * 生成/获取月度画册
     * GET /api/albums/month?openid=xxx&month=2026-09
     */
    @GetMapping("/month")
    public ApiResponse<MonthlyAlbum> getMonthAlbum(@RequestParam String openid, @RequestParam String month) {
        // 先查是否已生成
        Optional<MonthlyAlbum> existing = albumRepository.findByOpenidAndMonth(openid, month);
        if (existing.isPresent()) {
            return ApiResponse.ok(existing.get());
        }

        // 从记录数据生成
        List<UserRecord> records = recordRepository.findByOpenidAndRecordDateStartingWith(openid, month);
        if (records.isEmpty()) {
            return ApiResponse.error("本月暂无记录，无法生成画册");
        }

        MonthlyAlbum album = new MonthlyAlbum();
        album.setOpenid(openid);
        album.setMonth(month);
        album.setRecordIds(records.stream().map(r -> String.valueOf(r.getId())).collect(Collectors.joining(",")));
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
            .filter(r -> r.getDishName() != null)
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

        try {
            album.setStats(objectMapper.writeValueAsString(stats));
        } catch (Exception e) {
            album.setStats("{}");
        }

        // AI 寄语（MVP 阶段用模板）
        album.setAiSummary(generateAiSummary(month, stats));

        return ApiResponse.ok(albumRepository.save(album));
    }

    private String generateAiSummary(String month, Map<String, Object> stats) {
        long totalDays = (long) stats.getOrDefault("totalDays", 0L);
        String monthNum = month.substring(5);
        return monthNum + "月，你记录了 " + totalDays + " 天的美食。每一道菜都是对自己的温柔。" +
            "不管心情如何，你都没饿着自己。下个月，也要继续好好吃饭哦。";
    }
}
