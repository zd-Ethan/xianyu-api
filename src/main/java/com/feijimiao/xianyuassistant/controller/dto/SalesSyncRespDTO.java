package com.feijimiao.xianyuassistant.controller.dto;

import lombok.Data;

@Data
public class SalesSyncRespDTO {
    private String message;
    private int requestedAccountCount;
    private int successAccountCount;
    private int failedAccountCount;
    private int skippedAccountCount;
    private int syncedOrderCount;
    private int dataQualityErrorCount;
}
