package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.GrowthEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface GrowthEventRepository extends JpaRepository<GrowthEvent, Long> {
    List<GrowthEvent> findTop10ByOpenidOrderByCreatedAtDesc(String openid);

    /** 当日流水（每日喂锅仔上限的统计口径）。 */
    List<GrowthEvent> findByOpenidAndCreatedAtAfter(String openid, LocalDateTime since);
}
