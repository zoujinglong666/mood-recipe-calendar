package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.repository.RecipeRepository;
import com.moodrecipe.backend.service.DailyRecipeGenerationJob;
import com.moodrecipe.backend.service.RecipeCoverBackfillService;
import com.moodrecipe.backend.service.RecipeGenerationConfig;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 菜谱管理中心（仅管理员可访问）。
 * 用于审核/编辑/清理 AI 生成的假数据菜谱，尤其是占位图 /static/recipes/xxx.jpg 的条目。
 */
@RestController
@RequestMapping("/api/admin/recipes")
public class AdminRecipeController {

    private final RecipeRepository repository;
    private final Set<String> adminOpenids;
    private final RecipeGenerationConfig generationConfig;
    private final DailyRecipeGenerationJob generationJob;
    private final RecipeCoverBackfillService coverBackfill;

    public AdminRecipeController(RecipeRepository repository,
                                 RecipeGenerationConfig generationConfig,
                                 DailyRecipeGenerationJob generationJob,
                                 RecipeCoverBackfillService coverBackfill,
                                 @Value("${app.admin-openids:}") String adminOpenidsProp) {
        this.repository = repository;
        this.generationConfig = generationConfig;
        this.generationJob = generationJob;
        this.coverBackfill = coverBackfill;
        this.adminOpenids = Arrays.stream(adminOpenidsProp.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private void requireAdmin(String openid) {
        if (!adminOpenids.contains(openid)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无管理员权限");
        }
    }

    /** 当前登录用户是否为管理员。非管理员返回 admin=false（不抛 403），供前端决定是否显示入口。 */
    @GetMapping("/check")
    public ApiResponse<Map<String, Object>> check(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("admin", adminOpenids.contains(openid));
        return ApiResponse.ok(data);
    }

    /** 分页列表：搜索/来源/占位图筛选全部下推到 SQL，分页与 total 始终按筛选后口径 */
    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Boolean placeholderImage) {
        requireAdmin(openid);

        PageRequest pageReq = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)),
                Sort.by(Sort.Direction.DESC, "id"));
        var pageResult = repository.findAll(filterSpec(keyword, source, placeholderImage), pageReq);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", pageResult.getContent());
        data.put("total", pageResult.getTotalElements());
        data.put("placeholderCount", repository.count(placeholderSpec()));
        data.put("page", page);
        data.put("size", size);
        return ApiResponse.ok(data);
    }

    /** 统计概览（占位图口径与列表筛选一致：无图 / static 占位 / doubaocdn 死链） */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        requireAdmin(openid);
        long total = repository.count();
        long placeholder = repository.count(placeholderSpec());
        long aiCount = repository.findAll().stream()
                .filter(r -> "AI".equals(r.getSource())).count();
        long localCount = repository.findAll().stream()
                .filter(r -> "LOCAL".equals(r.getSource())).count();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total);
        data.put("placeholderImage", placeholder);
        data.put("ai", aiCount);
        data.put("local", localCount);
        return ApiResponse.ok(data);
    }

    /** 组合筛选条件（下推 SQL，避免内存过滤破坏分页） */
    private Specification<Recipe> filterSpec(String keyword, String source, Boolean placeholderImage) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(root.get("name"), "%" + keyword.trim() + "%"));
            }
            if (source != null && !source.isBlank()) {
                predicates.add(cb.equal(root.get("source"), source));
            }
            if (placeholderImage != null) {
                predicates.add(placeholderImage
                        ? missingImagePredicate(root, cb)
                        : cb.not(missingImagePredicate(root, cb)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** 缺图口径：无图 / /static 占位 / doubaocdn 过期短链（与 Recommendation 池口径一致） */
    private Specification<Recipe> placeholderSpec() {
        return (root, query, cb) -> missingImagePredicate(root, cb);
    }

    private Predicate missingImagePredicate(jakarta.persistence.criteria.Root<Recipe> root,
                                            jakarta.persistence.criteria.CriteriaBuilder cb) {
        return cb.or(
                root.get("image").isNull(),
                cb.equal(root.get("image"), ""),
                cb.like(root.get("image"), "/static/%"),
                cb.like(root.get("image"), "%doubaocdn.com%"));
    }

    /** 详情 */
    @GetMapping("/{id}")
    public ApiResponse<Recipe> detail(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
            @PathVariable Long id) {
        requireAdmin(openid);
        return repository.findById(id)
                .map(ApiResponse::ok)
                .orElseGet(() -> ApiResponse.error(404, "菜谱不存在"));
    }

    /** 更新菜谱（编辑菜名/描述/图片/食材/步骤/时间/难度/标签等） */
    @PutMapping("/{id}")
    public ApiResponse<Recipe> update(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
            @PathVariable Long id,
            @RequestBody RecipeUpdateRequest body) {
        requireAdmin(openid);
        Recipe recipe = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("菜谱不存在"));
        if (body.name() != null) recipe.setName(body.name());
        if (body.description() != null) recipe.setDescription(body.description());
        if (body.image() != null) recipe.setImage(body.image());
        if (body.ingredients() != null) recipe.setIngredients(body.ingredients());
        if (body.steps() != null) recipe.setSteps(body.steps());
        if (body.cookingTime() != null) recipe.setCookingTime(body.cookingTime());
        if (body.difficulty() != null) recipe.setDifficulty(body.difficulty());
        if (body.moodTags() != null) recipe.setMoodTags(body.moodTags());
        if (body.taste() != null) recipe.setTaste(body.taste());
        if (body.mealType() != null) recipe.setMealType(body.mealType());
        if (body.tags() != null) recipe.setTags(body.tags());
        if (body.seasonings() != null) recipe.setSeasonings(body.seasonings());
        if (body.season() != null) recipe.setSeason(body.season());
        return ApiResponse.ok(repository.save(recipe));
    }

    /** 删除菜谱 */
    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
            @PathVariable Long id) {
        requireAdmin(openid);
        if (!repository.existsById(id)) return ApiResponse.error(404, "菜谱不存在");
        repository.deleteById(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deleted", id);
        return ApiResponse.ok(data);
    }

    /**
     * 为无图/占位图菜谱异步生成 AI 配图（Agnes 文生图 → COS 转存 → 回写 image）。
     * 生图需 30-90s，超过小程序 POST 超时（60s），故走异步补拍线程，前端轮询详情获取结果。
     */
    @PostMapping("/{id}/image")
    public ApiResponse<Map<String, Object>> generateImage(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
            @PathVariable Long id) {
        requireAdmin(openid);
        Recipe recipe = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("菜谱不存在"));
        Map<String, Object> data = new LinkedHashMap<>();
        if (coverBackfill.hasRealImage(recipe)) {
            data.put("started", false);
            data.put("image", recipe.getImage());
            return ApiResponse.ok(data);
        }
        coverBackfill.schedule(id);
        data.put("started", true);
        return ApiResponse.ok(data);
    }

    /**
     * 批量补拍缺图菜谱的 AI 配图：按缺图口径筛选前 count 道（已含真实图的自动跳过），
     * 逐道异步补拍（与单菜谱「配图」同一闭环）。接口立即返回已排队的条数，真实图由前端轮询详情取回。
     */
    @PostMapping("/batch-image")
    public ApiResponse<Map<String, Object>> batchGenerateImage(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
            @RequestBody(required = false) Map<String, Object> body) {
        requireAdmin(openid);
        int count = 10;
        if (body != null && body.containsKey("count")) {
            count = Integer.parseInt(body.get("count").toString());
        }
        count = Math.max(1, Math.min(100, count));
        List<Recipe> targets = repository
                .findAll(placeholderSpec(),
                        PageRequest.of(0, count, Sort.by(Sort.Direction.DESC, "id")))
                .getContent();
        int queued = 0;
        for (Recipe r : targets) {
            if (coverBackfill.hasRealImage(r)) continue; // 已有真实图跳过
            coverBackfill.schedule(r.getId());
            queued++;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("queued", queued);
        data.put("scanned", targets.size());
        return ApiResponse.ok(data);
    }

    /** 获取自动生成任务配置 */
    @GetMapping("/generation-config")
    public ApiResponse<Map<String, Object>> getGenerationConfig(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        requireAdmin(openid);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", generationConfig.isEnabled());
        data.put("dailyCount", generationConfig.getDailyCount());
        data.put("lastRunAt", generationConfig.getLastRunAt());
        data.put("lastRunCount", generationConfig.getLastRunCount());
        data.put("lastRunMessage", generationConfig.getLastRunMessage());
        data.put("cron", "每天凌晨 03:17 自动执行");
        return ApiResponse.ok(data);
    }

    /** 更新自动生成任务配置 */
    @PutMapping("/generation-config")
    public ApiResponse<Map<String, Object>> updateGenerationConfig(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
            @RequestBody Map<String, Object> body) {
        requireAdmin(openid);
        if (body.containsKey("enabled")) {
            generationConfig.setEnabled(Boolean.TRUE.equals(body.get("enabled")));
        }
        if (body.containsKey("dailyCount")) {
            int count = Integer.parseInt(body.get("dailyCount").toString());
            generationConfig.setDailyCount(Math.max(1, Math.min(30, count)));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", generationConfig.isEnabled());
        data.put("dailyCount", generationConfig.getDailyCount());
        return ApiResponse.ok(data);
    }

    /** 手动触发一次生成（联网抓取+逐道配图较慢，后台执行，接口立即返回） */
    @PostMapping("/generation-run")
    public ApiResponse<Map<String, Object>> runGenerationNow(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
            @RequestBody(required = false) Map<String, Object> body) {
        requireAdmin(openid);
        int count = generationConfig.getDailyCount();
        if (body != null && body.containsKey("count")) {
            count = Integer.parseInt(body.get("count").toString());
        }
        return ApiResponse.ok(generationJob.startOnce(count));
    }

    public record RecipeUpdateRequest(
            String name, String description, String image,
            String ingredients, String steps,
            Integer cookingTime, String difficulty,
            String moodTags, String taste, String mealType,
            String tags, String seasonings, String season) {}
}
