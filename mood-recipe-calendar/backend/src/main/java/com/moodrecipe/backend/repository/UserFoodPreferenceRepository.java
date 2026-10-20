package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.UserFoodPreference;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserFoodPreferenceRepository extends JpaRepository<UserFoodPreference, Long> {
    // [2026-10-20] 用户画像写极少、读极多，60s 缓存即可大幅削减推荐路径的重复查询。
    @Cacheable(cacheNames = "userPreference", key = "#openid")
    Optional<UserFoodPreference> findByOpenid(String openid);
    void deleteByOpenid(String openid);
}
