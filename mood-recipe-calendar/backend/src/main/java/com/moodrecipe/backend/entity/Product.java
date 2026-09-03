package com.moodrecipe.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 锅仔周边商品（对应「周边商城」）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 商品名称 */
    private String name;

    /** 商品图片 */
    @Column(name = "image", columnDefinition = "TEXT")
    private String image;

    /** 原价（元） */
    private BigDecimal price;

    /** 1 元兑换价（连续签到 30 天可享） */
    @Column(name = "exchange_price")
    private BigDecimal exchangePrice;

    /** 分类：cup/clothes/kitchen/... */
    private String category;

    /** 商品简介 */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** 库存 */
    private Integer stock = 0;

    /** 排序权重（小的在前） */
    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    /** 是否上架 */
    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
