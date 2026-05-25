package com.example.kwai_data.service;

import com.example.kwai_data.data.SyncCheckpoint;
import com.example.kwai_data.repository.SyncCheckpointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * 增量同步 Checkpoint 管理
 *
 * 职责：
 *   - 读取上次同步结束时间（用于确定本次起点）
 *   - 同步成功后保存新的 checkpoint
 *
 * 使用方式（在 KwaiFacade 中）：
 *   long startMs = checkpointService.getLastEndMs(shopKey, "orders")
 *                      .orElse(now - INITIAL_LOOKBACK_MS);
 *   // ... 做同步 ...
 *   checkpointService.save(shopKey, "orders", nowMs);  // 成功后才保存
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncCheckpointService {

    /** 同步类型常量，避免散落字符串 */
    public static final String TYPE_ORDERS            = "orders";
    public static final String TYPE_UNSETTLED_ORDERS  = "unsettled_orders";

    private final SyncCheckpointRepository repo;

    /**
     * 读取上次同步的结束时间戳（毫秒）。
     * 若该店铺 + 类型从未同步过，返回 empty（调用方用首次回溯天数兜底）。
     */
    public Optional<Long> getLastEndMs(String shopKey, String syncType) {
        return repo.findByShopKeyAndSyncType(shopKey, syncType)
                .map(SyncCheckpoint::getLastEndMs);
    }

    /**
     * 保存 / 更新 checkpoint。
     * 只在同步成功后调用——失败时不调用，下次从旧 checkpoint 重试。
     *
     * @param shopKey  店铺标识
     * @param syncType 同步类型（TYPE_ORDERS / TYPE_UNSETTLED_ORDERS）
     * @param endMs    本次同步的结束时间戳（毫秒）
     */
    public void save(String shopKey, String syncType, long endMs) {
        SyncCheckpoint cp = repo.findByShopKeyAndSyncType(shopKey, syncType)
                .orElse(SyncCheckpoint.builder()
                        .shopKey(shopKey)
                        .syncType(syncType)
                        .build());
        cp.setLastEndMs(endMs);
        cp.setSyncedAt(Instant.now());
        repo.save(cp);
        log.debug("Checkpoint 已保存: shopKey={}, type={}, endMs={}", shopKey, syncType, endMs);
    }
}
