package com.moodrecipe.backend.service;

import com.moodrecipe.backend.config.AppClock;
import com.moodrecipe.backend.entity.UsageQuota;
import com.moodrecipe.backend.entity.User;
import com.moodrecipe.backend.repository.UsageQuotaRepository;
import com.moodrecipe.backend.repository.UserRepository;
import com.moodrecipe.backend.repository.CheckinRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

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

    // ---------- 看广告换额度（adReward） ----------

    /** 已耗尽的用户看 1 次广告：目标功能已用量 -1（净增 1 次可用），广告计数 +1。 */
    @Test
    void adRewardGrantsOneExtraUseWhenExhausted() {
        UsageQuota adRow = quota(0);
        UsageQuota target = quota(3); // HOME_RECOMMEND 免费 3 次已用尽
        when(quotas.findForUpdate(anyString(), anyString(), any()))
                .thenReturn(Optional.of(adRow), Optional.of(target));
        when(quotas.findByOpenidAndFeatureAndPeriodStart(anyString(), anyString(), any()))
                .thenReturn(Optional.of(target));

        UsageQuotaService.View view = service.adReward("user-1", UsageQuotaService.Feature.HOME_RECOMMEND, "ad-req-1");

        assertEquals(3, view.limit());
        assertEquals(1, view.remaining());
        assertEquals(2, target.getUsedCount());
        assertEquals(1, adRow.getUsedCount());
        assertEquals("ad-req-1", adRow.getLastRequestId());
    }

    @Test
    void adRewardRejectedWhenQuotaRemains() {
        UsageQuota adRow = quota(0);
        UsageQuota target = quota(1); // 还有 2 次，不该让用户「存」额度
        when(quotas.findForUpdate(anyString(), anyString(), any()))
                .thenReturn(Optional.of(adRow), Optional.of(target));

        assertThrows(IllegalStateException.class,
                () -> service.adReward("user-1", UsageQuotaService.Feature.HOME_RECOMMEND, "ad-req-x"));
    }

    @Test
    void adRewardRejectedAfterDailyAdCap() {
        UsageQuota adRow = quota(UsageQuotaService.DAILY_AD_REWARD_LIMIT);
        UsageQuota target = quota(3);
        when(quotas.findForUpdate(anyString(), anyString(), any()))
                .thenReturn(Optional.of(adRow), Optional.of(target));

        assertThrows(IllegalStateException.class,
                () -> service.adReward("user-1", UsageQuotaService.Feature.HOME_RECOMMEND, "ad-req-y"));
    }

    /** 网络重放（同一 requestId）：不重复计数、不重复发放。 */
    @Test
    void adRewardReplayDoesNotGrantTwice() {
        UsageQuota adRow = quota(1);
        adRow.setLastRequestId("ad-req-1");
        UsageQuota target = quota(2); // 上次已从 3 发到 2
        when(quotas.findForUpdate(anyString(), anyString(), any()))
                .thenReturn(Optional.of(adRow), Optional.of(target));
        when(quotas.findByOpenidAndFeatureAndPeriodStart(anyString(), anyString(), any()))
                .thenReturn(Optional.of(target));

        UsageQuotaService.View view = service.adReward("user-1", UsageQuotaService.Feature.HOME_RECOMMEND, "ad-req-1");

        assertEquals(2, target.getUsedCount());
        assertEquals(1, adRow.getUsedCount());
        assertEquals(1, view.remaining());
    }

    @Test
    void adRewardRejectedForMember() {
        User member = new User();
        member.setIsMember(1);
        member.setMemberExpire(LocalDateTime.now().plusDays(1));
        when(users.findByOpenid("member-1")).thenReturn(Optional.of(member));

        assertThrows(IllegalStateException.class,
                () -> service.adReward("member-1", UsageQuotaService.Feature.HOME_RECOMMEND, "ad-req-z"));
    }

    /** 未抽中对话券（limit=0）同样算耗尽：看广告后正好多得 1 次，usedCount 到 -1。 */
    @Test
    void adRewardGrantsConversationWhenNoTicketDrawn() {
        UsageQuotaService agentService = new UsageQuotaService(quotas, users, checkins);
        when(checkins.findByOpenidAndCheckinDate(anyString(), anyString())).thenReturn(Optional.empty());
        UsageQuota adRow = quota(0);
        UsageQuota target = quota(0);
        when(quotas.findForUpdate(anyString(), anyString(), any()))
                .thenReturn(Optional.of(adRow), Optional.of(target));
        when(quotas.findByOpenidAndFeatureAndPeriodStart(anyString(), anyString(), any()))
                .thenReturn(Optional.of(target));

        UsageQuotaService.View view = agentService.adReward("user-1", UsageQuotaService.Feature.AGENT_CONVERSATION, "ad-req-c");

        assertEquals(0, view.limit());
        assertEquals(1, view.remaining());
        assertEquals(-1, target.getUsedCount());
    }

    /** 目标行不存在（从未消耗过）且 limit=0：发放时新建行，usedCount = limit - 1。 */
    @Test
    void adRewardCreatesRowWhenNoneExists() {
        UsageQuotaService agentService = new UsageQuotaService(quotas, users, checkins);
        when(checkins.findByOpenidAndCheckinDate(anyString(), anyString())).thenReturn(Optional.empty());
        UsageQuota adRow = quota(0);
        when(quotas.findForUpdate(anyString(), anyString(), any()))
                .thenReturn(Optional.of(adRow), Optional.empty());
        ArgumentCaptor<UsageQuota> captor = ArgumentCaptor.forClass(UsageQuota.class);
        when(quotas.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        agentService.adReward("user-1", UsageQuotaService.Feature.AGENT_CONVERSATION, "ad-req-n");

        UsageQuota created = captor.getAllValues().stream()
                .filter(q -> "AGENT_CONVERSATION".equals(q.getFeature()))
                .findFirst()
                .orElseThrow();
        assertEquals(-1, created.getUsedCount());
    }
}
