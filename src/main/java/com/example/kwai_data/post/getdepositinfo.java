package com.example.kwai_data.post;

import com.kuaishou.merchant.open.api.common.utils.GsonUtils;
import com.kuaishou.merchant.open.api.request.funds.OpenFundsFinancialQueryBillListRequest;
import com.kuaishou.merchant.open.api.response.funds.OpenFundsFinancialQueryBillListResponse;
import com.kuaishou.merchant.open.api.client.AccessTokenKsMerchantClient;

import java.util.ArrayList;
import java.util.List;

public class getdepositinfo {

    public static void main(String[] args) throws Exception {
        String url = "https://openapi.kwaixiaodian.com";
        String appKey = "your appKey";
        String signSecret = "your app signSecret";
        String accessToken = "ChFvYXV0aC5hY2Nlc3NUb2tlbhJAayTkne429rOR_n_N1yCsMG1m9xTeAmn-ASzegTkgldFK599-NlQX-YkuL06PSMf8vkeB6wDlLsywBl6VhnYgIxoSiw1mSBTjSXC8E9eh6DgO295qIiBG5qPe_77jpxnSwwsjj4BRxMVrnWZMK0ylcxy-GQG8sigFMAE";

        AccessTokenKsMerchantClient client = new AccessTokenKsMerchantClient(url,appKey,signSecret);

        OpenFundsFinancialQueryBillListRequest request = new OpenFundsFinancialQueryBillListRequest();
        request.setAccessToken(accessToken);
        request.setApiMethodVersion(1L);

        request.setEndTime(1764518400000L);
        request.setScrollId("0");
        request.setOrderStatus(0);
        request.setStartTime(1765296000000L);
        request.setBillType("7");
        List<String> accountChannel1 = new ArrayList<>();

        OpenFundsFinancialQueryBillListResponse response = client.execute(request);

        System.out.println(GsonUtils.toJSON(response));
    }
}
