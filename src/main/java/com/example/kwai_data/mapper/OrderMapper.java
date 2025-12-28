package com.example.kwai_data.mapper;

// src/main/java/com/example/kwai_data/mapper/OrderMapper.java


import com.example.kwai_data.data.Order_Doc;
import com.example.kwai_data.data.OrderDto;

import java.util.List;
import java.util.stream.Collectors;

public class OrderMapper {

    public static Order_Doc toDocument(OrderDto dto) {
        if (dto == null) return null;

        return Order_Doc.builder()
                .orderNo(dto.getOrderNo())
                .payTime(dto.getPayTime())
                .orderStatus(dto.getOrderStatus())
                .freight(dto.getFreight())
                .promotionDiscount(dto.getPromotionDiscount())
                .subOrderTotalPrice(dto.getSubOrderTotalPrice())
                .shipTime(dto.getShipTime())
                .refundInitiateTime(dto.getRefundInitiateTime())
                .createTime(dto.getCreateTime())
                .updateTime(dto.getUpdateTime())
                .distributionType(dto.getDistributionType())
                .receiveTime(dto.getReceiveTime())
                .payType(dto.getPayType())
                .ksSkuId(dto.getKsSkuId())
                .productId(dto.getProductId())
                .productName(dto.getProductName())
                .skuQuantity(dto.getSkuQuantity())
                .unitPriceBeforePromoSnapshot(dto.getUnitPriceBeforePromoSnapshot())
                .discountAmount(dto.getDiscountAmount())
                .unitPriceSnapshot(dto.getUnitPriceSnapshot())
                .hasRefund(dto.getHasRefund())
                .refundStatus(dto.getRefundStatus())
                .handlingWay(dto.getHandlingWay())
                .build();
    }


}

