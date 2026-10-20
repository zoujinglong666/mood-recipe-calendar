package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.entity.RecipeInteraction;
import com.moodrecipe.backend.model.RecommendationMetrics;
import com.moodrecipe.backend.model.UserProfile;
import com.moodrecipe.backend.repository.RecipeInteractionRepository;
import com.moodrecipe.backend.repository.RecipeRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * [2026-10-20] 推荐可观测性：从行为日志实时聚合效果指标。
 * - overview()：全站指标，用于运营大盘；
 * - forUser()：单用户指标 + 偏好匹配度，用于验证"该用户是否被越推越懂"。
 * 偏好匹配度的实现思路：取该用户曝光过的菜，用学习引擎的 affinityScore 判是否正向契合，
 * 命中占比越高说明排序越贴合其画像——这是"个性化程度随时间提升"的核心量化证据。
 */
@Service
public class RecommendationMetricsService {

    private final RecipeInteractionRepository interactions;
    private final RecipeRepository recipes;
    private final FeedbackLearningService learning;

    public RecommendationMetricsService(RecipeInteractionRepository interactions,
                                         RecipeRepository recipes,
                                         FeedbackLearningService learning) {
        this.interactions = interactions;
        this.recipes = recipes;
        this.learning = learning;
    }

    public RecommendationMetrics overview() {
        return compute(null);
    }

    public RecommendationMetrics forUser(String openid) {
        return compute(openid);
    }

    private RecommendationMetrics compute(String openid) {
        boolean global = openid == null;
        long shown = global ? interactions.countByAction("SHOWN")
                            : interactions.countByOpenidAndAction(openid, "SHOWN");
        long like = global ? interactions.countByAction("LIKE")
                           : interactions.countByOpenidAndAction(openid, "LIKE");
        long dislike = global ? interactions.countByAction("DISLIKE")
                              : interactions.countByOpenidAndAction(openid, "DISLIKE");
        long made = global ? interactions.countByAction("MADE")
                           : interactions.countByOpenidAndAction(openid, "MADE");
        long skip = global ? interactions.countByAction("SKIP")
                           : interactions.countByOpenidAndAction(openid, "SKIP");

        RecommendationMetrics m = new RecommendationMetrics();
        m.shown = shown; m.like = like; m.dislike = dislike; m.made = made; m.skip = skip;
        m.ctr = ratio(like + dislike + made, shown);
        m.madeRate = ratio(made, shown);
        m.likeRate = ratio(like, shown);
        m.skipRate = ratio(skip, shown);
        m.positiveRatio = ratio(like, like + dislike);
        if (!global) m.preferenceMatch = preferenceMatch(openid, shown);
        return m;
    }

    /** 被曝光菜中"正向契合用户画像"的占比。 */
    private double preferenceMatch(String openid, long shown) {
        if (shown == 0) return 0;
        UserProfile profile = learning.getProfile(openid);
        if (profile.getConfidence() <= 0) return 0;
        List<RecipeInteraction> shownActs =
                interactions.findByOpenidAndActionInOrderByCreatedAtDesc(openid, List.of("SHOWN"));
        Set<Long> ids = shownActs.stream().map(RecipeInteraction::getRecipeId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return 0;
        Map<Long, Recipe> byId = StreamSupport.stream(recipes.findAllById(ids).spliterator(), false)
                .collect(Collectors.toMap(Recipe::getId, r -> r));
        long scored = 0, hit = 0;
        for (RecipeInteraction a : shownActs) {
            Recipe r = byId.get(a.getRecipeId());
            if (r == null) continue;
            scored++;
            if (learning.affinityScore(r, profile) > 0) hit++;
        }
        return scored == 0 ? 0 : (double) hit / scored;
    }

    private static double ratio(long num, long den) {
        return den == 0 ? 0 : (double) num / den;
    }
}
