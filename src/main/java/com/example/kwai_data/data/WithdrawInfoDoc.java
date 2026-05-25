package com.example.kwai_data.data;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "withdraw_records", uniqueConstraints = {
    @UniqueConstraint(name = "uk_shop_withdraw_no", columnNames = {"shop_key", "platform_withdraw_no"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawInfoDoc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_key", nullable = false, length = 64)
    private String shopKey;

    @Column(name = "platform_withdraw_no", nullable = false, length = 64)
    private String platformWithdrawNo;

    @Column(name = "withdraw_time")
    private Instant withdrawTime;

    @Column(name = "received_time")
    private Instant receivedTime;

    @Column(name = "state", length = 16)
    private String state;

    @Column(name = "withdraw_money")
    private Long withdrawMoney;

    @Column(name = "fail_reason", length = 256)
    private String failReason;

    @Column(name = "withdraw_desc", length = 256)
    private String withdrawDesc;

    @Column(name = "update_time")
    private Instant updateTime;
}
