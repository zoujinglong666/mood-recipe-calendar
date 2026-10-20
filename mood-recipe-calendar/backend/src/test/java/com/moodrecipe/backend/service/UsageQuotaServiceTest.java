package com.moodrecipe.backend.service;

import com.moodrecipe.backend.config.AppClock;
import com.moodrecipe.backend.entity.UsageQuota;
import com.moodrecipe.backend.entity.User;
import com.moodrecipe.backend.repository.UsageQuotaRepository;
import com.moodrecipe.backend.repository.UserRepository;
import com.moodrecipe.backend.repository.CheckinRepository;
import com.moodrecipe.backend.service.AdRewardService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UsageQuotaServiceTest {
    private final UsageQuotaRepository quotas = mock(UsageQuotaRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final CheckinRepository checkins = mock(CheckinRepository.class);
    private final UsageQuotaService service = new UsageQuotaService(quotas, users);

    @Test
    void regularUserGetsThreeDailyRecommendations() {
        UsageQuota quota = quota(2);
        when(quotas.findForUpdate(anyString(), anyString(), any())).thenReturn(Optional.of(quota));

        UsageQuotaService.View view = service.consume("user-1", UsageQuotaService.Feature.HOME_RECOMMEND);

        assertEquals(3, view.limit());
        assertEquals(0, view.remaining());
        assertEquals(3, quota.getUsedCount());
    }

    @Test
    void regularUserCannotExceedThreeDailyRecommendations() {
        when(quotas.findForUpdate(anyString(), anyString(), any())).thenReturn(Optional.of(quota(3)));

        assertThrows(IllegalStateException.class,
                () -> service.consume("user-1", UsageQuotaService.Feature.HOME_RECOMMEND));
    }

    @Test
    void memberGetsTwentyDailyRecommendations() {
        User member = new User();
        member.setIsMember(1);
        member.setMemberExpire(LocalDateTime.now().plusDays(1));
        when(users.findByOpenid("member-1")).thenReturn(Optional.of(member));
        UsageQuota quota = quota(19);
        when(quotas.findForUpdate(anyString(), anyString(), any())).thenReturn(Optional.of(quota));

        UsageQuotaService.View view = service.consume("member-1", UsageQuotaService.Feature.HOME_RECOMMEND);

        assertEquals(20, view.limit());
        assertEquals(0, view.remaining());
    }

    @Test
    void regularUserCanGenerateOnlyOneSimplePlanPerWeek() {
        when(quotas.findForUpdate(anyString(), anyString(), any())).thenReturn(Optional.of(quota(1)));

        assertThrows(IllegalStateException.class,
                () -> service.consume("user-1", UsageQuotaService.Feature.SIMPLE_WEEKLY_PLAN));
    }

    @Test
    void viewDoesNotTakeWriteLock() {
        when(quotas.findByOpenidAndFeatureAndPeriodStart(anyString(), anyString(), any())).thenReturn(Optional.of(quota(1)));

        UsageQuotaService.View view = service.view("user-1", UsageQuotaService.Feature.SIMPLE_WEEKLY_PLAN);

        assertEquals(0, view.remaining());
    }

    @Test
    void agentConversationRequiresTodaysCheckin() {
        UsageQuotaService agentService = new UsageQuotaService(quotas, users, checkins);
        when(checkins.findByOpenidAndCheckinDate(anyString(), anyString())).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> agentService.consume("user-1", UsageQuotaService.Feature.AGENT_CONVERSATION, "conversation-1"));
    }

    @Test
    void checkinConversationGiftGrantsThreeIdempotentConversations() {
        // 签到随机赠礼二选一：只有抽中「对话券」当天才授予 3 次对话。
        // 选一个今天确实抽中对话券的 openid，避免随日期哈希飘移导致偶发失败。
        String openid = conversationGiftOpenid();
        UsageQuotaService agentService = new UsageQuotaService(quotas, users, checkins);
        when(checkins.findByOpenidAndCheckinDate(anyString(), anyString())).thenReturn(Optional.of(new com.moodrecipe.backend.entity.Checkin()));
        UsageQuota quota = quota(0);
        when(quotas.findForUpdate(anyString(), anyString(), any())).thenReturn(Optional.of(quota));

        UsageQuotaService.View c1 = agentService.consume(openid, UsageQuotaService.Feature.AGENT_CONVERSATION, "conversation-1");
        assertEquals(3, c1.limit());
        assertEquals(2, c1.remaining());
        assertEquals(1, quota.getUsedCount());

        // 同一会话多轮发言幂等，不重复消耗
        UsageQuotaService.View c1again = agentService.consume(openid, UsageQuotaService.Feature.AGENT_CONVERSATION, "conversation-1");
        assertEquals(2, c1again.remaining());
        assertEquals(1, quota.getUsedCount());

        // 第 2、3 个不同会话各消耗一次
        agentService.consume(openid, UsageQuotaService.Feature.AGENT_CONVERSATION, "conversation-2");
        UsageQuotaService.View c3 = agentService.consume(openid, UsageQuotaService.Feature.AGENT_CONVERSATION, "conversation-3");
        assertEquals(0, c3.remaining());
        assertEquals(3, quota.getUsedCount());

        // 超出 3 次后拒绝
        assertThrows(IllegalStateException.class,
                () -> agentService.consume(openid, UsageQuotaService.Feature.AGENT_CONVERSATION, "conversation-4"));
    }

    /** 找一个今天签到抽中「对话券」的 openid，让测试在任意日期都确定性通过。 */
    private String conversationGiftOpenid() {
        LocalDate today = AppClock.today();
        for (int i = 1; i <= 64; i++) {
            String candidate = "user-" + i;
            if (new UsageQuotaService(quotas, users, checkins).giftOf(candidate, today)
                    == UsageQuotaService.DailyGift.CONVERSATION) {
                return candidate;
            }
        }
        throw new IllegalStateException("未找到今天抽中对话券的 openid");
    }

    @Test
    void fourthConversationIsBlockedAfterDailyGiftExhausted() {
        UsageQuotaService agentService = new UsageQuotaService(quotas, users, checkins);
        when(checkins.findByOpenidAndCheckinDate(anyString(), anyString())).thenReturn(Optional.of(new com.moodrecipe.backend.entity.Checkin()));
        UsageQuota quota = quota(3); // 3 次已用尽
        quota.setLastConversationId("conversation-1");
        when(quotas.findForUpdate(anyString(), anyString(), any())).thenReturn(Optional.of(quota));

        assertThrows(IllegalStateException.class,
                () -> agentService.consume("user-1", UsageQuotaService.Feature.AGENT_CONVERSATION, "conversation-2"));
    }

    @Test
    void sameRequestIdDoesNotConsumeTwice() {
        UsageQuota quota = quota(0);
        quota.setLastRequestId(null);
        when(quotas.findForUpdate(anyString(), anyString(), any())).thenReturn(Optional.of(quota));

        service.consume("user-1", UsageQuotaService.Feature.HOME_RECOMMEND, null, "request-1");
        service.consume("user-1", UsageQuotaService.Feature.HOME_RECOMMEND, null, "request-1");

        assertEquals(1, quota.getUsedCount());
    }

    private UsageQuota quota(int used) {
        UsageQuota quota = new UsageQuota();
        quota.setUsedCount(used);
        return quota;
    }

    // ===== 换一整天（DAILY_MEAL_PLAN_RESHUFFLE）额度 =====

    @Test
    void regularUserGetsOneReshufflePerDay() {
        UsageQuotaService svc = new UsageQuotaService(quotas, users);
        UsageQuota q = quota(0);
        when(quotas.findForUpdate(anyString(), anyString(), any())).thenReturn(Optional.of(q));

        UsageQuotaService.View v = svc.view("user-1", UsageQuotaService.Feature.DAILY_MEAL_PLAN_RESHUFFLE);
        assertEquals(1, v.limit());
        assertEquals(1, v.remaining());

        svc.consume("user-1", UsageQuotaService.Feature.DAILY_MEAL_PLAN_RESHUFFLE);
        assertEquals(1, q.getUsedCount());
        assertThrows(IllegalStateException.class,
                () -> svc.consume("user-1", UsageQuotaService.Feature.DAILY_MEAL_PLAN_RESHUFFLE));
    }

    @Test
    void memberGetsThreeReshufflesPerDay() {
        User member = new User();
        member.setIsMember(1);
        member.setMemberExpire(LocalDateTime.now().plusDays(1));
        when(users.findByOpenid("member-1")).thenReturn(Optional.of(member));
        UsageQuotaService svc = new UsageQuotaService(quotas, users);
        UsageQuota q = quota(0);
        when(quotas.findForUpdate(anyString(), anyString(), any())).thenReturn(Optional.of(q));

        UsageQuotaService.View v = svc.view("member-1", UsageQuotaService.Feature.DAILY_MEAL_PLAN_RESHUFFLE);
        assertEquals(3, v.limit());
        assertEquals(3, v.remaining());

        for (int i = 0; i < 3; i++) svc.consume("member-1", UsageQuotaService.Feature.DAILY_MEAL_PLAN_RESHUFFLE);
        assertEquals(3, q.getUsedCount());
        assertThrows(IllegalStateException.class,
                () -> svc.consume("member-1", UsageQuotaService.Feature.DAILY_MEAL_PLAN_RESHUFFLE));
    }

    @Test
    void watchingAdGrantsOneExtraReshuffleEach() throws Exception {
        AdRewardService adReward = mock(AdRewardService.class);
        when(adReward.countWatched(eq("user-1"), eq(AdRewardService.RESHUFFLE_UNLIMITED), any(LocalDate.class)))
                .thenReturn(2); // 看了 2 次广告 → +2 次
        UsageQuotaService svc = new UsageQuotaService(quotas, users);
        setAdReward(svc, adReward);
        UsageQuota q = quota(0);
        when(quotas.findForUpdate(anyString(), anyString(), any())).thenReturn(Optional.of(q));

        UsageQuotaService.View v = svc.view("user-1", UsageQuotaService.Feature.DAILY_MEAL_PLAN_RESHUFFLE);
        assertEquals(3, v.limit()); // 基础 1 + 广告 2

        for (int i = 0; i < 3; i++) svc.consume("user-1", UsageQuotaService.Feature.DAILY_MEAL_PLAN_RESHUFFLE);
        assertEquals(3, q.getUsedCount());
        assertThrows(IllegalStateException.class,
                () -> svc.consume("user-1", UsageQuotaService.Feature.DAILY_MEAL_PLAN_RESHUFFLE));
    }

    @Test
    void memberPlusAdsSums() throws Exception {
        User member = new User();
        member.setIsMember(1);
        member.setMemberExpire(LocalDateTime.now().plusDays(1));
        when(users.findByOpenid("member-1")).thenReturn(Optional.of(member));
        AdRewardService adReward = mock(AdRewardService.class);
        when(adReward.countWatched(eq("member-1"), eq(AdRewardService.RESHUFFLE_UNLIMITED), any(LocalDate.class)))
                .thenReturn(1); // 会员基础 3 + 广告 1
        UsageQuotaService svc = new UsageQuotaService(quotas, users);
        setAdReward(svc, adReward);

        UsageQuotaService.View v = svc.view("member-1", UsageQuotaService.Feature.DAILY_MEAL_PLAN_RESHUFFLE);
        assertEquals(4, v.limit());
    }

    private void setAdReward(UsageQuotaService svc, AdRewardService adReward) throws Exception {
        Field f = UsageQuotaService.class.getDeclaredField("adReward");
        f.setAccessible(true);
        f.set(svc, adReward);
    }
}
