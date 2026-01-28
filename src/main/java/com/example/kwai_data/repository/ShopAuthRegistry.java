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

/**
 * 店铺认证信息注册中心
 *
 * 启动时优先从 MongoDB 加载 token，若 DB 无数据则使用 YAML 配置初始化。
 * 支持运行时动态更新 token 并持久化到 MongoDB。
 */
@Component
public class ShopAuthRegistry {

    private static final Logger log = LoggerFactory.getLogger(ShopAuthRegistry.class);

    private final KwaiProperties props;
    private final ShopAuthMongoRepository mongoRepo;

    /** 内存中的可变 token 存储 */
    private final ConcurrentHashMap<String, ShopAuth> shops = new ConcurrentHashMap<>();

    /** 保持配置顺序的 shopKey 列表 */
    private List<String> orderedKeys = new ArrayList<>();

    public ShopAuthRegistry(KwaiProperties props, ShopAuthMongoRepository mongoRepo) {
        this.props = props;
        this.mongoRepo = mongoRepo;
    }

    @PostConstruct
    public void init() {
        Map<String, ShopAuth> yamlShops = props.getShops();
        if (yamlShops == null || yamlShops.isEmpty()) {
            throw new IllegalStateException("kwai.shops 未配置或为空");
        }

        // 保持 YAML 配置顺序
        orderedKeys = new ArrayList<>(yamlShops.keySet());

        for (String shopKey : orderedKeys) {
            ShopAuth yamlAuth = yamlShops.get(shopKey);
            if (yamlAuth == null) {
                throw new IllegalStateException("店铺 " + shopKey + " 的配置为空");
            }

            // 优先从 MongoDB 加载
            Optional<ShopAuthDoc> dbDoc = mongoRepo.findById(shopKey);

            ShopAuth auth = new ShopAuth();
            if (dbDoc.isPresent()) {
                // DB 有数据，使用 DB 中的 token
                ShopAuthDoc doc = dbDoc.get();
                auth.setAccessToken(doc.getAccessToken());
                auth.setRefreshToken(doc.getRefreshToken());
                log.info("店铺 {} 从 MongoDB 加载 token", shopKey);
            } else {
                // DB 无数据，使用 YAML 配置并持久化
                auth.setAccessToken(trim(yamlAuth.getAccessToken()));
                auth.setRefreshToken(trim(yamlAuth.getRefreshToken()));

                // 持久化到 MongoDB
                ShopAuthDoc doc = new ShopAuthDoc(shopKey, auth.getAccessToken(), auth.getRefreshToken());
                mongoRepo.save(doc);
                log.info("店铺 {} 从 YAML 初始化并持久化到 MongoDB", shopKey);
            }

            shops.put(shopKey, auth);
        }

        log.info("ShopAuthRegistry 初始化完成，共加载 {} 个店铺", shops.size());
    }

    /**
     * 更新店铺 token（线程安全）并持久化到 MongoDB
     *
     * @param shopKey 店铺标识
     * @param newAccessToken 新的访问令牌
     * @param newRefreshToken 新的刷新令牌（可为null，表示不更新）
     * @param expiresInSeconds token 有效期（秒）
     */
    public void updateToken(String shopKey, String newAccessToken, String newRefreshToken, long expiresInSeconds) {
        shops.computeIfPresent(shopKey, (k, auth) -> {
            auth.setAccessToken(newAccessToken);
            if (newRefreshToken != null && !newRefreshToken.isBlank()) {
                auth.setRefreshToken(newRefreshToken);
            }
            return auth;
        });

        // 持久化到 MongoDB
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
            mongoRepo.save(doc);
            log.info("店铺 {} token 已更新并持久化", shopKey);
        }
    }

    public Map<String, ShopAuth> asMap() {
        return Collections.unmodifiableMap(shops);
    }

    /** 获取所有店铺标识（保持配置顺序） */
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
