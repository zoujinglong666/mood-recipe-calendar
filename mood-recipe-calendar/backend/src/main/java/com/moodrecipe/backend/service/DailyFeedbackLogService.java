package com.moodrecipe.backend.service;

import com.moodrecipe.backend.config.AppClock;
import com.moodrecipe.backend.entity.UserFeedback;
import com.moodrecipe.backend.repository.UserFeedbackRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 每日反馈日志：次日 09:05 把前一天的 user_feedback 汇总推送到微信（WxPusher）。
 *
 * 内容 = 总数 + 四类计数 + 最近至多 3 条内容预览（含联系方式），
 * 让开发者不进后台就能读到用户原话；与 09:00 的运营日报错开 5 分钟，两条消息不撞车。
 * 推送完全异步且失败只记日志（WxPusherNotifier 已吞异常），不影响任何业务流程。
 */
@Service
public class DailyFeedbackLogService {

    private static final Logger log = LoggerFactory.getLogger(DailyFeedbackLogService.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MM-dd");
    private static final List<String> CATEGORIES = List.of("功能建议", "体验问题", "内容反馈", "其他");
    private static final int PREVIEW_LIMIT = 3;
    private static final int PREVIEW_CHARS = 40;

    private final UserFeedbackRepository feedback;
    private final WxPusherNotifier notifier;

    public DailyFeedbackLogService(UserFeedbackRepository feedback, WxPusherNotifier notifier) {
        this.feedback = feedback;
        this.notifier = notifier;
    }

    /** 次日 09:05 推送（比运营日报晚 5 分钟）。 */
    @Scheduled(cron = "0 5 9 * * ?", zone = "Asia/Shanghai")
    public void pushDailyFeedbackLog() {
        pushForDay(AppClock.today().minusDays(1));
    }

    /** 按天汇总并推送；返回推送文本，供管理员手动触发时确认内容。 */
    public String pushForDay(LocalDate day) {
        LocalDateTime start = day.atStartOfDay(AppClock.ZONE).toLocalDateTime();
        LocalDateTime end = day.plusDays(1).atStartOfDay(AppClock.ZONE).toLocalDateTime();
        List<UserFeedback> items = feedback.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end);

        StringBuilder message = new StringBuilder();
        message.append(String.format("[锅仔反馈] %s 收到 %d 条", day.format(DAY), items.size()));
        if (!items.isEmpty()) {
            Map<String, Long> byCategory = items.stream()
                    .collect(Collectors.groupingBy(UserFeedback::getCategory, Collectors.counting()));
            String cats = CATEGORIES.stream()
                    .map(c -> c + " " + byCategory.getOrDefault(c, 0L))
                    .collect(Collectors.joining(" ／ "));
            message.append("：").append(cats);
            message.append("。最近反馈");
            items.stream().limit(PREVIEW_LIMIT).forEach(f -> {
                String preview = f.getContent() == null ? "" : f.getContent().replaceAll("\\s+", " ").trim();
                if (preview.length() > PREVIEW_CHARS)
                    preview = preview.substring(0, PREVIEW_CHARS) + "…";
                String tail = f.getOpenid() == null || f.getOpenid().length() < 6
                        ? "" : " …" + f.getOpenid().substring(f.getOpenid().length() - 6);
                String contact = f.getContact() == null || f.getContact().isBlank()
                        ? "" : " 联系:" + f.getContact().replaceAll("\\s+", "");
                message.append(String.format(" %s)[%s]%s%s%s;",
                        f.getId(), f.getCategory(), preview, tail, contact));
            });
        }
        String text = message.toString();
        log.info("[feedback-daily] {}", text);
        notifier.send(text);
        return text;
    }
}
