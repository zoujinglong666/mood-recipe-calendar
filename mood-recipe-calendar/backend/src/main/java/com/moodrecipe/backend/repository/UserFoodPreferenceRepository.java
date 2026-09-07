package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.UserFoodPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserFoodPreferenceRepository extends JpaRepository<UserFoodPreference, Long> {
    Optional<UserFoodPreference> findByOpenid(String openid);
    void deleteByOpenid(String openid);
}
