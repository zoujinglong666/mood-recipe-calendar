package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.UserFeedback;
import com.moodrecipe.backend.repository.UserFeedbackRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyFeedbackLogServiceTest {
    private final UserFeedbackRepository feedback = mock(UserFeedbackRepository.class);
    private final WxPusherNotifier notifier = mock(WxPusherNotifier.class);
    private final DailyFeedbackLogService service = new DailyFeedbackLogService(feedback, notifier);

    @Test
    void pushesSummaryWithCategoryCountsAndPreviews() {
        LocalDate day = LocalDate.of(2026, 10, 18);
        when(feedback.findByCreatedAtBetweenOrderByCreatedAtDesc(any(), any())).thenReturn(List.of(
                feedback(1L, "体验问题", "转盘转到不喜欢的菜，希望能记住我不吃香菜", "wx:user1", "wx-abcdef123456"),
                feedback(2L, "功能建议", "想要一个早餐推荐入口", null, "wx-zzzzzz999999")));

        String message = service.pushForDay(day);

        assertTrue(message.contains("[锅仔反馈] 10-18 收到 2 条"), message);
        assertTrue(message.contains("功能建议 1"), message);
        assertTrue(message.contains("体验问题 1"), message);
        assertTrue(message.contains("内容反馈 0"), message);
        assertTrue(message.contains("转盘转到不喜欢的菜"), message);
        assertTrue(message.contains("联系:wx:user1"), message);
        assertTrue(message.contains("123456"), message);
        verify(notifier).send(message);
    }

    @Test
    void truncatesLongContent() {
        String longContent = "啊".repeat(80);
        when(feedback.findByCreatedAtBetweenOrderByCreatedAtDesc(any(), any())).thenReturn(List.of(
                feedback(9L, "其他", longContent, null, "openid-123456")));

        String message = service.pushForDay(LocalDate.of(2026, 10, 18));

        assertTrue(message.contains("…"), message);
        assertFalse(message.contains(longContent), message);
    }

    @Test
    void zeroFeedbackStillPushes() {
        when(feedback.findByCreatedAtBetweenOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());

        String message = service.pushForDay(LocalDate.of(2026, 10, 18));

        assertTrue(message.contains("收到 0 条"), message);
        assertFalse(message.contains("最近反馈"), message);
        verify(notifier).send(message);
    }

    @Test
    void usesShanghaiDayBoundaries() {
        when(feedback.findByCreatedAtBetweenOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());

        service.pushForDay(LocalDate.of(2026, 10, 18));

        ArgumentCaptor<LocalDateTime> start = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> end = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(feedback).findByCreatedAtBetweenOrderByCreatedAtDesc(start.capture(), end.capture());
        org.junit.jupiter.api.Assertions.assertEquals(LocalDateTime.of(2026, 10, 18, 0, 0), start.getValue());
        org.junit.jupiter.api.Assertions.assertEquals(LocalDateTime.of(2026, 10, 19, 0, 0), end.getValue());
    }

    private UserFeedback feedback(Long id, String category, String content, String contact, String openid) {
        UserFeedback f = new UserFeedback();
        f.setId(id);
        f.setCategory(category);
        f.setContent(content);
        f.setContact(contact);
        f.setOpenid(openid);
        return f;
    }
}
