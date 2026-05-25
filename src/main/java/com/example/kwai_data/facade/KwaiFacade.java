package com.example.kwai_data.facade;

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
import com.kuaishou.merchant.open.api.domain.order.OrderList;
import com.kuaishou.merchant.open.api.response.funds.OpenFundsCenterWirhdrawRecordListResponse;
import com.kuaishou.merchant.open.api.response.funds.OpenFundsFinancialStatementListResponse;
import com.kuaishou.merchant.open.api.response.user.OpenUserSellerGetResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class KwaiFacade {

    private final sellerInfoService sellerInfoService;
    private final FundsAccountInfoService fundsAccountInfoService;
    private final OrderCursorListService orderCursorListService;
    private final TimeRangeProvider timeRangeProvider;
    private final JdbcTemplate jdbcTemplate;
    private final ShopAuthRegistry registry;
    private final KwaiClientFactory clientFactory;
    private final UnsettledOrderService unsettledOrderService;

    /** 全量同步：清空后重新拉取过去一个月数据 */
    public void syncAll() throws Exception {

        clearSellerInfo();

        for (var e : registry.asMap().entrySet()) {
            String shopKey = e.getKey();
            ShopAuth auth  = e.getValue();
            System.out.println("同步店铺: " + shopKey);

            // 清空该店铺的历史数据（全量模式）
            clearShopData("unsettled_orders", shopKey);
            clearShopData("orders",           shopKey);
            clearShopData("withdraw_records", shopKey);

            AccessTokenKsMerchantClient client = clientFactory.getClient();

            // 拉取并写入卖家信息
            OpenUserSellerGetResponse sellerResp = sellerInfoService.fetchSellerInfo(client, auth.getAccessToken());
            sellerInfo totalData = sellerInfo.builder()
                    .shopId(sellerResp.getData().getSellerId())
                    .shopName(sellerResp.getData().getName())
                    .accountBalance(fundsAccountInfoService.getBalance(client, auth.getAccessToken()))
                    .build();
            sellerInfoService.sellerInfoupsert(totalData);

            // 拉取并写入提现记录
            OpenFundsCenterWirhdrawRecordListResponse wResp =
                    sellerInfoService.fetchWirhdrawRecordInfo(client, auth.getAccessToken());
            sellerInfoService.WRecordupsert(wResp, shopKey);

            // 拉取未结算订单
            syncRange(shopKey);

            // 拉取订单（按月份分片）
            LastMonthStartToTodayStart(shopKey);
        }
    }

    /** 清空 seller_info 表 */
    private void clearSellerInfo() {
        jdbcTemplate.update("DELETE FROM seller_info");
    }

    /** 按 shop_key 清空指定表 */
    private void clearShopData(String table, String shopKey) {
        jdbcTemplate.update("DELETE FROM " + table + " WHERE shop_key = ?", shopKey);
    }

    /** 将过去一个月按 7 天分片拉取订单 */
    public void LastMonthStartToTodayStart(String shopkey) throws Exception {
        TimeRangeMillis range = timeRangeProvider.lastMonthStartToNow();
        List<TimeRangeMillis> ranges = timeRangeProvider.splitByDays(range, 7, EndMsMode.INCLUSIVE);
        for (TimeRangeMillis r : ranges) {
            syncOrdersInRange(r, shopkey);
        }
    }

    /** 拉取指定时间段内的订单并批量写入 */
    public void syncOrdersInRange(TimeRangeMillis r, String shopkey) throws Exception {
        String cursor  = null;
        int pageSize   = 50;
        int maxPages   = 500;   // 防止死循环

        for (int i = 0; i < maxPages; i++) {
            var resp = orderCursorListService.fetchOnce(
                    shopkey, 1, pageSize, 0, 1,
                    r.getStartMs(), r.getEndMs(),
                    1, cursor
            );

            System.out.println(TimeUtil.toZonedDateTime(r.getStartMs(), "Asia/Shanghai")
                    + " ~ " + TimeUtil.toZonedDateTime(r.getEndMs(), "Asia/Shanghai")
                    + "  第 " + (i + 1) + " 页");

            Thread.sleep(1000);

            OrderList[] orderlist = orderCursorListService.extractOrderList(resp);
            if (orderlist.length == 0) break;

            // 整页转 DTO → 一次 batch INSERT
            List<OrderDto> dtos = Arrays.stream(orderlist)
                    .filter(Objects::nonNull)
                    .map(OrderDto::new)
                    .collect(Collectors.toList());

            int saved = orderCursorListService.upsertBatch(dtos, shopkey);
            System.out.println("  批量写入 " + saved + " 条（本页共 " + orderlist.length + " 条）");

            String nextCursor = orderCursorListService.extractNextCursor(resp);
            if (nextCursor == null || nextCursor.isBlank() || nextCursor.equals(cursor)) break;
            cursor = nextCursor;
        }
    }

    /** 拉取未结算订单（游标翻页） */
    public void syncRange(String shopkey) throws Exception {
        int maxPages = 500;
        TimeRangeMillis range = timeRangeProvider.lastMonthStartToYesterdayEndInclusive();
        String cursor = "";
        int pageCount = 0;

        while (pageCount < maxPages) {
            pageCount++;

            OpenFundsFinancialStatementListResponse resp = unsettledOrderService.fetchOnce(
                    shopkey, range.getStartMs(), range.getEndMs(), cursor
            );
            System.out.println("未结算订单第 " + pageCount + " 页, cursor=" + cursor);

            List<UnsettledOrderDto> records = unsettledOrderService.parseRecords(resp);
            if (records == null || records.isEmpty()) break;

            for (UnsettledOrderDto dto : records) {
                Instant createTime = unsettledOrderService.fetchOrderCreateTime(shopkey, dto.getOid());
                dto.setCreateTime(createTime);
                unsettledOrderService.upsertOne(dto, shopkey);
            }

            String nextCursor = unsettledOrderService.extractNextCursor(resp);
            if (nextCursor == null || nextCursor.isBlank()
                    || nextCursor.equals("no_more") || nextCursor.equals(cursor)) {
                break;
            }
            cursor = nextCursor;
        }
    }
}
