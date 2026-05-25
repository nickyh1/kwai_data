package com.example.kwai_data.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * 增量同步 Checkpoint 实体
 *
 * 每条记录对应一个店铺 + 同步类型的最近一次成功同步结束时间。
 * 下次同步时以此为起点，配合滚动窗口确保状态变更也能被捕获。
 *
 * sync_type 取值：
 *   "orders"           — 普通订单
 *   "unsettled_orders" — 未结算订单（财务账单）
 */
@Entity
@Table(name = "sync_checkpoint", uniqueConstraints = {
        @UniqueConstraint(name = "uk_shop_type", columnNames = {"shop_key", "sync_type"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncCheckpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 店铺标识，与 shop_auth.shop_key 一致 */
    @Column(name = "shop_key", nullable = false, length = 64)
    private String shopKey;

    /** 同步类型：orders / unsettled_orders */
    @Column(name = "sync_type", nullable = false, length = 32)
    private String syncType;

    /**
     * 上次同步的结束时间戳（毫秒，UTC）。
     * 下次同步的有效起点 = min(lastEndMs, now - ROLLING_WINDOW)
     */
    @Column(name = "last_end_ms", nullable = false)
    private Long lastEndMs;

    /** 上次同步完成的时刻 */
    @Column(name = "synced_at")
    private Instant syncedAt;
}
