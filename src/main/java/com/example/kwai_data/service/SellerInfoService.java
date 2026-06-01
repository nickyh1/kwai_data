package com.example.kwai_data.service;

import com.example.kwai_data.config.TimeRangeMillis;
import com.example.kwai_data.config.TimeRangeProvider;
import com.example.kwai_data.domain.SellerInfo_Doc;
import com.example.kwai_data.dto.SellerInfoDto;
import com.example.kwai_data.mapper.SellerInfoMapper;
import com.kuaishou.merchant.open.api.client.AccessTokenKsMerchantClient;
import com.kuaishou.merchant.open.api.domain.funds.WithdrawRecordDto;
import com.kuaishou.merchant.open.api.request.funds.OpenFundsCenterWirhdrawRecordListRequest;
import com.kuaishou.merchant.open.api.request.user.OpenUserSellerGetRequest;
import com.kuaishou.merchant.open.api.response.funds.OpenFundsCenterWirhdrawRecordListResponse;
import com.kuaishou.merchant.open.api.response.user.OpenUserSellerGetResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SellerInfoService {

    private final TimeRangeProvider timeRangeProvider;
    private final JdbcTemplate jdbcTemplate;

    private static final String UPSERT_SELLER_SQL = """
            INSERT INTO seller_info
                (shop_id, shop_name, account_balance, pending_settlement,
                 total_withdrawn, net_transaction_amount, update_time)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                shop_name       = VALUES(shop_name),
                account_balance = VALUES(account_balance),
                update_time     = VALUES(update_time)
            """;

    private static final String UPSERT_WITHDRAW_SQL = """
            INSERT INTO withdraw_records
                (shop_key, platform_withdraw_no, withdraw_time, received_time,
                 state, withdraw_money, fail_reason, withdraw_desc, update_time)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                received_time  = VALUES(received_time),
                state          = VALUES(state),
                withdraw_money = VALUES(withdraw_money),
                fail_reason    = VALUES(fail_reason),
                withdraw_desc  = VALUES(withdraw_desc),
                update_time    = VALUES(update_time)
            """;

    public OpenUserSellerGetResponse fetchSellerInfo(AccessTokenKsMerchantClient client, String accessToken) throws Exception {
        OpenUserSellerGetRequest request = new OpenUserSellerGetRequest();
        request.setAccessToken(accessToken);
        request.setApiMethodVersion(1L);
        return client.execute(request);
    }

    public OpenFundsCenterWirhdrawRecordListResponse fetchWirhdrawRecordInfo(AccessTokenKsMerchantClient client, String accessToken) throws Exception {
        TimeRangeMillis range = timeRangeProvider.lastMonthStartToYesterdayEndInclusive();
        OpenFundsCenterWirhdrawRecordListRequest request = new OpenFundsCenterWirhdrawRecordListRequest();
        request.setAccessToken(accessToken);
        request.setApiMethodVersion(1L);
        request.setLimit(10);
        request.setCreateTimeStart(range.getStartMs());
        request.setAccountChannel(4);
        request.setCreateTimeEnd(range.getEndMs());
        return client.execute(request);
    }

    public void sellerInfoupsert(SellerInfoDto dto) {
        SellerInfo_Doc info = SellerInfoMapper.toEntity(dto);
        Instant now = Instant.now().plus(Duration.ofHours(8));
        jdbcTemplate.update(UPSERT_SELLER_SQL,
                info.getShopId(),
                info.getShopName(),
                info.getAccountBalance(),
                info.getPendingSettlement(),
                info.getTotalWithdrawn(),
                info.getNetTransactionAmount(),
                toTs(now)
        );
    }

    public int WRecordupsert(OpenFundsCenterWirhdrawRecordListResponse resp, String shopKey) {
        if (resp == null || resp.getData() == null || resp.getData().getRecord() == null) return 0;
        List<WithdrawRecordDto> records = resp.getData().getRecord();
        if (records.isEmpty()) return 0;

        Instant now = Instant.now().plus(Duration.ofHours(8));
        List<Object[]> batchArgs = new ArrayList<>();

        for (WithdrawRecordDto r : records) {
            if (r == null) continue;
            if (r.getPlatformWithdrawNo() == null || r.getPlatformWithdrawNo().isBlank()) continue;

            Instant t = msToInstant(r.getSuccessTime());
            Instant t8 = (t == null) ? null : t.plus(Duration.ofHours(8));
            Instant withdrawTime = msToInstant(r.getCreateTime());
            Instant withdrawTime8 = (withdrawTime == null) ? null : withdrawTime.plus(Duration.ofHours(8));

            batchArgs.add(new Object[]{
                    shopKey,
                    r.getPlatformWithdrawNo(),
                    toTs(withdrawTime8),
                    toTs(t8),
                    r.getState(),
                    r.getWithdrawMoney(),
                    emptyToNull(r.getFailReason()),
                    emptyToNull(r.getWithdrawDesc()),
                    toTs(now)
            });
        }

        jdbcTemplate.batchUpdate(UPSERT_WITHDRAW_SQL, batchArgs);
        return batchArgs.size();
    }

    private static Instant msToInstant(Long ms) { return (ms == null || ms <= 0) ? null : Instant.ofEpochMilli(ms); }
    private static String emptyToNull(String s) { if (s == null) return null; String t = s.trim(); return t.isEmpty() ? null : t; }
    private static Timestamp toTs(Instant instant) { return instant == null ? null : Timestamp.from(instant); }
}
