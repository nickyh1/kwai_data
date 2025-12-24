package com.example.kwai_data.dto.order;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document("ks_orders")
public class KsOrderDoc {

    @Id
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

    // 原始时间戳（毫秒）
    private Long orderCreateTimeMs;
    private Long settlementTimeMs;

    // 可选：同时存 Instant 便于后续查询/聚合
    private Instant orderCreateTime;
    private Instant settlementTime;

    // 金额：由于单位/口径未完全确定，提供 BigDecimal（可按你的业务再统一为“分”Long）
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

    // 追溯字段（强烈建议保留）
    private String requestId;       // 来自顶层 response，便于排查链路
    private Instant ingestedAt;     // 入库时间
}

