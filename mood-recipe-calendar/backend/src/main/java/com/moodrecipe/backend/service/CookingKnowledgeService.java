package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.CookingKnowledgeChunk;
import com.moodrecipe.backend.repository.CookingKnowledgeChunkRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CookingKnowledgeService {
    private static final Map<String, String> SYNONYMS = Map.of(
            "熟了吗", "熟透 中心温度 温度计",
            "糊了", "烧焦 火大 补救",
            "出水", "水分 沥干 大火",
            "替换", "替代 没有 食材",
            "小孩", "儿童 熟透 少盐",
            "老人", "老人 软烂 熟透");

    private final CookingKnowledgeChunkRepository repository;

    public CookingKnowledgeService(CookingKnowledgeChunkRepository repository) {
        this.repository = repository;
    }

    public List<CookingKnowledgeChunk> search(String query, int limit) {
        String expanded = expand(query);
        return repository.findByEnabledTrue().stream()
                .filter(CookingKnowledgeService::auditable)
                .map(chunk -> Map.entry(chunk, score(chunk, expanded)))
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.<CookingKnowledgeChunk, Integer>comparingByValue().reversed()
                        .thenComparing(entry -> entry.getKey().getId()))
                .limit(Math.max(1, Math.min(limit, 5)))
                .map(Map.Entry::getKey)
                .toList();
    }

    static int score(CookingKnowledgeChunk chunk, String query) {
        if (chunk == null || query == null || query.isBlank()) return 0;
        String normalized = normalize(query);
        int score = contains(normalized, chunk.getTitle()) ? 5 : 0;
        for (String keyword : split(chunk.getKeywords())) {
            if (normalized.contains(normalize(keyword))) score += 3;
        }
        if (contains(normalized, chunk.getCategory())) score += 1;
        return score;
    }

    private static boolean auditable(CookingKnowledgeChunk chunk) {
        return chunk != null && Boolean.TRUE.equals(chunk.getEnabled())
                && notBlank(chunk.getTitle()) && notBlank(chunk.getContent())
                && notBlank(chunk.getSourceName()) && notBlank(chunk.getSourceUrl())
                && chunk.getReviewedAt() != null;
    }

    private static String expand(String query) {
        StringBuilder value = new StringBuilder(query == null ? "" : query);
        SYNONYMS.forEach((key, words) -> { if (value.indexOf(key) >= 0) value.append(' ').append(words); });
        return normalize(value.toString());
    }

    private static boolean contains(String query, String value) {
        return value != null && !value.isBlank() && query.contains(normalize(value));
    }

    private static List<String> split(String value) {
        return value == null ? List.of() : Arrays.stream(value.split("[,，、;；|\\s]+"))
                .map(String::trim).filter(item -> !item.isBlank()).toList();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\p{Z}]", "");
    }

    private static boolean notBlank(String value) { return value != null && !value.isBlank(); }
}
