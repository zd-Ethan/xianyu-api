package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.controller.dto.SalesSyncRespDTO;

public interface SalesOrderSyncService {
    /** 全量分页同步指定账号；账号为空时同步全部账号。 */
    SalesSyncRespDTO syncSalesOrders(Long accountId);
}
