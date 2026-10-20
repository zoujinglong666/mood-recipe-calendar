package com.moodrecipe.backend.repository;
import com.moodrecipe.backend.entity.DailyMealPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate; import java.util.List; import java.util.Optional;
public interface DailyMealPlanRepository extends JpaRepository<DailyMealPlan,Long> {
    Optional<DailyMealPlan> findByOpenidAndPlanDate(String openid, LocalDate planDate);
    /** 近几天的三餐计划：跨天换菜去重用，保证连着几天吃的不重样。 */
    List<DailyMealPlan> findByOpenidAndPlanDateGreaterThanEqual(String openid, LocalDate planDate);
}
