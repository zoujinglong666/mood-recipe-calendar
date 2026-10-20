package com.moodrecipe.backend.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * [2026-10-20] 统一用户画像聚合（动态、随行为持续进化）。
 *
 * 为什么需要它：原本「显式偏好(UserFoodPreference)」「记忆(AgentMemoryStore)」「行为日志(RecipeInteraction)」
 * 三套数据是割裂的，排序逻辑只用到其中一小部分，用户越用越懂他这件事并没有真正闭环。
 * 这里把三路信号聚合成一个可直接喂给排序的画像：
 *   - 亲和度(ingredient/tag/taste)：从 LIKE/DISLIKE/MADE/SKIP 行为按时间衰减累加得到（正=喜欢，负=厌恶）；
 *   - 显式偏好：家乡味、忌口、过敏原、收藏标签/菜系直接播种进亲和度，作为冷启动先验；
 *   - confidence(0~1)：行为样本越多越接近 1，排序据此决定「多信行为、少信先验」，实现冷启动→个性化平滑过渡；
 *   - 难度/时长倾向 + 场景权重：从做过的菜里归纳，让推荐贴合用户真实做饭能力。
 * 该对象同时被持久化为 UserProfileSnapshot，支持长期积累与回溯（见 requirement 4）。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class UserProfile {

    private String openid;
    /** 0~1：行为样本越多越接近 1；排序用它来缩放行为权重，实现冷启动→个性化。 */
    private double confidence;
    /** 食材级亲和度（正=爱吃的食材，负=避开的食材），key 为归一化食材名。 */
    private Map<String, Double> ingredientAffinity = new LinkedHashMap<>();
    /** 标签级亲和度（快手菜/汤品/主食…）。 */
    private Map<String, Double> tagAffinity = new LinkedHashMap<>();
    /** 口味亲和度（清淡/辣/鲜…），来自行为与显式 spiceLevel。 */
    private Map<String, Double> tasteAffinity = new LinkedHashMap<>();
    /** 菜系亲和度，主要来自显式收藏的菜系。 */
    private Map<String, Double> cuisineAffinity = new LinkedHashMap<>();
    /** 用户偏好的做菜难度倾向：SIMPLE / MEDIUM / CHALLENGE（由做过菜的众数难度归纳）。 */
    private String difficultyTendency;
    /** 用户偏好的做菜时长上界（分钟），由做过菜的平均时长归纳。 */
    private int durationTendencyMinutes;
    /** 用餐场景权重（WEEKDAY_QUICK / FAMILY …），用于解释与后续场景化排序。 */
    private Map<String, Double> sceneWeights = new LinkedHashMap<>();
    private FeedbackStats feedback = new FeedbackStats();
    private LocalDateTime computedAt;

    @Data
    public static class FeedbackStats {
        public long shown;     // 曝光（SHOWN 次数）
        public long like;      // 点赞
        public long dislike;   // 不喜欢
        public long made;      // 复做
        public long skip;      // 跳过
        public long meaningful;// like+dislike+made，用于 confidence
    }
}
