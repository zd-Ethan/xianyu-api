package com.feijimiao.xianyuassistant.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("xianyu_sales_sync_state")
public class XianyuSalesSyncState {
    @TableId
    private Long xianyuAccountId;
    private String syncStatus;
    private String lastStartedAt;
    private String lastSuccessAt;
    private String lastIncrementalSuccessAt;
    private String lastFullSuccessAt;
    private String lastError;
    private Integer syncedOrderCount;
    private Integer dataQualityErrorCount;
    private String updateTime;
}
