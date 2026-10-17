package com.moodrecipe.backend.service;

import com.moodrecipe.backend.config.AppClock;
import com.moodrecipe.backend.entity.SeasonalIngredient;
import com.moodrecipe.backend.repository.SeasonalIngredientRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 每周主题运营位：给用户「每周打开看看有什么新花样」的理由。
 *
 * 主题按周序号确定性轮换（同周所有人同一主题，刷新不变）；
 * 主题预设（健康目标/预算/排菜备注）由前端带入周计划生成接口——
 * 复用 GenerateRequest 既有字段，不新增生成通道。时令周会从
 * seasonal_ingredients（可商用数据）取当季食材做副标题。
 */
@Service
public class WeeklyThemeService {

    private final SeasonalIngredientRepository seasonal;

    public WeeklyThemeService(SeasonalIngredientRepository seasonal) {
        this.seasonal = seasonal;
    }

    private record Theme(String code, String emoji, String title, String subtitle,
                         String healthGoal, String budget, String notes) {}

    private static final List<Theme> THEMES = List.of(
            new Theme("LEAN_WEEK", "🥗", "轻一点 · 减脂周", "每天一道清爽菜，7 天吃出轻盈感",
                    "LEAN", "DAILY", "本周主题：减脂周，多安排清蒸、白灼、凉拌和优质蛋白，少油少糖"),
            new Theme("SOLO_WEEK", "🥢", "一人食周", "一个人的饭，也要认真吃",
                    "BALANCED", "DAILY", "本周主题：一人食周，菜量按一人份设计，简单快手不剩饭"),
            new Theme("BUDGET_WEEK", "🧾", "10 元搞定三菜", "省钱的尽头是会买",
                    "BALANCED", "SAVE", "本周主题：省钱周，优先平价食材（蛋、豆、土豆、时蔬），一菜多用、少买少浪费"),
            new Theme("SEASON_WEEK", "🍂", "时令进补周", "什么季节，吃什么菜",
                    "BALANCED", "DAILY", "本周主题：时令周，优先用当季食材入菜，新鲜便宜"),
            new Theme("GENTLE_WEEK", "🍚", "养胃温柔周", "肠胃舒服了，人就精神了",
                    "BALANCED", "DAILY", "本周主题：养胃周，多粥、汤、蒸、炖，少油炸重辣"),
            new Theme("QUICK_WEEK", "⚡", "快手 25 分钟周", "下班也能 25 分钟开饭",
                    "BALANCED", "DAILY", "本周主题：快手周，每道菜 25 分钟内完成，步骤不超过 5 步"));

    /** 本周主题：按「年内第几周」确定性轮换；同周结果恒定。 */
    public WeeklyThemeView current() {
        LocalDate today = AppClock.today();
        int weekIndex = ((today.getDayOfYear() - 1) / 7) % THEMES.size();
        Theme theme = THEMES.get(weekIndex);
        String seasonalTip = "SEASON_WEEK".equals(theme.code()) ? seasonalTip() : null;
        return new WeeklyThemeView(theme.code(), theme.emoji(), theme.title(), theme.subtitle(),
                theme.healthGoal(), theme.budget(), theme.notes(), seasonalTip);
    }

    /** 当季食材提示：从可商用季节数据里取当月命中的前 3 种；数据缺失静默为空。 */
    private String seasonalTip() {
        try {
            int month = AppClock.today().getMonthValue();
            List<String> names = seasonal.findByEnabledTrueAndLicenseStatus("COMMERCIALLY_USABLE").stream()
                    .filter(item -> inSeason(item, month))
                    .map(SeasonalIngredient::getName)
                    .filter(name -> name != null && !name.isBlank())
                    .distinct()
                    .limit(3)
                    .toList();
            return names.isEmpty() ? null : "当季正好吃：" + String.join("、", names);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private boolean inSeason(SeasonalIngredient item, int month) {
        Integer start = item.getStartMonth();
        Integer end = item.getEndMonth();
        if (start == null || end == null) return false;
        return start <= end ? month >= start && month <= end : month >= start || month <= end;
    }

    public record WeeklyThemeView(String code, String emoji, String title, String subtitle,
                                  String healthGoal, String budget, String notes, String seasonalTip) {}
}
