package com.example.kwai_data.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "kwai.time")
public class KwaiTimeProperties {
    private String zone = "Asia/Shanghai";
    private int lookbackDays = 7;
}

