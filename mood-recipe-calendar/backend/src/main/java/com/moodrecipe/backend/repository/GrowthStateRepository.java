package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.GrowthState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GrowthStateRepository extends JpaRepository<GrowthState, Long> {
    Optional<GrowthState> findByOpenid(String openid);
}
