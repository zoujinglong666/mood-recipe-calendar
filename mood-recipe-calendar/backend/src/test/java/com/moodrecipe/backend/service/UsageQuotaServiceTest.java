package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.UsageQuota;
import com.moodrecipe.backend.entity.User;
import com.moodrecipe.backend.repository.UsageQuotaRepository;
import com.moodrecipe.backend.repository.UserRepository;
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

    private UsageQuota quota(int used) {
        UsageQuota quota = new UsageQuota();
        quota.setUsedCount(used);
        return quota;
    }
}
