package com.feijimiao.xianyuassistant.service.sales;

import java.util.Locale;

/** 销售统计专用的平台状态，不复用自动发货状态归一化规则。 */
public final class SalesOrderStatus {
    public static final String PENDING_PAYMENT = "pending_payment";
    public static final String PENDING_SHIP = "pending_ship";
    public static final String SHIPPED = "shipped";
    public static final String COMPLETED = "completed";
    public static final String CANCELLED = "cancelled";
    public static final String REFUNDING = "refunding";
    public static final String REFUNDED = "refunded";
    public static final String REFUND_CLOSED = "refund_closed";
    public static final String UNKNOWN = "unknown";

    private SalesOrderStatus() {
    }

    /** 将平台中文或英文状态归一为销售统计状态。 */
    public static String normalize(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return UNKNOWN;
        }

        String status = rawStatus.trim();
        String lowerStatus = status.toLowerCase(Locale.ROOT);
        if (lowerStatus.equals(REFUND_CLOSED) || status.contains("退款关闭")) {
            return REFUND_CLOSED;
        }
        if (lowerStatus.equals(REFUNDED) || status.contains("退款成功") || status.contains("已退款")) {
            return REFUNDED;
        }
        if (lowerStatus.equals(REFUNDING) || status.contains("退款中") || status.contains("售后处理中")) {
            return REFUNDING;
        }
        if (lowerStatus.equals(PENDING_PAYMENT)
                || status.contains("待付款")
                || status.contains("等待买家付款")) {
            return PENDING_PAYMENT;
        }
        if (lowerStatus.equals(PENDING_SHIP)
                || status.contains("待发货")
                || status.contains("等待卖家发货")) {
            return PENDING_SHIP;
        }
        if (lowerStatus.equals(SHIPPED)
                || status.contains("已发货")
                || status.contains("待买家收货")
                || status.contains("等待买家收货")
                || status.contains("等待买家确认收货")) {
            return SHIPPED;
        }
        if (lowerStatus.equals(COMPLETED) || status.contains("交易成功")) {
            return COMPLETED;
        }
        if (lowerStatus.equals(CANCELLED) || status.contains("交易关闭")) {
            return CANCELLED;
        }
        return UNKNOWN;
    }

    public static boolean isRefundSucceeded(String platformStatus) {
        return REFUNDED.equals(platformStatus);
    }
}
