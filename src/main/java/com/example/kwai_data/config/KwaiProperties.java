package com.example.kwai_data.config;

import com.example.kwai_data.domain.ShopAuth;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;


@ConfigurationProperties(prefix = "kwai")
public class KwaiProperties {
    private String baseUrl;

    private String appKey;
    private String appSecret;
    private String signSecret;

    private Map<String, ShopAuth> shops = new LinkedHashMap<>();

    public  String getBaseUrl() {
        return baseUrl;
    }
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getAppKey() { return appKey; }
    public void setAppKey(String appKey) { this.appKey = appKey; }
    public String getSignSecret() { return signSecret; }
    public void setSignSecret(String signSecret) { this.signSecret = signSecret; }
    public String getAppSecret() { return appSecret; }
    public void setAppSecret(String appSecret) { this.appSecret = appSecret; }

    public Map<String, ShopAuth> getShops() { return shops; }

    public void setShops(Map<String, ShopAuth> shops) { this.shops = shops; }
}
