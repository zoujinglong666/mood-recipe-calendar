import pathlib
f = pathlib.Path(r'C:\Users\DELL\DoubaoWork\chats\2026-09-01\new-chat\mood-recipe-calendar\backend\src\main\java\com\moodrecipe\backend\service\DailyMenuService.java')
c = f.read_text(encoding='utf-8')

# 1. 加 import RecipeFavoriteRepository
old_import = 'import com.moodrecipe.backend.repository.RecipeInteractionRepository;'
new_import = '''import com.moodrecipe.backend.repository.RecipeInteractionRepository;
import com.moodrecipe.backend.repository.RecipeFavoriteRepository;'''
c = c.replace(old_import, new_import)

# 2. 加字段
old_field = '    private final RecipePool recipePool;'
new_field = '''    private final RecipePool recipePool;
    private final RecipeFavoriteRepository favorites;'''
c = c.replace(old_field, new_field)

# 3. 改构造函数
old_ctor = '''    public DailyMenuService(DailyMenuRepository dailyMenus, RecipeRepository recipes,
                            RecipeInteractionRepository interactions, UserRecordRepository records,
                            UserFoodPreferenceRepository preferences,
                            AllergenNormalizationService allergenNormalization, RecipePool recipePool) {
        this.dailyMenus = dailyMenus;
        this.recipes = recipes;
        this.interactions = interactions;
        this.records = records;
        this.preferences = preferences;
        this.allergenNormalization = allergenNormalization;
        this.recipePool = recipePool;
    }'''
new_ctor = '''    public DailyMenuService(DailyMenuRepository dailyMenus, RecipeRepository recipes,
                            RecipeInteractionRepository interactions, UserRecordRepository records,
                            UserFoodPreferenceRepository preferences,
                            AllergenNormalizationService allergenNormalization, RecipePool recipePool,
                            RecipeFavoriteRepository favorites) {
        this.dailyMenus = dailyMenus;
        this.recipes = recipes;
        this.interactions = interactions;
        this.records = records;
        this.preferences = preferences;
        this.allergenNormalization = allergenNormalization;
        this.recipePool = recipePool;
        this.favorites = favorites;
    }'''
c = c.replace(old_ctor, new_ctor)

# 4. 收藏菜权重×3 + 口味Profile匹配：在pick方法的评分里加
old_score = '''        return candidates.stream()
                .max(Comparator.comparingInt((Recipe recipe) ->
                        feedback.getOrDefault(recipe.getId(), 0)
                                + preferenceBonus(recipe, preference)
                                + quickBonus(recipe)
                                + ThreadLocalRandom.current().nextInt(4)))
                .orElse(null);'''
new_score = '''        // 收藏菜集合（权重×3）
        Set<Long> favoriteIds = favorites.findTop200ByOpenidOrderByCreatedAtDesc(openid).stream()
                .map(RecipeFavorite::getRecipeId).collect(Collectors.toSet());

        return candidates.stream()
                .max(Comparator.comparingInt((Recipe recipe) ->
                        feedback.getOrDefault(recipe.getId(), 0)
                                + preferenceBonus(recipe, preference)
                                + quickBonus(recipe)
                                + profileBonus(recipe, preference)
                                + (favoriteIds.contains(recipe.getId()) ? 5 : 0)
                                + ThreadLocalRandom.current().nextInt(4)))
                .orElse(null);'''
c = c.replace(old_score, new_score)

# 5. 加 import RecipeFavorite
old_import2 = 'import com.moodrecipe.backend.entity.RecipeInteraction;'
new_import2 = '''import com.moodrecipe.backend.entity.RecipeInteraction;
import com.moodrecipe.backend.entity.RecipeFavorite;'''
c = c.replace(old_import2, new_import2)

# 6. 加profileBonus方法（口味Profile匹配）
old_quick = '''    /** 快手菜加分：25分钟内能做好的菜额外加分，用户忙的时候更友好。 */
    private int quickBonus(Recipe recipe) {
        Integer t = recipe.getCookingTime();
        if (t != null && t > 0 && t <= 25) return 2;
        return 0;
    }'''
new_quick = '''    /** 快手菜加分：25分钟内能做好的菜额外加分，用户忙的时候更友好。 */
    private int quickBonus(Recipe recipe) {
        Integer t = recipe.getCookingTime();
        if (t != null && t > 0 && t <= 25) return 2;
        return 0;
    }

    /** 口味Profile匹配：根据用户健康目标和辣度偏好加权。 */
    private int profileBonus(Recipe recipe, UserFoodPreference preference) {
        if (preference == null) return 0;
        int bonus = 0;
        String taste = safe(recipe.getTaste());
        String tags = safe(recipe.getTags());
        String difficulty = safe(recipe.getDifficulty());

        // 健康目标匹配
        String goal = preference.getHealthGoal();
        if ("LIGHT".equals(goal) || "FAT_LOSS".equals(goal)) {
            // 清淡/减脂模式：清淡口味、低油加分
            if (taste.contains("清淡") || taste.contains("鲜")) bonus += 2;
            if (difficulty.equalsIgnoreCase("easy")) bonus += 1;
        }
        if ("BALANCED".equals(goal)) {
            bonus += 1; // 均衡模式不偏不倚
        }

        // 辣度偏好匹配
        String spice = preference.getSpiceLevel();
        if ("SPICY".equals(spice)) {
            if (taste.contains("辣")) bonus += 3;
        } else if ("MILD".equals(spice)) {
            if (taste.contains("清淡") || taste.contains("甜")) bonus += 2;
        }

        return bonus;
    }'''
c = c.replace(old_quick, new_quick)

# 7. 丰富金句库
old_lines = '''        String[] lines = {
                "今天不用纠结，锅仔替你定了：" + name + minutes,
                "就它了——" + name + "，锅仔觉得和今天很配",
                "今天这份 " + name + " 已经帮你留好啦" + minutes,
                "别想太多，今晚 " + name + "，吃过再说",
        };'''
new_lines = '''        String[] lines = {
                "今天不用纠结，锅仔替你定了：" + name + minutes,
                "就它了——" + name + "，锅仔觉得和今天很配",
                "今天这份 " + name + " 已经帮你留好啦" + minutes,
                "别想太多，今晚 " + name + "，吃过再说",
                "周五了吃点好的，" + name + " 犒劳一下自己",
                "打工人辛苦了，这道 " + name + " 给你充充电",
                "今天天气不错，配 " + name + " 刚刚好",
                "不想做饭的时候，" + name + " 就是你的救星",
                "连续吃外卖好几天了，试试这道 " + name + " 换换口味",
                "一个人吃饭也要认真，" + name + " 安排上了",
                "锅仔偷偷说：这道菜我自己也很喜欢 " + name,
                "今天的快乐是 " + name + " 给的" + minutes,
        };'''
c = c.replace(old_lines, new_lines)

f.write_text(c, encoding='utf-8')
print('DailyMenuService updated')
