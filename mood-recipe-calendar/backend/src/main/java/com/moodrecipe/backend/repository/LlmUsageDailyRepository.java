package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.LlmUsageDaily;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LlmUsageDailyRepository extends JpaRepository<LlmUsageDaily, Long> {

    Optional<LlmUsageDaily> findByUsageDateAndTier(LocalDate usageDate, String tier);

    List<LlmUsageDaily> findByUsageDate(LocalDate usageDate);
}
