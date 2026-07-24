package com.feijimiao.xianyuassistant.service.sales;

import com.feijimiao.xianyuassistant.entity.XianyuGoodsOrder;
import com.feijimiao.xianyuassistant.entity.XianyuSalesOrder;
import com.feijimiao.xianyuassistant.mapper.XianyuSalesOrderMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class LocalSalesFactServiceTest {
    private static final Clock SALES_CLOCK = Clock.fixed(
            Instant.parse("2026-07-23T00:30:00Z"), ZoneId.of("Asia/Shanghai"));

    @Test
    void shouldRecordPaymentMessageWithStableOrderId() {
        XianyuSalesOrderMapper mapper = mock(XianyuSalesOrderMapper.class);
        LocalSalesFactService service = service(mapper);

        assertTrue(service.recordPaymentMessage(7L, "ORDER-1", "ITEM-1"));

        verify(mapper).upsert(any(XianyuSalesOrder.class));
        var orderCaptor = org.mockito.ArgumentCaptor.forClass(XianyuSalesOrder.class);
        verify(mapper).upsert(orderCaptor.capture());
        XianyuSalesOrder order = orderCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals("ORDER-1", order.getOrderId());
        org.junit.jupiter.api.Assertions.assertEquals("2026-07-23", order.getPaidDate());
        org.junit.jupiter.api.Assertions.assertEquals(1, order.getQuantity());
    }

    @Test
    void shouldIgnoreLocalOrderWithoutStableOrderId() {
        XianyuSalesOrderMapper mapper = mock(XianyuSalesOrderMapper.class);
        LocalSalesFactService service = service(mapper);
        XianyuGoodsOrder order = new XianyuGoodsOrder();
        order.setXianyuAccountId(7L);

        assertFalse(service.recordGoodsOrder(order));
        verify(mapper, never()).upsert(any());
    }

    @Test
    void shouldRecordRefundFromOrderDetail() {
        XianyuSalesOrderMapper mapper = mock(XianyuSalesOrderMapper.class);
        LocalSalesFactService service = service(mapper);

        Map<String, Object> commonData = new HashMap<>();
        commonData.put("orderStatus", "退款成功");
        commonData.put("paySuccessTime", "2026-07-01 08:00:00");
        Map<String, Object> priceData = new HashMap<>();
        priceData.put("buyNum", 2);
        priceData.put("totalPrice", "20.00");
        Map<String, Object> detail = Map.of(
                "module", Map.of(
                        "merchantCommonData", commonData,
                        "merchantPriceVO", priceData));

        assertTrue(service.recordOrderDetail(7L, "ORDER-2", detail));

        var orderCaptor = org.mockito.ArgumentCaptor.forClass(XianyuSalesOrder.class);
        verify(mapper).upsert(orderCaptor.capture());
        XianyuSalesOrder order = orderCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals("refunded", order.getPlatformStatus());
        org.junit.jupiter.api.Assertions.assertEquals(2, order.getRefundedQuantity());
        org.junit.jupiter.api.Assertions.assertEquals(2000L, order.getRefundedAmountCents());
    }

    private LocalSalesFactService service(XianyuSalesOrderMapper mapper) {
        LocalSalesFactService service = new LocalSalesFactService();
        ReflectionTestUtils.setField(service, "salesOrderMapper", mapper);
        ReflectionTestUtils.setField(service, "salesClock", SALES_CLOCK);
        return service;
    }
}
