package com.moodrecipe.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String openid;

    private String nickname;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "first_use_date")
    private LocalDate firstUseDate;

    @Column(name = "is_member")
    private Integer isMember = 0;

    @Column(name = "member_expire")
    private LocalDateTime memberExpire;

    @Column(name = "remind_time")
    private String remindTime = "20:00";

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.firstUseDate == null) {
            this.firstUseDate = LocalDate.now();
        }
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
