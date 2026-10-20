package com.moodrecipe.backend.entity;

import com.moodrecipe.backend.config.AppClock;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 激励视频广告奖励记账：每看完一次广告记一条（同一用户当日可有多条），
 * 用于给「换一整天」等能力按当日观看次数累加额度（看一次规划一次）。
 */
@Data
@Entity
@Table(name = "ad_rewards")
public class AdReward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String openid;

    /** 奖励类型：RESHUFFLE_UNLIMITED=看完广告兑换一次换一整天 */
    @Column(name = "reward_type", nullable = false)
    private String rewardType;

    @Column(name = "reward_date", nullable = false)
    private LocalDate rewardDate;

    /** 广告位 id（可选，用于归因） */
    @Column(name = "ad_unit_id")
    private String adUnitId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = AppClock.now();
        this.rewardDate = AppClock.today();
    }
}
