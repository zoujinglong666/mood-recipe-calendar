package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.CookingLearningEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CookingLearningEventRepository extends JpaRepository<CookingLearningEvent, Long> {
    List<CookingLearningEvent> findTop50ByOpenidOrderByCreatedAtDesc(String openid);
    void deleteByOpenid(String openid);
}
