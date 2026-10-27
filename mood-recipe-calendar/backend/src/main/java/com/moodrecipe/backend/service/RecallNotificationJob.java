package com.moodrecipe.backend.service;

import com.moodrecipe.backend.config.AppClock;
import com.moodrecipe.backend.repository.DailyMenuRepository;
import com.moodrecipe.backend.repository.RecipeRepository;
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
    private final RecipeRepository recipes;

    public RecallNotificationJob(WechatSubscriptionMessageService subscriptions,
                                 WeeklyMealPlanRepository weeklyPlans,
                                 DailyMenuRepository dailyMenus,
                                 UserRepository users,
                                 RecipeRepository recipes) {
        this.subscriptions = subscriptions;
        this.weeklyPlans = weeklyPlans;
        this.dailyMenus = dailyMenus;
        this.users = users;
        this.recipes = recipes;
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

    /** 每天 10:30 沉睡召回：昨天和今天都没打开过的用户，推个性化菜名钩子。 */
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
                // [2026-10-27] 个性化钩子：最近一次菜牌的菜名 + 菜谱事实（时长/口味）做好处，
                // 回答"推了什么 / 为什么是你 / 吃了图什么"；查不到历史时回落通用文案。
                DishHook hook = lastDishHook(openid);
                subscriptions.sendMissYou(openid, hook.hook(), hook.benefit());
                sent++;
            } catch (RuntimeException ignored) {
                // 单个用户失败不影响批次
            }
        }
        log.info("沉睡召回任务完成：候选 {} 人", sent);
    }

    /** 召回文案素材：菜名钩子 + 好处短语（均 ≤20 字，模板 thing 字段截断由发送层兜底）。 */
    private record DishHook(String hook, String benefit) { }

    private DishHook lastDishHook(String openid) {
        try {
            return dailyMenus.findTopByOpenidOrderByMenuDateDesc(openid)
                    .flatMap(menu -> recipes.findById(menu.getRecipeId()))
                    .map(recipe -> {
                        String name = recipe.getName() == null ? "" : recipe.getName().trim();
                        if (name.length() < 2) return new DishHook(null, null);
                        if (name.length() > 10) name = name.substring(0, 10);
                        String hook = "「" + name + "」想你了";
                        String benefit = buildBenefit(recipe);
                        return new DishHook(hook, benefit);
                    })
                    .orElse(new DishHook(null, null));
        } catch (RuntimeException ignored) {
            return new DishHook(null, null);
        }
    }

    /** 好处短语：能落实的才说——时长从 cooking_time 来，口味从菜谱自身口味标签来，不编造。 */
    private String buildBenefit(com.moodrecipe.backend.entity.Recipe recipe) {
        List<String> parts = new java.util.ArrayList<>();
        Integer minutes = recipe.getCookingTime();
        if (minutes != null && minutes > 0) {
            parts.add(minutes <= 30 ? minutes + "分钟搞定" : "半小时慢慢做");
        }
        String flavor = (recipe.getTaste() == null ? "" : recipe.getTaste())
                + "," + (recipe.getTags() == null ? "" : recipe.getTags());
        if (flavor.contains("辣")) parts.add("够味下饭");
        else if (flavor.contains("清淡")) parts.add("清淡不腻");
        else if (flavor.contains("汤")) parts.add("热乎暖胃");
        else if (flavor.contains("甜")) parts.add("甜甜治愈");
        else if (flavor.contains("鲜")) parts.add("鲜得很");
        if (parts.isEmpty()) return null;
        return String.join("，", parts);
    }
}
