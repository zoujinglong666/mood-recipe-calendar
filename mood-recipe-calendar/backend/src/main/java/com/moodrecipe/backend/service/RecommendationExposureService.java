package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.RecommendationExposure;
import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.repository.RecommendationExposureRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

@Service
public class RecommendationExposureService {
    private static final Set<String> ACTIONS = Set.of("LIKE", "DISLIKE", "MADE");
    private final RecommendationExposureRepository repository;

    public RecommendationExposureService(RecommendationExposureRepository repository) {
        this.repository = repository;
    }

    public boolean wasRecentlyShownOrRejected(String openid, Recipe recipe) {
        String key = dishKey(recipe.getName());
        return repository.existsByOpenidAndDishKeyAndDislikedTrue(openid, key)
                || repository.findTop20ByOpenidOrderByCreatedAtDesc(openid).stream()
                .anyMatch(item -> item.getDishKey().equals(key) && !item.isLiked() && !item.isMade());
    }

    public String recordShown(String openid, Recipe recipe, String source) {
        RecommendationExposure exposure = new RecommendationExposure();
        exposure.setId(UUID.randomUUID().toString());
        exposure.setOpenid(openid);
        exposure.setDishKey(dishKey(recipe.getName()));
        exposure.setSource(source);
        repository.save(exposure);
        return exposure.getId();
    }

    @Transactional
    public boolean feedback(String openid, String exposureId, String action) {
        if (!ACTIONS.contains(action)) return false;
        RecommendationExposure exposure = repository.findById(exposureId)
                .filter(item -> openid.equals(item.getOpenid()))
                .orElse(null);
        if (exposure == null) return false;
        if ("LIKE".equals(action)) {
            exposure.setLiked(true);
            exposure.setDisliked(false);
        }
        if ("DISLIKE".equals(action)) {
            exposure.setDisliked(true);
            exposure.setLiked(false);
        }
        if ("MADE".equals(action)) exposure.setMade(true);
        repository.save(exposure);
        return true;
    }

    static String dishKey(String name) {
        try {
            String normalized = name == null ? "" : name.replaceAll("[\\s·・,，、_-]+", "").toLowerCase();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
