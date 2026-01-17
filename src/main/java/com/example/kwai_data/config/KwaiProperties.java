package com.example.kwai_data.config;

import com.example.kwai_data.data.ShopAuth;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;


@ConfigurationProperties(prefix = "kwai")
public class KwaiProperties {
    private String baseUrl;
    private Map<String, ShopAuth> shops = new LinkedHashMap<>();

    public  String getBaseUrl() {
        return baseUrl;
    }
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Map<String, ShopAuth> getShops() { return shops; }

    public void setShops(Map<String, ShopAuth> shops) { this.shops = shops; }
}
