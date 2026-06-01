package com.example.kwai_data.domain;

/**
 * 店铺认证信息
 */
public class ShopAuth {
    private String accessToken;
    private String refreshToken;

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
}
