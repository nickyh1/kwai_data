package com.example.kwai_data.data;


import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("withdraw_info")
public class WithdrawInfoDoc {

    @Id
    private String id;

    /** 提现时间 */
    private Instant withdrawTime;

    /** 提现状态 */
    private String status;

    /** 提现金额 */
    private BigDecimal amount;

    /** 到账时间 */
    private Instant receivedTime;
}

