package com.example.kwai_data.mapper;



import com.example.kwai_data.data.UnsettledOrderDoc;
import com.example.kwai_data.data.UnsettledOrderDto;

public class UnsettledOrderMapper {

    public static UnsettledOrderDoc toDoc(UnsettledOrderDto dto) {
        if (dto == null) return null;
        return UnsettledOrderDoc.builder()
                .oid(dto.getOid())
                .orderStatus(dto.getOrderStatus())
                .settlementStatus(dto.getSettlementStatus())
                .settlementTime(dto.getSettlementTime())
                //.unsettleorderStatus(dto.getUnsettleorderStatus())
                .freightWhenNow(dto.getFreightWhenNow())
                .billTime(dto.getBillTime())
                .amount(dto.getAmount())
                .build();
    }

    public static UnsettledOrderDto toDto(UnsettledOrderDoc doc) {
        if (doc == null) return null;
        return UnsettledOrderDto.builder()
                .oid(doc.getOid())
                .orderStatus(doc.getOrderStatus())
                .settlementStatus(doc.getSettlementStatus())
                .settlementTime(doc.getSettlementTime())
                //.unsettleorderStatus(doc.getUnsettleorderStatus())
                .freightWhenNow(doc.getFreightWhenNow())
                .billTime(doc.getBillTime())
                .amount(doc.getAmount())
                .build();
    }
}

