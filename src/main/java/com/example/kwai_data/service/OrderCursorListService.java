package com.example.kwai_data.service;

import com.example.kwai_data.client.KwaiClientFactory;
import com.example.kwai_data.data.OrderDto;
import com.example.kwai_data.data.ShopAuth;
import com.example.kwai_data.repository.ShopAuthRegistry;
import com.kuaishou.merchant.open.api.client.AccessTokenKsMerchantClient;
import com.kuaishou.merchant.open.api.domain.order.OrderList;
import com.kuaishou.merchant.open.api.request.order.OpenOrderCursorListRequest;
import com.kuaishou.merchant.open.api.response.order.OpenOrderCursorListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderCursorListService {

    private final JdbcTemplate jdbcTemplate;
    private final ShopAuthRegistry registry;
    private final KwaiClientFactory clientFactory;

    // INSERT ... ON DUPLICATE KEY UPDATE
    // - Immutable fields: NOT in UPDATE clause (set only on first insert via INSERT columns)
    // - Mutable fields: always updated
    // - update_time: GREATEST for out-of-order protection
    private static final String UPSERT_SQL = """
            INSERT INTO orders
                (shop_key, order_no, order_status, pay_time, freight, promotion_discount,
                 sub_order_total_price, ship_time, refund_initiate_time, create_time, update_time,
                 distribution_type, receive_time, pay_type, ks_sku_id, product_id, product_name,
                 sku_quantity, unit_price_before_promo_snapshot, discount_amount, unit_price_snapshot,
                 has_refund, refund_status, handling_way, last_ingested_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                order_status         = VALUES(order_status),
                refund_initiate_time = VALUES(refund_initiate_time),
                receive_time         = VALUES(receive_time),
                has_refund           = VALUES(has_refund),
                refund_status        = VALUES(refund_status),
                handling_way         = VALUES(handling_way),
                last_ingested_at     = VALUES(last_ingested_at),
                update_time          = GREATEST(COALESCE(update_time, '1970-01-01 00:00:00.000'), VALUES(update_time))
            """;

    public OpenOrderCursorListResponse fetchOnce(
            String shopKey,
            Integer orderViewStatus,
            Integer pageSize,
            Integer sort,
            Integer queryType,
            Long beginTime,
            Long endTime,
            Integer cpsType,
            String cursor
    ) throws Exception {
        ShopAuth auth = registry.get(shopKey);
        if (auth == null) {
            throw new IllegalArgumentException("未找到店铺配置: " + shopKey);
        }

        AccessTokenKsMerchantClient client = clientFactory.getClient();
        OpenOrderCursorListRequest request = new OpenOrderCursorListRequest();
        request.setAccessToken(auth.getAccessToken());
        request.setApiMethodVersion(1L);

        if (orderViewStatus != null) request.setOrderViewStatus(orderViewStatus);
        if (pageSize != null) request.setPageSize(pageSize);
        if (sort != null) request.setSort(sort);
        if (queryType != null) request.setQueryType(queryType);
        if (beginTime != null) request.setBeginTime(beginTime);
        if (endTime != null) request.setEndTime(endTime);
        if (cpsType != null) request.setCpsType(cpsType);
        if (cursor != null && !cursor.isBlank()) request.setCursor(cursor);

        return client.execute(request);
    }

    public List<OpenOrderCursorListResponse> fetchAllPages(
            String shopKey,
            Integer orderViewStatus,
            Integer pageSize,
            Integer sort,
            Integer queryType,
            Long beginTime,
            Long endTime,
            Integer cpsType,
            String initialCursor,
            int maxPages
    ) throws Exception {
        List<OpenOrderCursorListResponse> pages = new ArrayList<>();
        String cursor = initialCursor;

        for (int i = 0; i < maxPages; i++) {
            OpenOrderCursorListResponse resp = fetchOnce(
                    shopKey, orderViewStatus, pageSize, sort, queryType, beginTime, endTime, cpsType, cursor
            );
            pages.add(resp);

            String nextCursor = extractNextCursor(resp);
            if (nextCursor == null || nextCursor.isBlank() || nextCursor.equals(cursor)) break;
            cursor = nextCursor;
        }
        return pages;
    }

    /**
     * 批量幂等写入：整页 DTOs 一次性 BulkWrite 到 orders 表
     * - setOnInsert 效果通过 ON DUPLICATE KEY UPDATE 不包含不可变字段实现
     * - update_time 用 GREATEST 做乱序保护
     */
    public int upsertBatch(List<OrderDto> dtos, String shopKey) {
        if (dtos == null || dtos.isEmpty()) return 0;

        Instant now = Instant.now();

        // 去重（同页内可能有重复 orderNo，保留最后一条）
        Map<String, OrderDto> unique = new LinkedHashMap<>();
        for (OrderDto dto : dtos) {
            if (dto == null) continue;
            if (dto.getOrderNo() == null || dto.getOrderNo().isBlank()) continue;
            if (dto.getUpdateTime() == null) continue;   // updateTime 必须有
            unique.put(dto.getOrderNo(), dto);
        }
        if (unique.isEmpty()) return 0;

        List<Object[]> batchArgs = unique.values().stream()
                .map(dto -> toParams(dto, shopKey, now))
                .collect(Collectors.toList());

        jdbcTemplate.batchUpdate(UPSERT_SQL, batchArgs);
        return batchArgs.size();
    }

    /** 单条写入（供兼容/测试用） */
    public void upsertOne(OrderDto dto, String shopKey) {
        if (dto == null) return;
        if (dto.getOrderNo() == null || dto.getOrderNo().isBlank()) return;
        if (dto.getUpdateTime() == null) return;
        jdbcTemplate.update(UPSERT_SQL, toParams(dto, shopKey, Instant.now()));
    }

    private Object[] toParams(OrderDto dto, String shopKey, Instant now) {
        return new Object[]{
                shopKey,
                dto.getOrderNo(),
                dto.getOrderStatus(),
                toTs(dto.getPayTime()),
                dto.getFreight(),
                dto.getPromotionDiscount(),
                dto.getSubOrderTotalPrice(),
                toTs(dto.getShipTime()),
                toTs(dto.getRefundInitiateTime()),
                toTs(dto.getCreateTime()),
                toTs(dto.getUpdateTime()),
                dto.getDistributionType(),
                toTs(dto.getReceiveTime()),
                dto.getPayType(),
                dto.getKsSkuId(),
                dto.getProductId(),
                dto.getProductName(),
                dto.getSkuQuantity(),
                dto.getUnitPriceBeforePromoSnapshot(),
                dto.getDiscountAmount(),
                dto.getUnitPriceSnapshot(),
                dto.getHasRefund(),
                dto.getRefundStatus(),
                dto.getHandlingWay(),
                toTs(now)   // last_ingested_at
        };
    }

    public OrderList[] extractOrderList(OpenOrderCursorListResponse src) {
        if (src == null || src.getData() == null || src.getData().getOrderList() == null) {
            return new OrderList[0];
        }
        return src.getData().getOrderList();
    }

    public String extractNextCursor(OpenOrderCursorListResponse resp) {
        if (resp == null) return null;
        try {
            return resp.getData().getCursor();
        } catch (Exception ignore) {
            return null;
        }
    }

    private static Timestamp toTs(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
