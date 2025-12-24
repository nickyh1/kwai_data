package com.example.kwai_data.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "kwai")
public class KwaiProperties {
    private String url01;
    private String appKey02;
    private String signSecret02;
    private String accessToken02;
}
