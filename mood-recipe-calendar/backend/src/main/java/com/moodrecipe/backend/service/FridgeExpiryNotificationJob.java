package com.moodrecipe.backend.service;

import com.moodrecipe.backend.config.AppClock;
import com.moodrecipe.backend.entity.FridgeItem;
import com.moodrecipe.backend.repository.FridgeItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 冰箱临期提醒定时任务（补全提醒闭环）：
 * 此前只有会员在冰箱页手动点「提醒我」才发提醒（POST /fridge/notify-expiring），
 * 用户不进页面就永远不会被提醒——提醒的核心价值（防浪费）失效。
 *
 * 本任务每天早上扫描未来 3 天内到期（含已过期）的食材，按用户聚合后
 * 复用 FridgeExpiryNotifier 发一条汇总订阅消息；expiry_notified_on 限频，
 * 同一食材同一天绝不重复打扰。与 RecallNotificationJob 相同的送达机制：
 * 订阅消息一次性授权制，未授权得到 43101 仅记日志。
 * 会员判定与手动入口一致（UsageQuotaService.member，含家庭共享穿透）。
 */
@Service
public class FridgeExpiryNotificationJob {

    private static final Logger log = LoggerFactory.getLogger(FridgeExpiryNotificationJob.class);

    private final FridgeItemRepository fridgeItems;
    private final FridgeExpiryNotifier notifier;
    private final UsageQuotaService quotas;

    public FridgeExpiryNotificationJob(FridgeItemRepository fridgeItems,
                                       FridgeExpiryNotifier notifier,
                                       UsageQuotaService quotas) {
        this.fridgeItems = fridgeItems;
        this.notifier = notifier;
        this.quotas = quotas;
    }

    /** 每天 9:07 扫描临期食材（早于采购/召回推送，提醒发生在做饭决策之前）。 */
    @Scheduled(cron = "0 7 9 * * ?", zone = "Asia/Shanghai")
    public void notifyExpiring() {
        LocalDate today = AppClock.today();
        LocalDate horizon = today.plusDays(3);
        List<FridgeItem> candidates = fridgeItems.findByExpiresOnLessThanEqual(horizon);
        if (candidates.isEmpty()) return;

        Map<String, List<FridgeItem>> byUser = candidates.stream()
                // expiry_notified_on 已是今天的跳过：同一天绝不重复打扰
                .filter(item -> item.getExpiryNotifiedOn() == null || today.isAfter(item.getExpiryNotifiedOn()))
                .collect(Collectors.groupingBy(FridgeItem::getOpenid));

        int sent = 0;
        for (Map.Entry<String, List<FridgeItem>> entry : byUser.entrySet()) {
            String openid = entry.getKey();
            try {
                if (!quotas.member(openid)) continue;
                FridgeExpiryNotifier.Result result = notifier.notifyFor(openid);
                if (result == null || !result.sent()) continue;
                entry.getValue().forEach(item -> item.setExpiryNotifiedOn(today));
                fridgeItems.saveAll(entry.getValue());
                sent++;
            }
            catch (RuntimeException ignored) {
                // 单个用户失败不影响批次（与 RecallNotificationJob 同策略）
            }
        }
        log.info("冰箱临期提醒任务完成：候选 {} 人，送达 {} 人", byUser.size(), sent);
    }
}
