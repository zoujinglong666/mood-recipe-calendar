package com.moodrecipe.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 锅仔周边订单（「周边商城」购买/兑换）
 * 支付接入微信虚拟支付（个人/个体户能力），真实支付参数需在微信后台配置后接入
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "shop_orders")
public class ShopOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 业务订单号 */
    @Column(name = "order_no", unique = true, nullable = false)
    private String orderNo;

    /** 微信 openid */
    private String openid;

    /** 商品ID */
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "product_image")
    private String productImage;

    /** 实付金额（元）：normal=原价，exchange=兑换价(1元) */
    private BigDecimal amount;

    /** 支付类型：normal 原价购买 / exchange 1元兑换 */
    @Column(name = "pay_type")
    private String payType;

    /** 订单状态：pending 待支付 / paid 已支付 / cancelled 已取消 */
    private String status = "pending";

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
