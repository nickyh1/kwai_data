package com.example.kwai_data.facade;

// package com.example.kwai_data.facade;

import com.example.kwai_data.data.sellerInfo;
import com.example.kwai_data.service.FundsAccountInfoService;
import com.example.kwai_data.service.sellerInfoService;
import com.kuaishou.merchant.open.api.common.utils.GsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.awt.*;

@Component
@RequiredArgsConstructor
public class KwaiFacade {

    private final sellerInfoService sellerInfoService;
    private final FundsAccountInfoService fundsAccountInfoService;
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
        //System.out.println(fundsAccountInfoService.getBalance());

        totalData.setShopId(sellerInfoService.fetchSellerInfo().getData().getSellerId());
        totalData.setShopName(sellerInfoService.fetchSellerInfo().getData().getName());
        totalData.setAccountBalance(fundsAccountInfoService.getBalance());


        //totalData.setAccountBalance(fundsAccountInfoService.getAccountInfo().getData().get);
        //System.out.println(totalData.getShopId());
        // var seller  sellerInfoService.getSellerInfo();
        // var orders = orderService.queryOrders(...);
        // var funds = fundsService.queryBills(...);
        // 统一落库 / 汇总计算 / 发送消息等
    }

}

