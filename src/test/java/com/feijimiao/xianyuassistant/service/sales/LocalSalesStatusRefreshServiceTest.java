package com.feijimiao.xianyuassistant.service.sales;

import com.feijimiao.xianyuassistant.entity.XianyuAccount;
import com.feijimiao.xianyuassistant.mapper.XianyuAccountMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuSalesOrderMapper;
import com.feijimiao.xianyuassistant.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocalSalesStatusRefreshServiceTest {

    @Test
    void shouldRecordAttemptAfterOrderDetailRefresh() {
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);
        XianyuSalesOrderMapper salesOrderMapper = mock(XianyuSalesOrderMapper.class);
        OrderService orderService = mock(OrderService.class);
        XianyuAccount account = new XianyuAccount();
        account.setId(7L);
        when(accountMapper.selectList(null)).thenReturn(List.of(account));
        when(salesOrderMapper.selectOrderIdsForStatusRefresh(7L, 20))
                .thenReturn(List.of("ORDER-1"));
        when(orderService.getOrderDetailMap(7L, "ORDER-1")).thenReturn(Map.of());

        LocalSalesStatusRefreshService service = new LocalSalesStatusRefreshService();
        ReflectionTestUtils.setField(service, "accountMapper", accountMapper);
        ReflectionTestUtils.setField(service, "salesOrderMapper", salesOrderMapper);
        ReflectionTestUtils.setField(service, "orderService", orderService);
        ReflectionTestUtils.setField(service, "salesClock", Clock.fixed(
                Instant.parse("2026-07-23T00:30:00Z"), ZoneId.of("Asia/Shanghai")));

        service.refreshObservedOrders();

        verify(orderService).getOrderDetailMap(7L, "ORDER-1");
        verify(salesOrderMapper).markStatusRefreshAttempt(
                7L, "ORDER-1", "2026-07-23 08:30:00");
    }
}
