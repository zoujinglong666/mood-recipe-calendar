package com.moodrecipe.backend.model;

/**
 * [2026-10-20] 一条交互被持久化后广播的事件。
 * 由 RecipeInteraction 的 @PostPersist 经 InteractionListener 发出，
 * FeedbackLearningService 监听它来使缓存画像失效 / 落快照——这样无论交互来自
 * GuozaiAgent、RecipeController、DailyMenuService 还是 RecordLearningService，学习闭环都自动覆盖。
 */
public record InteractionRecordedEvent(String openid, String action, Long recipeId) {
}
