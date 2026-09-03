package com.moodrecipe.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/** 可通过微信小程序虚拟支付直接购买的数字权益 SKU。 */
@Data
@Entity
@Table(name = "virtual_products")
public class VirtualProduct {

    @Id
    @Column(length = 64)
    private String sku;

    /** 微信虚拟支付后台录入的道具 ID；未开通前可为空。 */
    @Column(name = "platform_item_id", length = 128)
    private String platformItemId;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "price_fen")
    private Integer priceFen;

    @Column(name = "entitlement_code", length = 64)
    private String entitlementCode;

    /** 次数型权益的数量；会员类传 0。 */
    @Column(name = "entitlement_amount")
    private Integer entitlementAmount;

    /** 购买后有效天数；永久权益传 0。 */
    @Column(name = "valid_days")
    private Integer validDays;

    private Boolean active = true;
    @Column(name = "sort_order")
    private Integer sortOrder = 0;
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public boolean isMemberPass() {
        return "MEMBER".equals(entitlementCode);
    }
}
