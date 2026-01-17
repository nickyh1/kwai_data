package com.example.kwai_data.client;

// package com.example.kwai_data.config;


import com.example.kwai_data.config.KwaiProperties;
import com.example.kwai_data.repository.ShopAuthRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(KwaiProperties.class)
public class KwaiClientConfig {

    @Bean
    public ShopAuthRegistry shopAuthRegistry(KwaiProperties props) {
        return new ShopAuthRegistry(props);
    }

    @Bean
    public KwaiClientFactory kwaiClientFactory(KwaiProperties props) {
        return new KwaiClientFactory(props);
    }
}


