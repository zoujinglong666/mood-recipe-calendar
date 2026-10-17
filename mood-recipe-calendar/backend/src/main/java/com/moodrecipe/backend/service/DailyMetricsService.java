package com.moodrecipe.backend.service;

import com.moodrecipe.backend.config.AppClock;
import com.moodrecipe.backend.repository.DailyActiveUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每日运营指标：活跃人数（DAU）与新增用户，并按日推送微信日报。
 *
 * 活跃口径：任何携带有效 session token 的请求都算当日活跃一次。
 * 去重靠 daily_active_users 的唯一约束 + INSERT IGNORE，内存里再缓存一份
 * 「今天已记过的人」，避免同一用户当天反复打库。
 */
@Service
public class DailyMetricsService {

    private static final Logger log = LoggerFactory.getLogger(DailyMetricsService.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MM-dd");

    /** 只缓存当天的 openid，换天自动作废，无需清理。 */
    private final Set<String> recordedToday = ConcurrentHashMap.newKeySet();
    private volatile LocalDate cachedDay = AppClock.today();

    private final JdbcTemplate jdbc;
    private final DailyActiveUserRepository actives;
    private final WxPusherNotifier notifier;
    /** 运营观测聚合（可选依赖，避免测试构造器签名变化）：日报附带留存/渗透/转化/K 因子 */
    private final OpsMetricsService opsMetrics;

    public DailyMetricsService(JdbcTemplate jdbc, DailyActiveUserRepository actives, WxPusherNotifier notifier) {
        this(jdbc, actives, notifier, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public DailyMetricsService(JdbcTemplate jdbc, DailyActiveUserRepository actives, WxPusherNotifier notifier,
                               OpsMetricsService opsMetrics) {
        this.jdbc = jdbc;
        this.actives = actives;
        this.notifier = notifier;
        this.opsMetrics = opsMetrics;
    }

    /**
     * 记录一次活跃。由鉴权拦截器在 token 校验通过后调用，属热路径，
     * 必须快速返回：命中内存缓存直接返回，未命中才写库。
     */
    public void markActive(String openid) {
        if (openid == null || openid.isEmpty()) return;
        LocalDate today = AppClock.today();
        if (!today.equals(cachedDay)) {
            recordedToday.clear();
            cachedDay = today;
        }
        if (!recordedToday.add(openid)) return;
        try {
            jdbc.update("insert ignore into daily_active_users (active_date, openid) values (?, ?)", today, openid);
        } catch (Exception e) {
            // 指标失败绝不影响业务请求
            recordedToday.remove(openid);
            log.warn("[metrics] 记录活跃失败 openid={}: {}", openid, e.getMessage());
        }
    }

    /** 次日 09:00 推送前一天的数据（当天数据要等结束才完整）。 */
    @Scheduled(cron = "0 0 9 * * ?", zone = "Asia/Shanghai")
    public void pushDailyReport() {
        LocalDate day = AppClock.today().minusDays(1);
        long active = actives.countByActiveDate(day);
        long newUsers = countNewUsers(day);
        long total = countTotalUsers();
        String message = String.format("[锅仔日报] %s 活跃 %d 人 ／ 新增 %d 人 ／ 累计用户 %d 人",
                day.format(DAY), active, newUsers, total);
        // 运营观测行：记录渗透率 / 付费 / 会员转化 / 分享 K 因子 / LLM 调用量与估算成本
        if (opsMetrics != null) {
            try {
                message += "\n" + opsMetrics.dailyReportLine(day);
                // 留存线：昨日注册 cohort 的 D1（观察期已到的最近一天）
                var retention = opsMetrics.retention(2);
                if (!retention.isEmpty()) {
                    var latest = retention.get(retention.size() - 1);
                    Object d1 = latest.get("d1Pct");
                    message += String.format("\n昨日新增 D1 留存 %s%%（cohort %s 人）",
                            d1 == null ? "未到观察期" : d1, latest.get("cohortSize"));
                }
            } catch (Exception e) {
                log.warn("[metrics] 运营观测行推送失败（不影响基础日报）: {}", e.getMessage());
            }
        }
        log.info("[metrics] {}", message);
        notifier.send(message);
    }

    private long countNewUsers(LocalDate day) {
        Long value = jdbc.queryForObject(
                "select count(*) from users where date(created_at) = ?", Long.class, day);
        return value == null ? 0 : value;
    }

    /** 累计注册用户数，登录通知里用来体现增长。 */
    public long countTotalUsers() {
        try {
            Long value = jdbc.queryForObject("select count(*) from users", Long.class);
            return value == null ? 0 : value;
        } catch (Exception e) {
            return 0;
        }
    }
}
