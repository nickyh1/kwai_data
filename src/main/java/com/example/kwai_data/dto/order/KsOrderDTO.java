package com.example.kwai_data.dto.order;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class KsOrderDTO {

    private String accountChannel;
    private String anchorHongBaoAmount;
    private String otherAmountDetail;
    private String kzkCommissionAmount;
    private String accountName;
    private String orderRemark;
    private String kzkId;

    // 报文里是数字：0
    private Integer sellerAllowanceAmount;

    private String totalOutgoingAmount;
    private String distributorCommissionAmount;
    private String productName;
    private String huabeiAmount;
    private String serviceCommissionRole;
    private String serviceAmount;
    private String merchantId;
    private String settlementStatus;

    // 报文里可能是数字：250 / 0
    private Integer platformPayMarketAllowanceAmount;

    private String activityUserCommissionAmount;

    private Integer governmentSubsidyAmount;
    private Integer presellSettleAmount;

    private String collectMode;
    private String otherAmountDesc;
    private String activityUserId;

    // 大整数，建议 Long
    private Long orderNo;

    private String productId;
    private String distributorId;

    // 你样例里是 "1762519671980" 这种字符串毫秒时间戳
    private String orderCreateTime;

    private String settlementRule;
    private String platformAllowanceAmount;
    private String platformCommissionAmount;

    private Integer productNum;

    // 样例是 ""，用 String
    private String czjAmount;

    private String totalIncome;
    private Integer otherAmount;
    private String actualPayAmount;

    private String serviceUserId;

    // 样例是 "1764723536914"
    private String settlementTime;

    private String hongbaoDetail;

    private List<KsRefundInfoDTO> refundInfo;

    private String settlementAmount;
    private String totalRefundAmount;
    private String mcnId;


}

