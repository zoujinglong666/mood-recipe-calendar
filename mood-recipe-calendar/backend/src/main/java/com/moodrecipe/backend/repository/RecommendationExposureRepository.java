package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.RecommendationExposure;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecommendationExposureRepository extends JpaRepository<RecommendationExposure, String> {
    List<RecommendationExposure> findTop20ByOpenidOrderByCreatedAtDesc(String openid);
    boolean existsByOpenidAndDishKeyAndDislikedTrue(String openid, String dishKey);
    void deleteByOpenid(String openid);
}
