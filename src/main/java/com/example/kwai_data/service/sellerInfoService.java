package com.example.kwai_data.service;


import com.example.kwai_data.config.KwaiProperties;
import com.example.kwai_data.dto.KsResponse;
import com.example.kwai_data.dto.sellerinfo.SellerInfo;
import com.kuaishou.merchant.open.api.client.AccessTokenKsMerchantClient;
import com.kuaishou.merchant.open.api.request.user.OpenUserSellerGetRequest;
import com.kuaishou.merchant.open.api.response.user.OpenUserSellerGetResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
//商家信息
@Service
@RequiredArgsConstructor
public class sellerInfoService {

    private final AccessTokenKsMerchantClient client;
    private final KwaiProperties props;

    public OpenUserSellerGetResponse fetchSellerInfo() throws Exception {
        OpenUserSellerGetRequest request = new OpenUserSellerGetRequest();
        request.setAccessToken(props.getAccessToken02());
        request.setApiMethodVersion(1L);
        return client.execute(request);
    }


}



