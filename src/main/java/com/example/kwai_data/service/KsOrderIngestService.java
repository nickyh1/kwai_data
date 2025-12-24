package com.example.kwai_data.service;

import com.example.kwai_data.dto.KsResponse;
import com.example.kwai_data.dto.order.KsOrderDTO;
import com.example.kwai_data.dto.order.KsOrderQueryData;
import com.example.kwai_data.dto.order.KsOrderDoc;
import com.example.kwai_data.interfac.KsOrderRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class KsOrderIngestService {

    private final ObjectMapper objectMapper;
    private final KsOrderRepository ksOrderRepository;

    /**
     * 报文处理函数：
     * 1) JSON -> KsApiResponse<KsOrderQueryData>
     * 2) orders -> 规范化 KsOrderDoc 列表
     */
    public List<KsOrderDoc> parseAndNormalize(String json) {
        try {
            KsResponse<KsOrderQueryData> resp = objectMapper.readValue(
                    json,
                    new TypeReference<KsResponse<KsOrderQueryData>>() {}
            );

            if (resp == null || resp.getData() == null || resp.getData().getOrders() == null) {
                return Collections.emptyList();
            }

            String requestId = resp.getRequestId();

            return resp.getData().getOrders().stream()
                    .filter(Objects::nonNull)
                    .map(o -> toDoc(o, requestId))
                    .toList();

        } catch (Exception e) {
            // 你可以换成项目里的日志框架 log.error(...)
            throw new RuntimeException("Failed to parse/normalize ks orders json", e);
        }
    }

    /**
     * 入库函数：批量 upsert
     * 以 KsOrderDoc.id = orderNo 做主键，saveAll 即为 upsert 语义
     */
    public List<KsOrderDoc> saveToMongo(List<KsOrderDoc> docs) {
        if (docs == null || docs.isEmpty()) {
            return Collections.emptyList();
        }
        // 可选：过滤掉没有 orderNo 的脏数据
        List<KsOrderDoc> valid = docs.stream()
                .filter(d -> d.getOrderNo() != null && d.getId() != null && !d.getId().isBlank())
                .toList();

        if (valid.isEmpty()) return Collections.emptyList();

        return ksOrderRepository.saveAll(valid);
    }

    private KsOrderDoc toDoc(KsOrderDTO o, String requestId) {
        Long orderCreateMs = toLong(o.getOrderCreateTime());
        Long settlementMs  = toLong(o.getSettlementTime());

        return KsOrderDoc.builder()
                .id(o.getOrderNo() == null ? null : String.valueOf(o.getOrderNo()))
                .orderNo(o.getOrderNo())
                .merchantId(o.getMerchantId())
                .productId(o.getProductId())
                .productName(o.getProductName())
                .productNum(o.getProductNum())
                .accountChannel(o.getAccountChannel())
                .accountName(o.getAccountName())
                .settlementStatus(o.getSettlementStatus())
                .settlementRule(o.getSettlementRule())
                .orderCreateTimeMs(orderCreateMs)
                .settlementTimeMs(settlementMs)
                .orderCreateTime(orderCreateMs == null ? null : Instant.ofEpochMilli(orderCreateMs))
                .settlementTime(settlementMs == null ? null : Instant.ofEpochMilli(settlementMs))

                // 金额字段（字符串/空串/数字混合，统一安全解析）
                .totalIncome(toBigDecimal(o.getTotalIncome()))
                .actualPayAmount(toBigDecimal(o.getActualPayAmount()))
                .settlementAmount(toBigDecimal(o.getSettlementAmount()))
                .platformCommissionAmount(toBigDecimal(o.getPlatformCommissionAmount()))
                .totalOutgoingAmount(toBigDecimal(o.getTotalOutgoingAmount()))
                .totalRefundAmount(toBigDecimal(o.getTotalRefundAmount()))

                .sellerAllowanceAmount(o.getSellerAllowanceAmount())
                .platformPayMarketAllowanceAmount(o.getPlatformPayMarketAllowanceAmount())
                .governmentSubsidyAmount(o.getGovernmentSubsidyAmount())
                .presellSettleAmount(o.getPresellSettleAmount())
                .otherAmount(o.getOtherAmount())

                .collectMode(o.getCollectMode())
                .requestId(requestId)
                .ingestedAt(Instant.now())
                .build();
    }

    private Long toLong(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        try {
            return Long.parseLong(t);
        } catch (Exception ignore) {
            return null;
        }
    }

    private BigDecimal toBigDecimal(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        try {
            return new BigDecimal(t);
        } catch (Exception ignore) {
            return null;
        }
    }
}

