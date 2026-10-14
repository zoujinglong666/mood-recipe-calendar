package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.UserAchievement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UserAchievementRepository extends JpaRepository<UserAchievement, Long> {
    List<UserAchievement> findByOpenid(String openid);
    boolean existsByOpenidAndAchievementCode(String openid, String code);
}
