package com.moodrecipe.backend.service;

import org.springframework.stereotype.Service;
import java.util.List;

/**
 * 成就目录（借鉴 NiniMenu 的成就体系）。
 * 每个成就配锅仔IP徽章图，统一暖橙配色、金色圆形边框。
 * 徽章图已下沉到前端 src/static/badges/，返回相对路径，小程序端直接 /static/badges/xxx.png 引用。
 */
@Service
public class AchievementCatalog {

    public record Achievement(String code, String name, String desc, String emoji, String badgeImage) {}

    public List<Achievement> all() {
        return List.of(
            // 记录次数
            new Achievement("first_pick", "初入厨房", "记录第一道菜", "🍳", "/static/badges/first_pick.png"),
            new Achievement("record_3", "三餐起步", "累计记录3道菜", "🥢", "/static/badges/record_3.png"),
            new Achievement("record_10", "十全十美", "累计记录10道菜", "🔟", "/static/badges/record_10.png"),
            new Achievement("record_30", "饭桌常客", "累计记录30道菜", "🍽️", "/static/badges/record_30.png"),
            new Achievement("record_50", "半百饭局", "累计记录50道菜", "🏅", "/static/badges/record_50.png"),
            new Achievement("record_100", "百味人生", "累计记录100道菜", "💯", "/static/badges/record_100.png"),

            // 不同菜品探索
            new Achievement("distinct_3", "尝鲜入门", "吃过3道不同菜", "🌱", "/static/badges/distinct_3.png"),
            new Achievement("distinct_10", "十味收藏", "吃过10道不同菜", "✨", "/static/badges/distinct_10.png"),
            new Achievement("distinct_30", "菜单探索者", "吃过30道不同菜", "🧭", "/static/badges/distinct_30.png"),
            new Achievement("distinct_50", "五十味图鉴", "吃过50道不同菜", "📖", "/static/badges/distinct_50.png"),

            // 连续天数
            new Achievement("streak_3", "三天不断炊", "连续3天记录", "🔥", "/static/badges/streak_3.png"),
            new Achievement("streak_7", "连续七天", "连续7天记录", "📅", "/static/badges/streak_7.png"),
            new Achievement("streak_14", "双周饭搭子", "连续14天记录", "📆", "/static/badges/streak_14.png"),
            new Achievement("streak_30", "月度厨房王", "连续30天记录", "🏆", "/static/badges/streak_30.png"),

            // 快手菜
            new Achievement("quick_5", "快手达人", "记录5道快手菜", "⚡", "/static/badges/quick_5.png"),
            new Achievement("quick_15", "十五分钟战神", "记录15道快手菜", "⏱️", "/static/badges/quick_15.png"),

            // 收藏/点赞
            new Achievement("like_5", "真香时刻", "点赞5道菜", "😋", "/static/badges/like_5.png"),
            new Achievement("like_15", "爱吃鬼", "点赞15道菜", "😍", "/static/badges/like_15.png"),

            // 评价
            new Achievement("review_1", "第一次点评", "写下第一条评价", "💬", "/static/badges/review_1.png"),
            new Achievement("review_10", "认真吃饭", "评价10道菜", "⭐", "/static/badges/review_10.png"),

            // 分享传播（运营主推：徽章必须绑定分享行为才有传播价值）
            new Achievement("share_1", "分享家", "第一次分享锅仔", "📣", "/static/badges/share_1.png"),
            new Achievement("share_10", "传菜大使", "累计分享10次", "📮", "/static/badges/share_10.png"),

            // 周菜单规划
            new Achievement("plan_1", "周排菜师", "第一次生成周菜单", "🗓️", "/static/badges/plan_1.png")
        );
    }
}
