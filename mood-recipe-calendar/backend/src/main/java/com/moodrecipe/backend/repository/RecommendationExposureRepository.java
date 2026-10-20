package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.RecommendationExposure;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecommendationExposureRepository extends JpaRepository<RecommendationExposure, String> {
    List<RecommendationExposure> findTop20ByOpenidOrderByCreatedAtDesc(String openid);
    boolean existsByOpenidAndDishKeyAndDislikedTrue(String openid, String dishKey);
    // [2026-10-20] 供 RecommendationExposureService.load 一次性批量取回"已拒绝"曝光，替代逐候选查库（N+1）。
    List<RecommendationExposure> findByOpenidAndDislikedTrue(String openid);
    java.util.Optional<RecommendationExposure> findByIdAndOpenid(String id, String openid);
    void deleteByOpenid(String openid);
}
