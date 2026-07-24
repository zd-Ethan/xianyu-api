package com.feijimiao.xianyuassistant.service.sales;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SalesOrderStatusTest {
    @Test
    void shouldKeepRefundClosedSeparateFromCancelled() {
        assertEquals(SalesOrderStatus.REFUND_CLOSED, SalesOrderStatus.normalize("退款关闭"));
        assertEquals(SalesOrderStatus.CANCELLED, SalesOrderStatus.normalize("交易关闭"));
    }

    @Test
    void shouldRecognizePlatformWaitingStatusVariants() {
        assertEquals(SalesOrderStatus.PENDING_PAYMENT, SalesOrderStatus.normalize("等待买家付款"));
        assertEquals(SalesOrderStatus.PENDING_SHIP, SalesOrderStatus.normalize("等待卖家发货"));
        assertEquals(SalesOrderStatus.SHIPPED, SalesOrderStatus.normalize("等待买家确认收货"));
    }
}
