package com.moodrecipe.backend.entity;

import com.moodrecipe.backend.config.AppClock;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 主题菜谱专辑：内容编排层。
 * sku 与 virtual_products.sku 一一对应，购买/解锁走既有虚拟支付链路（user_entitlements 永久权益）。
 */
@Data
@Entity
@Table(name = "theme_albums")
public class ThemeAlbum {
    @Id
    @Column(length = 64)
    private String sku;

    @Column(length = 100, nullable = false)
    private String title;

    @Column(length = 200)
    private String subtitle;

    @Column(length = 500)
    private String cover;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** 菜谱 ID 的 JSON 数组，如 [12, 45, 78]；顺序即专辑内展示顺序。 */
    @Column(columnDefinition = "TEXT")
    private String recipeIds;

    @Column(name = "preview_count", nullable = false)
    private int previewCount = 3;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() { createdAt = AppClock.now(); }
}
