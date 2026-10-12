package com.moodrecipe.backend.entity;

import com.moodrecipe.backend.config.AppClock;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 家庭共享组：owner 的会员权益穿透给全部成员。 */
@Data
@Entity
@Table(name = "family_groups")
public class FamilyGroup {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_openid", nullable = false)
    private String ownerOpenid;

    @Column(nullable = false, length = 50)
    private String name = "锅仔家庭";

    @Column(name = "invite_code", nullable = false, length = 16, unique = true)
    private String inviteCode;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() { createdAt = AppClock.now(); }
}
