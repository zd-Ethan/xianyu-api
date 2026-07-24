package com.feijimiao.xianyuassistant.service.sales;

import com.feijimiao.xianyuassistant.entity.XianyuSalesOrder;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SalesOrderParserTest {
    private final SalesOrderParser parser = new SalesOrderParser();
    private final LocalDateTime syncedAt = LocalDateTime.of(2026, 7, 22, 10, 30);

    @Test
    void shouldParsePaidOrderUsingShanghaiPaymentDate() {
        XianyuSalesOrder order = parser.parse(12L, buildOrder("待发货", "2026-07-21 23:45:10", "2", "19.90"), syncedAt);

        assertNotNull(order);
        assertEquals("pending_ship", order.getPlatformStatus());
        assertEquals("2026-07-21", order.getPaidDate());
        assertEquals(2, order.getQuantity());
        assertEquals(1990L, order.getGrossAmountCents());
        assertEquals(0, order.getRefundedQuantity());
    }

    @Test
    void shouldRemoveWholeOrderAfterRefundSucceeded() {
        XianyuSalesOrder order = parser.parse(12L, buildOrder("退款成功", "2026-07-01 08:00:00", 3, "30"), syncedAt);

        assertNotNull(order);
        assertEquals("refunded", order.getPlatformStatus());
        assertEquals(3, order.getRefundedQuantity());
        assertEquals(3000L, order.getRefundedAmountCents());
    }

    @Test
    void shouldNotInferWholeQuantityWhenOnlyPartialRefundAmountIsProvided() {
        Map<String, Object> source = buildOrder("退款成功", "2026-07-01 08:00:00", 3, "30");
        @SuppressWarnings("unchecked")
        Map<String, Object> priceData = (Map<String, Object>) source.get("priceVO");
        priceData.put("refundAmount", "10.00");

        XianyuSalesOrder order = parser.parse(12L, source, syncedAt);

        assertNotNull(order);
        assertEquals(0, order.getRefundedQuantity());
        assertEquals(1000L, order.getRefundedAmountCents());
        assertEquals(false, order.isRefundedQuantityProvided());
    }

    @Test
    void shouldNotInferWholeAmountWhenOnlyPartialRefundQuantityIsProvided() {
        Map<String, Object> source = buildOrder("退款成功", "2026-07-01 08:00:00", 3, "30");
        @SuppressWarnings("unchecked")
        Map<String, Object> priceData = (Map<String, Object>) source.get("priceVO");
        priceData.put("refundNum", 1);

        XianyuSalesOrder order = parser.parse(12L, source, syncedAt);

        assertNotNull(order);
        assertEquals(1, order.getRefundedQuantity());
        assertEquals(0L, order.getRefundedAmountCents());
        assertEquals(false, order.isRefundedAmountProvided());
    }

    @Test
    void shouldInferWholeQuantityWhenRefundAmountMatchesGrossAmount() {
        Map<String, Object> source = buildOrder("退款成功", "2026-07-01 08:00:00", 1, "10.00");
        @SuppressWarnings("unchecked")
        Map<String, Object> priceData = (Map<String, Object>) source.get("priceVO");
        priceData.put("refundAmount", "10.00");

        XianyuSalesOrder order = parser.parse(12L, source, syncedAt);

        assertNotNull(order);
        assertEquals(1, order.getRefundedQuantity());
        assertEquals(1000L, order.getRefundedAmountCents());
        assertEquals(true, order.isRefundedQuantityProvided());
    }

    @Test
    void shouldInferWholeAmountWhenRefundQuantityMatchesOrderQuantity() {
        Map<String, Object> source = buildOrder("退款成功", "2026-07-01 08:00:00", 1, "10.00");
        @SuppressWarnings("unchecked")
        Map<String, Object> priceData = (Map<String, Object>) source.get("priceVO");
        priceData.put("refundNum", 1);

        XianyuSalesOrder order = parser.parse(12L, source, syncedAt);

        assertNotNull(order);
        assertEquals(1, order.getRefundedQuantity());
        assertEquals(1000L, order.getRefundedAmountCents());
        assertEquals(true, order.isRefundedAmountProvided());
    }

    @Test
    void shouldTreatInvalidNumbersAsUnavailableInsteadOfZero() {
        Map<String, Object> source = buildOrder("交易成功", "2026-07-01 08:00:00", "--", "--");

        XianyuSalesOrder order = parser.parse(12L, source, syncedAt);

        assertNotNull(order);
        assertEquals(false, order.isQuantityProvided());
        assertEquals(false, order.isGrossAmountProvided());
        assertEquals(0, order.getQuantity());
        assertEquals(0L, order.getGrossAmountCents());
        assertEquals("购买数量格式非法；订单金额格式非法", order.getDataQualityError());
    }

    @Test
    void shouldResolveTradeIdForPaginationAndParsing() {
        Map<String, Object> source = buildOrder("待发货", "2026-07-01 08:00:00", 1, "10");
        @SuppressWarnings("unchecked")
        Map<String, Object> commonData = (Map<String, Object>) source.get("commonData");
        commonData.remove("orderId");
        commonData.put("tradeId", "TRADE-1001");

        assertEquals("TRADE-1001", parser.resolveOrderId(source));
        assertEquals("TRADE-1001", parser.parse(12L, source, syncedAt).getOrderId());
    }

    @Test
    void shouldNotOverwriteKnownStatusWithUnrecognizedPlatformText() {
        XianyuSalesOrder order = parser.parse(
                12L, buildOrder("平台新增状态", "2026-07-01 08:00:00", 1, "10"), syncedAt);

        assertNotNull(order);
        assertEquals("unknown", order.getPlatformStatus());
        assertEquals(false, order.isPlatformStatusProvided());
    }

    @Test
    void shouldCountOrderAgainWhenRefundWasClosed() {
        Map<String, Object> source = buildOrder("退款关闭", "2026-07-02 08:00:00", 1, "9.90");
        @SuppressWarnings("unchecked")
        Map<String, Object> priceData = (Map<String, Object>) source.get("priceVO");
        priceData.put("refundNum", 1);
        priceData.put("refundAmount", "9.90");

        XianyuSalesOrder order = parser.parse(12L, source, syncedAt);

        assertNotNull(order);
        assertEquals("refund_closed", order.getPlatformStatus());
        assertEquals(0, order.getRefundedQuantity());
        assertEquals(0L, order.getRefundedAmountCents());
    }

    @Test
    void shouldKeepSaleWhileRefundIsProcessing() {
        Map<String, Object> source = buildOrder("退款中", "2026-07-02 08:00:00", 2, "20.00");
        @SuppressWarnings("unchecked")
        Map<String, Object> priceData = (Map<String, Object>) source.get("priceVO");
        priceData.put("refundNum", 1);
        priceData.put("refundAmount", "10.00");

        XianyuSalesOrder order = parser.parse(12L, source, syncedAt);

        assertNotNull(order);
        assertEquals("refunding", order.getPlatformStatus());
        assertEquals(0, order.getRefundedQuantity());
        assertEquals(0L, order.getRefundedAmountCents());
    }

    @Test
    void shouldRecognizeRefundTagWhenMainStatusIsClosed() {
        Map<String, Object> source = buildOrder("交易关闭", "2026-07-03 08:00:00", 1, "12.00");
        @SuppressWarnings("unchecked")
        Map<String, Object> commonData = (Map<String, Object>) source.get("commonData");
        commonData.put("tags", java.util.List.of("退款成功"));

        XianyuSalesOrder order = parser.parse(12L, source, syncedAt);

        assertNotNull(order);
        assertEquals("refunded", order.getPlatformStatus());
        assertEquals(1, order.getRefundedQuantity());
    }

    @Test
    void shouldConvertEpochPaymentTimeToShanghaiTime() {
        XianyuSalesOrder order = parser.parse(12L, buildOrder("交易成功", "1784651400000", 1, "1"), syncedAt);

        assertNotNull(order);
        assertEquals("2026-07-22 00:30:00", order.getPaidAt());
        assertEquals("2026-07-22", order.getPaidDate());
    }

    private Map<String, Object> buildOrder(String status, String paidAt, Object quantity, String amount) {
        Map<String, Object> commonData = new HashMap<>();
        commonData.put("orderId", "ORDER-1001");
        commonData.put("itemId", "ITEM-2001");
        commonData.put("orderStatus", status);
        commonData.put("paySuccessTime", paidAt);

        Map<String, Object> priceData = new HashMap<>();
        priceData.put("buyNum", quantity);
        priceData.put("totalPrice", amount);

        Map<String, Object> source = new HashMap<>();
        source.put("commonData", commonData);
        source.put("priceVO", priceData);
        return source;
    }
}
