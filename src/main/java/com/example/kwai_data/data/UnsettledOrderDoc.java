package com.example.kwai_data.data;



import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "unsettled_orders")
public class UnsettledOrderDoc {

    @Id
    private String id;

    /** 订单ID（强烈建议唯一） */
    @Indexed(unique = true)
    private String oid;

    @Indexed
    private String orderStatus;

    @Indexed
    private String settlementStatus;

    @Indexed
    private Instant settlementTime;

//    @Indexed
//    private String unsettleorderStatus;

    private BigDecimal freightWhenNow;

    @Indexed
    private Instant billTime;

    private BigDecimal amount;

    /** 可选：用于乱序保护/幂等更新（如果你需要） */
    // @Indexed
    // private Instant updateTime;
}

