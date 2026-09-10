package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.WeeklyMealPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface WeeklyMealPlanRepository extends JpaRepository<WeeklyMealPlan, Long> {
    Optional<WeeklyMealPlan> findTopByOpenidOrderByUpdatedAtDesc(String openid);
}
