package com.moodrecipe.backend.service;

import com.moodrecipe.backend.agent.LlmUsageCounter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运营观测聚合：在既有事实表（daily_active_users / users / operational_events /
 * virtual_orders / share_records）上做 SQL 聚合，补齐判断「成功」所需的核心观测——
 * 留存曲线、推荐→记录漏斗、会员转化、分享 K 因子、LLM 调用量（成本 = 次数 × 档位单价）。
 *
 * 不新建表：所有指标由事实表实时推导，避免双写不一致；数据量到十万级 DAU 前都够用。
 */
@Service
public class OpsMetricsService {

    private final JdbcTemplate jdbc;
    private final LlmUsageCounter llmUsage;
    private final WxPusherNotifier notifier;
    /** 各 LLM 档位单次调用估算成本（元），由运营侧按供应商定价配置；用于日报成本估算 */
    private final double costFree;
    private final double costPaidPro;
    private final double costPaidFlash;

    public OpsMetricsService(JdbcTemplate jdbc, LlmUsageCounter llmUsage,
                             @Value("${ops.llm.cost.free:0}") double costFree,
                             @Value("${ops.llm.cost.paid-pro:0.02}") double costPaidPro,
                             @Value("${ops.llm.cost.paid-flash:0.002}") double costPaidFlash) {
        this(jdbc, llmUsage, null, costFree, costPaidPro, costPaidFlash);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public OpsMetricsService(JdbcTemplate jdbc, LlmUsageCounter llmUsage, WxPusherNotifier notifier,
                             @Value("${ops.llm.cost.free:0}") double costFree,
                             @Value("${ops.llm.cost.paid-pro:0.02}") double costPaidPro,
                             @Value("${ops.llm.cost.paid-flash:0.002}") double costPaidFlash) {
        this.jdbc = jdbc;
        this.llmUsage = llmUsage;
        this.notifier = notifier;
        this.costFree = costFree;
        this.costPaidPro = costPaidPro;
        this.costPaidFlash = costPaidFlash;
    }

    /** 单日运营总览（day 指标的自然日；LLM 计数为进程内存，重启后重新累计） */
    public Map<String, Object> overview(LocalDate day) {
        Map<String, Object> out = new LinkedHashMap<>();
        String d = day.toString();

        long dau = count("select count(*) from daily_active_users where active_date = ?", d);
        long newUsers = count("select count(*) from users where date(created_at) = ?", d);
        long totalUsers = count("select count(*) from users");

        // 漏斗：推荐成功（去重人数）→ 当日做饭记录（去重人数），渗透率 = 记录人数 / DAU
        long recommendUsers = countDistinctOpenid("RECOMMEND_DONE", d);
        long recordUsers = countDistinctOpenid("RECORD_SAVED", d);
        long recordCount = count("select count(*) from operational_events where event_type = 'RECORD_SAVED' and date(created_at) = ?", d);

        // 付费：当日发货订单与金额（分）；会员转化 = 累计付费用户 / 累计用户
        long payOrders = count("select count(*) from virtual_orders where status = 'DELIVERED' and date(delivered_at) = ?", d);
        long payAmountFen = count("select coalesce(sum(amount_fen), 0) from virtual_orders where status = 'DELIVERED' and date(delivered_at) = ?", d);
        long payUsers = count("select count(distinct openid) from virtual_orders where status = 'DELIVERED'");
        double memberConversion = totalUsers == 0 ? 0 : round2(payUsers * 100.0 / totalUsers);

        // 分享 K 因子：当日被邀请回流人数 / 当日主动分享人数（双边奖励的裂变效率）
        long shareCount = count("select count(distinct openid) from share_records where scene <> 'ACCEPT' and date(created_at) = ?", d);
        long acceptCount = count("select count(distinct openid) from share_records where scene = 'ACCEPT' and date(created_at) = ?", d);
        double kFactor = shareCount == 0 ? 0 : round2(acceptCount * 1.0 / shareCount);

        // LLM 调用量与估算成本（内存计数，截至读取时刻）
        Map<String, LlmUsageCounter.Bucket> llm = llmUsage.snapshotAndReset(false);
        long llmTotal = llm.values().stream().mapToLong(LlmUsageCounter.Bucket::calls).sum();
        double llmCost = llm.entrySet().stream().mapToDouble(entry -> {
            double unit = switch (entry.getKey()) {
                case LlmUsageCounter.PAID_PRO -> costPaidPro;
                case LlmUsageCounter.PAID_FLASH -> costPaidFlash;
                default -> costFree;
            };
            return entry.getValue().calls() * unit;
        }).sum();

        out.put("date", d);
        out.put("dau", dau);
        out.put("newUsers", newUsers);
        out.put("totalUsers", totalUsers);
        out.put("recommendUsers", recommendUsers);
        out.put("recordUsers", recordUsers);
        out.put("recordCount", recordCount);
        out.put("recordPenetrationPct", dau == 0 ? 0 : round2(recordUsers * 100.0 / dau));
        out.put("payOrders", payOrders);
        out.put("payAmountYuan", round2(payAmountFen / 100.0));
        out.put("payUsersTotal", payUsers);
        out.put("memberConversionPct", memberConversion);
        out.put("shareUsers", shareCount);
        out.put("acceptUsers", acceptCount);
        out.put("kFactor", kFactor);
        out.put("llmCalls", llmTotal);
        out.put("llmCallsByTier", llm);
        out.put("llmEstimatedCostYuan", round2(llmCost));
        return out;
    }

    /**
     * 注册 cohort 留存：最近 days 天每天注册的用户，其 D1/D3/D7 当日活跃占比。
     * 返回 [{cohortDate, cohortSize, d1Pct, d3Pct, d7Pct}]（未到观察期的为 null）。
     */
    public List<Map<String, Object>> retention(int days) {
        int bounded = Math.max(1, Math.min(days, 90));
        LocalDate today = com.moodrecipe.backend.config.AppClock.today();
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = bounded; i >= 1; i--) {
            LocalDate cohort = today.minusDays(i);
            String c = cohort.toString();
            long cohortSize = count("select count(*) from users where date(created_at) = ?", c);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("cohortDate", c);
            row.put("cohortSize", cohortSize);
            row.put("d1Pct", retentionPct(cohort, 1, cohortSize));
            row.put("d3Pct", retentionPct(cohort, 3, cohortSize));
            row.put("d7Pct", retentionPct(cohort, 7, cohortSize));
            out.add(row);
        }
        return out;
    }

    /** Dn 留存 = 注册日+n 当日活跃的注册用户 / 注册用户 */
    private Double retentionPct(LocalDate cohort, int offset, long cohortSize) {
        if (cohortSize == 0) return 0.0;
        LocalDate target = cohort.plusDays(offset);
        if (!target.isBefore(LocalDate.now())) return null; // 观察期未到
        long active = count("""
                select count(*) from users u
                where date(u.created_at) = ?
                  and exists (select 1 from daily_active_users a
                              where a.openid = u.openid and a.active_date = ?)
                """, cohort.toString(), target.toString());
        return round2(active * 100.0 / cohortSize);
    }

    /** 日报文本：给 DailyMetricsService 拼进微信推送 */
    public String dailyReportLine(LocalDate day) {
        Map<String, Object> o = overview(day);
        return String.format(
                "渗透 %.0f%%（%d/%d 人做记录）｜付费 %d 单 %.2f 元｜会员转化 %.1f%%｜K=%.2f｜LLM %d 次≈%.2f 元",
                num(o.get("recordPenetrationPct")), num(o.get("recordUsers")), num(o.get("dau")),
                num(o.get("payOrders")), dbl(o.get("payAmountYuan")),
                dbl(o.get("memberConversionPct")), dbl(o.get("kFactor")),
                num(o.get("llmCalls")), dbl(o.get("llmEstimatedCostYuan")));
    }

    /**
     * 立即推送完整运营播报到 WxPusher（复用已封装的 notifier，异步安全转义）。
     * 返回推送文本供管理端接口回显；notifier 未注入或未配置时仅返回文本不推送。
     */
    public String pushNow(LocalDate day) {
        Map<String, Object> o = overview(day);
        StringBuilder message = new StringBuilder();
        message.append(String.format("[锅仔运营播报] %s 活跃 %d ／ 新增 %d ／ 累计 %d",
                day.toString(), num(o.get("dau")), num(o.get("newUsers")), num(o.get("totalUsers"))));
        message.append(String.format("\n漏斗：推荐 %d 人 → 做记录 %d 人，渗透 %.0f%%",
                num(o.get("recommendUsers")), num(o.get("recordUsers")), num(o.get("recordPenetrationPct"))));
        message.append(String.format("\n付费：%d 单 %.2f 元｜会员转化 %.1f%%｜分享 K=%.2f",
                num(o.get("payOrders")), dbl(o.get("payAmountYuan")),
                dbl(o.get("memberConversionPct")), dbl(o.get("kFactor"))));
        message.append(String.format("\nLLM %d 次≈%.2f 元", num(o.get("llmCalls")), dbl(o.get("llmEstimatedCostYuan"))));
        // 留存：最近 3 个已过观察期的 cohort
        try {
            var retention = retention(10);
            var seen = retention.stream()
                    .filter(row -> row.get("d7Pct") != null)
                    .toList();
            for (var row : seen.subList(Math.max(0, seen.size() - 3), seen.size())) {
                message.append(String.format("\n留存 cohort %s（%d 人）：D1 %s%%｜D3 %s%%｜D7 %s%%",
                        row.get("cohortDate"), num(row.get("cohortSize")),
                        row.get("d1Pct"), row.get("d3Pct"), row.get("d7Pct")));
            }
        } catch (Exception ignored) { }
        String text = message.toString();
        if (notifier != null) {
            notifier.send(text);
        }
        return text;
    }

    private long countDistinctOpenid(String eventType, String day) {
        return count("select count(distinct openid) from operational_events where event_type = ? and date(created_at) = ?",
                eventType, day);
    }

    private long count(String sql, Object... args) {
        try {
            Long value = jdbc.queryForObject(sql, Long.class, args);
            return value == null ? 0 : value;
        } catch (Exception e) {
            return 0;
        }
    }

    private static long num(Object value) {
        return value instanceof Number n ? n.longValue() : 0;
    }

    private static double dbl(Object value) {
        return value instanceof Number n ? n.doubleValue() : 0;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
