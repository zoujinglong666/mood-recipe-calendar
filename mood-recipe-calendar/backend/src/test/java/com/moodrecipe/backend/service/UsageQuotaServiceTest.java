package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.UsageQuota;
import com.moodrecipe.backend.entity.User;
import com.moodrecipe.backend.repository.UsageQuotaRepository;
import com.moodrecipe.backend.repository.UserRepository;
import com.moodrecipe.backend.repository.CheckinRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
    void checkinGrantsOneIdempotentMultiTurnConversation() {
        UsageQuotaService agentService = new UsageQuotaService(quotas, users, checkins);
        when(checkins.findByOpenidAndCheckinDate(anyString(), anyString())).thenReturn(Optional.of(new com.moodrecipe.backend.entity.Checkin()));
        UsageQuota quota = quota(0);
        when(quotas.findForUpdate(anyString(), anyString(), any())).thenReturn(Optional.of(quota));

        UsageQuotaService.View first = agentService.consume("user-1", UsageQuotaService.Feature.AGENT_CONVERSATION, "conversation-1");
        UsageQuotaService.View second = agentService.consume("user-1", UsageQuotaService.Feature.AGENT_CONVERSATION, "conversation-1");

        assertEquals(0, first.remaining());
        assertEquals(0, second.remaining());
        assertEquals(1, quota.getUsedCount());
    }

    @Test
    void secondConversationIsBlockedAfterDailyGiftIsUsed() {
        UsageQuotaService agentService = new UsageQuotaService(quotas, users, checkins);
        when(checkins.findByOpenidAndCheckinDate(anyString(), anyString())).thenReturn(Optional.of(new com.moodrecipe.backend.entity.Checkin()));
        UsageQuota quota = quota(1);
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
}
