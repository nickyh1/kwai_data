package com.example.kwai_data.repository;

import com.example.kwai_data.config.KwaiProperties;
import com.example.kwai_data.data.ShopAuth;
import com.example.kwai_data.data.ShopAuthDoc;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ShopAuthRegistry {

    private static final Logger log = LoggerFactory.getLogger(ShopAuthRegistry.class);

    private final KwaiProperties props;
    private final ShopAuthJpaRepository jpaRepo;

    private final ConcurrentHashMap<String, ShopAuth> shops = new ConcurrentHashMap<>();
    private List<String> orderedKeys = new ArrayList<>();

    public ShopAuthRegistry(KwaiProperties props, ShopAuthJpaRepository jpaRepo) {
        this.props = props;
        this.jpaRepo = jpaRepo;
    }

    @PostConstruct
    public void init() {
        Map<String, ShopAuth> yamlShops = props.getShops();
        if (yamlShops == null || yamlShops.isEmpty()) {
            throw new IllegalStateException("kwai.shops 未配置或为空");
        }

        orderedKeys = new ArrayList<>(yamlShops.keySet());

        for (String shopKey : orderedKeys) {
            ShopAuth yamlAuth = yamlShops.get(shopKey);
            if (yamlAuth == null) {
                throw new IllegalStateException("店铺 " + shopKey + " 的配置为空");
            }

            Optional<ShopAuthDoc> dbDoc = jpaRepo.findById(shopKey);

            ShopAuth auth = new ShopAuth();
            if (dbDoc.isPresent()) {
                ShopAuthDoc doc = dbDoc.get();
                auth.setAccessToken(doc.getAccessToken());
                auth.setRefreshToken(doc.getRefreshToken());
                log.info("店铺 {} 从 MySQL 加载 token", shopKey);
            } else {
                auth.setAccessToken(trim(yamlAuth.getAccessToken()));
                auth.setRefreshToken(trim(yamlAuth.getRefreshToken()));
                ShopAuthDoc doc = new ShopAuthDoc(shopKey, auth.getAccessToken(), auth.getRefreshToken());
                jpaRepo.save(doc);
                log.info("店铺 {} 从 YAML 初始化并持久化到 MySQL", shopKey);
            }

            shops.put(shopKey, auth);
        }

        log.info("ShopAuthRegistry 初始化完成，共加载 {} 个店铺", shops.size());
    }

    public void updateToken(String shopKey, String newAccessToken, String newRefreshToken, long expiresInSeconds) {
        shops.computeIfPresent(shopKey, (k, auth) -> {
            auth.setAccessToken(newAccessToken);
            if (newRefreshToken != null && !newRefreshToken.isBlank()) {
                auth.setRefreshToken(newRefreshToken);
            }
            return auth;
        });

        ShopAuth auth = shops.get(shopKey);
        if (auth != null) {
            ShopAuthDoc doc = new ShopAuthDoc();
            doc.setShopKey(shopKey);
            doc.setAccessToken(auth.getAccessToken());
            doc.setRefreshToken(auth.getRefreshToken());
            doc.setLastRefreshedAt(Instant.now());
            if (expiresInSeconds > 0) {
                doc.setExpiresAt(Instant.now().plusSeconds(expiresInSeconds));
            }
            jpaRepo.save(doc);
            log.info("店铺 {} token 已更新并持久化", shopKey);
        }
    }

    public Map<String, ShopAuth> asMap() {
        return Collections.unmodifiableMap(shops);
    }

    public Set<String> shopKeys() {
        return new LinkedHashSet<>(orderedKeys);
    }

    public Collection<ShopAuth> values() {
        return shops.values();
    }

    public ShopAuth get(String shopKey) {
        return shops.get(shopKey);
    }

    private String trim(String s) {
        return s == null ? null : s.trim();
    }
}
