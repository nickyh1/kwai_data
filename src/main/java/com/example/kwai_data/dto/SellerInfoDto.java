package com.example.kwai_data.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

@Builder
@AllArgsConstructor
@Data
public class SellerInfoDto {
    private String shopName;                 // 小店名
    private Long shopId;                     // 小店ID
    private Long accountBalance;       // 账户余额
    private BigDecimal pendingSettlement;    // 待结算金额
    private BigDecimal totalWithdrawn;       // 累计提现金额
    private BigDecimal netTransactionAmount; // 净成交额

    @Builder.Default  // 关键：告诉 Builder 使用这个默认值
    private Instant updateTime = Instant.now();
}
