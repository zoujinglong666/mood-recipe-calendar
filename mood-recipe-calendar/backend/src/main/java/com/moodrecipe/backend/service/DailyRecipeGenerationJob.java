package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.repository.RecipeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 每日凌晨自动补充菜谱库（联网抓取式，而不是凭空 AI 编造）。
 *
 * 流程（凌晨 3:17，无用户流量，可以慢慢来）：
 * 1. 按「省菜 / 养生汤 / 家常菜 / 快手炒菜」四个方向分配当日名额；
 * 2. 每个方向让 LLM 出「真实存在的经典菜」候选 → 下厨房联网核验存在性（查无此菜即丢弃）；
 * 3. 核验通过的菜生成完整菜谱（食材/步骤/时间/难度），按方向打好分类标签；
 * 4. 同步生图（Agnes 文生图 → COS 转存），凌晨不赶时间，保证入库即有图；
 * 5. 重试循环直到名额填满（有轮次上限），「说 10 道就入库 10 道」。
 */
@Service
public class DailyRecipeGenerationJob {

    private static final Logger log = LoggerFactory.getLogger(DailyRecipeGenerationJob.class);
    /** 单方向最多出题轮数，防止 LLM 反复撞车已有菜名时无限循环。 */
    private static final int MAX_ROUNDS_PER_CATEGORY = 4;
    /** 每道菜生图最多尝试次数。 */
    private static final int MAX_IMAGE_ATTEMPTS = 2;

    private final AiRecipeService aiRecipeService;
    private final DishNameVerifier dishNameVerifier;
    private final AgnesRecipeImageService imageService;
    private final CosImageStorageService cosImageStorage;
    private final RecipeRepository recipeRepository;
    private final RecipeGenerationConfig config;

    public DailyRecipeGenerationJob(AiRecipeService aiRecipeService,
                                    DishNameVerifier dishNameVerifier,
                                    AgnesRecipeImageService imageService,
                                    CosImageStorageService cosImageStorage,
                                    RecipeRepository recipeRepository,
                                    RecipeGenerationConfig config) {
        this.aiRecipeService = aiRecipeService;
        this.dishNameVerifier = dishNameVerifier;
        this.imageService = imageService;
        this.cosImageStorage = cosImageStorage;
        this.recipeRepository = recipeRepository;
        this.config = config;
    }

    /** 八个抓取方向：权重决定名额分配（默认 10 道 ≈ 2/1/2/1/1/1/1/1）。 */
    private enum Category {
        PROVINCIAL("省菜", 3, "中国各省份的经典省菜与八大菜系代表菜，选大众认知度高、有明确地域归属的菜（每道来自不同省份）",
                "省菜,经典", "想家", "晚餐"),
        SOUP("养生汤", 2, "家常养生汤品：滋补炖汤、老火例汤、清润甜汤，食材温和、适合晚饭喝",
                "养生汤,滋补", "疲惫,低落", "晚餐"),
        HOME("家常菜", 3, "经典中国家常菜：下饭、荤素搭配、食材在任何菜场都能买到",
                "家常菜,下饭", "平静,开心", "晚餐"),
        QUICK("快手炒菜", 2, "15 分钟内能完成的快手小炒：步骤少、用料简单、上班族下班也能立刻做",
                "快手菜,15分钟", "嘴馋,疲惫", "午餐"),
        // moodTags 打「早餐」标签：入库后自动进早餐池，启动补拍与三餐早餐槽都会覆盖
        BREAKFAST("营养早餐", 2, "中式家常早餐：鸡蛋饼、粥品、包子花卷、吐司料理等 10-25 分钟能完成的早餐",
                "早餐,营养", "早餐", "早餐"),
        NOODLE("面食主食", 2, "面条、焖面、焖饭、盖浇饭等主食一锅出，管饱有满足感",
                "面食,主食", "嘴馋", "午餐"),
        COLD("开胃凉拌", 1, "凉拌菜与开胃小菜：爽口解腻、无需明火或只需焯水，适合夏天与配菜",
                "凉拌菜,开胃", "嘴馋", "午餐"),
        LIGHT("减脂轻食", 1, "低卡高蛋白减脂餐：少油少糖、热量可控但好吃管饱，鸡胸虾腿豆制品为主",
                "减脂,低卡", "焦虑", "晚餐");

        final String label;
        final int weight;
        final String direction;
        final String tags;
        final String moodTags;
        final String mealType;

        Category(String label, int weight, String direction, String tags, String moodTags, String mealType) {
            this.label = label;
            this.weight = weight;
            this.direction = direction;
            this.tags = tags;
            this.moodTags = moodTags;
            this.mealType = mealType;
        }
    }

    /** 每天凌晨 3:17 跑一次（错开整点，凌晨无用户流量，生图可以慢慢做）。 */
    @Scheduled(cron = "0 17 3 * * ?")
    public void runDaily() {
        if (!config.isEnabled()) {
            log.info("[菜谱自动生成] 已关闭，跳过");
            return;
        }
        int count = Math.max(1, Math.min(config.getDailyCount(), 30));
        log.info("[菜谱自动生成] 开始，目标 {} 道（省菜/养生汤/家常菜/快手炒菜/早餐/面食/凉拌/减脂）", count);
        int saved = generateAndSave(count);
        config.setLastRunAt(LocalDateTime.now());
        config.setLastRunCount(saved);
        config.setLastRunMessage("成功入库 " + saved + " 道新菜（均已联网核验并配图）");
        log.info("[菜谱自动生成] 完成，入库 {} 道", saved);
    }

    /**
     * 管理页手动触发：生图链路较慢（每道 30-90s），改在后台线程执行，
     * 接口立即返回，进度看「上次运行」。
     */
    public Map<String, Object> startOnce(int count) {
        int safe = Math.max(1, Math.min(count, 30));
        config.setLastRunAt(LocalDateTime.now());
        config.setLastRunMessage("后台生成中，约需几分钟…");
        Thread worker = new Thread(() -> {
            try {
                int saved = generateAndSave(safe);
                config.setLastRunAt(LocalDateTime.now());
                config.setLastRunCount(saved);
                config.setLastRunMessage("手动触发，入库 " + saved + " 道（已联网核验并配图）");
            } catch (Exception ex) {
                config.setLastRunAt(LocalDateTime.now());
                config.setLastRunCount(0);
                config.setLastRunMessage("生成失败：" + ex.getClass().getSimpleName());
                log.warn("[菜谱自动生成] 手动触发失败: {}", ex.toString());
            }
        }, "recipe-daily-manual");
        worker.setDaemon(true);
        worker.start();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("saved", 0);
        data.put("message", "已在后台按 8 个分类（省菜/养生汤/家常菜/快手炒菜/早餐/面食/凉拌/减脂）"
                + "联网抓取并配图，每道约需 1 分钟，完成后刷新查看「上次运行」");
        return data;
    }

    private int generateAndSave(int count) {
        Set<String> existingNames = loadExistingNames();
        Map<Category, Integer> quotas = allocateQuotas(count);
        int totalSaved = 0;

        for (Map.Entry<Category, Integer> entry : quotas.entrySet()) {
            Category category = entry.getKey();
            int saved = fillCategory(category, entry.getValue(), existingNames);
            totalSaved += saved;
            log.info("[菜谱自动生成] {} 入库 {}/{}", category.label, saved, entry.getValue());
        }
        return totalSaved;
    }

    /** 单个方向循环出题→核验→生成→配图→入库，直到名额填满或轮次耗尽。 */
    private int fillCategory(Category category, int quota, Set<String> existingNames) {
        int saved = 0;
        for (int round = 1; round <= MAX_ROUNDS_PER_CATEGORY && saved < quota; round++) {
            int need = quota - saved;
            // 每轮多要一倍候选：核验淘汰 + 与库内撞名后仍有余量
            int ask = Math.min(21, Math.max(2, need * 2));
            String prompt = """
                    请给出%d道%s。方向：%s。
                    硬性要求：菜名必须真实存在、大众叫法（不要自创融合菜/奇葩名）；本批菜名互不重复。
                    """.formatted(ask, category.label, category.direction);

            Optional<List<Recipe>> result = aiRecipeService.recommendWeekly(prompt, ask);
            if (result.isEmpty() || result.get().isEmpty()) {
                log.warn("[菜谱自动生成] {} 第 {} 轮 AI 未返回有效菜谱", category.label, round);
                continue;
            }
            for (Recipe recipe : result.get()) {
                if (saved >= quota) break;
                if (accept(category, recipe, existingNames)) {
                    attachImage(recipe);
                    recipe.setSource("AI");
                    recipe.setTags(category.tags);
                    recipe.setMoodTags(category.moodTags);
                    recipe.setMealType(category.mealType);
                    recipe.setCreatedAt(LocalDateTime.now());
                    try {
                        recipeRepository.save(recipe);
                        existingNames.add(recipe.getName().trim());
                        saved++;
                    } catch (Exception e) {
                        log.warn("[菜谱自动生成] 保存失败 {}: {}", recipe.getName(), e.getMessage());
                    }
                }
            }
        }
        if (saved < quota) {
            log.warn("[菜谱自动生成] {} 仅入库 {}/{}（候选重复或核验淘汰过多）", category.label, saved, quota);
        }
        return saved;
    }

    /** 联网核验 + 查重；返回是否可入库。 */
    private boolean accept(Category category, Recipe recipe, Set<String> existingNames) {
        String name = recipe.getName() == null ? "" : recipe.getName().trim();
        if (name.isEmpty() || existingNames.contains(name)) return false;
        // 质量门槛：名字、描述、食材、步骤齐全才算一道能用的菜
        if (isBlank(recipe.getDescription()) || isBlank(recipe.getIngredients()) || isBlank(recipe.getSteps())) {
            return false;
        }
        if (!dishNameVerifier.isRealDish(name)) {
            log.info("[菜谱自动生成] {} 联网查无此菜，丢弃：{}", category.label, name);
            return false;
        }
        return true;
    }

    /** 生图并转存 COS：凌晨离线任务，同步等待保证「入库即有图」；失败不阻塞入库。 */
    private void attachImage(Recipe recipe) {
        for (int attempt = 1; attempt <= MAX_IMAGE_ATTEMPTS && isBlank(recipe.getImage()); attempt++) {
            Optional<String> cover = imageService.generateCover(recipe)
                    .flatMap(url -> cosImageStorage.transferFromUrl(url, "recipes/"));
            cover.ifPresent(recipe::setImage);
        }
        if (isBlank(recipe.getImage())) {
            log.warn("[菜谱自动生成] {} 生图失败，先入库无图（可在管理页手动补配图）", recipe.getName());
        }
    }

    /** 按方向权重把 count 拆成各方向名额，余数从左到右补齐（10 → 3/2/3/2）。 */
    private Map<Category, Integer> allocateQuotas(int count) {
        int totalWeight = 0;
        for (Category c : Category.values()) totalWeight += c.weight;
        Map<Category, Integer> quotas = new LinkedHashMap<>();
        int allocated = 0;
        Category[] categories = Category.values();
        for (int i = 0; i < categories.length; i++) {
            Category c = categories[i];
            int quota = (i == categories.length - 1)
                    ? count - allocated
                    : (int) Math.round((double) count * c.weight / totalWeight);
            quota = Math.max(0, quota);
            quotas.put(c, quota);
            allocated += quota;
        }
        return quotas;
    }

    private Set<String> loadExistingNames() {
        Set<String> names = new HashSet<>();
        for (Recipe r : recipeRepository.findAll()) {
            if (r.getName() != null) names.add(r.getName().trim());
        }
        return names;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
