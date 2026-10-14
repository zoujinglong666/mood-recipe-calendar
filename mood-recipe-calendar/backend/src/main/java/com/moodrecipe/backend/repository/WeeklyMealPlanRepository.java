package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.WeeklyMealPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface WeeklyMealPlanRepository extends JpaRepository<WeeklyMealPlan, Long> {
    Optional<WeeklyMealPlan> findTopByOpenidOrderByCreatedAtDesc(String openid);
    List<WeeklyMealPlan> findTop20ByOpenidOrderByCreatedAtDesc(String openid);

    /** 周六清单提醒：最近 N 天生成过周菜单的用户（N=7，天然去重）。 */
    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT p.openid FROM WeeklyMealPlan p WHERE p.createdAt >= :since")
    java.util.List<String> findOpenidsByCreatedAtAfter(@org.springframework.data.repository.query.Param("since") java.time.LocalDateTime since);
}
