package com.example.kwai_data.service;



import com.example.kwai_data.KwaiDataApplication;
import com.example.kwai_data.client.KwaiClientFactory;
import com.example.kwai_data.config.KwaiProperties;
import com.example.kwai_data.data.OrderDto;
import com.example.kwai_data.data.Order_Doc;
import com.example.kwai_data.data.ShopAuth;
import com.example.kwai_data.repository.OrderRepository;
import com.example.kwai_data.mapper.OrderMapper;
import com.example.kwai_data.repository.ShopAuthRegistry;
import com.kuaishou.merchant.open.api.client.AccessTokenKsMerchantClient;
import com.kuaishou.merchant.open.api.domain.order.OrderList;
import com.kuaishou.merchant.open.api.request.order.OpenOrderCursorListRequest;
import com.kuaishou.merchant.open.api.response.order.OpenOrderCursorListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.Kernel;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Service
@RequiredArgsConstructor
public class OrderCursorListService {

    //private final AccessTokenKsMerchantClient client;
    //private final KwaiProperties props;
    private final OrderRepository orderRepository;
    private final MongoTemplate mongoTemplate;
    private final ShopAuthRegistry registry;
    private final KwaiClientFactory clientFactory;


    /**
     * 单次拉取（对应 demo）
     */
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

        // 可选参数保持原逻辑
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

    /**
     * 拉取全部（自动翻页）
     *
     * 说明：
     * - pageSize 建议你按接口上限设置
     * - cursor 初始可传 null（从第一页开始）
     */
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
                    shopKey ,orderViewStatus, pageSize, sort, queryType, beginTime, endTime, cpsType, cursor
            );
            pages.add(resp);

            // ====== 下面这一段：你需要根据真实 response 结构确认 nextCursor 字段路径 ======
            String nextCursor = extractNextCursor(resp);

            // 如果没有下一页，退出
            if (nextCursor == null || nextCursor.isBlank() || nextCursor.equals(cursor)) {
                break;
            }
            cursor = nextCursor;
        }

        return pages;
    }

    /**
     * 单条幂等写入：存在则更新，不存在则插入
     */
//    public Order_Doc upsertOne(OrderDto dto) {
//        if (dto == null) return null;
//        Order_Doc doc = OrderMapper.toDocument(dto);
//
//        // 如果已存在 orderNo，复用其 _id，这样 repository.save() 就是 update
//        orderRepository.findByOrderNo(doc.getOrderNo())
//                .ifPresent(exist -> doc.setId(exist.getId()));
//
//        return orderRepository.save(doc);
//    }

    public Order_Doc upsertOne(OrderDto dto, String shopKey) {
        if (dto == null) return null;

        String collection = orderCollection(shopKey);

        Order_Doc incoming = OrderMapper.toDocument(dto);
        if (incoming.getOrderNo() == null || incoming.getOrderNo().isBlank()) {
            throw new IllegalArgumentException("orderNo is required for upsert");
        }

        // 平台更新时间：建议你在 DTO 里明确一个字段，比如 dto.getUpdateTime()/getPlatformUpdateTime()
        Instant platformUpdateTime = incoming.getUpdateTime();
        if (platformUpdateTime == null) {
            // 不建议默认 now()；更建议强制上游提供平台更新时间，否则无法做乱序保护
            throw new IllegalArgumentException("platform updateTime is required for out-of-order protection");
        }

        Instant now = Instant.now();

        // 乱序保护：只接受更“新”的平台更新时间
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("orderNo").is(incoming.getOrderNo()),
                new Criteria().orOperator(
                        Criteria.where("updateTime").exists(false),
                        Criteria.where("updateTime").lt(platformUpdateTime)
                )
        ));

        Update update = new Update()
                // 仅首次插入写入
                .setOnInsert("orderNo", incoming.getOrderNo())
                .setOnInsert("payTime", incoming.getPayTime())
                .setOnInsert("freight", incoming.getFreight())
                .setOnInsert("promotionDiscount", incoming.getPromotionDiscount())
                .setOnInsert("subOrderTotalPrice", incoming.getSubOrderTotalPrice())
                .setOnInsert("shipTime", incoming.getShipTime())
                //.setOnInsert("orderStatus", incoming.getOrderStatus())//
                //.setOnInsert("refundInitiateTime", incoming.getRefundInitiateTime())//
                .setOnInsert("createTime", incoming.getCreateTime())
                //.setOnInsert("updateTime", incoming.getUpdateTime())//
                .setOnInsert("distributionType",  incoming.getDistributionType())
                //.setOnInsert("receiveTime", incoming.getReceiveTime())//
                .setOnInsert("payType", incoming.getPayType())
                .setOnInsert("ksSkuId", incoming.getKsSkuId())
                .setOnInsert("productId", incoming.getProductId())
                .setOnInsert("productName", incoming.getProductName())
                .setOnInsert("skuQuantity", incoming.getSkuQuantity())
                .setOnInsert("unitPriceBeforePromoSnapshot", incoming.getUnitPriceBeforePromoSnapshot())
                .setOnInsert("discountAmount",  incoming.getDiscountAmount())
                .setOnInsert("unitPriceSnapshot",  incoming.getUnitPriceSnapshot())
                //.setOnInsert("hasRefund", incoming.getHasRefund()) //
                //.setOnInsert("refundStatus", incoming.getRefundStatus()) //
                //.setOnInsert("handlingWay", incoming.getHandlingWay())  //


                // 成功更新/插入时写入（注意：这里的 updateTime 是平台更新时间）
                .set("updateTime", incoming.getUpdateTime())
                .set("lastIngestedAt", now.plus(Duration.ofHours(8)))
                .set("orderStatus", incoming.getOrderStatus())
                .set("refundInitiateTime", incoming.getRefundInitiateTime())
                .set("receiveTime", incoming.getReceiveTime())
                .set("hasRefund", incoming.getHasRefund())
                .set("refundStatus", incoming.getRefundStatus())
                .set("handlingWay", incoming.getHandlingWay());



        // 把业务字段写入（根据你的 Order_Doc 字段补齐）
        // 这里给你示例几项：你按实际字段继续 set(...)


        // ... 继续把你需要的字段 set 进去（不要 set null，避免把已有值抹掉）

        FindAndModifyOptions options = FindAndModifyOptions.options()
                .upsert(true)
                .returnNew(true);


        Order_Doc updated = mongoTemplate.findAndModify(query, update, options, Order_Doc.class, collection);
        //Order_Doc updated = mongoTemplate.findAndModify(query, update, options, Order_Doc.class);

        // 如果 updated == null，说明本次是“旧 updateTime”的乱序数据，被保护条件拦下了；
        // 可选择返回库里现有版本（更符合调用方预期）
        if (updated == null) {
            Query q2 = new Query(Criteria.where("orderNo").is(incoming.getOrderNo()));
            return mongoTemplate.findOne(q2, Order_Doc.class, collection);
        }
        return updated;
    }


    /**
     * 批量幂等写入（推荐）：按 orderNo 去重并 bulk upsert
     * 返回：本次批量实际处理的 doc 数量（去重后）
     */
    public int upsertBatch(List<OrderDto> dtos) {
        if (dtos == null || dtos.isEmpty()) return 0;

        // 1) dto -> doc
        List<Order_Doc> docs = dtos.stream()
                .filter(Objects::nonNull)
                .map(OrderMapper::toDocument)
                .filter(d -> d.getOrderNo() != null && !d.getOrderNo().isBlank())
                .collect(Collectors.toList());

        if (docs.isEmpty()) return 0;

        // 2) 按 orderNo 去重（同一批里可能重复）
        Map<String, Order_Doc> unique = new LinkedHashMap<>();
        for (Order_Doc d : docs) unique.put(d.getOrderNo(), d);
        List<Order_Doc> uniqueDocs = new ArrayList<>(unique.values());

        // 3) bulk upsert（用 orderNo 做匹配条件）
        BulkOperations ops = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Order_Doc.class);

        for (Order_Doc doc : uniqueDocs) {
            Query q = new Query(where("orderNo").is(doc.getOrderNo()));
            Update u = buildUpdateFromDocument(doc);
            ops.upsert(q, u);
        }

        ops.execute();
        return uniqueDocs.size();
    }

    private String orderCollection(String shopKey) {
        if (shopKey == null || shopKey.isBlank()) throw new IllegalArgumentException("shopKey is required");
        // 强烈建议限制字符，避免注入/脏命名
        //if (!shopKey.matches("[a-zA-Z0-9_a-zA-Z0-9]+")) throw new IllegalArgumentException("invalid shopKey");
        return "orders_" + shopKey; // orders_shop01
    }

    /**
     * 将 Document 转成 Update（避免手写每个字段的 update）
     * 说明：这里列出常用字段，你可以按你的 OrderDocument 字段补齐。
     */
    private Update buildUpdateFromDocument(Order_Doc doc) {
        Update u = new Update();

        // 不要 set id（_id），Mongo 不允许更新 _id
        // u.set("_id", doc.getId()); // 禁止

        u.set("orderNo", doc.getOrderNo());

        u.set("payTime", doc.getPayTime());
        u.set("orderStatus", doc.getOrderStatus());
        u.set("freight", doc.getFreight());
        u.set("promotionDiscount", doc.getPromotionDiscount());
        u.set("subOrderTotalPrice", doc.getSubOrderTotalPrice());
        u.set("shipTime", doc.getShipTime());
        u.set("refundInitiateTime", doc.getRefundInitiateTime());
        u.set("createTime", doc.getCreateTime());
        u.set("updateTime", doc.getUpdateTime());

        u.set("distributionType", doc.getDistributionType());
        u.set("receiveTime", doc.getReceiveTime());
        u.set("payType", doc.getPayType());

        u.set("ksSkuId", doc.getKsSkuId());
        u.set("productId", doc.getProductId());
        u.set("productName", doc.getProductName());
        u.set("skuQuantity", doc.getSkuQuantity());

        u.set("unitPriceBeforePromoSnapshot", doc.getUnitPriceBeforePromoSnapshot());
        u.set("discountAmount", doc.getDiscountAmount());
        u.set("unitPriceSnapshot", doc.getUnitPriceSnapshot());

        // 退款（按你最新优化：latestRefund/hasRefund 或者 orderRefundList）
        u.set("hasRefund", doc.getHasRefund());
        u.set("RefundStatus", doc.getRefundStatus());
        u.set("RefundHandlingWay",doc.getHandlingWay());

        return u;
    }

    public OrderList[] extractOrderList(OpenOrderCursorListResponse src) {
        if (src == null || src.getData() == null || src.getData().getOrderList() == null) {
            return new OrderList[0];
        }
        return src.getData().getOrderList();
    }

    /**
     * 从响应中提取下一页 cursor（常见命名：data.cursor / data.nextCursor 等）
     * 你拿到真实 JSON 后，把这里改成准确的 getter 路径即可。
     */
    public String extractNextCursor(OpenOrderCursorListResponse resp) {
        if (resp == null) return null;

        try {

            // 常见结构示例（需你按实际 SDK getter 调整）：
            // return resp.getData().getCursor();
            // 或 return resp.getData().getNextCursor();

            // 这里先返回 null，避免你误用导致死循环
            return resp.getData().getCursor();
        } catch (Exception ignore) {
            return null;
        }
    }
}

