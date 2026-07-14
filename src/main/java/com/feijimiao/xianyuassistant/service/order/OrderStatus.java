package com.feijimiao.xianyuassistant.service.order;

public final class OrderStatus {

    private OrderStatus() {
    }

    public static final int DELIVERY_FAILED = -1;
    public static final int DELIVERY_PENDING = 0;
    public static final int DELIVERY_SUCCESS = 1;
    public static final int DELIVERY_RUNNING = 2;

    public static final String PLATFORM_PENDING_PAYMENT = "pending_payment";
    public static final String PLATFORM_PENDING_SHIP = "pending_ship";
    public static final String PLATFORM_SHIPPED = "shipped";
    public static final String PLATFORM_COMPLETED = "completed";
    public static final String PLATFORM_CANCELLED = "cancelled";
    public static final String PLATFORM_REFUNDING = "refunding";
    public static final String PLATFORM_REFUNDED = "refunded";
    public static final String PLATFORM_UNKNOWN = "unknown";

    private static final String TEXT_PENDING_PAYMENT = "\u5f85\u4ed8\u6b3e";
    private static final String TEXT_PENDING_SHIP = "\u5f85\u53d1\u8d27";
    private static final String TEXT_SHIPPED = "\u5df2\u53d1\u8d27";
    private static final String TEXT_COMPLETED = "\u4ea4\u6613\u6210\u529f";
    private static final String TEXT_CLOSED = "\u4ea4\u6613\u5173\u95ed";
    private static final String TEXT_REFUNDING = "\u9000\u6b3e\u4e2d";
    private static final String TEXT_REFUND_SUCCESS = "\u9000\u6b3e\u6210\u529f";
    private static final String TEXT_REFUNDED = "\u5df2\u9000\u6b3e";
    private static final String TEXT_REFUND_CLOSED = "\u9000\u6b3e\u5173\u95ed";

    public static String normalizePlatformStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return PLATFORM_UNKNOWN;
        }
        String status = rawStatus.trim();
        if (status.equals(PLATFORM_PENDING_PAYMENT) || status.contains(TEXT_PENDING_PAYMENT)) {
            return PLATFORM_PENDING_PAYMENT;
        }
        if (status.equals(PLATFORM_PENDING_SHIP) || status.contains(TEXT_PENDING_SHIP)) {
            return PLATFORM_PENDING_SHIP;
        }
        if (status.equals(PLATFORM_SHIPPED) || status.contains(TEXT_SHIPPED)) {
            return PLATFORM_SHIPPED;
        }
        if (status.equals(PLATFORM_COMPLETED) || status.contains(TEXT_COMPLETED)) {
            return PLATFORM_COMPLETED;
        }
        if (status.equals(PLATFORM_REFUNDING) || status.contains(TEXT_REFUNDING)) {
            return PLATFORM_REFUNDING;
        }
        if (status.equals(PLATFORM_REFUNDED) || status.contains(TEXT_REFUND_SUCCESS) || status.contains(TEXT_REFUNDED)) {
            return PLATFORM_REFUNDED;
        }
        if (status.equals(PLATFORM_CANCELLED) || status.contains(TEXT_CLOSED) || status.contains(TEXT_REFUND_CLOSED)) {
            return PLATFORM_CANCELLED;
        }
        return PLATFORM_UNKNOWN;
    }

    public static boolean isPendingShip(String rawStatus) {
        return PLATFORM_PENDING_SHIP.equals(normalizePlatformStatus(rawStatus));
    }

    public static boolean isDeliveryBlockedPlatformStatus(String rawStatus) {
        String status = normalizePlatformStatus(rawStatus);
        return PLATFORM_PENDING_PAYMENT.equals(status)
                || PLATFORM_CANCELLED.equals(status)
                || PLATFORM_REFUNDING.equals(status)
                || PLATFORM_REFUNDED.equals(status);
    }

    public static String getDeliveryBlockedReason(String rawStatus) {
        String status = normalizePlatformStatus(rawStatus);
        if (PLATFORM_PENDING_PAYMENT.equals(status)) {
            return "订单尚未付款，不能发货";
        }
        if (PLATFORM_CANCELLED.equals(status)) {
            return "订单已关闭，不能发货";
        }
        if (PLATFORM_REFUNDING.equals(status)) {
            return "订单退款中，不能发货";
        }
        if (PLATFORM_REFUNDED.equals(status)) {
            return "订单已退款，不能发货";
        }
        return null;
    }

    public static boolean isAlreadyDelivered(Integer deliveryState) {
        return deliveryState != null && deliveryState == DELIVERY_SUCCESS;
    }

    public static boolean isDelivering(Integer deliveryState) {
        return deliveryState != null && deliveryState == DELIVERY_RUNNING;
    }

    public static boolean canStartDelivery(Integer deliveryState) {
        return !isAlreadyDelivered(deliveryState) && !isDelivering(deliveryState);
    }
}
