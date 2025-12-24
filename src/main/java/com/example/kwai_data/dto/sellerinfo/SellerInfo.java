package com.example.kwai_data.dto.sellerinfo;

import lombok.Data;

@Data
public class SellerInfo {

    private String name;
    private String sex;
    private String head;
    private String bigHead;
    private Long sellerId;
    private String openId;
    private StaffInfo staffInfo;
}



