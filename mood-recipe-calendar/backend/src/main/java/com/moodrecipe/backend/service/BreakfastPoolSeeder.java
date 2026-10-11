package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.repository.RecipeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 早餐组合菜谱池。
 *
 * 背景：「今日三餐」的早餐槽此前与午/晚餐共用同一个菜谱池，早餐会选出炒菜。
 * 早餐的实际形态是「主食 + 饮品」的组合（包子/蒸饺/煎饺/汤面/肠粉/粥 搭配 豆浆/牛奶），
 * 建模为一条组合菜谱（饮品写进名称与食材），不做多菜槽位扩展。
 *
 * 池子判定：moodTags 打「早餐」标签。三餐生成的早餐槽只从这个池子选；
 * 池子为空时回退全池，保证三餐不空窗。
 *
 * 生成：复用 AiRecipeService.recommendWeekly 的一次性批量文本请求（省 token、省时间），
 * 再逐道生成封面图并转存 COS，保证图片长期可用（doubaocdn 短链已证明有时效风险）。
 */
@Service
public class BreakfastPoolSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BreakfastPoolSeeder.class);

    /** 早餐池打标标签，同时是三餐选菜的过滤依据。 */
    public static final String BREAKFAST_TAG = "早餐";

    /** 早餐池目标规模：12 道组合足够每天换着来一周不重样。 */
    private static final int TARGET_POOL_SIZE = 12;

    /**
     * 早餐组合的硬性要求，作为 recommendWeekly 的上下文注入。
     * 名称格式「主食 · 配饮品」让用户一眼看出这是一顿早饭；
     * 饮品只占一个简短步骤，避免把组合写成两道菜。
     */
    private static final String BREAKFAST_CONTEXT =
            "这是为「今日三餐」功能补充早餐菜谱池的后台任务，与具体用户无关。"
            + "每道\"菜\"必须是一顿完整的中式早餐组合：一份主食（从包子、蒸饺、煎饺、汤面、米粉、肠粉、"
            + "馄饨、粥、蛋饼、烧麦中选一种，不要炒菜、不要炒饭）搭配一杯饮品（从豆浆、牛奶、酸奶、米糊中选一种）。"
            + "菜名格式严格为「主食 · 配饮品」，例如「鲜肉小笼包 · 配豆浆」「鲜虾肠粉 · 配牛奶」。"
            + "组合要像一顿真实早餐：主食为主体，饮品点到即止（只写一个简短步骤，如现磨豆浆、热一杯牛奶）。"
            + "description 写这句早餐的暖胃一句话。";

    private final RecipeRepository recipes;
    private final AiRecipeService ai;
    private final AgnesRecipeImageService imageService;
    private final CosImageStorageService cosImageStorage;

    @Autowired
    public BreakfastPoolSeeder(RecipeRepository recipes, AiRecipeService ai,
                               AgnesRecipeImageService imageService, CosImageStorageService cosImageStorage) {
        this.recipes = recipes;
        this.ai = ai;
        this.imageService = imageService;
        this.cosImageStorage = cosImageStorage;
    }

    /** 启动后异步补池：不阻塞启动，失败只记日志（下次启动重试）。 */
    @Override
    public void run(ApplicationArguments args) {
        Thread worker = new Thread(() -> {
            try {
                int saved = seedIfNeeded(TARGET_POOL_SIZE);
                if (saved > 0) log.info("早餐池补齐完成，本轮新增 {} 道", saved);
            } catch (Exception ex) {
                log.warn("早餐池启动补齐失败（可在服务可用后手动触发）: {}", ex.toString());
            }
        }, "breakfast-pool-seeder");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * 检查早餐池规模，不足 target 时批量生成补齐。
     * @return 本轮实际落库的组合数
     */
    public int seedIfNeeded(int target) {
        long have = breakfastPoolSize();
        if (have >= target) return 0;
        int need = (int) Math.min(target - have, 10);
        log.info("早餐池当前 {} 道，目标 {} 道，开始生成 {} 道组合", have, target, need);

        List<Recipe> generated = ai.recommendWeekly(BREAKFAST_CONTEXT, need + 4).orElse(List.of());
        int saved = 0;
        for (Recipe recipe : generated) {
            if (saved >= need) break;
            if (recipe.getName() == null || recipe.getName().isBlank()
                    || recipes.findByName(recipe.getName()).isPresent()) continue;
            recipe.setMoodTags(BREAKFAST_TAG);
            recipe.setSource("AI");
            // 封面图即时生成并转存 COS，短链类图源已证明会过期
            Optional<String> cover = imageService.generateCover(recipe)
                    .flatMap(url -> cosImageStorage.transferFromUrl(url, "recipes/"));
            cover.ifPresent(recipe::setImage);
            recipes.save(recipe);
            saved++;
        }
        log.info("早餐池生成落库 {} 道（AI 返回 {} 道候选）", saved, generated.size());
        return saved;
    }

    public long breakfastPoolSize() {
        return recipes.countBySourceAndMoodTagsContaining("AI", BREAKFAST_TAG);
    }
}
