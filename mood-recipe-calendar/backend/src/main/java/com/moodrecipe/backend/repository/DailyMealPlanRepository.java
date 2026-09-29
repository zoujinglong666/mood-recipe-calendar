package com.moodrecipe.backend.repository;
import com.moodrecipe.backend.entity.DailyMealPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate; import java.util.Optional;
public interface DailyMealPlanRepository extends JpaRepository<DailyMealPlan,Long> { Optional<DailyMealPlan> findByOpenidAndPlanDate(String openid, LocalDate planDate); }
