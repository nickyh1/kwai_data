package com.example.kwai_data.facade;

// package com.example.kwai_data.facade;

import com.example.kwai_data.config.TimeRangeProvider;
import com.example.kwai_data.data.OrderDto;
import com.example.kwai_data.data.sellerInfo;
import com.example.kwai_data.service.FundsAccountInfoService;
import com.example.kwai_data.service.OrderCursorListService;
import com.example.kwai_data.service.sellerInfoService;
import com.kuaishou.merchant.open.api.common.utils.GsonUtils;
import com.kuaishou.merchant.open.api.domain.order.OrderList;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.awt.*;

@Component
@RequiredArgsConstructor
public class KwaiFacade {

    private final sellerInfoService sellerInfoService;
    private final FundsAccountInfoService fundsAccountInfoService;
    private final OrderCursorListService orderCursorListService;
    private final TimeRangeProvider timeRangeProvider;
    // 预留：后续继续注入
    // private final OrderService orderService;
    // private final FundsService fundsService;

    /** 示例：对外提供“获取店铺信息（JSON字符串）” */
    public String getSellerInfoJson() throws Exception {
        return GsonUtils.toJSON(sellerInfoService.fetchSellerInfo());

    }

    /** 示例：对外提供“获取店铺信息（对象）” */
    public Object getSellerInfo() throws Exception {
        return sellerInfoService.fetchSellerInfo();
    }

    /** 预留：后续可做“全量同步”编排 */
    public void syncAll() throws Exception {
        // 例如：
        sellerInfo totalData = new sellerInfo();
        System.out.println(fundsAccountInfoService.getBalance());
        //System.out.println(sellerInfoService.fetchSellerInfo().getMsg());
        totalData.setShopId(sellerInfoService.fetchSellerInfo().getData().getSellerId());
        totalData.setShopName(sellerInfoService.fetchSellerInfo().getData().getName());
        totalData.setAccountBalance(fundsAccountInfoService.getBalance());//设置成函数

//        String cursor = null;
//        int pageSize = 50;
//        var range = timeRangeProvider.yesterdayToTodayStart();
//        var resp = orderCursorListService.fetchOnce(
//                1, pageSize, 0, 1,
//                range.getStartMs(), range.getEndMs(),
//                1, cursor
//        );


        syncLast7Days();

        sellerInfoService.upsert(totalData);


        //totalData.setAccountBalance(fundsAccountInfoService.getAccountInfo().getData().get);
        //System.out.println(totalData.getShopId());
        // var seller  sellerInfoService.getSellerInfo();
        // var orders = orderService.queryOrders(...);
        // var funds = fundsService.queryBills(...);
        // 统一落库 / 汇总计算 / 发送消息等
    }
    public void syncLast7Days() throws Exception {
        var range = timeRangeProvider.yesterdayToTodayStart();

        String cursor = null;
        int pageSize = 50;
        int maxPages = 500;          // 防止死循环
        int batchSize = 200;         // saveAll 分批大小（可调）


        for (int i = 0; i < maxPages; i++) {
            var resp = orderCursorListService.fetchOnce(
                    1, pageSize, 0, 1,
                    range.getStartMs(), range.getEndMs(),
                    1, cursor
            );
            System.out.println("1");
            //Thread.sleep(1000);
            // 1) 提取订单列表（按实际结构改）
            OrderList orderlist[] = orderCursorListService.extractOrderList(resp);

            for (OrderList item : orderlist) {
                if (item == null) continue;
                OrderDto dto = new OrderDto(item);
                orderCursorListService.upsertOne(dto);
                System.out.println(dto.getCreateTime());

            }



            // 4) 推进游标（按实际结构改）
            String nextCursor = orderCursorListService.extractNextCursor(resp);
            if (nextCursor == null || nextCursor.isBlank() || nextCursor.equals(cursor)) break;
            cursor = nextCursor;
        }

    }



}

