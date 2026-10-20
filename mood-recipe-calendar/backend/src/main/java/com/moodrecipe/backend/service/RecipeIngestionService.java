package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.repository.RecipeRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 采集/时令菜谱统一入库口：LLM 抽取出的候选在落库前必须过全部硬校验，
 * 未通过的给出人话原因（写进给管理员的报告），通过的以 source=CRAWL 入库。
 */
@Service
public class RecipeIngestionService {

    /** LLM 抽取出的菜谱草稿。 */
    public record Draft(String name, String description, List<String> ingredients, List<String> steps,
                        Integer cookingTime, String difficulty, String taste, List<String> moodTags,
                        String mealType, String season, String reason) { }

    public record IngestResult(boolean ok, Long id, String reason) { }

    private final RecipeRepository recipes;
    private final RecipePool recipePool;

    public RecipeIngestionService(RecipeRepository recipes, RecipePool recipePool) {
        this.recipes = recipes;
        this.recipePool = recipePool;
    }

    /** 现有菜谱的归一化菜名集合（采集候选与库内比对的基准）。 */
    public Set<String> existingNormalizedNames() {
        return recipePool.all().stream()
                .map(r -> normalize(r.getName()))
                .filter(n -> !n.isEmpty())
                .collect(Collectors.toSet());
    }

    public static String normalize(String name) {
        return DuplicateRecipeAnalysisService.normalizeName(name);
    }

    /**
     * 校验并入库。硬校验依次为：字段完整 → 食材/步骤数量 → 内容无乱码 → 安全策略 → 库内未重名。
     * 任一不过返回 ok=false 并携带原因；通过则以 source=CRAWL 入库（封面由懒生成任务补拍）。
     */
    public IngestResult ingest(Draft draft) {
        String name = draft.name() == null ? "" : draft.name().trim();
        if (name.isEmpty() || name.length() > 40) return new IngestResult(false, null, "菜名缺失或超长");
        List<String> ingredients = draft.ingredients() == null ? List.of()
                : draft.ingredients().stream().filter(s -> s != null && !s.isBlank()).map(String::trim).toList();
        List<String> steps = draft.steps() == null ? List.of()
                : draft.steps().stream().filter(s -> s != null && !s.isBlank()).map(String::trim).toList();
        if (ingredients.size() < 2) return new IngestResult(false, null, "食材不足 2 条");
        if (steps.size() < 2) return new IngestResult(false, null, "步骤不足 2 条");

        Recipe recipe = new Recipe();
        recipe.setName(name);
        recipe.setDescription(clip(draft.description(), 240));
        recipe.setIngredients(toJsonArray(ingredients));
        recipe.setSteps(toJsonArray(steps));
        recipe.setCookingTime(draft.cookingTime() != null && draft.cookingTime() > 0 ? draft.cookingTime() : null);
        recipe.setDifficulty(clip(draft.difficulty(), 30));
        recipe.setTaste(clip(draft.taste(), 60));
        recipe.setMoodTags(clip(String.join(",", draft.moodTags() == null ? List.of() : draft.moodTags()), 120));
        recipe.setMealType(clip(draft.mealType(), 30));
        recipe.setSeason(clip(draft.season(), 30));
        recipe.setSource("CRAWL");

        if (RecipeTextGuard.broken(recipe)) return new IngestResult(false, null, "内容含乱码字符");
        if (!RecipeSafetyPolicy.isSafe(recipe)) return new IngestResult(false, null, "含高危内容（生食/医疗宣称），安全策略拒绝");
        String normalized = normalize(name);
        if (existingNormalizedNames().contains(normalized)) return new IngestResult(false, null, "库内已有同名菜");
        if (recipes.findByName(name).isPresent()) return new IngestResult(false, null, "库内已有同名菜");

        Recipe saved = recipes.save(recipe);
        return new IngestResult(true, saved.getId(), "收录成功");
    }

    private static String clip(String value, int maxLength) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    private static String toJsonArray(List<String> items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append('"').append(items.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        return sb.append("]").toString();
    }
}
