package com.moodrecipe.backend.service;

import com.moodrecipe.backend.common.HealthMetrics;
import com.moodrecipe.backend.config.AppClock;
import com.moodrecipe.backend.entity.LlmUsageDaily;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * [2026-09-26→2026-10-26] 每日健康摘要（架构评审 P2-9）。
 *
 * <p>此前告警只有 WxPusher 实时 500 一条通道——「没报错但一直在降级」的问题
 * （LLM 大量回落本地规则、接口频繁 5xx）会被静默吞掉。每天 09:00 推送一条
 * Markdown 摘要给管理员：昨日 LLM 用量（次数/失败率/均延迟，按档位）、
 * 后端 500 次数、前端错误上报次数。未配置 WxPusher 时降级为 INFO 日志。
 */
@Service
public class DailyHealthReportService {

    private static final Logger log = LoggerFactory.getLogger(DailyHealthReportService.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MM-dd");

    private final LlmUsageDailyService llmUsageDailyService;
    private final HealthMetrics metrics;
    private final WxPusherService wxPusher;

    public DailyHealthReportService(LlmUsageDailyService llmUsageDailyService,
                                    HealthMetrics metrics,
                                    WxPusherService wxPusher) {
        this.llmUsageDailyService = llmUsageDailyService;
        this.metrics = metrics;
        this.wxPusher = wxPusher;
    }

    /** 每天早上 9 点推昨日健康摘要（昨天 23:57 的用量落库已完成后执行）。 */
    @Scheduled(cron = "${app.health-report.cron:0 0 9 * * ?}")
    public void report() {
        LocalDate yesterday = AppClock.today().minusDays(1);
        List<LlmUsageDaily> usage = llmUsageDailyService.usageOf(yesterday);
        long serverErrors = metrics.snapshotAndResetServerErrors();
        long clientErrors = metrics.snapshotAndResetClientErrors();

        StringBuilder md = new StringBuilder();
        md.append("## 锅仔每日健康摘要 ").append(yesterday.format(DATE)).append("\n\n");

        md.append("**LLM 用量**");
        if (usage.isEmpty()) {
            md.append("：昨日无调用\n");
        } else {
            md.append("\n");
            for (LlmUsageDaily row : usage) {
                double failRate = row.getCalls() == 0 ? 0 : 100.0 * row.getFailures() / row.getCalls();
                long avgLatency = row.getCalls() == 0 ? 0 : row.getTotalLatencyMs() / row.getCalls();
                md.append(String.format("- %s：调用 %d 次，失败率 %.1f%%，均延迟 %d ms%n",
                        row.getTier(), row.getCalls(), failRate, avgLatency));
            }
        }

        md.append("\n**稳定性**\n")
          .append(String.format("- 后端 500：%d 次%n", serverErrors))
          .append(String.format("- 前端错误上报：%d 次%n", clientErrors))
          .append(String.format("- 限流拦截：%d 次（设计红线=0，非 0 即误伤，需查限流配置）%n",
                  metrics.snapshotAndResetRateLimited()));

        if (serverErrors > 0 || clientErrors > 10) {
            md.append("\n> ⚠️ 存在异常信号，建议翻看当日日志定位。");
        }

        wxPusher.sendToAdmin("锅仔每日健康摘要 " + yesterday.format(DATE), md.toString());
        log.info("每日健康摘要已生成 date={} llmTiers={} serverErrors={} clientErrors={}",
                yesterday, usage.size(), serverErrors, clientErrors);
    }
}
