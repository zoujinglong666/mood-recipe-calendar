package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.PlanDishOutcome;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanDishOutcomeRepository extends JpaRepository<PlanDishOutcome, Long> {

    List<PlanDishOutcome> findTop50ByOpenidOrderByUpdatedAtDesc(String openid);

    Optional<PlanDishOutcome> findByOpenidAndPlanIdAndDayIndexAndDishIndex(String openid, Long planId,
                                                                           int dayIndex, int dishIndex);
}
