package com.example.kwai_data.service;


import com.example.kwai_data.config.KwaiProperties;
import com.example.kwai_data.config.TimeRangeMillis;
import com.example.kwai_data.config.TimeRangeProvider;
import com.example.kwai_data.data.SellerInfo_Doc;
import com.example.kwai_data.data.sellerInfo;
import com.example.kwai_data.dto.KsResponse;

import com.example.kwai_data.mapper.SellerInfoMapper;
import com.example.kwai_data.repository.SellerInfoRepository;
import com.kuaishou.merchant.open.api.client.AccessTokenKsMerchantClient;
import com.kuaishou.merchant.open.api.domain.funds.ListWithdrawRecordDto;
import com.kuaishou.merchant.open.api.domain.funds.WithdrawRecordDto;
import com.kuaishou.merchant.open.api.request.funds.OpenFundsCenterWirhdrawRecordListRequest;
import com.kuaishou.merchant.open.api.request.user.OpenUserSellerGetRequest;
import com.kuaishou.merchant.open.api.response.funds.OpenFundsCenterWirhdrawRecordListResponse;
import com.kuaishou.merchant.open.api.response.user.OpenUserSellerGetResponse;
import lombok.RequiredArgsConstructor;
import com.example.kwai_data.data.WithdrawInfoDoc;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

//商家信息
@Service
@RequiredArgsConstructor
public class sellerInfoService {


    //private final KwaiProperties props;
    private final TimeRangeProvider timeRangeProvider;
    private final SellerInfoRepository sellerInfoRepository;
    private final MongoTemplate mongoTemplate;

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

    public int WRecordupsert(OpenFundsCenterWirhdrawRecordListResponse resp, String shopKey) {

        if (resp == null || resp.getData() == null || resp.getData().getRecord() == null) {
            return 0;
        }

        List<WithdrawRecordDto> records = resp.getData().getRecord();
        if (records.isEmpty()) return 0;

        Instant now = Instant.now();
        BulkOperations bulk = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, "Withdraw_"+shopKey);

        for (WithdrawRecordDto r : records) {
            if (r == null) continue;
            if (r.getPlatformWithdrawNo() == null || r.getPlatformWithdrawNo().isBlank()) continue;

            Query q = Query.query(Criteria.where("platformWithdrawNo").is(r.getPlatformWithdrawNo()));

            Update u = new Update()
                    .set("platformWithdrawNo", r.getPlatformWithdrawNo())
                    .set("withdrawTime", msToInstant(r.getCreateTime()).plus(Duration.ofHours(8)))
                    .set("receivedTime", msToInstant(r.getSuccessTime()).plus(Duration.ofHours(8)))
                    .set("state", r.getState())
                    .set("withdrawMoney", r.getWithdrawMoney())
                    .set("failReason", emptyToNull(r.getFailReason()))
                    .set("withdrawDesc", emptyToNull(r.getWithdrawDesc()))
                    .set("updateTime", now.plus(Duration.ofHours(8)));

            bulk.upsert(q, u);
        }

        bulk.execute();
        return records.size();
    }

    private static Instant msToInstant(Long ms) {
        return (ms == null || ms <= 0) ? null : Instant.ofEpochMilli(ms);
    }

    private static String emptyToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    public SellerInfo_Doc sellerInfoupsert(sellerInfo dto) {

        SellerInfo_Doc info = SellerInfoMapper.toEntity(dto);

        info.setUpdateTime(Instant.now().plus(Duration.ofHours(8)));

        return sellerInfoRepository.findByShopId(info.getShopId())
                .map(existing -> {
                    existing.setShopName(info.getShopName());
                    existing.setAccountBalance(info.getAccountBalance());
                    //existing.setTotalWithdrawn(info.getTotalWithdrawn());
                    existing.setUpdateTime(info.getUpdateTime());
                    return sellerInfoRepository.save(existing);
                })
                .orElseGet(() -> sellerInfoRepository.save(info));
    }


}



