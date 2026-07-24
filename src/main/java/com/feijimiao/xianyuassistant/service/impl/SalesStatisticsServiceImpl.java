package com.feijimiao.xianyuassistant.service.impl;

import com.feijimiao.xianyuassistant.controller.dto.SalesDailyPointDTO;
import com.feijimiao.xianyuassistant.controller.dto.SalesOverviewRespDTO;
import com.feijimiao.xianyuassistant.entity.bo.SalesDailyBO;
import com.feijimiao.xianyuassistant.entity.bo.SalesSummaryBO;
import com.feijimiao.xianyuassistant.mapper.XianyuAccountMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuSalesOrderMapper;
import com.feijimiao.xianyuassistant.service.SalesStatisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SalesStatisticsServiceImpl implements SalesStatisticsService {
    @Autowired
    private XianyuSalesOrderMapper salesOrderMapper;

    @Autowired
    private XianyuAccountMapper accountMapper;

    @Autowired
    private Clock salesClock;

    @Override
    public SalesOverviewRespDTO getOverview(Long accountId, YearMonth month) {
        if (accountId != null && accountMapper.selectById(accountId) == null) {
            throw new IllegalArgumentException("所选闲鱼账号不存在");
        }

        LocalDate today = LocalDate.now(salesClock);
        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();
        SalesSummaryBO summary = selectSummary(
                accountId, today, monthStart, monthEnd);

        List<SalesDailyBO> rows = selectDailySales(
                accountId, monthStart.toString(), monthEnd.toString());
        Map<String, SalesDailyBO> rowsByDate = new HashMap<>();
        for (SalesDailyBO row : rows) {
            rowsByDate.put(row.getDate(), row);
        }

        List<SalesDailyPointDTO> dailySales = new ArrayList<>();
        for (LocalDate date = monthStart; !date.isAfter(monthEnd); date = date.plusDays(1)) {
            SalesDailyBO row = rowsByDate.get(date.toString());
            dailySales.add(new SalesDailyPointDTO(
                    date.toString(),
                    row == null ? 0L : valueOrZero(row.getQuantity()),
                    row == null ? 0L : valueOrZero(row.getAmountCents())
            ));
        }

        SalesOverviewRespDTO response = new SalesOverviewRespDTO();
        response.setTotalSold(valueOrZero(summary.getTotalQuantity()));
        response.setTodaySold(valueOrZero(summary.getTodayQuantity()));
        response.setLast7DaysSold(valueOrZero(summary.getLast7DaysQuantity()));
        response.setLast30DaysSold(valueOrZero(summary.getLast30DaysQuantity()));
        response.setSelectedMonthSold(valueOrZero(summary.getMonthQuantity()));
        response.setTotalAmountCents(valueOrZero(summary.getTotalAmountCents()));
        response.setSelectedMonthAmountCents(valueOrZero(summary.getMonthAmountCents()));
        response.setSelectedMonth(month.toString());
        response.setDailySales(dailySales);
        applyLocalDataStatus(response, accountId);
        return response;
    }

    @Override
    public List<Long> getDailyRevenueCents(Long accountId, LocalDate startDate, LocalDate endDate) {
        Map<String, Long> amountByDate = new HashMap<>();
        for (SalesDailyBO row : selectDailySales(accountId, startDate.toString(), endDate.toString())) {
            amountByDate.put(row.getDate(), valueOrZero(row.getAmountCents()));
        }

        List<Long> result = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            result.add(amountByDate.getOrDefault(date.toString(), 0L));
        }
        return result;
    }

    private SalesSummaryBO selectSummary(
            Long accountId, LocalDate today, LocalDate monthStart, LocalDate monthEnd) {
        return salesOrderMapper.selectSummary(
                accountId,
                today.toString(),
                today.minusDays(6).toString(),
                today.minusDays(29).toString(),
                monthStart.toString(),
                monthEnd.toString());
    }

    private List<SalesDailyBO> selectDailySales(Long accountId, String startDate, String endDate) {
        return salesOrderMapper.selectDailySales(accountId, startDate, endDate);
    }

    /** 本地订单会随 WebSocket、订单详情和发货流程更新，不依赖外部全量同步状态。 */
    private void applyLocalDataStatus(SalesOverviewRespDTO response, Long accountId) {
        response.setSyncStatus("local");
        response.setStale(false);
        response.setFullSyncStale(false);
        response.setAccountCount(accountId == null ? Math.toIntExact(accountMapper.selectCount(null)) : 1);
        response.setFailedAccountCount(0);
        response.setDataQualityErrorCount(0);
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }
}
