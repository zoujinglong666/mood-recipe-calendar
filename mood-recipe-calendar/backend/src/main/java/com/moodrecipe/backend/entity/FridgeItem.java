package com.moodrecipe.backend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 用户主动维护的冰箱食材库存。数量和日期是事实数据，不交给模型推断。 */
@Data
@Entity
@Table(name = "fridge_items", indexes = @Index(name = "idx_fridge_openid_expiry", columnList = "openid, expires_on"))
public class FridgeItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String openid;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;

    @Column(nullable = false, length = 20)
    private String unit;

    @Column(name = "purchased_on")
    private LocalDate purchasedOn;

    @Column(name = "expires_on")
    private LocalDate expiresOn;

    @Column(length = 240)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
