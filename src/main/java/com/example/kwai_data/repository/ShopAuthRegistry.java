package com.example.kwai_data.repository;

// AccessTokenRegistry.java
import com.example.kwai_data.config.KwaiProperties;
import com.example.kwai_data.data.ShopAuth;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.*;


public class ShopAuthRegistry {

    private final KwaiProperties props;
    private Map<String, ShopAuth> shops; // 容器：shopKey -> 三元组

    public ShopAuthRegistry(KwaiProperties props) {
        this.props = props;
    }

    @PostConstruct
    public void init() {
        Map<String, ShopAuth> src = props.getShops();
        if (src == null || src.isEmpty()) {
            throw new IllegalStateException("kwai.shops 未配置或为空");
        }

        // LinkedHashMap 保留 yaml 顺序，方便你“按配置顺序循环”
        LinkedHashMap<String, ShopAuth> tmp = new LinkedHashMap<>();

        for (var e : src.entrySet()) {
            String shopKey = e.getKey();     // 这里就是店铺名称（你 yaml 的 key）
            ShopAuth auth = e.getValue();

            if (shopKey == null || shopKey.isBlank()) {
                throw new IllegalStateException("存在空的店铺 key");
            }
            if (auth == null) {
                throw new IllegalStateException("店铺 " + shopKey + " 的配置为空");
            }
            if (isBlank(auth.getAppKey()) || isBlank(auth.getSignSecret()) || isBlank(auth.getAccessToken())) {
                throw new IllegalStateException("店铺 " + shopKey + " 的 appKey/signSecret/accessToken 存在空值");
            }

            auth.setAppKey(auth.getAppKey().trim());
            auth.setSignSecret(auth.getSignSecret().trim());
            auth.setAccessToken(auth.getAccessToken().trim());

            tmp.put(shopKey, auth);
        }

        this.shops = Collections.unmodifiableMap(tmp);
    }

    public Map<String, ShopAuth> asMap() {
        return shops;
    }

    /** 你需要“循环所有店铺”就用这个 */
    public Set<String> shopKeys() {
        return shops.keySet();
    }

    public Collection<ShopAuth> values() {
        return shops.values();
    }

    public ShopAuth get(String shopKey) {
        return shops.get(shopKey);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
