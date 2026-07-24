package com.feijimiao.xianyuassistant.controller;

import com.feijimiao.xianyuassistant.common.ResultObject;
import com.feijimiao.xianyuassistant.controller.dto.SalesOverviewReqDTO;
import com.feijimiao.xianyuassistant.controller.dto.SalesOverviewRespDTO;
import com.feijimiao.xianyuassistant.controller.dto.SalesSyncRespDTO;
import com.feijimiao.xianyuassistant.service.SalesStatisticsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

@Slf4j
@RestController
@RequestMapping("/api/data-panel")
@CrossOrigin(origins = "*")
public class SalesDashboardController {
    @Autowired
    private SalesStatisticsService salesStatisticsService;

    @Autowired
    private Clock salesClock;

    /** 查询统一口径的销售汇总、自然月日序列和同步状态。 */
    @PostMapping("/sales-overview")
    public ResultObject<SalesOverviewRespDTO> getSalesOverview(
            @RequestBody(required = false) SalesOverviewReqDTO request) {
        try {
            Long accountId = request == null ? null : request.getAccountId();
            YearMonth month = parseMonth(request == null ? null : request.getMonth());
            return ResultObject.success(salesStatisticsService.getOverview(accountId, month));
        } catch (IllegalArgumentException exception) {
            return ResultObject.validateFailed(exception.getMessage());
        } catch (Exception exception) {
            log.error("查询销售数据面板失败", exception);
            return ResultObject.failed("查询销售数据面板失败: " + exception.getMessage());
        }
    }

    /** 手动全量分页同步一个或全部账号的销售订单。 */
    @PostMapping("/sales-sync")
    public ResultObject<SalesSyncRespDTO> syncSalesOrders() {
        try {
            SalesSyncRespDTO response = new SalesSyncRespDTO();
            response.setSkippedAccountCount(1);
            response.setMessage("销售面板使用本平台订单记录，无需同步闲鱼历史订单");
            return ResultObject.success(response);
        } catch (IllegalArgumentException exception) {
            return ResultObject.validateFailed(exception.getMessage());
        } catch (Exception exception) {
            log.error("同步销售订单失败", exception);
            return ResultObject.failed("同步销售订单失败: " + exception.getMessage());
        }
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now(salesClock);
        }
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("月份格式必须为 YYYY-MM");
        }
    }
}
