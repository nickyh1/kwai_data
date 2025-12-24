package com.example.kwai_data.service;
//订单

import com.example.kwai_data.config.KwaiProperties;
import com.kuaishou.merchant.open.api.client.AccessTokenKsMerchantClient;
import com.kuaishou.merchant.open.api.request.funds.OpenFundsFinancialSettledBillDetailRequest;
import com.kuaishou.merchant.open.api.response.funds.OpenFundsFinancialSettledBillDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class BillDetailService {

    private final AccessTokenKsMerchantClient client;
    private final KwaiProperties props;

    /**
     * 查询结算账单明细（毫秒时间戳）
     *
     * @param settlementStartTimeMs 结算开始时间（毫秒时间戳）
     * @param settlementEndTimeMs   结算结束时间（毫秒时间戳）
     * @param size                 单次拉取条数（建议按接口上限传）
     */
    public OpenFundsFinancialSettledBillDetailResponse fetchSettledBillDetail(
            long settlementStartTimeMs,
            long settlementEndTimeMs,
            int size
    ) throws Exception {

        OpenFundsFinancialSettledBillDetailRequest request = new OpenFundsFinancialSettledBillDetailRequest();
        request.setAccessToken(props.getAccessToken02());
        request.setApiMethodVersion(1L);

        request.setSettlementStartTime(settlementStartTimeMs);
        request.setSettlementEndTime(settlementEndTimeMs);
        request.setSize(size);

        return client.execute(request);
    }

    /**
     * 查询结算账单明细（Instant -> 毫秒）
     */
    public OpenFundsFinancialSettledBillDetailResponse fetchSettledBillDetail(
            Instant settlementStart,
            Instant settlementEnd,
            int size
    ) throws Exception {
        return fetchSettledBillDetail(settlementStart.toEpochMilli(), settlementEnd.toEpochMilli(), size);
    }
}

