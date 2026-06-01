package com.example.kwai_data.facade;

import com.example.kwai_data.config.EndMsMode;
import com.example.kwai_data.config.KwaiProperties;
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
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class KwaiFacade {

    // ── 依赖注入 ────────────────────────────────────────────────
    private final SellerInfoService       sellerInfoService;
    private final FundsAccountInfoService fundsAccountInfoService;
    private final OrderCursorListService  orderCursorListService;
    private final UnsettledOrderService   unsettledOrderService;
    private final SyncCheckpointService   checkpointService;
    private final TimeRangeProvider       timeRangeProvider;
    private final ShopAuthRegistry        registry;
    private final KwaiClientFactory       clientFactory;
    private final KwaiProperties props;

    // ── 常量 ────────────────────────────────────────────────────
    /** 首次同步回溯天数（无 checkpoint 时兜底） */
    private static final long INITIAL_LOOKBACK_MS = 30L * 24 * 60 * 60 * 1000;

    /**
     * 滚动窗口：每次同步至少回溯 7 天，确保退款/结算状态变更被捕获。
     */
    private static final long ROLLING_WINDOW_MS   =  7L * 24 * 60 * 60 * 1000;

    // ── 线程池 ───────────────────────────────────────────────────
    /**
     * 每个店铺独占一个线程，并发拉取互不干扰。
     * 线程池大小 = 店铺数量（上限 10，防止配置异常撑爆资源）。
     */
    private ExecutorService shopExecutor;

    private String maskSecret(String value) {
        if (value == null) {
            return "null";
        }
        if (value.length() <= 12) {
            return "len=" + value.length();
        }
        return "len=" + value.length()
                + ", head=" + value.substring(0, 6)
                + ", tail=" + value.substring(value.length() - 6);
    }

    private String maskToken(String token) {
        if (token == null) {
            return "null";
        }
        if (token.length() <= 12) {
            return "len=" + token.length();
        }
        return "len=" + token.length()
                + ", head=" + token.substring(0, 6)
                + ", tail=" + token.substring(token.length() - 6);
    }

    @PostConstruct
    private void initExecutor() {
        int shopCount = registry.asMap().size();
        int threads   = Math.min(Math.max(shopCount, 1), 10);
        shopExecutor  = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r);
            t.setName("shop-sync-" + t.getId());
            t.setDaemon(true);
            return t;
        });
        log.info("店铺并行同步线程池已初始化：店铺数={}, 线程数={}", shopCount, threads);
    }

    @PreDestroy
    private void shutdownExecutor() {
        shopExecutor.shutdown();
        try {
            if (!shopExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                shopExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            shopExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ── 主入口 ──────────────────────────────────────────────────

    /**
     * 增量全量同步入口，由定时任务触发。
     *
     * <p>各店铺并行执行：每个店铺提交给专用线程池，主线程等待全部完成后返回。
     * 单店异常只影响本店（checkpoint 不更新，下次从旧位置重试），不中断其他店铺。
     */
    public void syncAll() {
        log.info("===== 开始全局增量同步 =====");

        log.info("Kwai appKey={}", maskSecret(props.getAppKey()));
        log.info("Kwai appSecret={}", maskSecret(props.getAppSecret()));
        log.info("Kwai signSecret={}", maskSecret(props.getSignSecret()));
        log.info("Kwai baseUrl={}", props.getBaseUrl());

        List<Map.Entry<String, ShopAuth>> entries = new ArrayList<>(registry.asMap().entrySet());

        // 每个店铺异步提交
        List<CompletableFuture<String>> futures = entries.stream()
                .map(e -> CompletableFuture.supplyAsync(() -> {
                    String   shopKey = e.getKey();
                    ShopAuth auth    = e.getValue();
                    try {
                        syncShop(shopKey, auth);
                        return "OK:" + shopKey;
                    } catch (Exception ex) {
                        log.error("[{}] 同步失败，下次将从旧 checkpoint 重试: {}",
                                shopKey, ex.getMessage(), ex);
                        return "FAIL:" + shopKey;
                    }
                }, shopExecutor))
                .collect(Collectors.toList());

        // 等待所有店铺完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // 汇总结果
        List<String> results = futures.stream().map(CompletableFuture::join).collect(Collectors.toList());
        long ok   = results.stream().filter(s -> s.startsWith("OK")).count();
        long fail = results.size() - ok;
        if (fail > 0) {
            List<String> failed = results.stream()
                    .filter(s -> s.startsWith("FAIL"))
                    .map(s -> s.substring(5))
                    .collect(Collectors.toList());
            log.warn("===== 全局增量同步结束：成功 {} 店，失败 {} 店 {} =====", ok, fail, failed);
        } else {
            log.info("===== 全局增量同步结束：全部 {} 店成功 =====", ok);
        }
    }

    // ── 单店完整同步流程 ─────────────────────────────────────────

    private void syncShop(String shopKey, ShopAuth auth) throws Exception {
        log.info("[{}] 开始同步 (线程: {})", shopKey, Thread.currentThread().getName());

        AccessTokenKsMerchantClient client = clientFactory.getClient();

        // 1. 卖家基础信息（余额、店铺名）
        syncSellerInfo(shopKey, auth, client);

        // 2. 提现记录
        OpenFundsCenterWirhdrawRecordListResponse wResp =
                sellerInfoService.fetchWirhdrawRecordInfo(client, auth.getAccessToken());
        sellerInfoService.WRecordupsert(wResp, shopKey);

        // 3. 未结算订单（增量 + 滚动窗口）
        syncUnsettledIncremental(shopKey);

        // 4. 普通订单（增量 + 滚动窗口）
        syncOrdersIncremental(shopKey);

        log.info("[{}] 同步完成", shopKey);
    }

    // ── 各类数据同步 ─────────────────────────────────────────────

    /** 刷新卖家信息（余额、店铺名） */
    private void syncSellerInfo(String shopKey, ShopAuth auth,
                                AccessTokenKsMerchantClient client) throws Exception {
        log.info("[{}] 使用 token: accessToken={}, refreshToken={}",
                shopKey,
                maskToken(auth.getAccessToken()),
                maskToken(auth.getRefreshToken()));

        OpenUserSellerGetResponse sellerResp =
                sellerInfoService.fetchSellerInfo(client, auth.getAccessToken());

        if (sellerResp == null) {
            log.error("[{}] 获取卖家信息失败：sellerResp=null", shopKey);
            throw new IllegalStateException("获取卖家信息失败：响应为空");
        }

        log.info("[{}] open.user.seller.get 返回: success={}, result={}, code={}, msg={}, subCode={}, subMsg={}, errorMsg={}, requestId={}, dataNull={}",
                shopKey,
                sellerResp.isSuccess(),
                sellerResp.getResult(),
                sellerResp.getCode(),
                sellerResp.getMsg(),
                sellerResp.getSubCode(),
                sellerResp.getSubMsg(),
                sellerResp.getErrorMsg(),
                sellerResp.getRequestId(),
                sellerResp.getData() == null);

        if (!sellerResp.isSuccess() || sellerResp.getData() == null) {
            throw new IllegalStateException("获取卖家信息失败，详见上一行快手接口返回");
        }

        SellerInfoDto totalData = SellerInfoDto.builder()
                .shopId(sellerResp.getData().getSellerId())
                .shopName(sellerResp.getData().getName())
                .accountBalance(fundsAccountInfoService.getBalance(client, auth.getAccessToken()))
                .build();
    }

    /**
     * 普通订单增量同步。
     *
     * <p>时间范围：effectiveStart = min(checkpoint, now - 7天)，endMs = now。
     * 首次同步（无 checkpoint）回溯 30 天；长时间宕机后从旧 checkpoint 续跑。
     */
    public void syncOrdersIncremental(String shopKey) throws Exception {
        long nowMs = System.currentTimeMillis();

        long checkpointMs = checkpointService
                .getLastEndMs(shopKey, SyncCheckpointService.TYPE_ORDERS)
                .orElse(nowMs - INITIAL_LOOKBACK_MS);

        long effectiveStart = Math.min(checkpointMs, nowMs - ROLLING_WINDOW_MS);

        log.info("[{}] 订单增量同步: {} ~ {}",
                shopKey,
                TimeUtil.toZonedDateTime(effectiveStart, "Asia/Shanghai"),
                TimeUtil.toZonedDateTime(nowMs,          "Asia/Shanghai"));

        // 按 7 天分片，避免单次请求跨度过大
        TimeRangeMillis range = new TimeRangeMillis(effectiveStart, nowMs);
        List<TimeRangeMillis> ranges = timeRangeProvider.splitByDays(range, 7, EndMsMode.INCLUSIVE);

        for (TimeRangeMillis r : ranges) {
            syncOrdersInRange(r, shopKey);
        }

        // ✅ 成功后才保存 checkpoint
        checkpointService.save(shopKey, SyncCheckpointService.TYPE_ORDERS, nowMs);
        log.info("[{}] 订单 checkpoint 已更新至 {}", shopKey, nowMs);
    }

    /**
     * 未结算订单增量同步（财务账单）。
     *
     * <p>结束时间取"昨天结束"，当天账单可能尚未入账。
     */
    public void syncUnsettledIncremental(String shopKey) throws Exception {
        long nowMs = System.currentTimeMillis();

        long checkpointMs = checkpointService
                .getLastEndMs(shopKey, SyncCheckpointService.TYPE_UNSETTLED_ORDERS)
                .orElse(nowMs - INITIAL_LOOKBACK_MS);

        long effectiveStart = Math.min(checkpointMs, nowMs - ROLLING_WINDOW_MS);
        long endMs = timeRangeProvider.lastMonthStartToYesterdayEndInclusive().getEndMs();

        if (effectiveStart >= endMs) {
            log.info("[{}] 未结算订单已是最新，跳过同步", shopKey);
            return;
        }

        log.info("[{}] 未结算订单增量同步: {} ~ {}",
                shopKey,
                TimeUtil.toZonedDateTime(effectiveStart, "Asia/Shanghai"),
                TimeUtil.toZonedDateTime(endMs,          "Asia/Shanghai"));

        int    maxPages  = 500;
        String cursor    = "";
        int    pageCount = 0;

        while (pageCount < maxPages) {
            pageCount++;

            OpenFundsFinancialStatementListResponse resp =
                    unsettledOrderService.fetchOnce(shopKey, effectiveStart, endMs, cursor);
            log.info("[{}] 未结算订单第 {} 页, cursor={}", shopKey, pageCount, cursor);

            List<UnsettledOrderDto> records = unsettledOrderService.parseRecords(resp);
            if (records == null || records.isEmpty()) break;

            int saved = unsettledOrderService.upsertBatch(records, shopKey);
            log.debug("[{}] 未结算订单第 {} 页写入 {} 条", shopKey, pageCount, saved);

            String nextCursor = unsettledOrderService.extractNextCursor(resp);
            if (nextCursor == null || nextCursor.isBlank()
                    || nextCursor.equals("no_more") || nextCursor.equals(cursor)) break;
            cursor = nextCursor;
        }

        // ✅ 成功后才保存 checkpoint
        checkpointService.save(shopKey, SyncCheckpointService.TYPE_UNSETTLED_ORDERS, endMs);
        log.info("[{}] 未结算订单 checkpoint 已更新至 {}", shopKey, endMs);
    }

    // ── 内部工具方法 ──────────────────────────────────────────────

    /**
     * 拉取指定时间段内的订单并批量写入（整页一次 batch INSERT）。
     */
    public void syncOrdersInRange(TimeRangeMillis r, String shopKey) throws Exception {
        String cursor  = null;
        int    pageSize = 50;
        int    maxPages = 500;

        for (int i = 0; i < maxPages; i++) {
            var resp = orderCursorListService.fetchOnce(
                    shopKey, 1, pageSize, 0, 1,
                    r.getStartMs(), r.getEndMs(),
                    1, cursor
            );

            log.debug("[{}] 订单分片 {} ~ {}  第 {} 页",
                    shopKey,
                    TimeUtil.toZonedDateTime(r.getStartMs(), "Asia/Shanghai"),
                    TimeUtil.toZonedDateTime(r.getEndMs(),   "Asia/Shanghai"),
                    i + 1);

            Thread.sleep(1000);   // 限流保护（每页间隔，各店铺线程独立计时）

            OrderList[] orderlist = orderCursorListService.extractOrderList(resp);
            if (orderlist.length == 0) break;

            List<OrderDto> dtos = Arrays.stream(orderlist)
                    .filter(Objects::nonNull)
                    .map(OrderDto::new)
                    .collect(Collectors.toList());

            int saved = orderCursorListService.upsertBatch(dtos, shopKey);
            log.debug("[{}]   批量写入 {} 条（本页共 {} 条）", shopKey, saved, orderlist.length);

            String nextCursor = orderCursorListService.extractNextCursor(resp);
            if (nextCursor == null || nextCursor.isBlank() || nextCursor.equals(cursor)) break;
            cursor = nextCursor;
        }
    }
}
