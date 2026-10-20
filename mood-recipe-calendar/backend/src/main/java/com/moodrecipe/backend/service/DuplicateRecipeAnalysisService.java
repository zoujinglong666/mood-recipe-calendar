package com.moodrecipe.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.repository.RecipeFavoriteRepository;
import com.moodrecipe.backend.repository.RecipeInteractionRepository;
import com.moodrecipe.backend.repository.RecipeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 同名菜分析与去重（菜谱库均衡管理的去重核心，供每日 Job 与管理端接口共用）。
 *
 * 去重判定规则：
 * 1. 归一化键 = 菜名去掉空白/中英文标点（· ， , 。 . ! ！ ？）后转小写——归一后相同即视为同名；
 *    「番茄炒蛋」「番茄炒蛋 」「番茄,炒蛋」互为重复，做法微调的多个版本也算同名。
 * 2. 每组只保留一行，保留规则按优先级打分：
 *    ① 内容可展示（菜名/描述无 '?'、U+FFFD 乱码）+100
 *    ② 通过安全校验（不含生食/医疗宣称等高危内容）+20
 *    ③ 有真实菜品图（非 /static 占位、非 doubaocdn 死链）+10
 *    ④ 信息完整度 = 食材条数 + 步骤条数×1.5 + 描述长度/20（与融合脚本 richness 同口径）
 *    ⑤ 同分取 id 最小（最早入库）。
 * 3. 淘汰行删除时连带清理收藏与互动引用，保证不留悬挂数据。
 */
@Service
public class DuplicateRecipeAnalysisService {

    private final RecipeRepository recipes;
    private final RecipeFavoriteRepository favorites;
    private final RecipeInteractionRepository interactions;
    private final ObjectMapper json;

    public DuplicateRecipeAnalysisService(RecipeRepository recipes, RecipeFavoriteRepository favorites,
                                          RecipeInteractionRepository interactions, ObjectMapper json) {
        this.recipes = recipes;
        this.favorites = favorites;
        this.interactions = interactions;
        this.json = json;
    }

    /** 一行参与去重的菜谱（轻量视图，报告直接展示用）。 */
    public record DuplicateRow(long id, String name, String source, boolean hasImage,
                               int ingredientCount, int stepCount, String note) { }

    /** 一组同名重复：keeper 保留行，removals 待删行。 */
    public record DuplicateGroup(String name, DuplicateRow keeper, List<DuplicateRow> removals) { }

    public record ApplyResult(int groups, int removedRows, List<String> removedNames) { }

    /** 菜名归一化键：全系统去重/比对统一用它。 */
    public static String normalizeName(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("[\\s·，,。.!！?？]", "").toLowerCase(Locale.ROOT);
    }

    /** 扫描全库同名菜，返回去重建议（只读，不动数据）。 */
    public List<DuplicateGroup> analyze() {
        List<DuplicateGroup> groups = new ArrayList<>();
        for (String name : recipes.findDuplicatedNames()) {
            List<Recipe> rows = new ArrayList<>(recipes.findByNameOrderByIdAsc(name));
            if (rows.size() < 2) continue;
            rows.sort(Comparator
                    .comparingInt(this::keeperScore).reversed()
                    .thenComparing(Recipe::getId, Comparator.nullsLast(Comparator.naturalOrder())));
            Recipe keeper = rows.get(0);
            List<DuplicateRow> removals = new ArrayList<>();
            for (int i = 1; i < rows.size(); i++) removals.add(toRow(rows.get(i)));
            groups.add(new DuplicateGroup(name, toRow(keeper), removals));
        }
        return groups;
    }

    /** 执行去重：删除每组中除保留行外的所有行（连带收藏/互动引用）。幂等：行已不存在则跳过。 */
    @Transactional
    public ApplyResult applyAll(List<DuplicateGroup> groups) {
        int removed = 0;
        List<String> removedNames = new ArrayList<>();
        for (DuplicateGroup group : groups) {
            for (DuplicateRow row : group.removals()) {
                if (recipes.findById(row.id()).isEmpty()) continue;
                favorites.deleteByRecipeId(row.id());
                interactions.deleteByRecipeId(row.id());
                recipes.deleteById(row.id());
                removedNames.add(group.name() + "(id=" + row.id() + ")");
                removed += 1;
            }
        }
        return new ApplyResult(groups.size(), removed, removedNames);
    }

    /** 保留规则打分：可展示 > 安全 > 有图 > 完整度 > id 最小。 */
    private int keeperScore(Recipe r) {
        int score = 0;
        if (!RecipeTextGuard.broken(r)) score += 100;
        if (RecipeSafetyPolicy.isSafe(r)) score += 20;
        if (hasRealImage(r)) score += 10;
        score += Math.round(richness(r));
        return score;
    }

    /** 信息完整度：食材条数 + 步骤条数×1.5 + 描述长度/20（与 gen_merge_sql 的 richness 同口径）。 */
    private float richness(Recipe r) {
        return jsonListCount(r.getIngredients()) + jsonListCount(r.getSteps()) * 1.5f
                + Math.min(r.getDescription() == null ? 0 : r.getDescription().length(), 60) / 20f;
    }

    /** JSON 数组字段（ingredients/steps）的条目数；非数组或解析失败按逗号分段兜底。 */
    private int jsonListCount(String raw) {
        if (raw == null || raw.isBlank()) return 0;
        try {
            JsonNode node = json.readTree(raw);
            if (node.isArray()) return node.size();
        } catch (Exception ignored) {
            // 落到逗号分段兜底
        }
        return (int) java.util.Arrays.stream(raw.split("[,，]")).filter(s -> !s.isBlank()).count();
    }

    private DuplicateRow toRow(Recipe r) {
        boolean broken = RecipeTextGuard.broken(r);
        String note;
        if (broken) {
            note = "内容含乱码";
        } else if (!RecipeSafetyPolicy.isSafe(r)) {
            note = "含高危内容（生食/医疗宣称）";
        } else if (!hasRealImage(r)) {
            note = "无真实图";
        } else {
            note = "完整度较低";
        }
        return new DuplicateRow(r.getId() == null ? 0 : r.getId(), r.getName(),
                r.getSource(), hasRealImage(r), jsonListCount(r.getIngredients()),
                jsonListCount(r.getSteps()), note);
    }

    private boolean hasRealImage(Recipe r) {
        String img = r.getImage();
        return img != null && !img.isBlank() && !img.startsWith("/static/") && !img.contains("doubaocdn.com");
    }
}
