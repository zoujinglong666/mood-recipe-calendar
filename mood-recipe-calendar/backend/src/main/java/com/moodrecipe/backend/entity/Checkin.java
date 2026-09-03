package com.moodrecipe.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户每日签到记录（「签到福利」）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "checkins", uniqueConstraints = @UniqueConstraint(columnNames = {"openid", "checkin_date"}))
public class Checkin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 微信 openid */
    private String openid;

    /** 签到日期 YYYY-MM-DD */
    @Column(name = "checkin_date")
    private String checkinDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
