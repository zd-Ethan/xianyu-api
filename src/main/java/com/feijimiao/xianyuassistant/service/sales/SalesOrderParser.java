package com.feijimiao.xianyuassistant.service.sales;

import com.feijimiao.xianyuassistant.entity.XianyuSalesOrder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 将卖家订单列表项转换为独立销售事实。 */
public class SalesOrderParser {
    private static final ZoneId SALES_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DB_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final List<DateTimeFormatter> LOCAL_DATE_TIME_FORMATS = List.of(
            DB_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME
    );
    private static final Pattern DECIMAL_PATTERN = Pattern.compile("-?[0-9][0-9,]*(?:\\.[0-9]+)?");

    /**
     * 解析平台订单。
     *
     * @param accountId 闲鱼账号 ID。
     * @param source 平台订单列表项。
     * @param syncedAt 本次同步时间。
     * @return 可写入数据库的销售订单；缺少订单 ID 时返回 null。
     */
    public XianyuSalesOrder parse(Long accountId, Map<String, Object> source, LocalDateTime syncedAt) {
        Map<String, Object> commonData = asMap(source.get("commonData"));
        Map<String, Object> priceData = asMap(source.get("priceVO"));

        String orderId = resolveOrderId(source);
        if (orderId == null || orderId.isBlank()) {
            return null;
        }

        String rawStatus = resolveRawStatus(source, commonData);
        String platformStatus = SalesOrderStatus.normalize(rawStatus);
        boolean platformStatusProvided = rawStatus != null
                && !SalesOrderStatus.UNKNOWN.equals(platformStatus);
        ParsedNumber<Integer> quantityField = firstNonNegativeInt(priceData, "buyNum", "quantity", "num");
        ParsedNumber<Long> grossAmountField = firstNonNegativeAmountCents(
                priceData, "totalPrice", "actualPay", "payAmount");
        boolean quantityProvided = quantityField.valid();
        boolean grossAmountProvided = grossAmountField.valid();
        int quantity = quantityField.value();
        long grossAmountCents = grossAmountField.value();

        Map<String, Object> refundData = asMap(source.get("refundInfoVO"));
        List<Map<String, Object>> refundSources = List.of(refundData, priceData, commonData);
        ParsedNumber<Integer> refundedQuantityField = firstNonNegativeIntAcrossMaps(
                refundSources, "refundedQuantity", "refundQuantity", "refundNum");
        ParsedNumber<Long> refundedAmountField = firstNonNegativeAmountCentsAcrossMaps(
                refundSources, "refundedAmount", "refundAmount", "refundFee");
        boolean refundedQuantityProvided = refundedQuantityField.valid();
        boolean refundedAmountProvided = refundedAmountField.valid();
        int refundedQuantity = refundedQuantityField.value();
        long refundedAmountCents = refundedAmountField.value();

        List<String> qualityErrors = new ArrayList<>();
        addStatusQualityError(qualityErrors, rawStatus, platformStatusProvided);
        addNumberQualityError(qualityErrors, quantityField, "购买数量格式非法");
        addNumberQualityError(qualityErrors, grossAmountField, "订单金额格式非法");
        addNumberQualityError(qualityErrors, refundedQuantityField, "退款数量格式非法");
        addNumberQualityError(qualityErrors, refundedAmountField, "退款金额格式非法");

        if (SalesOrderStatus.REFUND_CLOSED.equals(platformStatus)) {
            refundedQuantity = 0;
            refundedAmountCents = 0L;
            refundedQuantityProvided = true;
            refundedAmountProvided = true;
        } else if (SalesOrderStatus.isRefundSucceeded(platformStatus)) {
            // 两侧都缺失或单侧明确等于订单总值时，退款成功才可补成整单退款。
            if (!refundedQuantityField.present() && !refundedAmountField.present()) {
                if (quantityProvided) {
                    refundedQuantity = quantity;
                    refundedQuantityProvided = true;
                }
                if (grossAmountProvided) {
                    refundedAmountCents = grossAmountCents;
                    refundedAmountProvided = true;
                }
            }
            if (!refundedQuantityField.present() && refundedAmountProvided
                    && quantityProvided && grossAmountProvided && grossAmountCents > 0
                    && refundedAmountCents >= grossAmountCents) {
                refundedQuantity = quantity;
                refundedQuantityProvided = true;
            }
            if (!refundedAmountField.present() && refundedQuantityProvided
                    && quantityProvided && grossAmountProvided && quantity > 0
                    && refundedQuantity >= quantity) {
                refundedAmountCents = grossAmountCents;
                refundedAmountProvided = true;
            }
        } else if (SalesOrderStatus.REFUNDING.equals(platformStatus)) {
            refundedQuantity = 0;
            refundedAmountCents = 0L;
            refundedQuantityProvided = true;
            refundedAmountProvided = true;
        } else if (quantityProvided && grossAmountProvided && refundedAmountProvided
                && grossAmountCents > 0 && refundedAmountCents >= grossAmountCents
                && !refundedQuantityField.present()) {
            refundedQuantity = quantity;
            refundedQuantityProvided = true;
        }

        if (quantityProvided) {
            refundedQuantity = Math.min(refundedQuantity, quantity);
        }
        if (grossAmountProvided) {
            refundedAmountCents = Math.min(refundedAmountCents, grossAmountCents);
        }

        Object paidAtValue = firstValue(commonData, "paySuccessTime", "payTime", "paidTime");
        LocalDateTime paidAt = parsePlatformTime(paidAtValue);
        if (paidAtValue != null && paidAt == null) {
            qualityErrors.add("付款时间格式非法");
        }

        XianyuSalesOrder order = new XianyuSalesOrder();
        order.setXianyuAccountId(accountId);
        order.setOrderId(orderId);
        order.setXyGoodsId(firstString(commonData, "itemId", "goodsId"));
        order.setPlatformStatus(platformStatus);
        order.setRawStatus(rawStatus);
        order.setPaidAt(paidAt == null ? null : paidAt.format(DB_DATE_TIME));
        order.setPaidDate(paidAt == null ? null : paidAt.toLocalDate().toString());
        order.setQuantity(quantity);
        order.setGrossAmountCents(grossAmountCents);
        order.setRefundedQuantity(refundedQuantity);
        order.setRefundedAmountCents(refundedAmountCents);
        order.setLastSyncedAt(syncedAt.format(DB_DATE_TIME));
        order.setPlatformStatusProvided(platformStatusProvided);
        order.setQuantityProvided(quantityProvided);
        order.setGrossAmountProvided(grossAmountProvided);
        order.setRefundedQuantityProvided(refundedQuantityProvided);
        order.setRefundedAmountProvided(refundedAmountProvided);
        order.setDataQualityError(String.join("；", qualityErrors));
        return order;
    }

    /**
     * 读取平台订单的稳定 ID，供解析、分页水位和重复页检测共用。
     *
     * @param source 平台订单列表项。
     * @return 订单 ID；平台未返回时为 null。
     */
    public String resolveOrderId(Map<String, Object> source) {
        return firstString(asMap(source.get("commonData")), "orderId", "tradeId", "id");
    }

    /** 合并订单主状态、退款状态和标签，避免将退款后的交易关闭误判为普通取消。 */
    private String resolveRawStatus(Map<String, Object> source, Map<String, Object> commonData) {
        List<String> statusParts = new ArrayList<>();
        addStatusPart(statusParts, firstValue(commonData, "orderStatus", "status"));
        addStatusPart(statusParts, firstValue(commonData, "refundStatus", "refundStatusDesc"));
        addStatusPart(statusParts, firstValue(source, "refundStatus", "refundStatusDesc"));

        Map<String, Object> refundData = asMap(source.get("refundInfoVO"));
        addStatusPart(statusParts, firstValue(refundData, "refundStatus", "status", "statusDesc"));

        Object tags = commonData.get("tags");
        if (tags instanceof List<?> tagList) {
            for (Object tag : tagList) {
                addStatusPart(statusParts, tag);
            }
        }

        String rawStatus = String.join(" / ", statusParts);
        if (rawStatus.isBlank()) {
            return null;
        }
        return rawStatus.length() > 100 ? rawStatus.substring(0, 100) : rawStatus;
    }

    private void addStatusPart(List<String> statusParts, Object value) {
        if (value == null || value.toString().isBlank()) {
            return;
        }
        String status = value.toString().trim();
        if (!statusParts.contains(status)) {
            statusParts.add(status);
        }
    }

    private ParsedNumber<Integer> firstNonNegativeIntAcrossMaps(
            List<Map<String, Object>> candidates, String... keys) {
        boolean present = false;
        for (Map<String, Object> candidate : candidates) {
            ParsedNumber<Integer> field = firstNonNegativeInt(candidate, keys);
            present = present || field.present();
            if (field.valid()) {
                return new ParsedNumber<>(field.value(), true, true);
            }
        }
        return new ParsedNumber<>(0, present, false);
    }

    private ParsedNumber<Long> firstNonNegativeAmountCentsAcrossMaps(
            List<Map<String, Object>> candidates, String... keys) {
        boolean present = false;
        for (Map<String, Object> candidate : candidates) {
            ParsedNumber<Long> field = firstNonNegativeAmountCents(candidate, keys);
            present = present || field.present();
            if (field.valid()) {
                return new ParsedNumber<>(field.value(), true, true);
            }
        }
        return new ParsedNumber<>(0L, present, false);
    }

    private void addStatusQualityError(
            List<String> qualityErrors,
            String rawStatus,
            boolean platformStatusProvided
    ) {
        if (rawStatus == null) {
            qualityErrors.add("缺少订单状态");
        } else if (!platformStatusProvided) {
            qualityErrors.add("无法识别订单状态：" + rawStatus);
        }
    }

    private void addNumberQualityError(
            List<String> qualityErrors,
            ParsedNumber<?> field,
            String errorMessage
    ) {
        if (field.present() && !field.valid()) {
            qualityErrors.add(errorMessage);
        }
    }

    private LocalDateTime parsePlatformTime(Object value) {
        if (value == null) {
            return null;
        }

        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }

        if (text.matches("\\d{10,13}")) {
            long epochValue = Long.parseLong(text);
            long epochMillis = text.length() == 10 ? epochValue * 1000L : epochValue;
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), SALES_ZONE);
        }

        for (DateTimeFormatter formatter : LOCAL_DATE_TIME_FORMATS) {
            try {
                return LocalDateTime.parse(text, formatter);
            } catch (DateTimeParseException ignored) {
                // 继续尝试带时区的平台时间格式。
            }
        }

        try {
            return OffsetDateTime.parse(text).atZoneSameInstant(SALES_ZONE).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) map;
            return result;
        }
        return Map.of();
    }

    private Object firstValue(Map<String, Object> values, String... keys) {
        for (String key : keys) {
            Object value = values.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String firstString(Map<String, Object> values, String... keys) {
        Object value = firstValue(values, keys);
        return value == null ? null : value.toString().trim();
    }

    private ParsedNumber<Integer> firstNonNegativeInt(Map<String, Object> values, String... keys) {
        boolean present = false;
        for (String key : keys) {
            Object value = values.get(key);
            if (value == null || value.toString().isBlank()) {
                continue;
            }
            present = true;
            try {
                int parsed = new BigDecimal(value.toString().trim()).intValueExact();
                if (parsed >= 0) {
                    return new ParsedNumber<>(parsed, true, true);
                }
            } catch (ArithmeticException | NumberFormatException ignored) {
                // 继续尝试平台可能返回的同义字段。
            }
        }
        return new ParsedNumber<>(0, present, false);
    }

    private ParsedNumber<Long> firstNonNegativeAmountCents(Map<String, Object> values, String... keys) {
        boolean present = false;
        for (String key : keys) {
            Object value = values.get(key);
            if (value == null || value.toString().isBlank()) {
                continue;
            }
            present = true;
            Matcher matcher = DECIMAL_PATTERN.matcher(value.toString());
            if (!matcher.find()) {
                continue;
            }
            try {
                BigDecimal amount = new BigDecimal(matcher.group().replace(",", ""));
                long cents = amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
                if (cents >= 0) {
                    return new ParsedNumber<>(cents, true, true);
                }
            } catch (ArithmeticException | NumberFormatException ignored) {
                // 继续尝试平台可能返回的同义字段。
            }
        }
        return new ParsedNumber<>(0L, present, false);
    }

    /** 数值字段的解析结果，同时保留“出现过”和“解析成功”两个语义。 */
    private record ParsedNumber<T>(T value, boolean present, boolean valid) {
    }
}
