package com.example.kwai_data.data;

// src/main/java/com/example/kwai_data/dto/order/OrderDto.java


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.kuaishou.merchant.open.api.domain.order.OrderList;

import com.kuaishou.merchant.open.api.domain.order.OrderRefundInfo;

import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderDto {

    /** 订单号 */
    private String orderNo;

    /** 付款时间 */
    private Instant payTime;

    /** 订单状态 */
    private String orderStatus;

    /** 运费 */
    private BigDecimal freight;

    /** 促销减价 */
    private BigDecimal promotionDiscount;

    /** 子订单商品总价 */
    private BigDecimal subOrderTotalPrice;

    /** 发货时间 */
    private Instant shipTime;

    /** 发起退款时间 */
    private Instant refundInitiateTime;

    /** 创建时间 */
    private Instant createTime;

    /** 更新时间 */
    private Instant updateTime;

    /** 分销类型 */
    private String distributionType;

    /** 收货时间 */
    private Instant receiveTime;

    /** 支付方式 payType */
    @JsonProperty("payType")
    private String payType;

    /** 快手商品 skuid */
    private Long ksSkuId;

    /** 商品 id */
    private Long productId;

    /** 商品名称 */
    private String productName;

    /** sku 数量 */
    private Integer skuQuantity;

    /** 商品促销前单价快照 */
    private BigDecimal unitPriceBeforePromoSnapshot;

    /** 折扣金额 */
    private BigDecimal discountAmount;

    /** 商品单价快照 */
    private BigDecimal unitPriceSnapshot;

    /** 退款信息列表 */


    /** 是否存在退款（便于查询/展示） */
    private String hasRefund;

    private String refundStatus;

    private String handlingWay;



    public OrderDto(OrderList src) {
        if (src == null) return;

        var base =src.getOrderBaseInfo();
        var item = src.getOrderItemInfo();

        // 订单号
        this.orderNo = (base == null || base.getOid() == null) ? null : String.valueOf(base.getOid());

        // 时间类字段（毫秒时间戳）
        this.payTime = toInstantMs(base == null ? null : base.getPayTime());
        this.shipTime = toInstantMs(base == null ? null : base.getSendTime());
        this.refundInitiateTime = toInstantMs(base == null ? null : base.getRefundTime());
        this.createTime = toInstantMs(base == null ? null : base.getCreateTime());
        this.createTime.atZone(ZoneId.of("Asia/Shanghai"));
        this.updateTime = toInstantMs(base == null ? null : base.getUpdateTime());
        this.receiveTime = toInstantMs(base == null ? null : base.getRecvTime());
        System.out.println(orderNo+" "+createTime);

        // 状态/类型
        this.orderStatus = base == null ? null : String.valueOf(base.getStatus());
        this.distributionType = base == null ? null : String.valueOf(base.getCpsType());
        this.payType = base == null ? null : String.valueOf(base.getPayType());

        // 金额（你的报文里看起来是“分”）
        this.freight = fenToYuan(base == null ? null : base.getExpressFee());
        this.promotionDiscount = fenToYuan(base == null ? null : base.getDiscountFee());

        // 商品字段
        this.ksSkuId = item == null ? null : item.getSkuId();
        this.productId = item == null ? null : item.getItemId();
        this.productName = item == null ? null : item.getItemTitle();
        this.skuQuantity = item == null ? null : item.getNum();

        this.unitPriceBeforePromoSnapshot = fenToYuan(item == null ? null : item.getOriginalPrice());
        this.discountAmount = fenToYuan(item == null ? null : item.getDiscountFee());
        this.unitPriceSnapshot = fenToYuan(item == null ? null : item.getPrice());

        // 子订单商品总价：price * num（分 → 元）
        if (item != null && item.getPrice() != null && item.getNum() != null) {
            long totalFen = item.getPrice() * (long) item.getNum();
            this.subOrderTotalPrice = fenToYuan(totalFen);
        } else {
            this.subOrderTotalPrice = null;
        }
        // 退款列表：refundStatus + handlingWay=10
        OrderRefundInfo[] arr = src.getOrderRefundList();
        if (arr != null && arr.length > 0 && arr[0] != null) {
            this.hasRefund = "1";
            this.refundStatus = String.valueOf(arr[0].getRefundStatus());
            this.handlingWay = String.valueOf(arr[0].getHandlingWay());

        } else {
            this.hasRefund = "0";
            this.refundStatus = "0";
            this.handlingWay = "0";
        }

    }

    // ----------------- helpers -----------------

    private static Instant toInstantMs(Long ms) {
        if (ms == null || ms <= 0) return null;
        return Instant.ofEpochMilli(ms);

    }
    private static BigDecimal fenToYuan(Long fen) {
        if (fen == null) return null;
        return BigDecimal.valueOf(fen)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal fenToYuan(long fen) {
        return BigDecimal.valueOf(fen)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

}

