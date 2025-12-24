package com.example.kwai_data.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

//一层

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class KsResponse<T> {
    private Integer result;
    private String msg;

    @JsonProperty("error_msg")
    private String errorMsg;

    private String code;
    private T data;

    private String requestId;

    @JsonProperty("sub_msg")
    private String subMsg;

    @JsonProperty("sub_code")
    private String subCode;
}

