package com.example.kwai_data.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "orders", uniqueConstraints = {
    @UniqueConstraint(name = "uk_shop_order_no", columnNames = {"shop_key", "order_no"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order_Doc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_key", nullable = false, length = 64)
    private String shopKey;

    @Column(name = "order_no", nullable = false, length = 64)
    private String orderNo;

    @Column(name = "order_status", length = 16)
    private String orderStatus;

    @Column(name = "pay_time")
    private Instant payTime;

    @Column(name = "freight", precision = 12, scale = 2)
    private BigDecimal freight;

    @Column(name = "promotion_discount", precision = 12, scale = 2)
    private BigDecimal promotionDiscount;

    @Column(name = "sub_order_total_price", precision = 12, scale = 2)
    private BigDecimal subOrderTotalPrice;

    @Column(name = "ship_time")
    private Instant shipTime;

    @Column(name = "refund_initiate_time")
    private Instant refundInitiateTime;

    @Column(name = "create_time")
    private Instant createTime;

    @Column(name = "update_time")
    private Instant updateTime;

    @Column(name = "distribution_type", length = 32)
    private String distributionType;

    @Column(name = "receive_time")
    private Instant receiveTime;

    @Column(name = "pay_type", length = 32)
    private String payType;

    @Column(name = "ks_sku_id")
    private Long ksSkuId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "product_name", length = 512)
    private String productName;

    @Column(name = "sku_quantity")
    private Integer skuQuantity;

    @Column(name = "unit_price_before_promo_snapshot", precision = 12, scale = 2)
    private BigDecimal unitPriceBeforePromoSnapshot;

    @Column(name = "discount_amount", precision = 12, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "unit_price_snapshot", precision = 12, scale = 2)
    private BigDecimal unitPriceSnapshot;

    @Column(name = "has_refund", length = 4)
    private String hasRefund;

    @Column(name = "refund_status", length = 16)
    private String refundStatus;

    @Column(name = "handling_way", length = 16)
    private String handlingWay;

    @Column(name = "last_ingested_at")
    private Instant lastIngestedAt;
}
