package com.moodrecipe.backend.service;

import com.moodrecipe.backend.config.AppClock;
import com.moodrecipe.backend.entity.AdReward;
import com.moodrecipe.backend.repository.AdRewardRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/** 激励视频广告奖励：每看完一次广告记一条，按当日观看次数累加「换一整天」等能力额度（看一次规划一次）。 */
@Service
public class AdRewardService {
    /** 看完广告兑换「换一整天」一次机会。 */
    public static final String RESHUFFLE_UNLIMITED = "RESHUFFLE_UNLIMITED";

    private final AdRewardRepository rewards;

    public AdRewardService(AdRewardRepository rewards) {
        this.rewards = rewards;
    }

    /** 记录一次看完的激励视频：每调用一次记一条，可累加（同一用户当日可有多条）。 */
    @Transactional
    public void record(String openid, String rewardType, String adUnitId) {
        AdReward reward = new AdReward();
        reward.setOpenid(openid);
        reward.setRewardType(rewardType);
        reward.setAdUnitId(adUnitId);
        rewards.save(reward);
    }

    /** 某用户当日某奖励类型的观看次数（即已兑换的次数）。 */
    public int countWatched(String openid, String rewardType, LocalDate date) {
        return (int) rewards.countByOpenidAndRewardTypeAndRewardDate(openid, rewardType, date);
    }
}
