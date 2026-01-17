package com.example.kwai_data.data;



import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawInfoDto {

    private Instant withdrawTime;
    private String status;
    private BigDecimal amount;
    private Instant receivedTime;

}

