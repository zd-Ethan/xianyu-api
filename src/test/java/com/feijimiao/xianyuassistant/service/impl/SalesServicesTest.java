package com.feijimiao.xianyuassistant.service.impl;

import com.feijimiao.xianyuassistant.controller.dto.SalesOverviewRespDTO;
import com.feijimiao.xianyuassistant.controller.dto.SalesSyncRespDTO;
import com.feijimiao.xianyuassistant.entity.XianyuAccount;
import com.feijimiao.xianyuassistant.entity.XianyuSalesSyncState;
import com.feijimiao.xianyuassistant.entity.bo.SalesSummaryBO;
import com.feijimiao.xianyuassistant.mapper.XianyuAccountMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuSalesOrderMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuSalesSyncStateMapper;
import com.feijimiao.xianyuassistant.service.OrderService;
import com.feijimiao.xianyuassistant.service.order.SellerOrderPage;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesServicesTest {
    private static final Clock SALES_CLOCK = Clock.fixed(
            Instant.parse("2026-07-22T16:30:00Z"), ZoneId.of("Asia/Shanghai"));

    @Test
    void shouldUseShanghaiDateAndAlwaysReportLocalDataStatus() {
        XianyuSalesOrderMapper salesOrderMapper = mock(XianyuSalesOrderMapper.class);
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);
        SalesStatisticsServiceImpl service = new SalesStatisticsServiceImpl();
        ReflectionTestUtils.setField(service, "salesOrderMapper", salesOrderMapper);
        ReflectionTestUtils.setField(service, "accountMapper", accountMapper);
        ReflectionTestUtils.setField(service, "salesClock", SALES_CLOCK);

        when(accountMapper.selectCount(null)).thenReturn(2L);
        when(salesOrderMapper.selectSummary(any(), any(), any(), any(), any(), any()))
                .thenReturn(new SalesSummaryBO());
        when(salesOrderMapper.selectDailySales(any(), any(), any())).thenReturn(List.of());
        SalesOverviewRespDTO response = service.getOverview(null, YearMonth.of(2026, 7));

        assertEquals("local", response.getSyncStatus());
        assertEquals(false, response.isStale());
        assertEquals(false, response.isFullSyncStale());
        assertEquals(2, response.getAccountCount());
        verify(salesOrderMapper).selectSummary(
                null, "2026-07-23", "2026-07-17", "2026-06-24", "2026-07-01", "2026-07-31");
    }

    @Test
    void shouldUseLocalOrdersForSalesOverview() {
        XianyuSalesOrderMapper salesOrderMapper = mock(XianyuSalesOrderMapper.class);
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);
        SalesStatisticsServiceImpl service = new SalesStatisticsServiceImpl();
        ReflectionTestUtils.setField(service, "salesOrderMapper", salesOrderMapper);
        ReflectionTestUtils.setField(service, "accountMapper", accountMapper);
        ReflectionTestUtils.setField(service, "salesClock", SALES_CLOCK);

        XianyuAccount account = new XianyuAccount();
        account.setId(7L);
        when(accountMapper.selectById(7L)).thenReturn(account);
        when(salesOrderMapper.selectSummary(
                eq(7L), eq("2026-07-23"), eq("2026-07-17"), eq("2026-06-24"),
                eq("2026-07-01"), eq("2026-07-31"))).thenReturn(new SalesSummaryBO());
        when(salesOrderMapper.selectDailySales(7L, "2026-07-01", "2026-07-31"))
                .thenReturn(List.of());

        SalesOverviewRespDTO response = service.getOverview(7L, YearMonth.of(2026, 7));

        assertEquals("local", response.getSyncStatus());
        assertEquals(false, response.isStale());
        verify(salesOrderMapper).selectSummary(any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldNotPersistAnyOrderWhenLaterPageFails() {
        OrderService orderService = mock(OrderService.class);
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);
        XianyuSalesOrderMapper salesOrderMapper = mock(XianyuSalesOrderMapper.class);
        XianyuSalesSyncStateMapper syncStateMapper = mock(XianyuSalesSyncStateMapper.class);
        SalesOrderSyncServiceImpl service = new SalesOrderSyncServiceImpl();
        ReflectionTestUtils.setField(service, "orderService", orderService);
        ReflectionTestUtils.setField(service, "accountMapper", accountMapper);
        ReflectionTestUtils.setField(service, "salesOrderMapper", salesOrderMapper);
        ReflectionTestUtils.setField(service, "syncStateMapper", syncStateMapper);
        ReflectionTestUtils.setField(service, "salesClock", SALES_CLOCK);

        XianyuAccount account = new XianyuAccount();
        account.setId(7L);
        when(accountMapper.selectById(7L)).thenReturn(account);
        when(orderService.querySellerOrderPage(7L, "ALL", 1, 50))
                .thenReturn(SellerOrderPage.success(List.of(buildOrder()), true));
        when(orderService.querySellerOrderPage(7L, "ALL", 2, 50))
                .thenReturn(SellerOrderPage.failed("第二页请求失败"));
        when(salesOrderMapper.selectExistingOrderIds(eq(7L), any())).thenReturn(List.of());

        SalesSyncRespDTO response = service.syncSalesOrders(7L);

        assertEquals(1, response.getFailedAccountCount());
        assertEquals(0, response.getSyncedOrderCount());
        verify(salesOrderMapper, never()).upsert(any());
        verify(syncStateMapper).markFailed(eq(7L), any(), eq("第二页请求失败"), eq(0));
    }

    @Test
    void shouldFailWholeSyncWhenAnOrderHasNoStableId() {
        OrderService orderService = mock(OrderService.class);
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);
        XianyuSalesOrderMapper salesOrderMapper = mock(XianyuSalesOrderMapper.class);
        XianyuSalesSyncStateMapper syncStateMapper = mock(XianyuSalesSyncStateMapper.class);
        SalesOrderSyncServiceImpl service = new SalesOrderSyncServiceImpl();
        ReflectionTestUtils.setField(service, "orderService", orderService);
        ReflectionTestUtils.setField(service, "accountMapper", accountMapper);
        ReflectionTestUtils.setField(service, "salesOrderMapper", salesOrderMapper);
        ReflectionTestUtils.setField(service, "syncStateMapper", syncStateMapper);
        ReflectionTestUtils.setField(service, "salesClock", SALES_CLOCK);

        XianyuAccount account = new XianyuAccount();
        account.setId(7L);
        when(accountMapper.selectById(7L)).thenReturn(account);
        Map<String, Object> invalidOrder = new HashMap<>(buildOrder());
        invalidOrder.put("commonData", Map.of(
                "orderStatus", "待发货",
                "paySuccessTime", "2026-07-23 00:10:00"
        ));
        when(orderService.querySellerOrderPage(7L, "ALL", 1, 50))
                .thenReturn(SellerOrderPage.success(List.of(invalidOrder), false));

        SalesSyncRespDTO response = service.syncSalesOrders(7L);

        assertEquals(1, response.getFailedAccountCount());
        assertEquals(1, response.getDataQualityErrorCount());
        verify(salesOrderMapper, never()).upsert(any());
        verify(syncStateMapper).markFailed(eq(7L), any(), any(), eq(1));
    }

    private XianyuSalesSyncState successState(Long accountId, String successAt, String fullSuccessAt) {
        XianyuSalesSyncState state = new XianyuSalesSyncState();
        state.setXianyuAccountId(accountId);
        state.setSyncStatus("success");
        state.setLastSuccessAt(successAt);
        state.setLastFullSuccessAt(fullSuccessAt);
        return state;
    }

    private Map<String, Object> buildOrder() {
        Map<String, Object> commonData = new HashMap<>();
        commonData.put("orderId", "ORDER-1");
        commonData.put("orderStatus", "待发货");
        commonData.put("paySuccessTime", "2026-07-23 00:10:00");

        Map<String, Object> priceData = new HashMap<>();
        priceData.put("buyNum", 1);
        priceData.put("totalPrice", "10.00");
        return Map.of("commonData", commonData, "priceVO", priceData);
    }
}
