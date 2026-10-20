package com.moodrecipe.backend.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * [2026-10-20] 推荐效果与个性化程度的可观测指标（requirement 6）。
 * 这些指标让"系统是否越用越聪明"从感性变成可量化、可监控、可迭代的东西。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class RecommendationMetrics {
    public long shown;          // 曝光次数（SHOWN）
    public long like;           // 点赞
    public long dislike;        // 不喜欢
    public long made;           // 复做
    public long skip;           // 跳过

    /** 互动率 = (点赞+不喜欢+复做) / 曝光，衡量推荐是否引动用户行为。 */
    public double ctr;
    /** 复做率 = 复做 / 曝光，最强的"推荐有价值"信号。 */
    public double madeRate;
    /** 喜欢率 = 点赞 / 曝光。 */
    public double likeRate;
    /** 跳过率 = 跳过 / 曝光，越高说明推得越不准。 */
    public double skipRate;
    /** 正向反馈比 = 点赞 / (点赞+不喜欢)，衡量偏好判断的准确率。 */
    public double positiveRatio;
    /** 偏好匹配度（仅按用户）：被曝光菜里"正向亲和度>0"的占比，个性化质量的直接代理指标。 */
    public double preferenceMatch;
}
