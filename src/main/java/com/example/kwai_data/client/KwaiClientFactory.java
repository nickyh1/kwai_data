package com.example.kwai_data.client;

import com.example.kwai_data.config.KwaiProperties;
import com.example.kwai_data.data.ShopAuth;
import com.kuaishou.merchant.open.api.client.AccessTokenKsMerchantClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class KwaiClientFactory {

    private final KwaiProperties props;
    private final Map<String, AccessTokenKsMerchantClient> cache = new ConcurrentHashMap<>();

    public KwaiClientFactory(KwaiProperties props) {
        this.props = props;
    }

    public AccessTokenKsMerchantClient getClient(String shopKey) {
        ShopAuth auth = props.getShops().get(shopKey);
        if (auth == null) throw new IllegalArgumentException("未找到店铺配置: " + shopKey);

        return cache.computeIfAbsent(shopKey, k ->
                new AccessTokenKsMerchantClient(
                        props.getBaseUrl(),
                        auth.getAppKey(),
                        auth.getSignSecret()
                )
        );
    }
}
