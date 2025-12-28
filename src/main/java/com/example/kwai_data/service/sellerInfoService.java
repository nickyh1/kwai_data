package com.example.kwai_data.service;


import com.example.kwai_data.config.KwaiProperties;
import com.example.kwai_data.data.SellerInfo_Doc;
import com.example.kwai_data.data.sellerInfo;
import com.example.kwai_data.dto.KsResponse;

import com.example.kwai_data.mapper.SellerInfoMapper;
import com.example.kwai_data.repository.SellerInfoRepository;
import com.kuaishou.merchant.open.api.client.AccessTokenKsMerchantClient;
import com.kuaishou.merchant.open.api.request.user.OpenUserSellerGetRequest;
import com.kuaishou.merchant.open.api.response.user.OpenUserSellerGetResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

//商家信息
@Service
@RequiredArgsConstructor
public class sellerInfoService {

    private final AccessTokenKsMerchantClient client;
    private final KwaiProperties props;
    private final SellerInfoRepository sellerInfoRepository;

    public OpenUserSellerGetResponse fetchSellerInfo() throws Exception {
        OpenUserSellerGetRequest request = new OpenUserSellerGetRequest();
        request.setAccessToken(props.getAccessToken02());
        request.setApiMethodVersion(1L);
        return client.execute(request);
    }

    public SellerInfo_Doc upsert(sellerInfo dto) {

        SellerInfo_Doc info = SellerInfoMapper.toEntity(dto);

        info.setUpdateTime(Instant.now());

        return sellerInfoRepository.findByShopId(info.getShopId())
                .map(existing -> {
                    existing.setShopName(info.getShopName());
                    existing.setAccountBalance(info.getAccountBalance());
                    //existing.setTotalWithdrawn(info.getTotalWithdrawn());
                    existing.setUpdateTime(info.getUpdateTime());
                    return sellerInfoRepository.save(existing);
                })
                .orElseGet(() -> sellerInfoRepository.save(info));
    }


}



