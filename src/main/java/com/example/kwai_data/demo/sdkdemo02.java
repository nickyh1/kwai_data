//package com.example.kwai_data.demo;
//
//import com.kuaishou.merchant.open.api.common.utils.GsonUtils;
//import com.kuaishou.merchant.open.api.request.funds.OpenFundsFinancialSettledBillDetailRequest;
//import com.kuaishou.merchant.open.api.response.funds.OpenFundsFinancialSettledBillDetailResponse;
//import com.kuaishou.merchant.open.api.client.AccessTokenKsMerchantClient;
////订单
//public class sdkdemo02 {
//
//    public static void main(String[] args) throws Exception {
//        String url = "https://openapi.kwaixiaodian.com";
//        String appKey = "your appKey";
//        String signSecret = "your app signSecret";
//        String accessToken = "ChFvYXV0aC5hY2Nlc3NUb2tlbhJAd8wo5H3jyak445L_IhtaXzxkhSXLyZnkcdIJukfql90RCDxyvSfAZ_bs4hnWPeU8yIyJnfccQ8Z5mHPJBm7hpxoSU_jn3kMTSVSgDtzIvdb2oXsEIiCOfQe4qvz3A825YPh2gI6DhZZkGVIQcdjV_Q0OqP-rrigFMAE";
//
//        AccessTokenKsMerchantClient client = new AccessTokenKsMerchantClient(url,appKey,signSecret);
//
//        OpenFundsFinancialSettledBillDetailRequest request = new OpenFundsFinancialSettledBillDetailRequest();
//        request.setAccessToken(accessToken);
//        request.setApiMethodVersion(1L);
//
//        request.setSettlementStartTime(1765465200000L);
//        request.setSettlementEndTime(1765551600000L);
//        request.setSize(100);
//
//        OpenFundsFinancialSettledBillDetailResponse response = client.execute(request);
//
//        System.out.println(GsonUtils.toJSON(response));
//    }
//}