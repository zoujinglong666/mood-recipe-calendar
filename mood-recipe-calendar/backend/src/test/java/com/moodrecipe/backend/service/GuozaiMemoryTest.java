package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.UserFoodPreference;
import com.moodrecipe.backend.agent.AgentMemoryStore;
import com.moodrecipe.backend.agent.UserProfile;
import com.moodrecipe.backend.entity.UserRecord;
import com.moodrecipe.backend.repository.OperationalEventRepository;
import com.moodrecipe.backend.repository.UserFoodPreferenceRepository;
import com.moodrecipe.backend.repository.UserRecordRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuozaiMemoryTest {
    @Test
    void ignoresInvalidHistoricalDates() {
        UserRecordRepository records = mock(UserRecordRepository.class);
        UserFoodPreferenceRepository preferences = mock(UserFoodPreferenceRepository.class);
        OperationalEventRepository events = mock(OperationalEventRepository.class);
        UserRecord dirty = record("not-a-date");
        UserRecord today = record(LocalDate.now().toString());
        when(records.findByOpenidOrderByCreatedAtDesc("user-1")).thenReturn(List.of(dirty, today));
        when(preferences.findByOpenid("user-1")).thenReturn(Optional.empty());

        GuozaiMemory.MemorySnapshot snapshot = new GuozaiMemory(records, preferences, events)
                .snapshot("user-1", 12);

        assertTrue(snapshot.moodTrend().last7Days().containsKey("平静"));
        verify(events, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void completedPreferenceMeansUserIsKnownWithoutRecords() {
        UserRecordRepository records = mock(UserRecordRepository.class);
        UserFoodPreferenceRepository preferences = mock(UserFoodPreferenceRepository.class);
        OperationalEventRepository events = mock(OperationalEventRepository.class);
        UserFoodPreference preference = new UserFoodPreference();
        preference.setOnboardingCompleted(true);
        when(records.findByOpenidOrderByCreatedAtDesc("user-1")).thenReturn(List.of());
        when(preferences.findByOpenid("user-1")).thenReturn(Optional.of(preference));

        GuozaiMemory.MemorySnapshot snapshot = new GuozaiMemory(records, preferences, events)
                .snapshot("user-1", 12);

        assertFalse(snapshot.isNewUser());
    }

    @Test
    void readsLongTermCuisineFromUnifiedFoodProfile() {
        UserRecordRepository records = mock(UserRecordRepository.class);
        UserFoodPreferenceRepository preferences = mock(UserFoodPreferenceRepository.class);
        OperationalEventRepository events = mock(OperationalEventRepository.class);
        AgentMemoryStore profileStore = mock(AgentMemoryStore.class);
        when(records.findByOpenidOrderByCreatedAtDesc("user-1")).thenReturn(List.of());
        when(preferences.findByOpenid("user-1")).thenReturn(Optional.empty());
        when(profileStore.profile("user-1", AgentMemoryStore.Scene.SINGLE_RECIPE)).thenReturn(new UserProfile(
                "user-1", null, null, List.of(), null, null, null, null, null,
                List.of("赣菜"), List.of(), List.of(), List.of(), List.of("藜蒿炒腊肉"),
                List.of(), List.of(), List.of(), Map.of(), 35, true, 0, List.of()));
        when(profileStore.personalizationEnabled("user-1")).thenReturn(true);

        GuozaiMemory.MemorySnapshot snapshot = new GuozaiMemory(records, preferences, events, profileStore)
                .snapshot("user-1", 12);

        assertTrue(snapshot.favoriteCuisine().contains("赣菜"));
        assertTrue(snapshot.favorite().contains("藜蒿炒腊肉"));
        assertTrue(snapshot.preferSimple());
        assertTrue(snapshot.lovedDishes().contains("藜蒿炒腊肉"));
        assertTrue(snapshot.personalizationEnabled());
        org.junit.jupiter.api.Assertions.assertEquals(35, snapshot.maxCookingMinutes());
    }

    private UserRecord record(String date) {
        UserRecord record = new UserRecord();
        record.setRecordDate(date);
        record.setMoodTag("平静");
        record.setDishName("番茄炒蛋");
        return record;
    }
}
