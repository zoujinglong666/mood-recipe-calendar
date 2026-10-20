package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.AdReward;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface AdRewardRepository extends JpaRepository<AdReward, Long> {
    /** 某用户当日某种奖励的观看次数（每看完一次广告记一条，故可累加）。 */
    long countByOpenidAndRewardTypeAndRewardDate(String openid, String rewardType, LocalDate rewardDate);
}
