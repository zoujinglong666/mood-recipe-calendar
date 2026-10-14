package com.moodrecipe.backend.service;

import org.springframework.stereotype.Service;
import java.util.List;

/**
 * 成就目录（借鉴 NiniMenu 的成就体系）。
 * 每个成就配锅仔IP徽章图，统一暖橙配色、金色圆形边框。
 */
@Service
public class AchievementCatalog {

    public record Achievement(String code, String name, String desc, String emoji, String badgeImage) {}

    public List<Achievement> all() {
        return List.of(
            // 记录次数
            new Achievement("first_pick", "初入厨房", "记录第一道菜", "🍳", "https://aka.doubaocdn.com/s/oSiHGxVGrD"),
            new Achievement("record_3", "三餐起步", "累计记录3道菜", "🥢", "https://aka.doubaocdn.com/s/ou7Kb94pVu"),
            new Achievement("record_10", "十全十美", "累计记录10道菜", "🔟", "https://aka.doubaocdn.com/s/vVKKM0uQnV"),
            new Achievement("record_30", "饭桌常客", "累计记录30道菜", "🍽️", "https://aka.doubaocdn.com/s/CNjzdtEfHn"),
            new Achievement("record_50", "半百饭局", "累计记录50道菜", "🏅", "https://aka.doubaocdn.com/s/z6Zhqsw7OG"),
            new Achievement("record_100", "百味人生", "累计记录100道菜", "💯", "https://aka.doubaocdn.com/s/kMUh5ek5lX"),

            // 不同菜品探索
            new Achievement("distinct_3", "尝鲜入门", "吃过3道不同菜", "🌱", "https://aka.doubaocdn.com/s/LF6CpnNp7R"),
            new Achievement("distinct_10", "十味收藏", "吃过10道不同菜", "✨", "https://aka.doubaocdn.com/s/zYdBFHxJo3"),
            new Achievement("distinct_30", "菜单探索者", "吃过30道不同菜", "🧭", "https://aka.doubaocdn.com/s/W0zEe6yYRi"),
            new Achievement("distinct_50", "五十味图鉴", "吃过50道不同菜", "📖", "https://aka.doubaocdn.com/s/7zvjTfCH7G"),

            // 连续天数
            new Achievement("streak_3", "三天不断炊", "连续3天记录", "🔥", "https://aka.doubaocdn.com/s/Pf0UFE8Oxv"),
            new Achievement("streak_7", "连续七天", "连续7天记录", "📅", "https://aka.doubaocdn.com/s/V8tlVlhmts"),
            new Achievement("streak_14", "双周饭搭子", "连续14天记录", "📆", "https://aka.doubaocdn.com/s/1SmtaVZWoF"),
            new Achievement("streak_30", "月度厨房王", "连续30天记录", "🏆", "https://aka.doubaocdn.com/s/KLHOYbIGMd"),

            // 快手菜
            new Achievement("quick_5", "快手达人", "记录5道快手菜", "⚡", "https://aka.doubaocdn.com/s/Y5l9hjpvTi"),
            new Achievement("quick_15", "十五分钟战神", "记录15道快手菜", "⏱️", "https://aka.doubaocdn.com/s/XF13awJLac"),

            // 收藏/点赞
            new Achievement("like_5", "真香时刻", "点赞5道菜", "😋", "https://aka.doubaocdn.com/s/6pXEw5Gu1U"),
            new Achievement("like_15", "爱吃鬼", "点赞15道菜", "😍", "https://aka.doubaocdn.com/s/UBA8ZvNFzM"),

            // 评价
            new Achievement("review_1", "第一次点评", "写下第一条评价", "💬", "https://aka.doubaocdn.com/s/SWTGa9wTZB"),
            new Achievement("review_10", "认真吃饭", "评价10道菜", "⭐", "https://aka.doubaocdn.com/s/uGGWZRrBvI")
        );
    }
}
