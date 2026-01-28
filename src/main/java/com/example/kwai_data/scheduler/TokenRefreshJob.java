package com.example.kwai_data.scheduler;

import com.example.kwai_data.service.TokenRefreshService;
import com.example.kwai_data.service.TokenRefreshService.TokenRefreshResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Token 定时刷新任务
 *
 * 定期刷新所有店铺的 access_token，确保 token 不会过期。
 * 快手 access_token 有效期通常为 24 小时，每 12 小时刷新一次可确保 token 始终有效。
 */
@Component
public class TokenRefreshJob {

    private static final Logger log = LoggerFactory.getLogger(TokenRefreshJob.class);

    private final TokenRefreshService tokenRefreshService;

    public TokenRefreshJob(TokenRefreshService tokenRefreshService) {
        this.tokenRefreshService = tokenRefreshService;
    }

    /**
     * 定时刷新所有店铺的 token
     *
     * 执行策略：
     * - fixedRate: 每 12 小时执行一次
     * - initialDelay: 应用启动 1 分钟后首次执行
     */
    @Scheduled(fixedRate = 12 * 60 * 60 * 1000L, initialDelay = 60_000L)
    public void refreshTokens() {
        log.info("========== 开始定时刷新 Token ==========");

        List<TokenRefreshResult> results = tokenRefreshService.refreshAll();

        int successCount = 0;
        int failCount = 0;

        for (TokenRefreshResult result : results) {
            if (result.isSuccess()) {
                successCount++;
                log.info("[成功] 店铺 {} - token 刷新成功，有效期 {} 秒",
                        result.getShopKey(), result.getExpiresIn());
            } else {
                failCount++;
                log.error("[失败] 店铺 {} - token 刷新失败: {}",
                        result.getShopKey(), result.getErrorMsg());
            }
        }

        log.info("========== Token 刷新完成: 成功 {}, 失败 {} ==========", successCount, failCount);
    }
}
