package com.example.kwai_data.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "seller_info")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerInfo_Doc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", unique = true)
    private Long shopId;

    @Column(name = "shop_name", length = 256)
    private String shopName;

    @Column(name = "account_balance")
    private Long accountBalance;

    @Column(name = "pending_settlement", precision = 16, scale = 2)
    private BigDecimal pendingSettlement;

    @Column(name = "total_withdrawn", precision = 16, scale = 2)
    private BigDecimal totalWithdrawn;

    @Column(name = "net_transaction_amount", precision = 16, scale = 2)
    private BigDecimal netTransactionAmount;

    @Column(name = "update_time")
    private Instant updateTime;
}
