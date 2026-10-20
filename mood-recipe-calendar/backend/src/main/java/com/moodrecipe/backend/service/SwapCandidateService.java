package com.moodrecipe.backend.service;

import com.moodrecipe.backend.config.AppClock;
import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.model.InteractionRecordedEvent;
import com.moodrecipe.backend.model.UserProfile;
import com.moodrecipe.backend.repository.DailyMenuRepository;
import com.moodrecipe.backend.repository.UserFoodPreferenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * [2026-10-20] 换菜预生成引擎：把「换一道 / 三餐换菜」从"请求时现场排序"改为"离线预生成 + 队列消费"。
 *
 * 为什么：现场挑选（DailyMenuService.pick / DailyMealPlanService.replace）每次要打 5~8 个查询
 * （交互/打卡/收藏/画像/近几天计划）再加全池排序，高峰期数百毫秒起步；对齐毫秒级的正确做法不是抠单次查询，
 * 而是把排序整体搬到请求之外——队列按
 * {@code scope(消费方) × openid × mood × mealType × 画像签名} 维度离线生成，
 * 请求路径只剩"出队 + 轻量验收"，稳定毫秒级。
 *
 * 画像签名是"越用越智能"与"缓存正确性"的交汇点：
 *  - 签名 = 用户动态画像（亲和度/置信度/倾向）+ 显式忌口/过敏原 + 日期的 SHA-256 摘要；
 *  - 点赞/复做/跳过等有意义的交互 → 画像变 → 签名变 → 旧队列自然失效，事件触发异步重建，
 *    个性化更新在"下一次换菜"即刻生效，且无需人工清缓存；
 *  - SHOWN（曝光）不改画像，刻意不失效，否则每次换菜都会把队列打穿、永远回不到毫秒级。
 *
 * 降级策略：队列未命中（冷启动/刚失效/耗尽）时返回 empty，消费方走原现场挑选兜底并异步补货，
 * 因此本服务任何故障都不会导致换菜不可用，最坏退化为改造前的耗时。
 */
@Service
public class SwapCandidateService {

    private static final Logger log = LoggerFactory.getLogger(SwapCandidateService.class);

    /** 消费方scope：今日卡「换一道」。 */
    public static final String SCOPE_TODAY_BOARD = "today-board";
    /** 消费方scope：三餐计划「换菜」。 */
    public static final String SCOPE_MEAL_PLAN = "meal-plan";
    /** 今日卡暂无 mood 维度，统一落 ANY；mood 已进键位，场景化推荐接入后按 mood 预热即可。 */
    public static final String ANY_MOOD = "ANY";

    /** 队列长度：够连换 20+ 次不空窗；重建对用户无感。 */
    static final int QUEUE_SIZE = 24;
    /** 签名缓存 60s：显式偏好改动最迟 1 分钟反映到队列键（交互类改动走事件即时失效）。 */
    private static final long SIGNATURE_TTL_MS = 60_000;
    /** 队列过期时间：超龄队列下次消费时废弃重建（日期/跨天去重语义随时间漂移，宁缺毋旧）。 */
    private static final long ENTRY_TTL_MS = 30 * 60_000;
    /** 队列剩余低于该阈值即后台补货，用户连续换菜时永不空窗。 */
    private static final int REFILL_THRESHOLD = 6;
    /** 单队列已消费记录上限：超过即清空（键随画像签名过期，正常生命周期内远达不到）。 */
    private static final int SERVED_MAX = 500;
    /** 预热用户上限：活跃用户（昨日/今日打开过今日卡）才有预生成的价值。 */
    private static final int PREWARM_MAX_USERS = 500;

    /** 不改画像的交互：曝光只用于 CTR 统计，不得触发队列失效。 */
    private static final Set<String> NON_PROFILE_ACTIONS = Set.of("SHOWN");

    /**
     * 换菜队列构建器：由各消费方注册自己的排序口径（硬过滤+忌口+去重+软加分），
     * 保证预生成队列与慢路径挑菜**同源同口径**，避免两套排序逻辑漂移。
     */
    public interface SwapQueueBuilder {
        String scope();
        /** 全量排序候选（最终顺序），服务侧截取队头入列。只做离线调用，允许打库。 */
        List<Recipe> build(String openid, String mood, String mealType);
    }

    private final ObjectProvider<SwapQueueBuilder> builders;
    private final FeedbackLearningService learning;
    private final UserFoodPreferenceRepository preferences;
    private final AllergenNormalizationService allergenNormalization;
    private final DailyMenuRepository dailyMenus;

    private final ConcurrentHashMap<String, Entry> queues = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Signature> signatures = new ConcurrentHashMap<>();

    public SwapCandidateService(ObjectProvider<SwapQueueBuilder> builders,
                                FeedbackLearningService learning,
                                UserFoodPreferenceRepository preferences,
                                AllergenNormalizationService allergenNormalization,
                                DailyMenuRepository dailyMenus) {
        this.builders = builders;
        this.learning = learning;
        this.preferences = preferences;
        this.allergenNormalization = allergenNormalization;
        this.dailyMenus = dailyMenus;
    }

    /**
     * 消费下一个换菜候选：出队直到 {@code accept} 通过（被拒的候选直接丢弃，队列继续前进）。
     * 未命中返回 empty 并触发异步重建——消费方应当走慢路径兜底，本次之后恢复毫秒级。
     *
     * @param accept 轻量验收（如"不在今天已推列表"），必须是纯内存判断，禁止打库
     */
    public java.util.Optional<Recipe> next(String scope, String openid, String mood, String mealType,
                                           Predicate<Recipe> accept) {
        Entry entry = queues.computeIfAbsent(key(scope, openid, mood, mealType), Entry::new);
        entry.lastAccess = System.currentTimeMillis();
        if (System.currentTimeMillis() - entry.builtAt > ENTRY_TTL_MS) {
            rebuildAsync(entry);
            return java.util.Optional.empty();
        }
        Recipe picked = poll(entry, accept);
        if (picked != null) {
            entry.served.add(servedKey(picked));
            if (entry.queue.size() < REFILL_THRESHOLD) rebuildAsync(entry);
            return java.util.Optional.of(picked);
        }
        rebuildAsync(entry);
        return java.util.Optional.empty();
    }

    /** 预热入口：队列缺失或过期时异步重建，命中则零开销。供定时预热与事件失效复用。 */
    public void ensure(String scope, String openid, String mood, String mealType) {
        Entry entry = queues.computeIfAbsent(key(scope, openid, mood, mealType), Entry::new);
        entry.lastAccess = System.currentTimeMillis();
        if (System.currentTimeMillis() - entry.builtAt > ENTRY_TTL_MS) rebuildAsync(entry);
    }

    private Recipe poll(Entry entry, Predicate<Recipe> accept) {
        synchronized (entry) {
            while (!entry.queue.isEmpty()) {
                Recipe candidate = entry.queue.poll();
                try {
                    if (candidate != null && accept.test(candidate)) return candidate;
                } catch (RuntimeException ignored) {
                    // 单个候选验收异常视为不合格，队列继续前进，不让一次异常拖垮换菜
                }
            }
        }
        return null;
    }

    /** 异步重建（单飞）：构建失败保留旧队列，building 复位后下次消费重试。 */
    private void rebuildAsync(Entry entry) {
        if (!entry.building.compareAndSet(false, true)) return;
        CompletableFuture.runAsync(() -> {
            try {
                String[] parts = entry.key.split("\\|", 5);
                List<Recipe> ranked = builders.stream()
                        .filter(builder -> builder.scope().equals(parts[0]))
                        .findFirst()
                        .map(builder -> builder.build(parts[1], parts[2], parts[3]))
                        .orElse(List.of());
                synchronized (entry) {
                    entry.queue.clear();
                    // 已消费的候选不回灌：补货重建不给"刚换掉的菜"复活机会，也不浪费队列槽位
                    ranked.stream()
                            .filter(recipe -> !entry.served.contains(servedKey(recipe)))
                            .limit(QUEUE_SIZE)
                            .forEach(entry.queue::add);
                    entry.builtAt = System.currentTimeMillis();
                    if (entry.served.size() > SERVED_MAX) entry.served.clear();
                }
            } catch (RuntimeException e) {
                log.warn("换菜队列重建失败（{}）: {}", entry.key, e.getMessage());
            } finally {
                entry.building.set(false);
            }
        });
    }

    // ---- 预热与失效 ----

    /** 定时预热活跃用户当前餐别的队列：昨日/今日打开过今日卡的用户，15 分钟一轮。 */
    @Scheduled(fixedDelay = 15 * 60 * 1000, initialDelay = 60_000)
    public void prewarm() {
        try {
            String meal = DailyMenuService.mealTypeOf(LocalDateTime.now(AppClock.ZONE));
            List<String> openids = new java.util.ArrayList<>();
            openids.addAll(dailyMenus.findOpenidsByMenuDate(AppClock.today()));
            openids.addAll(dailyMenus.findOpenidsByMenuDate(AppClock.today().minusDays(1)));
            openids.stream().distinct().limit(PREWARM_MAX_USERS).forEach(openid -> {
                ensure(SCOPE_TODAY_BOARD, openid, ANY_MOOD, meal);
                ensure(SCOPE_MEAL_PLAN, openid, ANY_MOOD, "MAIN");
            });
        } catch (RuntimeException ignored) {
            // 预热失败不影响线上换菜：未命中走慢路径兜底
        }
    }

    /** 有意义的交互 → 签名失效 + 队列废弃 + 当前餐别立即预重建，下一次换菜恢复毫秒级。 */
    @EventListener
    public void onInteraction(InteractionRecordedEvent e) {
        if (NON_PROFILE_ACTIONS.contains(e.action())) return;
        signatures.remove(e.openid());
        queues.keySet().removeIf(key -> {
            String[] parts = key.split("\\|", 5);
            return parts.length > 1 && parts[1].equals(e.openid());
        });
        try {
            String meal = DailyMenuService.mealTypeOf(LocalDateTime.now(AppClock.ZONE));
            ensure(SCOPE_TODAY_BOARD, e.openid(), ANY_MOOD, meal);
            ensure(SCOPE_MEAL_PLAN, e.openid(), ANY_MOOD, "MAIN");
        } catch (RuntimeException ignored) {
            // 预重建失败无妨：下次消费 miss 时会再触发
        }
    }

    /** 兜底清理：无人消费的过期队列与陈旧签名定时淘汰，防长驻内存泄漏。 */
    @Scheduled(fixedDelay = 30 * 60 * 1000)
    public void sweep() {
        long now = System.currentTimeMillis();
        queues.entrySet().removeIf(e ->
                e.getValue().lastAccess < now - ENTRY_TTL_MS && !e.getValue().building.get());
        signatures.entrySet().removeIf(e -> e.getValue().at < now - SIGNATURE_TTL_MS * 5);
    }

    // ---- 画像签名 ----

    /**
     * 画像签名：动态画像 + 显式偏好 + 日期的摘要。任何一项变化都会生成全新队列键，
     * 旧队列随之自然作废——这是"缓存不 serving 过期个性化结果"的唯一保障。
     */
    private String key(String scope, String openid, String mood, String mealType) {
        return scope + "|" + openid + "|" + norm(mood) + "|" + norm(mealType) + "|" + signature(openid);
    }

    private String signature(String openid) {
        long now = System.currentTimeMillis();
        Signature cached = signatures.get(openid);
        if (cached != null && now - cached.at < SIGNATURE_TTL_MS) return cached.value;
        String value = computeSignature(openid);
        signatures.put(openid, new Signature(value, now));
        return value;
    }

    private String computeSignature(String openid) {
        StringBuilder raw = new StringBuilder(openid).append('|').append(AppClock.today());
        try {
            UserProfile profile = learning.getProfile(openid); // 自带 60s 缓存 + 事件失效
            raw.append('|').append(String.format(Locale.ROOT, "%.3f", profile.getConfidence()));
            appendAffinity(raw, profile.getIngredientAffinity());
            appendAffinity(raw, profile.getTagAffinity());
            appendAffinity(raw, profile.getTasteAffinity());
            appendAffinity(raw, profile.getCuisineAffinity());
            raw.append('|').append(profile.getDifficultyTendency())
                    .append('|').append(profile.getDurationTendencyMinutes());
        } catch (RuntimeException ignored) {
            // 画像暂不可得时签名退化为 openid+日期，换菜仍可用（只是这轮队列不随行为进化）
        }
        preferences.findByOpenid(openid).ifPresent(pref -> {
            raw.append('|').append(allergenNormalization.normalize(
                    pref.getAvoidIngredients(), pref.getAllergens()));
            if (pref.getNormalizedBlockedTerms() != null) raw.append('|').append(pref.getNormalizedBlockedTerms());
            raw.append('|').append(nvl(pref.getFavoriteTags()))
                    .append('|').append(nvl(pref.getFavoriteCuisines()))
                    .append('|').append(nvl(pref.getFavoriteDishes()))
                    .append('|').append(nvl(pref.getHealthGoal()))
                    .append('|').append(nvl(pref.getSpiceLevel()));
        });
        return sha256(raw.toString());
    }

    private void appendAffinity(StringBuilder raw, java.util.Map<String, Double> affinity) {
        if (affinity == null || affinity.isEmpty()) return;
        raw.append('|');
        affinity.forEach((k, v) -> raw.append(k).append(':')
                .append(String.format(Locale.ROOT, "%.2f", v == null ? 0 : v)).append(','));
    }

    private String sha256(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8; i++) hex.append(String.format("%02x", digest[i]));
            return hex.toString();
        } catch (Exception e) {
            return "nosig"; // 摘要不可用时所有用户共享一个键位，等价于关闭签名隔离，不阻断换菜
        }
    }

    private static String norm(String value) {
        return value == null || value.isBlank() ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    /** 已消费候选的标识：优先菜谱 id，无 id 退化为菜名。 */
    private static String servedKey(Recipe recipe) {
        return recipe.getId() != null ? "id:" + recipe.getId() : "name:" + recipe.getName();
    }

    private static String nvl(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class Entry {
        final String key;
        final ArrayDeque<Recipe> queue = new ArrayDeque<>();
        final AtomicBoolean building = new AtomicBoolean(false);
        /** 本队列已消费出去的候选：补货重建时剔除，避免"刚换掉的菜"复活到队头。 */
        final java.util.Set<String> served = ConcurrentHashMap.newKeySet();
        volatile long builtAt;
        volatile long lastAccess;

        Entry(String key) {
            this.key = key;
            this.lastAccess = System.currentTimeMillis();
        }
    }

    private record Signature(String value, long at) { }
}
