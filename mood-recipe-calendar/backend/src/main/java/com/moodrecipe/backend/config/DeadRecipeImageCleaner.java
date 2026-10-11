package com.moodrecipe.backend.config;

import com.moodrecipe.backend.repository.RecipeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 启动时清理菜谱表里的过期图链。
 * doubaocdn 短链有时效，过期后 403；置空后这些菜谱凭菜名回到推荐/抽奖池，
 * 前端走兜底插画，不再出现挂图。幂等：已是 NULL 的行不会被命中。
 */
@Component
public class DeadRecipeImageCleaner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DeadRecipeImageCleaner.class);

    private final RecipeRepository recipes;

    public DeadRecipeImageCleaner(RecipeRepository recipes) {
        this.recipes = recipes;
    }

    @Transactional
    @Override
    public void run(ApplicationArguments args) {
        try {
            int cleaned = recipes.nullOutDeadImages();
            if (cleaned > 0) log.info("已清理 {} 条过期 doubaocdn 图链（置空，菜谱保留）", cleaned);
        } catch (Exception ex) {
            log.warn("过期图链清理失败（不影响启动）: {}", ex.toString());
        }
    }
}
