package com.example.kwai_data.data;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnsettledOrderDto {

    /** 订单ID（平台侧 oid） */
    private String oid;

    /** 订单状态（平台码值/枚举名） */
    private String orderStatus;

    /** 结算状态（平台码值/枚举名） */
    private String settlementStatus;

    /** 结算时间（如平台返回；可能为空） */
    private Instant settlementTime;

    /** 待结算订单状态（平台码值/枚举名） */
    //private String unsettleorderStatus;

    /** 当前运费（字段名按你给的 freightWhenNow） */
    private BigDecimal freightWhenNow;

    /** 账单时间 */
    private Instant billTime;

    /** 金额（订单/待结算金额，具体语义你后续可再细分） */
    private BigDecimal amount;

    /** 订单创建时间（通过 open.order.detail API 获取） */
    private Instant createTime;
}

