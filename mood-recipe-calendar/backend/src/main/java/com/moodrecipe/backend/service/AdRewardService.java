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
    /** 看完广告兑换「冰箱拍照识别」一次试用（非会员每日看一次用一次，会员不需要）。 */
    public static final String FRIDGE_OCR = "FRIDGE_OCR";

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

    /**
     * 消耗当日一条未使用的奖励（看一次 = 用一次，防「看一次广告无限用」的薅法）。
     * 返回是否消耗成功；无可用记录返回 false。调用方应在业务成功后调用，
     * 业务失败不调即可保留凭证。
     */
    @Transactional
    public boolean consumeOne(String openid, String rewardType, LocalDate date) {
        return rewards.findFirstByOpenidAndRewardTypeAndRewardDateOrderByIdAsc(openid, rewardType, date)
                .map(reward -> {
                    rewards.delete(reward);
                    return true;
                })
                .orElse(false);
    }
}
