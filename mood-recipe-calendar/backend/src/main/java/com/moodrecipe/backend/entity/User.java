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

    /**
     * code2session 返回的 session_key，经服务端密钥加密后保存。
     * 仅用于生成微信虚拟支付的用户态签名，绝不下发给小程序。
     */
    @Column(name = "session_key_encrypted", length = 512)
    private String sessionKeyEncrypted;

    @Column(name = "session_token_hash", length = 64)
    private String sessionTokenHash;

    @Column(name = "session_token_expires_at")
    private LocalDateTime sessionTokenExpiresAt;

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
