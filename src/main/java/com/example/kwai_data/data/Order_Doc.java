package com.example.kwai_data.data;

// src/main/java/com/example/kwai_data/data/order/OrderDocument.java


import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "orders")
public class Order_Doc {

    @Id
    private String id;

    /** 订单号：建议唯一索引，确保幂等写入 */
    @Indexed(unique = true)
    private String orderNo;

    private Instant payTime;
    private String orderStatus;//

    private BigDecimal freight; //运费
    private BigDecimal promotionDiscount;
    private BigDecimal subOrderTotalPrice;

    private Instant shipTime;//发货时间
    private Instant refundInitiateTime;//发起退款时间

    private Instant createTime;
    private Instant updateTime;

    private String distributionType;
    private Instant receiveTime;

    /** 支付方式 */
    private String payType;

    /** 可按需加索引（如果你常按商品维度查询/聚合） */
    // @Indexed
    private Long ksSkuId;

    // @Indexed
    private Long productId;

    private String productName;
    private Integer skuQuantity;

    private BigDecimal unitPriceBeforePromoSnapshot;//原始价格
    private BigDecimal discountAmount; //折扣价格
    private BigDecimal unitPriceSnapshot;  //单价快照

    private String hasRefund;

    private String refundStatus;

    private String handlingWay;
}

