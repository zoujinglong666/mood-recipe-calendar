package com.moodrecipe.backend.service;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 冰箱临期提醒编排：找出临期/过期食材 → 组装文案 → 发订阅消息。
 *
 * 会员校验在调用方（Controller）完成；本服务只负责"提醒"这一动作，
 * 且发送失败绝不影响用户当前操作。
 */
@Service
public class FridgeExpiryNotifier {

    private final FridgeInventoryService inventory;
    private final WechatSubscriptionMessageService messages;

    public FridgeExpiryNotifier(FridgeInventoryService inventory,
                                WechatSubscriptionMessageService messages) {
        this.inventory = inventory;
        this.messages = messages;
    }

    public record Result(boolean sent, int count, String names) {}

    /** 为用户发送一条临期汇总提醒。 */
    public Result notifyFor(String openid) {
        FridgeInventoryService.SummaryView summary = inventory.summary(openid);
        List<FridgeInventoryService.ItemView> priority = summary.priorityItems();
        if (priority == null || priority.isEmpty()) {
            return new Result(false, 0, "");
        }
        String names = priority.stream()
                .map(FridgeInventoryService.ItemView::name)
                .reduce((a, b) -> a + "、" + b)
                .orElse("");
        int minDaysLeft = priority.stream()
                .map(FridgeInventoryService.ItemView::daysLeft)
                .filter(java.util.Objects::nonNull)
                .min(Integer::compareTo)
                .orElse(0);
        messages.sendFridgeExpiring(openid, names, minDaysLeft);
        return new Result(true, priority.size(), names);
    }
}
