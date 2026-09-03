package com.moodrecipe.backend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 用户已交付的次数型或永久型数字权益。 */
@Data
@Entity
@Table(name = "user_entitlements")
public class UserEntitlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 64)
    private String openid;
    @Column(nullable = false, length = 64)
    private String code;
    @Column(name = "remaining_uses")
    private Integer remainingUses;
    @Column(name = "source_order_no", nullable = false, unique = true, length = 48)
    private String sourceOrderNo;
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
