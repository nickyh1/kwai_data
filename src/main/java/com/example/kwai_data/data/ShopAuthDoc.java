package com.example.kwai_data.data;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "shop_auth")
public class ShopAuthDoc {

    @Id
    @Column(name = "shop_key")
    private String shopKey;

    @Column(name = "access_token", columnDefinition = "TEXT")
    private String accessToken;

    @Column(name = "refresh_token", columnDefinition = "TEXT")
    private String refreshToken;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "last_refreshed_at")
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
