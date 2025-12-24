package com.example.kwai_data.service;
//余额

import com.example.kwai_data.config.KwaiProperties;
import com.kuaishou.merchant.open.api.client.AccessTokenKsMerchantClient;
import com.kuaishou.merchant.open.api.domain.funds.AccountInfoDto;
import com.kuaishou.merchant.open.api.request.funds.OpenFundsCenterAccountInfoRequest;
import com.kuaishou.merchant.open.api.response.funds.OpenFundsCenterAccountInfoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FundsAccountInfoService {

    private final AccessTokenKsMerchantClient client;
    private final KwaiProperties props;

    /**
     * 查询资金中心账户信息（余额/可用等，具体以返回字段为准）
     */
    public OpenFundsCenterAccountInfoResponse fetchAccountInfo() throws Exception {
        OpenFundsCenterAccountInfoRequest request = new OpenFundsCenterAccountInfoRequest();
        request.setAccessToken(props.getAccessToken02());
        request.setApiMethodVersion(1L);
        return client.execute(request);
    }

    public Long getBalance() throws Exception {
        OpenFundsCenterAccountInfoResponse resp = fetchAccountInfo();
        List<AccountInfoDto> data = resp.getData();

        if (data != null) {
            for (AccountInfoDto item : data) {
                Long balance = item.getBalance();

                if (balance != null && balance != 0L) {
                    return balance;
                }
            }
        }
        return 0L; // 兜底：没找到非0余额
    }

}

