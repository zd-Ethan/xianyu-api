package com.feijimiao.xianyuassistant.controller.dto;

import lombok.Data;

import java.util.List;

@Data
public class SalesOverviewRespDTO {
    private long totalSold;
    private long todaySold;
    private long last7DaysSold;
    private long last30DaysSold;
    private long selectedMonthSold;
    private long totalAmountCents;
    private long selectedMonthAmountCents;
    private String selectedMonth;
    private List<SalesDailyPointDTO> dailySales;
    private String syncStatus;
    private String lastSyncedAt;
    private String lastFullSyncedAt;
    private String lastError;
    private boolean stale;
    private boolean fullSyncStale;
    private int accountCount;
    private int failedAccountCount;
    private int dataQualityErrorCount;
}
