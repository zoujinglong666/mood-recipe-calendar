package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.RecipeInteraction;
import com.moodrecipe.backend.model.InteractionRecordedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import jakarta.persistence.PostPersist;

/**
 * [2026-10-20] JPA 实体监听器（Spring 托管的 Bean），在每一条 RecipeInteraction 落库后广播事件。
 * 关键点：所有写交互的入口（GuozaiAgent / RecipeController / DailyMenuService / RecordLearningService）
 * 最终都走到 interactions.save(...)，因此在这里统一埋点，学习闭环无需在每个调用方改代码。
 */
@Component
public class InteractionListener {

    private final ApplicationEventPublisher publisher;

    public InteractionListener(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @PostPersist
    public void onPersist(RecipeInteraction interaction) {
        // 防御：若 JPA 未走 Spring BeanContainer 注入 publisher（极端配置下），宁可放弃事件也不阻塞持久化。
        if (publisher != null) {
            publisher.publishEvent(new InteractionRecordedEvent(
                    interaction.getOpenid(), interaction.getAction(), interaction.getRecipeId()));
        }
    }
}
