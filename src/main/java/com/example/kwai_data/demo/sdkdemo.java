//package com.example.kwai_data.demo;
//
//
//
//import com.kuaishou.merchant.open.api.client.AccessTokenKsMerchantClient;
//import com.kuaishou.merchant.open.api.common.utils.GsonUtils;
//import com.kuaishou.merchant.open.api.request.user.OpenUserSellerGetRequest;
//import com.kuaishou.merchant.open.api.response.user.OpenUserSellerGetResponse;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.stereotype.Component;
////商家信息
//@Component
//public class sdkdemo implements CommandLineRunner {
//
//    @Override
//    public void run(String... args) throws Exception {
//
//        String url = "https://openapi.kwaixiaodian.com";
//        String appKey = "ks705376292653020303";
//        String signSecret = "3f3350b6b21c54ffa08be7bfb75c5611";
//        String accessToken = "ChFvYXV0aC5hY2Nlc3NUb2tlbhJAXkC2Xd5uTpUpvqjjFqHW1VRHej1zBxlwYkZmGuyUiYZcfk_u7yO7ne07C7o5SqJk2rIIm7uHNIqjL9OQndr4ABoSzj-H-jvzSB-XTbR7Zv-PlxPEIiCPaVM9mMt5Ljo3MCRYSLnRgiiYSoF-sgi5e8kR2m9fKCgFMAE";
//
//        AccessTokenKsMerchantClient client = new AccessTokenKsMerchantClient(url, appKey, signSecret);
//
//        OpenUserSellerGetRequest request = new OpenUserSellerGetRequest();
//        request.setAccessToken(accessToken);
//        request.setApiMethodVersion(1L);
//
//        OpenUserSellerGetResponse response = client.execute(request);
//
//        System.out.println(GsonUtils.toJSON(response));
//    }
//}
//
