package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.AdReward;
import com.moodrecipe.backend.repository.AdRewardRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdRewardServiceTest {
    private final AdRewardRepository repo = mock(AdRewardRepository.class);
    private final AdRewardService service = new AdRewardService(repo);

    @Test
    void recordSavesOneRowPerWatch() {
        service.record("user-1", AdRewardService.RESHUFFLE_UNLIMITED, "adunit-x");
        verify(repo).save(any(AdReward.class));
    }

    @Test
    void eachWatchIsCountedIndependently() {
        service.record("u", AdRewardService.RESHUFFLE_UNLIMITED, "a1");
        service.record("u", AdRewardService.RESHUFFLE_UNLIMITED, "a2");
        verify(repo, times(2)).save(any(AdReward.class));
    }

    @Test
    void countWatchedDelegatesToRepository() {
        when(repo.countByOpenidAndRewardTypeAndRewardDate(eq("user-1"), eq(AdRewardService.RESHUFFLE_UNLIMITED), any(LocalDate.class)))
                .thenReturn(3L);
        assertEquals(3, service.countWatched("user-1", AdRewardService.RESHUFFLE_UNLIMITED, LocalDate.now()));
    }
}
