package com.moodrecipe.backend.entity;

import com.moodrecipe.backend.config.AppClock;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 分享记录：用户从「今日推荐 / 月度画册 / 锅仔管饭」等高光时刻转发给好友后落一条。
 * 用于：1) 发放「分享家」徽章（isSharer=存在任意记录）；2) 非会员当日分享可 +1 次智能体对话。
 */
@Data
@Entity
@Table(name = "share_records")
public class ShareRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String openid;

    /** 分享场景：recipe / album / meal-agent */
    @Column(nullable = false)
    private String scene;

    @Column(name = "share_date", nullable = false)
    private LocalDate shareDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = AppClock.now();
        this.shareDate = AppClock.today();
    }
}
