package com.example.kwai_data.client;

import com.example.kwai_data.config.KwaiProperties;
import com.example.kwai_data.domain.ShopAuth;
import com.kuaishou.merchant.open.api.client.AccessTokenKsMerchantClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class KwaiClientFactory {

    private final KwaiProperties props;
    //private final Map<String, AccessTokenKsMerchantClient> cache = new ConcurrentHashMap<>();

    public KwaiClientFactory(KwaiProperties props) {
        this.props = props;
    }

    public AccessTokenKsMerchantClient getClient() {
        return
                new AccessTokenKsMerchantClient(
                        props.getBaseUrl(),
                        props.getAppKey(),
                        props.getSignSecret()
                );
    }
}
