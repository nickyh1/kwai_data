package com.example.kwai_data.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "unsettled_orders", uniqueConstraints = {
    @UniqueConstraint(name = "uk_shop_oid", columnNames = {"shop_key", "oid"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnsettledOrderDoc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_key", nullable = false, length = 64)
    private String shopKey;

    @Column(name = "oid", nullable = false, length = 64)
    private String oid;

    @Column(name = "order_status", length = 16)
    private String orderStatus;

    @Column(name = "settlement_status", length = 16)
    private String settlementStatus;

    @Column(name = "settlement_time")
    private Instant settlementTime;

    @Column(name = "freight_when_now", precision = 12, scale = 2)
    private BigDecimal freightWhenNow;

    @Column(name = "bill_time")
    private Instant billTime;

    @Column(name = "amount", precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "platform_commission_amount", precision = 12, scale = 2)
    private BigDecimal platformCommissionAmount;

    @Column(name = "create_time")
    private Instant createTime;
}
