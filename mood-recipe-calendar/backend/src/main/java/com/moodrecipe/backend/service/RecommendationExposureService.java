package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.RecommendationExposure;
import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.repository.RecommendationExposureRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RecommendationExposureService {
    private static final Set<String> ACTIONS = Set.of("LIKE", "DISLIKE", "MADE");
    private final RecommendationExposureRepository repository;

    public RecommendationExposureService(RecommendationExposureRepository repository) {
        this.repository = repository;
    }

    public boolean wasRecentlyShownOrRejected(String openid, Recipe recipe) {
        return isRejected(openid, recipe) || wasRecentlyShown(openid, recipe);
    }

    public boolean isRejected(String openid, Recipe recipe) {
        return repository.existsByOpenidAndDishKeyAndDislikedTrue(openid, dishKey(recipe.getName()));
    }

    public boolean wasRecentlyShown(String openid, Recipe recipe) {
        String key = dishKey(recipe.getName());
        return repository.findTop20ByOpenidOrderByCreatedAtDesc(openid).stream()
                .anyMatch(item -> item.getDishKey().equals(key) && !item.isLiked() && !item.isMade());
    }

    /**
     * [2026-10-20] 一次把某用户全部曝光状态捞进内存，供排序时反复判断而不再每条候选查一次库。
     * 背景：wasRecentlyShown / wasRecentlyShownOrRejected 此前在 GuozaiAgent 的排序循环里被逐候选调用，
     * 每个候选都触发一次 findTop20ByOpenid… 的 DB 查询（候选池几百道 = 几百次往返，典型 N+1），
     * 这是推荐回退路径慢的主因。改为进排序前 load 一次，之后纯内存 Set 判断，复杂度由 O(候选数) 次 DB 降为 O(1)。
     * 任何异常都降级为"不过滤"（空集合），绝不因曝光查询失败而阻断推荐主流程。
     */
    public ExposureView load(String openid) {
        Set<String> rejected;
        Set<String> shown;
        try {
            rejected = repository.findByOpenidAndDislikedTrue(openid).stream()
                    .map(RecommendationExposure::getDishKey)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            shown = repository.findTop20ByOpenidOrderByCreatedAtDesc(openid).stream()
                    .filter(item -> !item.isLiked() && !item.isMade())
                    .map(RecommendationExposure::getDishKey)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        } catch (RuntimeException e) {
            rejected = Set.of();
            shown = Set.of();
        }
        return new ExposureView(rejected, shown);
    }

    /**
     * [2026-10-20] 单次 load 后内存态的曝光判断视图。方法名对齐原 Service 接口，方便调用方无痛替换。
     */
    public record ExposureView(Set<String> rejected, Set<String> shown) {
        /** 降级空视图：曝光服务缺席/查询失败时"不过滤"，绝不因此阻断推荐主流程。 */
        public static final ExposureView EMPTY = new ExposureView(Set.of(), Set.of());

        public boolean isRejected(Recipe recipe) {
            return rejected.contains(dishKey(recipe.getName()));
        }
        public boolean wasRecentlyShown(Recipe recipe) {
            return shown.contains(dishKey(recipe.getName()));
        }
        public boolean wasRecentlyShownOrRejected(Recipe recipe) {
            return isRejected(recipe) || wasRecentlyShown(recipe);
        }
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
    public FeedbackState feedback(String openid, String exposureId, String action) {
        if (!ACTIONS.contains(action)) return null;
        RecommendationExposure exposure = repository.findByIdAndOpenid(exposureId, openid).orElse(null);
        if (exposure == null) return null;
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
        return state(exposure);
    }

    public FeedbackState state(String openid, String exposureId) {
        return repository.findByIdAndOpenid(exposureId, openid).map(this::state).orElse(null);
    }

    private FeedbackState state(RecommendationExposure exposure) {
        return new FeedbackState(exposure.isLiked(), exposure.isDisliked(), exposure.isMade());
    }

    public record FeedbackState(boolean liked, boolean disliked, boolean made) { }

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
