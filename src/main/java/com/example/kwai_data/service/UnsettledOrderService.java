package com.example.kwai_data.service;

import com.example.kwai_data.client.KwaiClientFactory;
import com.example.kwai_data.domain.ShopAuth;
import com.example.kwai_data.dto.UnsettledOrderDto;
import com.example.kwai_data.repository.ShopAuthRegistry;
import com.kuaishou.merchant.open.api.client.AccessTokenKsMerchantClient;
import com.kuaishou.merchant.open.api.request.funds.OpenFundsFinancialStatementListRequest;
import com.kuaishou.merchant.open.api.request.order.OpenOrderDetailRequest;
import com.kuaishou.merchant.open.api.response.funds.OpenFundsFinancialStatementListResponse;
import com.kuaishou.merchant.open.api.response.order.OpenOrderDetailResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UnsettledOrderService {

    private final KwaiClientFactory clientFactory;
    private final ShopAuthRegistry registry;
    private final JdbcTemplate jdbcTemplate;

    private static final String UPSERT_SQL = """
            INSERT INTO unsettled_orders
                (shop_key, oid, order_status, settlement_status, settlement_time,
                 freight_when_now, bill_time, amount, platform_commission_amount, create_time)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                order_status               = VALUES(order_status),
                settlement_status          = VALUES(settlement_status),
                settlement_time            = VALUES(settlement_time),
                freight_when_now           = VALUES(freight_when_now),
                bill_time                  = VALUES(bill_time),
                amount                     = VALUES(amount),
                platform_commission_amount = VALUES(platform_commission_amount),
                create_time                = COALESCE(create_time, VALUES(create_time))
            """;

    public OpenFundsFinancialStatementListResponse fetchOnce(
            String shopKey,
            long startTimeMs,
            long endTimeMs,
            String cursor
    ) {
        ShopAuth auth = registry.get(shopKey);
        if (auth == null) throw new IllegalArgumentException("未找到店铺配置: " + shopKey);

        String accessToken = auth.getAccessToken();
        if (accessToken == null || accessToken.isBlank()) throw new IllegalArgumentException("accessToken is required");
        if (startTimeMs <= 0 || endTimeMs <= 0) throw new IllegalArgumentException("startTimeMs/endTimeMs must be positive");
        if (startTimeMs > endTimeMs) throw new IllegalArgumentException("startTimeMs must be <= endTimeMs");

        AccessTokenKsMerchantClient client = clientFactory.getClient();

        OpenFundsFinancialStatementListRequest req = new OpenFundsFinancialStatementListRequest();
        req.setAccessToken(accessToken);
        req.setApiMethodVersion(1L);
        if (cursor != null && !cursor.isBlank()) req.setCursor(cursor);
        req.setStartTime(startTimeMs);
        req.setEndTime(endTimeMs);
        req.setSubMchId("");

        try {
            OpenFundsFinancialStatementListResponse resp = client.execute(req);
            if (resp == null) throw new RuntimeException("Kwai openapi response is null");
            return resp;
        } catch (Exception e) {
            throw new RuntimeException("Kwai openapi execute failed", e);
        }
    }

    public Instant fetchOrderCreateTime(String shopKey, String oid) {
        if (oid == null || oid.isBlank()) return null;
        ShopAuth auth = registry.get(shopKey);
        if (auth == null) { log.warn("fetchOrderCreateTime: 未找到店铺配置: {}", shopKey); return null; }

        AccessTokenKsMerchantClient client = clientFactory.getClient();
        OpenOrderDetailRequest req = new OpenOrderDetailRequest();
        req.setAccessToken(auth.getAccessToken());
        req.setApiMethodVersion(1L);
        req.setOid(Long.parseLong(oid));

        for (int attempt = 1; attempt <= 5; attempt++) {
            try {
                OpenOrderDetailResponse resp = client.execute(req);
                if (resp == null || resp.getData() == null) {
                    if (attempt < 5) { Thread.sleep(1000L * attempt); continue; }
                    return null;
                }
                Long createTimeMs = resp.getData().getOrderBaseInfo().getCreateTime();
                return createTimeMs == null ? null : Instant.ofEpochMilli(createTimeMs);
            } catch (Exception e) {
                log.warn("fetchOrderCreateTime failed, oid={}, attempt={}: {}", oid, attempt, e.getMessage());
                if (attempt < 5) {
                    try { Thread.sleep(1000L * attempt); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return null; }
                }
            }
        }
        return null;
    }

    public List<UnsettledOrderDto> parseRecords(OpenFundsFinancialStatementListResponse resp) {
        if (resp == null || resp.getData() == null || resp.getData().getRecord() == null)
            return Collections.emptyList();
        return resp.getData().getRecord().stream()
                .filter(Objects::nonNull)
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public void upsertOne(UnsettledOrderDto dto, String shopKey) {
        if (dto == null) return;
        if (dto.getOid() == null || dto.getOid().isBlank()) throw new IllegalArgumentException("oid is required");

        Instant settlementTime = dto.getSettlementTime() == null ? null : dto.getSettlementTime().plus(Duration.ofHours(8));
        Instant billTime       = dto.getBillTime()       == null ? null : dto.getBillTime().plus(Duration.ofHours(8));
        Instant createTime     = dto.getCreateTime()     == null ? null : dto.getCreateTime().plus(Duration.ofHours(8));

        jdbcTemplate.update(UPSERT_SQL,
                shopKey,
                dto.getOid(),
                dto.getOrderStatus(),
                dto.getSettlementStatus(),
                toTs(settlementTime),
                dto.getFreightWhenNow(),
                toTs(billTime),
                dto.getAmount(),
                dto.getPlatformCommissionAmount(),
                toTs(createTime)
        );
    }

    public String extractNextCursor(OpenFundsFinancialStatementListResponse resp) {
        if (resp == null) return null;
        Object data = resp.getData();
        if (data == null) {
            String top = firstNonBlank(
                    asString(tryInvoke(resp, "getNextCursor")),
                    asString(tryInvoke(resp, "getCursor"))
            );
            return blankToNull(top);
        }
        return blankToNull(resp.getData().getCursor());
    }

    private UnsettledOrderDto toDto(com.kuaishou.merchant.open.api.domain.funds.BillStatementDtoShow r) {
        BigDecimal amount = (r.getAmount() == null) ? null : BigDecimal.valueOf(r.getAmount());
        BigDecimal freight = null;
        if (r.getFreightWhenNow() != null && !r.getFreightWhenNow().isBlank()) {
            freight = new BigDecimal(r.getFreightWhenNow());
        }
        BigDecimal platformCommission = (r.getPlatformAmount() == null) ? null : BigDecimal.valueOf(r.getPlatformAmount());
        Instant billTime       = (r.getBillTime()       == null) ? null : Instant.ofEpochMilli(r.getBillTime());
        Instant settlementTime = (r.getSettlementTime() == null) ? null : Instant.ofEpochMilli(r.getSettlementTime());

        return UnsettledOrderDto.builder()
                .oid(r.getOutOrderId())
                .orderStatus(r.getOrderStatus() == null ? null : String.valueOf(r.getOrderStatus()))
                .settlementStatus(r.getSettlementStatus())
                .settlementTime(settlementTime)
                .freightWhenNow(freight)
                .billTime(billTime)
                .amount(amount)
                .platformCommissionAmount(platformCommission)
                .build();
    }

    private static Timestamp toTs(Instant instant) { return instant == null ? null : Timestamp.from(instant); }
    private static Object tryInvoke(Object target, String methodName) {
        try { Method m = target.getClass().getMethod(methodName); return m.invoke(target); } catch (Exception ignored) { return null; }
    }
    private static String asString(Object o) { return o == null ? null : String.valueOf(o); }
    private static String firstNonBlank(String... arr) {
        if (arr == null) return null;
        for (String s : arr) { if (s != null && !s.isBlank()) return s; }
        return null;
    }
    private static String blankToNull(String s) { return (s == null || s.isBlank()) ? null : s; }
}
