package com.example.kwai_data.service;

import com.example.kwai_data.config.KwaiProperties;
import com.example.kwai_data.domain.ShopAuth;
import com.example.kwai_data.repository.ShopAuthRegistry;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Token 刷新服务
 *
 * 调用快手 OAuth2 refresh_token 接口刷新 access_token 和 refresh_token
 */
@Service
public class TokenRefreshService {

    private static final Logger log = LoggerFactory.getLogger(TokenRefreshService.class);

    /** 快手 OAuth2 刷新 token 接口 */
    private static final String REFRESH_TOKEN_URL = "https://open.kwaixiaodian.com/oauth2/refresh_token";

    /** 最大重试次数 */
    private static final int MAX_RETRIES = 3;

    private final KwaiProperties props;
    private final ShopAuthRegistry registry;
    private final RestTemplate restTemplate;

    public TokenRefreshService(KwaiProperties props, ShopAuthRegistry registry) {
        this.props = props;
        this.registry = registry;
        this.restTemplate = new RestTemplate();
    }

    /**
     * 刷新单个店铺的 token
     *
     * @param shopKey 店铺标识
     * @return 刷新结果
     */
    public TokenRefreshResult refresh(String shopKey) {
        ShopAuth auth = registry.get(shopKey);
        if (auth == null) {
            return TokenRefreshResult.failure(shopKey, "店铺不存在");
        }

        String refreshToken = auth.getRefreshToken();
        if (refreshToken == null || refreshToken.isBlank()) {
            return TokenRefreshResult.failure(shopKey, "refresh_token 未配置");
        }

        // 带重试的刷新逻辑
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                TokenResponse response = doRefresh(refreshToken);

                if (response.getResult() == 1) {
                    // 刷新成功，更新 Registry
                    registry.updateToken(
                            shopKey,
                            response.getAccessToken(),
                            response.getRefreshToken(),
                            response.getExpiresIn()
                    );

                    log.info("店铺 {} token 刷新成功，有效期 {} 秒", shopKey, response.getExpiresIn());
                    return TokenRefreshResult.success(shopKey, response.getExpiresIn());
                } else {
                    // API 返回错误
                    String errorMsg = String.format("API错误: result=%d, error=%s, msg=%s",
                            response.getResult(), response.getErrorCode(), response.getErrorMsg());
                    log.warn("店铺 {} token 刷新失败: {}", shopKey, errorMsg);
                    return TokenRefreshResult.failure(shopKey, errorMsg);
                }

            } catch (Exception e) {
                lastException = e;
                log.warn("店铺 {} token 刷新第 {} 次尝试失败: {}", shopKey, attempt, e.getMessage());

                if (attempt < MAX_RETRIES) {
                    // 指数退避
                    try {
                        Thread.sleep((long) Math.pow(2, attempt) * 1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        String errorMsg = "重试 " + MAX_RETRIES + " 次后仍失败: " +
                (lastException != null ? lastException.getMessage() : "未知错误");
        log.error("店铺 {} token 刷新最终失败: {}", shopKey, errorMsg);
        return TokenRefreshResult.failure(shopKey, errorMsg);
    }

    /**
     * 刷新所有店铺的 token
     *
     * @return 所有店铺的刷新结果
     */
    public List<TokenRefreshResult> refreshAll() {
        List<TokenRefreshResult> results = new ArrayList<>();

        for (String shopKey : registry.shopKeys()) {
            TokenRefreshResult result = refresh(shopKey);
            results.add(result);
        }

        return results;
    }

    /**
     * 执行实际的刷新请求
     */
    private TokenResponse doRefresh(String refreshToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("app_id", props.getAppKey());
        params.add("app_secret", props.getAppSecret());
        params.add("refresh_token", refreshToken);
        params.add("grant_type", "refresh_token");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        ResponseEntity<TokenResponse> response = restTemplate.exchange(
                REFRESH_TOKEN_URL,
                HttpMethod.POST,
                request,
                TokenResponse.class
        );

        return response.getBody();
    }

    /**
     * Token 刷新结果
     */
    public static class TokenRefreshResult {
        private final String shopKey;
        private final boolean success;
        private final long expiresIn;
        private final String errorMsg;

        private TokenRefreshResult(String shopKey, boolean success, long expiresIn, String errorMsg) {
            this.shopKey = shopKey;
            this.success = success;
            this.expiresIn = expiresIn;
            this.errorMsg = errorMsg;
        }

        public static TokenRefreshResult success(String shopKey, long expiresIn) {
            return new TokenRefreshResult(shopKey, true, expiresIn, null);
        }

        public static TokenRefreshResult failure(String shopKey, String errorMsg) {
            return new TokenRefreshResult(shopKey, false, 0, errorMsg);
        }

        public String getShopKey() { return shopKey; }
        public boolean isSuccess() { return success; }
        public long getExpiresIn() { return expiresIn; }
        public String getErrorMsg() { return errorMsg; }
    }

    /**
     * 快手 OAuth2 刷新接口响应
     */
    private static class TokenResponse {
        private int result;

        @JsonProperty("access_token")
        private String accessToken;

        @JsonProperty("refresh_token")
        private String refreshToken;

        @JsonProperty("expires_in")
        private long expiresIn;

        @JsonProperty("error")
        private String errorCode;

        @JsonProperty("error_msg")
        private String errorMsg;

        public int getResult() { return result; }
        public void setResult(int result) { this.result = result; }

        public String getAccessToken() { return accessToken; }
        public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

        public String getRefreshToken() { return refreshToken; }
        public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

        public long getExpiresIn() { return expiresIn; }
        public void setExpiresIn(long expiresIn) { this.expiresIn = expiresIn; }

        public String getErrorCode() { return errorCode; }
        public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

        public String getErrorMsg() { return errorMsg; }
        public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
    }
}
