package com.example.kwai_data.facade;

import com.example.kwai_data.config.EndMsMode;
import com.example.kwai_data.config.TimeRangeMillis;
import com.example.kwai_data.config.TimeRangeProvider;
import com.example.kwai_data.client.KwaiClientFactory;
import com.example.kwai_data.dto.OrderDto;
import com.example.kwai_data.domain.ShopAuth;
import com.example.kwai_data.dto.UnsettledOrderDto;
import com.example.kwai_data.dto.SellerInfoDto;
import com.example.kwai_data.repository.ShopAuthRegistry;
import com.example.kwai_data.service.*;
import com.example.kwai_data.util.TimeUtil;
import com.kuaishou.merchant.open.api.client.AccessTokenKsMerchantClient;
import com.kuaishou.merchant.open.api.domain.order.OrderList;
import com.kuaishou.merchant.open.api.response.funds.OpenFundsCenterWirhdrawRecordListResponse;
import com.kuaishou.merchant.open.api.response.funds.OpenFundsFinancialStatementListResponse;
import com.kuaishou.merchant.open.api.response.user.OpenUserSellerGetResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class KwaiFacade {

    // ── 依赖注入 ────────────────────────────────────────────────
    private final SellerInfoService      sellerInfoService;
    private final FundsAccountInfoService fundsAccountInfoService;
    private final OrderCursorListService orderCursorListService;
    private final UnsettledOrderService  unsettledOrderService;
    private final SyncCheckpointService  checkpointService;
    private final TimeRangeProvider      timeRangeProvider;
    private final ShopAuthRegistry       registry;
    private final KwaiClientFactory      clientFactory;

    // ── 常量 ────────────────────────────────────────────────────
    /** 首次同步回溯天数（无 checkpoint 时兜底） */
    private static final long INITIAL_LOOKBACK_MS = 30L * 24 * 60 * 60 * 1000;

    /**
     * 滚动窗口：每次同步至少回溯 7 天。
     * 即使 checkpoint 距今只有 3 小时，也会重新拉取过去 7 天的数据，
     * 确保退款状态变更、结算状态变更等可以被捕获。
     */
    private static final long ROLLING_WINDOW_MS   =  7L * 24 * 60 * 60 * 1000;

    // ── 主入口 ──────────────────────────────────────────────────

    /**
     * 增量全量同步入口，由定时任务触发。
     *
     * 不再清空数据 —— upsert 天然处理新增 / 状态更新 / 重复数据。
     * 各店铺串行执行；若单店失败，记录日志后继续下一个店铺。
     */
    public void syncAll() throws Exception {
        log.info("===== 开始全局增量同步 =====");

        for (var e : registry.asMap().entrySet()) {
            String   shopKey = e.getKey();
            ShopAuth auth    = e.getValue();
            log.info("同步店铺: {}", shopKey);

            try {
                AccessTokenKsMerchantClient client = clientFactory.getClient();

                // 1. 卖家基础信息（每次刷新，数据量小）
                syncSellerInfo(shopKey, auth, client);

                // 2. 提现记录（每次刷新，数据量小）
                OpenFundsCenterWirhdrawRecordListResponse wResp =
                        sellerInfoService.fetchWirhdrawRecordInfo(client, auth.getAccessToken());
                sellerInfoService.WRecordupsert(wResp, shopKey);

                // 3. 未结算订单（增量 + 滚动窗口）
                syncUnsettledIncremental(shopKey);

                // 4. 普通订单（增量 + 滚动窗口）
                syncOrdersIncremental(shopKey);

                log.info("店铺 {} 同步完成", shopKey);

            } catch (Exception ex) {
                // 单店失败不影响其他店铺；checkpoint 不更新，下次自动重试
                log.error("店铺 {} 同步失败，下次将从旧 checkpoint 重试: {}", shopKey, ex.getMessage(), ex);
            }
        }

        log.info("===== 全局增量同步结束 =====");
    }

    // ── 各类数据同步 ─────────────────────────────────────────────

    /** 刷新卖家信息（余额、店铺名） */
    private void syncSellerInfo(String shopKey, ShopAuth auth,
                                AccessTokenKsMerchantClient client) throws Exception {
        OpenUserSellerGetResponse sellerResp =
                sellerInfoService.fetchSellerInfo(client, auth.getAccessToken());
        SellerInfoDto totalData = SellerInfoDto.builder()
                .shopId(sellerResp.getData().getSellerId())
                .shopName(sellerResp.getData().getName())
                .accountBalance(fundsAccountInfoService.getBalance(client, auth.getAccessToken()))
                .build();
        sellerInfoService.sellerInfoupsert(totalData);
    }

    /**
     * 普通订单增量同步。
     *
     * 时间范围逻辑：
     *   effectiveStart = min(上次 checkpoint, now - 7天)
     *   endMs          = now（本次同步时刻）
     *
     * 首次同步（无 checkpoint）：回溯 30 天。
     * 正常运行（3 小时一次）：滚动窗口 7 天，确保退款状态更新。
     * 长时间宕机重启：从上次 checkpoint 恢复，不丢数据。
     */
    public void syncOrdersIncremental(String shopKey) throws Exception {
        long nowMs = System.currentTimeMillis();

        long checkpointMs = checkpointService
                .getLastEndMs(shopKey, SyncCheckpointService.TYPE_ORDERS)
                .orElse(nowMs - INITIAL_LOOKBACK_MS);

        // 滚动窗口：取 checkpoint 和 7天前 中更早的那个
        long effectiveStart = Math.min(checkpointMs, nowMs - ROLLING_WINDOW_MS);

        log.info("店铺 {} 订单增量同步: {} ~ {}",
                shopKey,
                TimeUtil.toZonedDateTime(effectiveStart, "Asia/Shanghai"),
                TimeUtil.toZonedDateTime(nowMs, "Asia/Shanghai"));

        // 按 7 天分片（避免单次请求时间跨度过大）
        TimeRangeMillis range  = new TimeRangeMillis(effectiveStart, nowMs);
        List<TimeRangeMillis> ranges = timeRangeProvider.splitByDays(range, 7, EndMsMode.INCLUSIVE);

        for (TimeRangeMillis r : ranges) {
            syncOrdersInRange(r, shopKey);
        }

        // ✅ 成功后才保存 checkpoint；异常时 checkpoint 不变，下次从旧位置重试
        checkpointService.save(shopKey, SyncCheckpointService.TYPE_ORDERS, nowMs);
        log.info("店铺 {} 订单 checkpoint 已更新至 {}", shopKey, nowMs);
    }

    /**
     * 未结算订单增量同步（财务账单）。
     *
     * 时间范围逻辑：
     *   effectiveStart = min(上次 checkpoint, now - 7天)
     *   endMs          = yesterday end（账单按天结算，当天可能未完结）
     */
    public void syncUnsettledIncremental(String shopKey) throws Exception {
        long nowMs = System.currentTimeMillis();

        long checkpointMs = checkpointService
                .getLastEndMs(shopKey, SyncCheckpointService.TYPE_UNSETTLED_ORDERS)
                .orElse(nowMs - INITIAL_LOOKBACK_MS);

        long effectiveStart = Math.min(checkpointMs, nowMs - ROLLING_WINDOW_MS);
        // 账单端点取"昨天结束"，当天账单可能尚未入账
        long endMs = timeRangeProvider.lastMonthStartToYesterdayEndInclusive().getEndMs();

        if (effectiveStart >= endMs) {
            log.info("店铺 {} 未结算订单已是最新，跳过同步", shopKey);
            return;
        }

        log.info("店铺 {} 未结算订单增量同步: {} ~ {}",
                shopKey,
                TimeUtil.toZonedDateTime(effectiveStart, "Asia/Shanghai"),
                TimeUtil.toZonedDateTime(endMs, "Asia/Shanghai"));

        int maxPages  = 500;
        String cursor = "";
        int pageCount = 0;

        while (pageCount < maxPages) {
            pageCount++;

            OpenFundsFinancialStatementListResponse resp =
                    unsettledOrderService.fetchOnce(shopKey, effectiveStart, endMs, cursor);
            log.info("店铺 {} 未结算订单第 {} 页, cursor={}", shopKey, pageCount, cursor);

            List<UnsettledOrderDto> records = unsettledOrderService.parseRecords(resp);
            if (records == null || records.isEmpty()) break;

            for (UnsettledOrderDto dto : records) {
                Instant createTime = unsettledOrderService.fetchOrderCreateTime(shopKey, dto.getOid());
                dto.setCreateTime(createTime);
                unsettledOrderService.upsertOne(dto, shopKey);
            }

            String nextCursor = unsettledOrderService.extractNextCursor(resp);
            if (nextCursor == null || nextCursor.isBlank()
                    || nextCursor.equals("no_more") || nextCursor.equals(cursor)) break;
            cursor = nextCursor;
        }

        // ✅ 成功后才保存 checkpoint
        checkpointService.save(shopKey, SyncCheckpointService.TYPE_UNSETTLED_ORDERS, endMs);
        log.info("店铺 {} 未结算订单 checkpoint 已更新至 {}", shopKey, endMs);
    }

    // ── 内部工具方法 ──────────────────────────────────────────────

    /**
     * 拉取指定时间段内的订单并批量写入（整页一次 batch INSERT）。
     */
    public void syncOrdersInRange(TimeRangeMillis r, String shopKey) throws Exception {
        String cursor  = null;
        int pageSize   = 50;
        int maxPages   = 500;

        for (int i = 0; i < maxPages; i++) {
            var resp = orderCursorListService.fetchOnce(
                    shopKey, 1, pageSize, 0, 1,
                    r.getStartMs(), r.getEndMs(),
                    1, cursor
            );

            log.debug("订单分片 {} ~ {}  第 {} 页",
                    TimeUtil.toZonedDateTime(r.getStartMs(), "Asia/Shanghai"),
                    TimeUtil.toZonedDateTime(r.getEndMs(),   "Asia/Shanghai"),
                    i + 1);

            Thread.sleep(1000);   // 限流保护

            OrderList[] orderlist = orderCursorListService.extractOrderList(resp);
            if (orderlist.length == 0) break;

            List<OrderDto> dtos = Arrays.stream(orderlist)
                    .filter(Objects::nonNull)
                    .map(OrderDto::new)
                    .collect(Collectors.toList());

            int saved = orderCursorListService.upsertBatch(dtos, shopKey);
            log.debug("  批量写入 {} 条（本页共 {} 条）", saved, orderlist.length);

            String nextCursor = orderCursorListService.extractNextCursor(resp);
            if (nextCursor == null || nextCursor.isBlank() || nextCursor.equals(cursor)) break;
            cursor = nextCursor;
        }
    }
}
