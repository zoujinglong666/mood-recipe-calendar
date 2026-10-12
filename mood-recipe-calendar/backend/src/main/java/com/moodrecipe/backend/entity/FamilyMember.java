package com.moodrecipe.backend.entity;

import com.moodrecipe.backend.config.AppClock;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 家庭成员：一个用户同一时间只属于一个家庭。 */
@Data
@Entity
@Table(name = "family_members")
public class FamilyMember {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(nullable = false, unique = true)
    private String openid;

    /** OWNER / MEMBER。 */
    @Column(nullable = false, length = 16)
    private String role = "MEMBER";

    @Column(name = "joined_at")
    private LocalDateTime joinedAt;

    @PrePersist
    void prePersist() { joinedAt = AppClock.now(); }
}
