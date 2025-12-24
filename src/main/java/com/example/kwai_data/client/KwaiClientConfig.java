package com.example.kwai_data.client;

// package com.example.kwai_data.config;

import com.example.kwai_data.config.KwaiProperties;
import com.kuaishou.merchant.open.api.client.AccessTokenKsMerchantClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KwaiClientConfig {

    @Bean
    public AccessTokenKsMerchantClient ksMerchantClient(KwaiProperties props) {
        return new AccessTokenKsMerchantClient(props.getUrl01(), props.getAppKey02(), props.getSignSecret02());
    }
}

