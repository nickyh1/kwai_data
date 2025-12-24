package com.example.kwai_data.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class AccountBalance {

    private BigDecimal balance;
    private BigDecimal frozenBalance;
    private String state;
    private Integer accountChannel;
    private String subMerchantId;
}

