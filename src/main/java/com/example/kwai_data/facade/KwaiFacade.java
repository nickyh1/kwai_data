package com.example.kwai_data.facade;

// package com.example.kwai_data.facade;

import com.example.kwai_data.client.KwaiClientFactory;
import com.example.kwai_data.config.EndMsMode;
import com.example.kwai_data.config.TimeRangeMillis;
import com.example.kwai_data.config.TimeRangeProvider;
import com.example.kwai_data.data.OrderDto;
import com.example.kwai_data.data.ShopAuth;
import com.example.kwai_data.data.UnsettledOrderDto;
import com.example.kwai_data.data.sellerInfo;
import com.example.kwai_data.repository.ShopAuthRegistry;
import com.example.kwai_data.service.FundsAccountInfoService;
import com.example.kwai_data.service.OrderCursorListService;
import com.example.kwai_data.service.UnsettledOrderService;
import com.example.kwai_data.service.sellerInfoService;
import com.example.kwai_data.util.TimeUtil;
import com.kuaishou.merchant.open.api.client.AccessTokenKsMerchantClient;
import com.kuaishou.merchant.open.api.common.utils.GsonUtils;
import com.kuaishou.merchant.open.api.domain.order.OrderList;
import com.kuaishou.merchant.open.api.response.funds.OpenFundsCenterWirhdrawRecordListResponse;
import com.kuaishou.merchant.open.api.response.funds.OpenFundsFinancialStatementListResponse;
import com.kuaishou.merchant.open.api.response.user.OpenUserSellerGetResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component
@RequiredArgsConstructor
public class KwaiFacade {

    private final sellerInfoService sellerInfoService;
    private final FundsAccountInfoService fundsAccountInfoService;
    private final OrderCursorListService orderCursorListService;
    private final TimeRangeProvider timeRangeProvider;
    private final MongoTemplate erpMongoTemplate;
    private final ShopAuthRegistry registry;
    private final KwaiClientFactory clientFactory;
    private final UnsettledOrderService unsettledOrderService;
    // 预留：后续继续注入
    // private final OrderService orderService;
    // private final FundsService fundsService;

    /** 示例：对外提供“获取店铺信息（JSON字符串）” */
//    public String getSellerInfoJson() throws Exception {
//        return GsonUtils.toJSON(sellerInfoService.fetchSellerInfo());
//
//    }
//
//    /** 示例：对外提供“获取店铺信息（对象）” */
//    public Object getSellerInfo() throws Exception {
//        return sellerInfoService.fetchSellerInfo();
//    }

    /** 预留：后续可做“全量同步”编排 */
    public void syncAll() throws Exception {
        // 例如：

        clearSellerCollectionsKeepIndexes();

        for (var e : registry.asMap().entrySet()) {

            String shopKey = e.getKey();
            ShopAuth auth = e.getValue();
            System.out.println(shopKey);

            clearShopCollection("Unsetllement_",shopKey);
            clearShopCollection("orders_",shopKey);
            clearShopCollection("Withdraw_",shopKey);


            AccessTokenKsMerchantClient client = clientFactory.getClient();

            OpenUserSellerGetResponse SellerInforesp = sellerInfoService.fetchSellerInfo(client, auth.getAccessToken());
            //System.out.println(SellerInforesp.getMsg());
            sellerInfo totalData = sellerInfo.builder()
                    .shopId(SellerInforesp.getData().getSellerId())
                    .shopName(SellerInforesp.getData().getName())
                    .accountBalance(fundsAccountInfoService.getBalance(client, auth.getAccessToken()))
                    .build();

            sellerInfoService.sellerInfoupsert(totalData);
            //获取数据

            OpenFundsCenterWirhdrawRecordListResponse WRecord_resp = sellerInfoService.fetchWirhdrawRecordInfo(client, auth.getAccessToken());
            sellerInfoService.WRecordupsert(WRecord_resp, shopKey);


            syncRange(shopKey);

            LastMonthStartToTodayStart(shopKey);
        }


        //totalData.setAccountBalance(fundsAccountInfoService.getAccountInfo().getData().get);
        //System.out.println(totalData.getShopId());
        // var seller  sellerInfoService.getSellerInfo();
        // var orders = orderService.queryOrders(...);
        // var funds = fundsService.queryBills(...);
        // 统一落库 / 汇总计算 / 发送消息等
    }

    public void clearSellerCollectionsKeepIndexes() {
        Query all = new Query(); // 空查询匹配全部
        erpMongoTemplate.remove(all, "seller_info");

    }

    private void clearShopCollection(String collectionPrefix, String shopKey) {
        Query all = new Query();
        erpMongoTemplate.remove(all,collectionPrefix  + shopKey);
    }



    public void LastMonthStartToTodayStart(String shopkey) throws Exception {
        TimeRangeMillis range = timeRangeProvider.lastMonthStartToNow();
        //System.out.println(range.getStartMs()+" "+range.getEndMs());
        List<TimeRangeMillis> ranges = timeRangeProvider.splitByDays(range, 7, EndMsMode.INCLUSIVE);

        for (TimeRangeMillis r : ranges) {
            syncOrdersInRange(r, shopkey);
        }
    }

    public void syncOrdersInRange(TimeRangeMillis r, String shopkey) throws Exception {
        //var range = timeRangeProvider.yesterdayToTodayStart();

        String cursor = null;
        int pageSize = 50;
        int maxPages = 500;          // 防止死循环



        for (int i = 0; i < maxPages; i++) {
            var resp = orderCursorListService.fetchOnce(
                    shopkey,1, pageSize, 0, 1,
                    r.getStartMs(), r.getEndMs(),
                    1, cursor

            );

            System.out.println("\n");
            System.out.println(TimeUtil.toZonedDateTime(r.getStartMs(), "Asia/Shanghai")
                    +" "+TimeUtil.toZonedDateTime(r.getEndMs(), "Asia/Shanghai"));
            Thread.sleep(500);
            // 1) 提取订单列表（按实际结构改）
            OrderList orderlist[] = orderCursorListService.extractOrderList(resp);

            for (OrderList item : orderlist) {
                if (item == null) continue;
                OrderDto dto = new OrderDto(item);
                orderCursorListService.upsertOne(dto, shopkey);
                //System.out.println(dto.getCreateTime());

            }



            // 4) 推进游标（按实际结构改）
            String nextCursor = orderCursorListService.extractNextCursor(resp);
            if (nextCursor == null || nextCursor.isBlank() || nextCursor.equals(cursor)) break;
            cursor = nextCursor;
        }


    }



    public void syncRange(String shopkey) throws Exception{


        int maxPages = 500;

        TimeRangeMillis range = timeRangeProvider.lastMonthStartToYesterdayEndInclusive();
        String cursor = "";

        int pageCount = 0;

        while (pageCount < maxPages) {
            pageCount++;

            OpenFundsFinancialStatementListResponse resp = unsettledOrderService.fetchOnce(
                    shopkey,
                    range.getStartMs(),
                    range.getEndMs(),
                    cursor
            );
            Thread.sleep(100);
            System.out.println(pageCount+" "+cursor);
            // 从 response 转 DTO records（如果你想把这段放回 service，也可以）
            List<UnsettledOrderDto> records = unsettledOrderService.parseRecords(resp);

            if (records == null || records.isEmpty()) {
                // 没有数据：结束
                break;
            }

            for (UnsettledOrderDto dto : records) {
                // 通过订单详情 API 获取 createTime
                Thread.sleep(300);
                Instant createTime = unsettledOrderService.fetchOrderCreateTime(shopkey, dto.getOid());
                dto.setCreateTime(createTime);

                unsettledOrderService.upsertOne(dto, shopkey);
            }

            // 翻页 cursor
            String nextCursor = unsettledOrderService.extractNextCursor(resp);
            if (nextCursor == null || nextCursor.isBlank() || nextCursor.equals("no_more"))  {
                break;
            }

            // 防止 cursor 不变化导致死循环
            if (nextCursor.equals(cursor)) {
                break;
            }


            cursor = nextCursor;
        }

    }





}

