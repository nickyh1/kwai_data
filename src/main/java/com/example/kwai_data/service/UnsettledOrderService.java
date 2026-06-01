package com.example.kwai_data.service;

import com.example.kwai_data.domain.ShopAuth;
import com.example.kwai_data.dto.UnsettledOrderDto;
import com.example.kwai_data.repository.ShopAuthRegistry;
import com.kuaishou.merchant.open.api.client.AccessTokenKsMerchantClient;
import com.example.kwai_data.client.KwaiClientFactory;
import com.kuaishou.merchant.open.api.request.funds.OpenFundsFinancialStatementListRequest;
import com.kuaishou.merchant.open.api.response.funds.OpenFundsFinancialStatementListResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
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

    /**
     * 批量写入一页未结算订单，消除 N+1。
     *
     * <p>create_time 来源（按优先级）：
     * <ol>
     *   <li>dto 自带（已设置）</li>
     *   <li>本地 {@code orders} 表批量查询（1 次 SQL，替代原来每条 1 次 API 调用）</li>
     *   <li>null — UPSERT 中 {@code COALESCE(create_time, VALUES(create_time))} 保留已有值；
     *       全新行暂为 null，下次同步时 orders 表已有数据后自动补全</li>
     * </ol>
     */
    public int upsertBatch(List<UnsettledOrderDto> dtos, String shopKey) {
        if (dtos == null || dtos.isEmpty()) return 0;

        // 1. 收集尚无 create_time 的 OID
        List<String> missingOids = dtos.stream()
                .filter(d -> d != null && d.getOid() != null && !d.getOid().isBlank()
                        && d.getCreateTime() == null)
                .map(UnsettledOrderDto::getOid)
                .distinct()
                .collect(Collectors.toList());

        // 2. 一次 SQL 批量查询 orders 表（替代 N 次 API 调用）
        Map<String, Instant> createTimeByOid = missingOids.isEmpty()
                ? Collections.emptyMap()
                : batchLookupCreateTimes(shopKey, missingOids);

        if (!createTimeByOid.isEmpty()) {
            log.debug("[{}] 从 orders 表批量查得 create_time：命中 {}/{} 条",
                    shopKey, createTimeByOid.size(), missingOids.size());
        }

        // 3. 组装批量参数
        List<Object[]> batchArgs = new ArrayList<>(dtos.size());
        for (UnsettledOrderDto dto : dtos) {
            if (dto == null || dto.getOid() == null || dto.getOid().isBlank()) continue;

            Instant createTime = dto.getCreateTime() != null
                    ? dto.getCreateTime()
                    : createTimeByOid.get(dto.getOid()); // may still be null → COALESCE handles it

            batchArgs.add(new Object[]{
                    shopKey,
                    dto.getOid(),
                    dto.getOrderStatus(),
                    dto.getSettlementStatus(),
                    toTs(shift8(dto.getSettlementTime())),
                    dto.getFreightWhenNow(),
                    toTs(shift8(dto.getBillTime())),
                    dto.getAmount(),
                    dto.getPlatformCommissionAmount(),
                    toTs(shift8(createTime))
            });
        }

        if (batchArgs.isEmpty()) return 0;
        jdbcTemplate.batchUpdate(UPSERT_SQL, batchArgs);
        return batchArgs.size();
    }

    /**
     * 从本地 {@code orders} 表批量查询 create_time。
     *
     * <p>orders.create_time 存储时已做 UTC→UTC+8 偏移；
     * 取回时减去 8h 还原为 UTC，与 {@link UnsettledOrderDto#getCreateTime()} 约定一致。
     */
    private Map<String, Instant> batchLookupCreateTimes(String shopKey, List<String> oids) {
        String placeholders = oids.stream().map(s -> "?").collect(Collectors.joining(","));
        String sql = "SELECT order_no, create_time FROM orders " +
                "WHERE shop_key = ? AND order_no IN (" + placeholders + ") " +
                "AND create_time IS NOT NULL";

        Object[] params = new Object[1 + oids.size()];
        params[0] = shopKey;
        for (int i = 0; i < oids.size(); i++) params[i + 1] = oids.get(i);

        Map<String, Instant> result = new HashMap<>();
        jdbcTemplate.query(sql, params, rs -> {
            Timestamp ts = rs.getTimestamp("create_time");
            if (ts != null) {
                // orders 存储的是 UTC+8 偏移值 → 减 8h 还原为 UTC
                result.put(rs.getString("order_no"), ts.toInstant().minus(Duration.ofHours(8)));
            }
        });
        return result;
    }

    public List<UnsettledOrderDto> parseRecords(OpenFundsFinancialStatementListResponse resp) {
        if (resp == null || resp.getData() == null || resp.getData().getRecord() == null)
            return Collections.emptyList();
        return resp.getData().getRecord().stream()
                .filter(Objects::nonNull)
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /** 单条写入（供兼容/测试用）。批量同步请使用 {@link #upsertBatch}。 */
    public void upsertOne(UnsettledOrderDto dto, String shopKey) {
        if (dto == null) return;
        if (dto.getOid() == null || dto.getOid().isBlank()) throw new IllegalArgumentException("oid is required");
        jdbcTemplate.update(UPSERT_SQL,
                shopKey,
                dto.getOid(),
                dto.getOrderStatus(),
                dto.getSettlementStatus(),
                toTs(shift8(dto.getSettlementTime())),
                dto.getFreightWhenNow(),
                toTs(shift8(dto.getBillTime())),
                dto.getAmount(),
                dto.getPlatformCommissionAmount(),
                toTs(shift8(dto.getCreateTime()))
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
    private static Instant   shift8(Instant t)     { return t == null ? null : t.plus(Duration.ofHours(8)); }
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
