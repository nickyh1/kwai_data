package com.example.kwai_data.data;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "seller_info")
public class SellerInfo_Doc {

    @Id
    private String id;

    @Indexed(unique = true)     // 通常用 shopId 做唯一键，避免重复插入
    private Long shopId;

    private String shopName;
    private Long accountBalance;
    private BigDecimal pendingSettlement;
    private BigDecimal totalWithdrawn;
    private BigDecimal netTransactionAmount;

    private Instant updateTime;
}

