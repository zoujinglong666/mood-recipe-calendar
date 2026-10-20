package com.moodrecipe.backend.service;

import com.moodrecipe.backend.config.AppClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 每日同名菜去重 Job（菜谱库均衡管理的日常巡检）。
 *
 * 调度：每天 02:57（避开 03:17 每日生成 / 03:37 采集 / 04:17 乱码清理 / 04:37 心情分类）。
 * 流程：dry-run 分析 → 有重复才推送 WxPusher 报告给管理员 → 管理员在管理后台确认执行；
 * 配置 recipes.dedupe.auto-apply=true 时自动执行并推送执行结果（默认关闭，删数据永远过一道人）。
 */
@Service
public class DuplicateRecipeJob {

    private static final Logger log = LoggerFactory.getLogger(DuplicateRecipeJob.class);
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final DuplicateRecipeAnalysisService analysis;
    private final WxPusherService wxpusher;
    private final boolean enabled;
    private final boolean autoApply;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public DuplicateRecipeJob(DuplicateRecipeAnalysisService analysis, WxPusherService wxpusher,
                              @Value("${recipes.dedupe.enabled:true}") boolean enabled,
                              @Value("${recipes.dedupe.auto-apply:false}") boolean autoApply) {
        this.analysis = analysis;
        this.wxpusher = wxpusher;
        this.enabled = enabled;
        this.autoApply = autoApply;
    }

    @Scheduled(cron = "${recipes.dedupe.cron:0 57 2 * * ?}", zone = "Asia/Shanghai")
    public void run() {
        if (!enabled || !running.compareAndSet(false, true)) return;
        try {
            List<DuplicateRecipeAnalysisService.DuplicateGroup> groups = analysis.analyze();
            if (groups.isEmpty()) {
                log.info("同名菜巡检完成：无重复，不推送");
                return;
            }
            if (autoApply) {
                DuplicateRecipeAnalysisService.ApplyResult result = analysis.applyAll(groups);
                wxpusher.sendToAdmin("菜谱库自动去重完成", buildAppliedReport(groups, result));
            } else {
                wxpusher.sendToAdmin("菜谱库发现同名重复菜，待确认去重", buildPendingReport(groups));
            }
        } catch (Exception e) {
            log.warn("同名菜去重分析失败: {}", e.getMessage());
        } finally {
            running.set(false);
        }
    }

    /** 待确认报告：每组列出保留行与待删行 + 处理入口。 */
    private String buildPendingReport(List<DuplicateRecipeAnalysisService.DuplicateGroup> groups) {
        int pendingRows = groups.stream().mapToInt(g -> g.removals().size()).sum();
        StringBuilder sb = new StringBuilder();
        sb.append("## 菜谱库同名菜去重报告\n")
                .append("时间：").append(AppClock.now().format(HM)).append("　发现 **")
                .append(groups.size()).append("** 组同名，涉及 ").append(pendingRows).append(" 行待删\n\n");
        int i = 1;
        for (DuplicateRecipeAnalysisService.DuplicateGroup g : groups) {
            sb.append("### ").append(i++).append(". ").append(g.name())
                    .append("（").append(g.removals().size() + 1).append(" 行 → 保留 1）\n")
                    .append("- ✅ 保留 id=").append(g.keeper().id())
                    .append("：").append(rowSummary(g.keeper())).append('\n');
            for (DuplicateRecipeAnalysisService.DuplicateRow row : g.removals()) {
                sb.append("- ❌ 待删 id=").append(row.id())
                        .append("：").append(rowSummary(row)).append('\n');
            }
        }
        sb.append("\n**处理方式**：管理后台「菜谱管理 → 同名去重」确认执行；")
                .append("或调用 `POST /api/admin/recipes/duplicates/apply`。")
                .append("未处理的话明天同一时间会再次提醒。");
        return sb.toString();
    }

    /** 执行结果报告（auto-apply 模式或管理员确认后推送）。 */
    private String buildAppliedReport(List<DuplicateRecipeAnalysisService.DuplicateGroup> groups,
                                      DuplicateRecipeAnalysisService.ApplyResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 菜谱库去重执行结果\n")
                .append("时间：").append(AppClock.now().format(HM))
                .append("　处理 ").append(result.groups()).append(" 组，删除 ")
                .append(result.removedRows()).append(" 行\n\n");
        int shown = 0;
        for (String name : result.removedNames()) {
            if (shown++ >= 30) {
                sb.append("- ……等共 ").append(result.removedRows()).append(" 行\n");
                break;
            }
            sb.append("- 删除 ").append(name).append('\n');
        }
        sb.append("\n每组均已保留信息最完整的一道，收藏/互动引用已同步清理。");
        return sb.toString();
    }

    private String rowSummary(DuplicateRecipeAnalysisService.DuplicateRow row) {
        StringBuilder sb = new StringBuilder("「").append(row.name()).append("」 ");
        sb.append(row.hasImage() ? "有图" : "无图").append(" · 食材 ").append(row.ingredientCount())
                .append(" 步骤 ").append(row.stepCount());
        if (row.source() != null && !row.source().isBlank()) sb.append(" · 来源 ").append(row.source());
        sb.append("（").append(row.note()).append("）");
        return sb.toString();
    }
}
