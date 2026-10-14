package com.moodrecipe.backend.service;

import com.moodrecipe.backend.config.AppClock;
import com.moodrecipe.backend.repository.DailyMenuRepository;
import com.moodrecipe.backend.repository.UserRepository;
import com.moodrecipe.backend.repository.WeeklyMealPlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 订阅消息召回任务（运营留存基建）：
 * - 周六 10:00：「买菜清单已备好」——发给最近 7 天生成过周菜单的用户（周菜单天然带购物清单场景）；
 * - 每天 10:30：「锅仔想你了」——连续 2 天未打开（昨天、今天都没有首页今日菜单行）的用户。
 *
 * 微信订阅消息是一次性授权制：只有用户此前在对应场景勾选授权才能送达，未授权发送会得到
 * errcode=43101 并仅记日志——这是平台机制，运营侧需持续在关键动作后请求授权。
 * 模板 ID 未配置（空）时任务直接跳过，不产生任何调用。
 */
@Service
public class RecallNotificationJob {

    private static final Logger log = LoggerFactory.getLogger(RecallNotificationJob.class);

    private final WechatSubscriptionMessageService subscriptions;
    private final WeeklyMealPlanRepository weeklyPlans;
    private final DailyMenuRepository dailyMenus;
    private final UserRepository users;

    public RecallNotificationJob(WechatSubscriptionMessageService subscriptions,
                                 WeeklyMealPlanRepository weeklyPlans,
                                 DailyMenuRepository dailyMenus,
                                 UserRepository users) {
        this.subscriptions = subscriptions;
        this.weeklyPlans = weeklyPlans;
        this.dailyMenus = dailyMenus;
        this.users = users;
    }

    /** 周六上午清单提醒：让用户照着本周菜单采购，把「生成菜单」延伸成「周末采购」回访。 */
    @Scheduled(cron = "0 0 10 ? * SAT", zone = "Asia/Shanghai")
    public void shoppingListSaturday() {
        LocalDateTime since = AppClock.today().minusDays(7).atStartOfDay();
        List<String> openids = weeklyPlans.findOpenidsByCreatedAtAfter(since);
        if (openids.isEmpty()) return;
        int sent = 0;
        for (String openid : openids) {
            try {
                subscriptions.sendShoppingListReady(openid);
                sent++;
            } catch (RuntimeException ignored) {
                // 单个用户失败不影响批次
            }
        }
        log.info("周六清单提醒任务完成：候选 {} 人", sent);
    }

    /** 每天 10:30 沉睡召回：昨天和今天都没打开过的用户，推今日菜牌预告。 */
    @Scheduled(cron = "0 30 10 * * ?", zone = "Asia/Shanghai")
    public void missYouRecall() {
        LocalDate today = AppClock.today();
        Set<String> active = new HashSet<>(dailyMenus.findOpenidsByMenuDate(today));
        active.addAll(dailyMenus.findOpenidsByMenuDate(today.minusDays(1)));
        Set<String> all = new HashSet<>(users.findAllOpenids());
        all.removeAll(active);
        if (all.isEmpty()) return;
        int sent = 0;
        for (String openid : all) {
            try {
                subscriptions.sendMissYou(openid);
                sent++;
            } catch (RuntimeException ignored) {
                // 单个用户失败不影响批次
            }
        }
        log.info("沉睡召回任务完成：候选 {} 人", sent);
    }
}
