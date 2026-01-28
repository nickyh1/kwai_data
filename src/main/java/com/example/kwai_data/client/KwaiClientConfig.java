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

    // ShopAuthRegistry 已标注 @Component，由 Spring 自动管理，无需手动创建 Bean

    @Bean
    public KwaiClientFactory kwaiClientFactory(KwaiProperties props) {
        return new KwaiClientFactory(props);
    }
}


