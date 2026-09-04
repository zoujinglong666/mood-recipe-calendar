package com.moodrecipe.backend.config;

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
    public com.fasterxml.jackson.databind.ObjectMapper jackson2ObjectMapper() {
        return new com.fasterxml.jackson.databind.ObjectMapper();
    }
}
