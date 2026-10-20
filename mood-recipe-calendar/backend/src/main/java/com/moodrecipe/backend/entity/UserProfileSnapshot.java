package com.moodrecipe.backend.entity;

import com.moodrecipe.backend.config.AppClock;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * [2026-10-20] 用户画像快照：每次有意义的交互（LIKE/DISLIKE/MADE）触发一次，
 * 把当时完整的 UserProfile(JSON) 落库，形成「用户如何随时间变聪明」的可回溯时间线。
 * 这是 requirement 4「数据持久化：支持长期积累与回溯」的落地——行为日志是原始流水，
 * 快照是聚合后的状态点，二者结合既能回放也能做离线进化分析。
 */
@Data
@Entity
@Table(name = "user_profile_snapshots", indexes = {
        @Index(name = "idx_profile_snapshot_openid_time", columnList = "openid,snapshot_at")
})
public class UserProfileSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String openid;

    @Column(name = "snapshot_at", nullable = false)
    private LocalDateTime snapshotAt;

    /** 触发原因：LIKE / DISLIKE / MADE / SCHEDULED。 */
    @Column(length = 16)
    private String trigger;

    /** 当时的置信度，便于画「个性化程度随时间提升」曲线。 */
    private Double confidence;

    /** 完整 UserProfile 的 JSON，便于回溯与离线重算。 */
    @Column(columnDefinition = "TEXT")
    private String profileJson;

    @PrePersist
    void prePersist() {
        this.snapshotAt = AppClock.now();
    }
}
