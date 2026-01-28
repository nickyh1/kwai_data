package com.example.kwai_data.data;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * 店铺认证信息 MongoDB 文档
 * 用于持久化 token，支持应用重启后恢复
 */
@Document(collection = "shop_auth")
public class ShopAuthDoc {

    @Id
    private String shopKey;

    private String accessToken;

    private String refreshToken;

    /** token 过期时间 */
    private Instant expiresAt;

    /** 最后刷新时间 */
    private Instant lastRefreshedAt;

    public ShopAuthDoc() {}

    public ShopAuthDoc(String shopKey, String accessToken, String refreshToken) {
        this.shopKey = shopKey;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.lastRefreshedAt = Instant.now();
    }

    public String getShopKey() { return shopKey; }
    public void setShopKey(String shopKey) { this.shopKey = shopKey; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getLastRefreshedAt() { return lastRefreshedAt; }
    public void setLastRefreshedAt(Instant lastRefreshedAt) { this.lastRefreshedAt = lastRefreshedAt; }
}
