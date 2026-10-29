package com.moodrecipe.backend.service;

import com.moodrecipe.backend.config.AppClock;
import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.entity.RecipeInteraction;
import com.moodrecipe.backend.entity.UserFoodPreference;
import com.moodrecipe.backend.entity.UserProfileSnapshot;
import com.moodrecipe.backend.model.InteractionRecordedEvent;
import com.moodrecipe.backend.model.UserProfile;
import com.moodrecipe.backend.repository.RecipeInteractionRepository;
import com.moodrecipe.backend.repository.RecipeRepository;
import com.moodrecipe.backend.repository.UserFoodPreferenceRepository;
import com.moodrecipe.backend.repository.UserProfileSnapshotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * [2026-10-20] 推荐系统的「学习与进化」引擎。
 *
 * 职责：
 *  1) 把行为日志（LIKE/DISLIKE/MADE/SKIP）按时间衰减累加成食材/标签/口味亲和度 → 这就是"用户越用越懂"的载体；
 *  2) 融合显式偏好（家乡味/忌口/过敏原/收藏）作为冷启动先验；
 *  3) 产出 confidence，让排序在"样本少时信先验、样本多时信行为"之间平滑过渡；
 *  4) 每条交互落库后（见 InteractionRecordedEvent）即时失效缓存画像并落一份快照，闭环自动、对所有写入口统一生效。
 *
 * 设计取舍：画像采用"按需聚合 + 缓存"而非"单独增量写表"。理由——
 *   - 聚合逻辑是唯一真相源，避免增量更新与全量重算双份逻辑漂移；
 *   - 行为是权威日志，回溯/重训只需重放交互即可，快照仅作状态点；
 *   - 缓存 60s + 事件失效，使"刚点的赞"在下次推荐即可体现，又不至于每次请求都重算。
 */
@Service
public class FeedbackLearningService {

    private static final double HALF_LIFE_DAYS = 30.0;   // 亲和度半衰期：30 天前的互动权重衰减到约一半
    private static final Set<String> MEANINGFUL = Set.of("LIKE", "DISLIKE", "MADE", "SKIP");
    private static final List<String> PREF_ACTIONS = List.of("LIKE", "DISLIKE", "MADE", "SKIP");

    private final RecipeInteractionRepository interactions;
    private final RecipeRepository recipes;
    private final UserFoodPreferenceRepository preferences;
    private final UserProfileSnapshotRepository snapshots;
    private final CacheManager cacheManager;
    private final ObjectMapper objectMapper;

    public FeedbackLearningService(RecipeInteractionRepository interactions,
                                   RecipeRepository recipes,
                                   UserFoodPreferenceRepository preferences,
                                   UserProfileSnapshotRepository snapshots,
                                   CacheManager cacheManager,
                                   ObjectMapper objectMapper) {
        this.interactions = interactions;
        this.recipes = recipes;
        this.preferences = preferences;
        this.snapshots = snapshots;
        this.cacheManager = cacheManager;
        this.objectMapper = objectMapper;
    }

    /** 自注入：快照降级捕获需要调用带 REQUIRES_NEW 事务的代理方法（同 AgentMemoryStore/GrowthService 惯例）。 */
    @Autowired @Lazy private FeedbackLearningService self;

    /** 取当前画像（带缓存；交互事件会失效它，保证"越用越智能"即时可见）。 */
    @Cacheable(cacheNames = "userProfile", key = "#openid")
    public UserProfile getProfile(String openid) {
        return buildProfile(openid);
    }

    /** 真正的学习核心：聚合显式偏好 + 行为信号。纯函数式、可重放、可单测。 */
    public UserProfile buildProfile(String openid) {
        UserProfile profile = new UserProfile();
        profile.setOpenid(openid);
        profile.setComputedAt(AppClock.now());

        UserFoodPreference pref = preferences.findByOpenid(openid).orElse(null);
        if (pref != null) seedFromPrefs(profile, pref);

        List<RecipeInteraction> acts = interactions
                .findByOpenidAndActionInOrderByCreatedAtDesc(openid, PREF_ACTIONS);
        Set<Long> ids = acts.stream().map(RecipeInteraction::getRecipeId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Recipe> byId = ids.isEmpty() ? Map.of()
                : StreamSupport.stream(recipes.findAllById(ids).spliterator(), false)
                    .collect(Collectors.toMap(Recipe::getId, r -> r));

        for (RecipeInteraction a : acts) {
            Recipe r = byId.get(a.getRecipeId());
            if (r == null) continue;
            double w = actionWeight(a.getAction()) * recencyDecay(a.getCreatedAt());
            accumulate(profile, r, w);
            bump(profile.getFeedback(), a.getAction());
        }

        // SHOWN 仅用于 CTR 等观测指标，不参与亲和度
        profile.getFeedback().shown = interactions.countByOpenidAndAction(openid, "SHOWN");
        profile.getFeedback().meaningful =
                profile.getFeedback().like + profile.getFeedback().dislike + profile.getFeedback().made;
        profile.setConfidence(confidence(profile.getFeedback().meaningful));
        deriveTendencies(profile, byId, acts);
        return profile;
    }

    // ---- 以下为学习算法的具体实现 ----

    /** 显式偏好作为冷启动先验播进亲和度（避免新用户画像全 0）。 */
    private void seedFromPrefs(UserProfile p, UserFoodPreference pref) {
        for (String t : split(pref.getFavoriteTags())) p.getTagAffinity().merge(t, 1.0, Double::sum);
        for (String c : split(pref.getFavoriteCuisines())) p.getCuisineAffinity().merge(c, 1.0, Double::sum);
        for (String a : split(pref.getAvoidIngredients())) p.getIngredientAffinity().merge(a, -1.5, Double::sum);
        for (String al : split(pref.getAllergens())) p.getIngredientAffinity().merge(al, -2.0, Double::sum);
        if (pref.getSpiceLevel() != null) {
            double v = switch (pref.getSpiceLevel()) {
                case "HOT", "EXTRA_HOT" -> 1.0;
                case "MILD", "NONE" -> -1.0;
                default -> 0.0;
            };
            if (v != 0) p.getTasteAffinity().merge("辣", v, Double::sum);
        }
    }

    /** 行为信号累加：正权重提升亲和度，负权重（不喜欢/跳过）压低，使排序自动避开厌恶项。 */
    private void accumulate(UserProfile p, Recipe r, double w) {
        for (String ing : split(r.getIngredients())) p.getIngredientAffinity().merge(ing, w, Double::sum);
        for (String t : split(r.getTags())) p.getTagAffinity().merge(t, w * 0.8, Double::sum);
        if (r.getTaste() != null && !r.getTaste().isBlank())
            p.getTasteAffinity().merge(r.getTaste().trim(), w, Double::sum);
    }

    /** 从"做过/点赞"的菜归纳难度与时长倾向，让推荐贴合真实做饭能力。 */
    private void deriveTendencies(UserProfile p, Map<Long, Recipe> byId, List<RecipeInteraction> acts) {
        List<Recipe> positive = acts.stream()
                .filter(a -> "LIKE".equals(a.getAction()) || "MADE".equals(a.getAction()))
                .map(a -> byId.get(a.getRecipeId())).filter(Objects::nonNull).toList();
        if (positive.isEmpty()) return;
        double avg = positive.stream()
                .mapToInt(r -> r.getCookingTime() == null ? 0 : r.getCookingTime()).average().orElse(0);
        p.setDurationTendencyMinutes((int) Math.round(avg));
        p.setDifficultyTendency(mode(positive.stream().map(Recipe::getDifficulty)
                .filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).toList()));
    }

    private void bump(UserProfile.FeedbackStats s, String action) {
        switch (action) {
            case "LIKE" -> s.like++;
            case "DISLIKE" -> s.dislike++;
            case "MADE" -> s.made++;
            case "SKIP" -> s.skip++;
            default -> { }
        }
    }

    /** 交互权重：复做 > 点赞 > 跳过(轻微负) > 不喜欢(强负)。 */
    private double actionWeight(String action) {
        return switch (action) {
            case "MADE" -> 1.5;
            case "LIKE" -> 1.0;
            case "SKIP" -> -0.5;
            case "DISLIKE" -> -1.5;
            default -> 0.0;
        };
    }

    /** 指数时间衰减：今天的互动权重为 1，每过 HALF_LIFE_DAYS 减半。 */
    private double recencyDecay(LocalDateTime at) {
        if (at == null) return 0.5;
        long days = ChronoUnit.DAYS.between(at.toLocalDate(), AppClock.today());
        return Math.exp(-Math.max(0, days) / HALF_LIFE_DAYS);
    }

    /** confidence：样本越多越接近 1（约 15 条有意义互动达 0.63，30 条达 0.86）。 */
    private double confidence(long meaningful) {
        return 1.0 - Math.exp(-meaningful / 15.0);
    }

    private static String mode(List<String> values) {
        return values.stream().collect(Collectors.groupingBy(v -> v, Collectors.counting()))
                .entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    static List<String> split(String v) {
        if (v == null || v.isBlank()) return List.of();
        return Arrays.stream(v.split("[,，、;/；;\\s]+"))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /**
     * [2026-10-20] 计算某道菜与用户画像的亲和度原始分（正=契合，负=抵触）。
     * 抽成公共方法，供 DailyMenuService 排序打分与 RecommendationMetricsService 的「偏好匹配度」共用同一套语义，
     * 避免重复实现漂移。
     */
    public double affinityScore(Recipe r, UserProfile p) {
        if (p == null || r == null) return 0;
        double s = 0;
        for (String ing : split(r.getIngredients())) s += p.getIngredientAffinity().getOrDefault(ing, 0.0);
        for (String t : split(r.getTags())) s += 0.8 * p.getTagAffinity().getOrDefault(t, 0.0);
        if (r.getTaste() != null && !r.getTaste().isBlank())
            s += p.getTasteAffinity().getOrDefault(r.getTaste().trim(), 0.0);
        return s;
    }

    // ---- 事件驱动的学习闭环 ----

    /**
     * [2026-10-20] 监听任意交互落库事件：失效缓存画像（下次推荐即时用上新偏好）+ 对有意义互动落快照。
     * 用 CacheManager 手动失效而非 @CacheEvict，避免事件监听方法上 AOP 代理的不确定性，行为完全确定。
     *
     * [2026-10-29] 由 @EventListener 改为 AFTER_COMMIT：此前本方法在 @PostPersist 回调内
     * 同线程同事务执行，buildProfile 的查询会对处于 persist 回调中的 session 触发 auto-flush，
     * 一旦失败被 catch 吞掉，交互主写事务提交时即抛
     * AssertionFailure "null identifier / session flushed after an exception"（AGENTS.md 铁律 1）。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onInteraction(InteractionRecordedEvent e) {
        Cache cache = cacheManager.getCache("userProfile");
        if (cache != null) cache.evict(e.openid());
        if (MEANINGFUL.contains(e.action())) {
            try {
                self.saveSnapshot(e.openid(), e.action());
            } catch (RuntimeException ignored) {
                // 快照失败不应影响主流程；AFTER_COMMIT + REQUIRES_NEW 保证失败不污染任何主写事务。
            }
        }
    }

    /**
     * 把当前画像 JSON 化落库，形成可回溯的演化时间线（requirement 4）。
     * REQUIRES_NEW 独立事务：失败只回滚本次快照，不污染请求共享的持久化上下文（铁律 1）。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveSnapshot(String openid, String trigger) {
        UserProfile p = buildProfile(openid); // 事件已失效缓存，这里重算得到含最新互动的画像
        UserProfileSnapshot s = new UserProfileSnapshot();
        s.setOpenid(openid);
        s.setTrigger(trigger);
        s.setConfidence(p.getConfidence());
        try {
            s.setProfileJson(objectMapper.writeValueAsString(p));
        } catch (Exception ignored) {
            s.setProfileJson("");
        }
        snapshots.save(s);
    }
}
