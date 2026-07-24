package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.controller.dto.SalesOverviewRespDTO;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public interface SalesStatisticsService {
    /** 查询指定账号和自然月的统一销售统计。 */
    SalesOverviewRespDTO getOverview(Long accountId, YearMonth month);

    /** 查询净销售额日序列，供兼容接口复用。 */
    List<Long> getDailyRevenueCents(Long accountId, LocalDate startDate, LocalDate endDate);
}
