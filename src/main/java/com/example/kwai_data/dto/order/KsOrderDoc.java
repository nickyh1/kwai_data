package com.example.kwai_data.dto.order;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 快手结算订单文档（保留结构，暂未使用）
 */
@Entity
@Table(name = "ks_orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KsOrderDoc {

    @Id
    @Column(name = "id", length = 64)
    private String id;              // = orderNo

    private Long orderNo;
    private String merchantId;
    private String productId;
    private String productName;
    private Integer productNum;

    private String accountChannel;
    private String accountName;

    private String settlementStatus;
    private String settlementRule;

    private Long orderCreateTimeMs;
    private Long settlementTimeMs;

    private Instant orderCreateTime;
    private Instant settlementTime;

    private BigDecimal totalIncome;
    private BigDecimal actualPayAmount;
    private BigDecimal settlementAmount;

    private BigDecimal platformCommissionAmount;
    private BigDecimal totalOutgoingAmount;
    private BigDecimal totalRefundAmount;

    private Integer sellerAllowanceAmount;
    private Integer platformPayMarketAllowanceAmount;
    private Integer governmentSubsidyAmount;
    private Integer presellSettleAmount;
    private Integer otherAmount;

    private String collectMode;

    private String requestId;
    private Instant ingestedAt;
}
