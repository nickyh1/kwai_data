package com.example.kwai_data.mapper;

import com.example.kwai_data.data.SellerInfo_Doc;
import com.example.kwai_data.data.sellerInfo;


public final class SellerInfoMapper {
    private SellerInfoMapper() {}

    public static SellerInfo_Doc toEntity(sellerInfo dto) {
        return SellerInfo_Doc.builder()
                .shopId(dto.getShopId())
                .shopName(dto.getShopName())
                .accountBalance(dto.getAccountBalance())
                .pendingSettlement(dto.getPendingSettlement())
                .totalWithdrawn(dto.getTotalWithdrawn())
                .netTransactionAmount(dto.getNetTransactionAmount())
                .build();
    }
}
