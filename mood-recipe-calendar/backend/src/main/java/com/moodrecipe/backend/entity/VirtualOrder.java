package com.moodrecipe.backend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 虚拟商品订单；支付成功和权益发货必须分别记录，便于重试与审计。 */
@Data
@Entity
@Table(name = "virtual_orders")
public class VirtualOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, unique = true, length = 48)
    private String orderNo;
    @Column(nullable = false, length = 64)
    private String openid;
    @Column(nullable = false, length = 64)
    private String sku;
    @Column(name = "amount_fen", nullable = false)
    private Integer amountFen;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "platform_transaction_id", length = 128)
    private String platformTransactionId;
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    @Column(name = "paid_at")
    private LocalDateTime paidAt;
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;
}
