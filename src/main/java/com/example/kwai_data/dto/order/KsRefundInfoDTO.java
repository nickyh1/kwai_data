package com.example.kwai_data.dto.order;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public  class KsRefundInfoDTO {

    /**
     * 退款信息列表（可能为空数组）
     */
    @Builder.Default
    private List<GeneralRefundInfoDTO> refundInfo = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GeneralRefundInfoDTO {

        /**
         * 平台补贴退款金额（分）
         */
        private Long platformAllowanceRefund;

        /**
         * 用户实付退款金额（分）
         */
        private Long actualPayRefund;

        /**
         * 退款单号
         */
        private Long refundId;

        /**
         * 支付营销补贴回退退款金额（分）
         *
         * 如果你接口返回字段名就是 platformPayMarketAllowanceRefund，
         * 这里不写 @JsonProperty 也能正常映射；写上更稳。
         */
        @JsonProperty("platformPayMarketAllowanceRefund")
        private Long platformPayMarketAllowanceRefund;
    }
}

