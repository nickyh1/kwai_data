package com.example.kwai_data.service;

import com.example.kwai_data.client.KwaiClientFactory;
import com.example.kwai_data.data.ShopAuth;
import com.example.kwai_data.data.UnsettledOrderDoc;
import com.example.kwai_data.data.UnsettledOrderDto;
import com.example.kwai_data.repository.ShopAuthRegistry;
import com.kuaishou.merchant.open.api.client.AccessTokenKsMerchantClient;
import com.kuaishou.merchant.open.api.request.funds.OpenFundsFinancialStatementListRequest;
import com.kuaishou.merchant.open.api.response.funds.OpenFundsFinancialStatementListResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.awt.*;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.query.Criteria.where;


@Service
@RequiredArgsConstructor
public class UnsettledOrderService {

    private final KwaiClientFactory clientFactory;
    private final ShopAuthRegistry registry;
    private final ObjectMapper objectMapper;
    private final MongoTemplate mongoTemplate;
    /**
     * 建议你把 url/appKey/signSecret 放到配置里，通过 @Configuration 注入 client。
     * 这里为了示例，直接通过构造器注入一个已配置好的 client。
     */


    /**
     * 拉取一页（一次请求）。
     *
     * @param accessToken 店铺 accessToken
     * @param cursor      上一次返回的 cursor；首次传 null/blank
     * @param startTimeMs 开始时间戳（毫秒）
     * @param endTimeMs   结束时间戳（毫秒）
     * @param subMchId    子商户号（如不需要可传 null）
     */
    public OpenFundsFinancialStatementListResponse fetchOnce(
            String shopKey,
            long startTimeMs,
            long endTimeMs,
            String cursor
    ) {
        ShopAuth auth = registry.get(shopKey);
        if (auth == null) {
            throw new IllegalArgumentException("未找到店铺配置: " + shopKey);
        }

        AccessTokenKsMerchantClient client = clientFactory.getClient();

        String accessToken = auth.getAccessToken();

        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("accessToken is required");
        }
        if (startTimeMs <= 0 || endTimeMs <= 0) {
            throw new IllegalArgumentException("startTimeMs/endTimeMs must be positive");
        }
        if (startTimeMs > endTimeMs) {
            throw new IllegalArgumentException("startTimeMs must be <= endTimeMs");
        }



        OpenFundsFinancialStatementListRequest req = new OpenFundsFinancialStatementListRequest();
        req.setAccessToken(accessToken);
        req.setApiMethodVersion(1L);

        if (cursor != null && !cursor.isBlank()) {
            req.setCursor(cursor);
        }
        req.setStartTime(startTimeMs);
        req.setEndTime(endTimeMs);

        req.setSubMchId("");


        OpenFundsFinancialStatementListResponse resp;
        try {
            resp = client.execute(req);
        } catch (Exception e) {
            // SDK execute 有的抛 RuntimeException / checked exception，统一封装一下
            System.out.println("fetchOnce failed: cursor={}, start={}, end={}, subMchId={}");
            throw new RuntimeException("Kwai openapi execute failed", e);
        }

        // 这里不要强依赖 SDK 字段名（不同版本可能 result/code/msg 不一致），做个弱校验
        if (resp == null) {
            throw new RuntimeException("Kwai openapi response is null");
        }

        // 如果你明确知道 SDK 成功码字段（例如 getResult()==1），可以在这里强校验
        // 示例：validateSuccess(resp);

        return resp;
    }

    public List<UnsettledOrderDto> parseRecords(OpenFundsFinancialStatementListResponse resp) {
        if (resp == null ) return Collections.emptyList();

        try {
            //KwaiStatementResp resp = objectMapper.readValue(json, KwaiStatementResp.class);
            if (resp.getData() == null || resp.getData().getRecord() == null) {
                return Collections.emptyList();
            }

            return resp.getData().getRecord().stream()
                    .filter(Objects::nonNull)
                    .map(this::toDto)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            System.out.println("Failed to parse kwai statement json");
            throw new RuntimeException("Failed to parse kwai statement json", e);
        }
    }

    /**
     * 每次插入/更新一条 dto（按 oid 幂等 upsert）
     */
    public void upsertOne(UnsettledOrderDto dto, String shopKey) {
        if (dto == null) return;
        if (dto.getOid() == null || dto.getOid().isBlank()) {
            throw new IllegalArgumentException("oid is required");
        }

        String collection = "Unsetllement_"+shopKey;

        Query q = new Query(where("oid").is(dto.getOid()));

        Update u = new Update()
                .set("oid", dto.getOid())
                .set("orderStatus", dto.getOrderStatus())
                .set("settlementStatus", dto.getSettlementStatus())
                .set("settlementTime", dto.getSettlementTime().plus(Duration.ofHours(8)))
                //.set("unsettleorderStatus", dto.getUnsettleorderStatus())
                .set("freightWhenNow", dto.getFreightWhenNow())
                .set("billTime", dto.getBillTime().plus(Duration.ofHours(8)))
                .set("amount", dto.getAmount());

        mongoTemplate.upsert(q, u, collection);
    }

    private UnsettledOrderDto toDto(com.kuaishou.merchant.open.api.domain.funds.BillStatementDtoShow r) {
        // amount：先按原值保存（很可能是“分”）。如需存“元”：BigDecimal.valueOf(r.getAmount()).movePointLeft(2)
        BigDecimal amount = (r.getAmount() == null) ? null : BigDecimal.valueOf(r.getAmount());

        BigDecimal freight = null;
        if (r.getFreightWhenNow() != null && !r.getFreightWhenNow().isBlank()) {
            freight = new BigDecimal(r.getFreightWhenNow()); // 同理如需“元”，按 movePointLeft(2)
        }

        Instant billTime = (r.getBillTime() == null) ? null : Instant.ofEpochMilli(r.getBillTime());
        Instant settlementTime = (r.getSettlementTime() == null) ? null : Instant.ofEpochMilli(r.getSettlementTime());

        return UnsettledOrderDto.builder()
                .oid(r.getOutOrderId())
                .orderStatus(r.getOrderStatus() == null ? null : String.valueOf(r.getOrderStatus()))
                .settlementStatus(r.getSettlementStatus())
                .settlementTime(settlementTime)
                //.unsettleorderStatus(null) // 样例无该字段，先留空
                .freightWhenNow(freight)
                .billTime(billTime)
                .amount(amount)
                .build();
    }

    /**
     * 从响应中提取下一页 cursor。
     *
     * 说明：
     * - 不同接口/SDK 版本 cursor 字段可能叫 nextCursor/cursor/next 等
     * - 这里用反射做兜底：先取 resp.getData()，再尝试常见 getter 名称
     *
     * @return 下一页 cursor；若没有下一页返回 null
     */
    public String extractNextCursor(OpenFundsFinancialStatementListResponse resp) {
        if (resp == null) return null;

        Object data = tryInvoke(resp, "getData");
        if (data == null) {
            // 有些 SDK 直接把 cursor 放在 resp 顶层
            String top = firstNonBlank(
                    asString(tryInvoke(resp, "getNextCursor")),
                    asString(tryInvoke(resp, "getCursor"))
            );
            return blankToNull(top);
        }

        String next = firstNonBlank(
                asString(tryInvoke(data, "getNextCursor")),
                asString(tryInvoke(data, "getNext")),
                asString(tryInvoke(data, "getNextPageCursor")),
                asString(tryInvoke(data, "getCursor")) // 有的接口 data.cursor 就是“下一页 cursor”
        );

        return blankToNull(next);
    }

    // ----------------- 可选：强校验成功码（你明确字段时再启用） -----------------

    @SuppressWarnings("unused")
    private void validateSuccess(OpenFundsFinancialStatementListResponse resp) {
        // 示例：按常见字段名尝试取 result/code/msg
        Object result = tryInvoke(resp, "getResult");
        Object code = tryInvoke(resp, "getCode");
        Object msg = tryInvoke(resp, "getMsg");
        Object errMsg = tryInvoke(resp, "getErrorMsg");

        // 下面逻辑仅示例：你应按快手 openapi 实际返回定义来判断
        // 例如：result==1 代表成功；否则抛异常
        if (result instanceof Number && ((Number) result).intValue() != 1) {
            throw new RuntimeException("Kwai API error: result=" + result + ", code=" + code
                    + ", msg=" + msg + ", errorMsg=" + errMsg);
        }
    }

    // ----------------- 反射/工具方法 -----------------

    private static Object tryInvoke(Object target, String methodName) {
        try {
            Method m = target.getClass().getMethod(methodName);
            return m.invoke(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String asString(Object o) {
        return (o == null) ? null : String.valueOf(o);
    }

    private static String firstNonBlank(String... arr) {
        if (arr == null) return null;
        for (String s : arr) {
            if (s != null && !s.isBlank()) return s;
        }
        return null;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}



