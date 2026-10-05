package com.moodrecipe.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 4.x 默认启用 Jackson 3（包名 tools.jackson.*），
 * 而项目中 AiRecipeService / WechatService 等仍基于 Jackson 2（com.fasterxml.jackson.*）编写。
 * 这里显式提供 Jackson 2 的 ObjectMapper Bean，供这些服务按原类型注入，保持代码零改动。
 */
@Configuration
public class Jackson2Config {

    @Bean
    public ObjectMapper jackson2ObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // 注册 JavaTime 模块：否则序列化 Recipe.createdAt(LocalDateTime) / PlanView.date(LocalDate) 等
        // Java 8 时间类型会抛 InvalidDefinitionException，导致 daily-meal-plan 生成静默失败、接口 422/500。
        // 注册后日期序列化为 ISO-8601 字符串（如 2026-10-05），前端拼接也更友好。
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
}
