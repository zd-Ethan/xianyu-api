package com.feijimiao.xianyuassistant.service.sales;

import com.feijimiao.xianyuassistant.entity.XianyuGoodsOrder;
import com.feijimiao.xianyuassistant.entity.XianyuSalesOrder;
import com.feijimiao.xianyuassistant.mapper.XianyuSalesOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/** 将本平台已观察到的订单增量写入销售事实表。 */
@Slf4j
@Service
public class LocalSalesFactService {
    private static final DateTimeFormatter DB_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private XianyuSalesOrderMapper salesOrderMapper;

    @Autowired
    private Clock salesClock;

    private final SalesOrderParser orderParser = new SalesOrderParser();

    /**
     * 记录卖家订单列表中已返回的订单。
     *
     * @param accountId 闲鱼账号 ID。
     * @param source 卖家订单列表项。
     * @return 是否写入了具有稳定订单号的销售事实。
     */
    public boolean recordSellerOrder(Long accountId, Map<String, Object> source) {
        if (accountId == null || source == null || source.isEmpty()) {
            return false;
        }
        return parseAndUpsert(accountId, source, LocalDateTime.now(salesClock));
    }

    /**
     * 记录 WebSocket 已付款消息；消息本身没有价格时，后续订单详情会补齐金额。
     *
     * @param accountId 闲鱼账号 ID。
     * @param orderId 稳定订单号。
     * @param xyGoodsId 闲鱼商品 ID。
     */
    public boolean recordPaymentMessage(Long accountId, String orderId, String xyGoodsId) {
        if (accountId == null || isBlank(orderId)) {
            return false;
        }

        LocalDateTime observedAt = LocalDateTime.now(salesClock);
        Map<String, Object> commonData = new HashMap<>();
        commonData.put("orderId", orderId.trim());
        commonData.put("itemId", xyGoodsId);
        commonData.put("orderStatus", SalesOrderStatus.PENDING_SHIP);
        commonData.put("paySuccessTime", observedAt.format(DB_DATE_TIME));

        Map<String, Object> priceData = new HashMap<>();
        priceData.put("buyNum", 1);

        Map<String, Object> source = new HashMap<>();
        source.put("commonData", commonData);
        source.put("priceVO", priceData);
        return parseAndUpsert(accountId, source, observedAt);
    }

    /**
     * 把本地发货订单中的有效业务字段补入销售事实。
     *
     * @param order 本地发货订单。
     */
    public boolean recordGoodsOrder(XianyuGoodsOrder order) {
        if (order == null || order.getXianyuAccountId() == null || isBlank(order.getOrderId())) {
            return false;
        }

        Map<String, Object> commonData = new HashMap<>();
        commonData.put("orderId", order.getOrderId());
        commonData.put("itemId", order.getXyGoodsId());
        commonData.put("orderStatus", order.getOrderStatus());
        putIfNotBlank(commonData, "paySuccessTime", firstNotBlank(
                order.getPaySuccessTime(), order.getOrderCreateTime(), order.getCreateTime()));

        Map<String, Object> priceData = new HashMap<>();
        if (order.getBuyNum() != null) {
            priceData.put("buyNum", order.getBuyNum());
        }
        putIfNotBlank(priceData, "totalPrice", order.getTotalPrice());

        Map<String, Object> source = new HashMap<>();
        source.put("commonData", commonData);
        source.put("priceVO", priceData);
        return recordSellerOrder(order.getXianyuAccountId(), source);
    }

    /**
     * 将订单详情响应转换成列表项结构，刷新状态、退款数量和退款金额。
     *
     * @param accountId 闲鱼账号 ID。
     * @param orderId 稳定订单号。
     * @param detailResponse 订单详情接口 data 节点。
     */
    public boolean recordOrderDetail(Long accountId, String orderId, Map<String, Object> detailResponse) {
        if (accountId == null || isBlank(orderId) || detailResponse == null) {
            return false;
        }

        Map<String, Object> module = mutableMap(detailResponse.get("module"));
        if (module.isEmpty()) {
            module = detailResponse;
        }

        Map<String, Object> commonData = firstMutableMap(
                module.get("merchantCommonData"), module.get("commonData"));
        commonData.putIfAbsent("orderId", orderId.trim());
        copyFirstPresent(module, commonData, "orderStatus", "orderStatus", "status");
        copyFirstPresent(module, commonData, "refundStatus", "refundStatus", "refundStatusDesc");

        Map<String, Object> itemData = firstMutableMap(
                module.get("merchantItemVO"), module.get("itemVO"));
        copyFirstPresent(itemData, commonData, "itemId", "itemId", "goodsId");

        Map<String, Object> priceData = firstMutableMap(
                module.get("merchantPriceVO"), module.get("priceVO"));
        Map<String, Object> refundData = firstMutableMap(
                module.get("refundInfoVO"), module.get("merchantRefundInfoVO"),
                commonData.get("refundInfoVO"), detailResponse.get("refundInfoVO"));

        Map<String, Object> source = new HashMap<>();
        source.put("commonData", commonData);
        source.put("priceVO", priceData);
        if (!refundData.isEmpty()) {
            source.put("refundInfoVO", refundData);
        }
        copyIfPresent(module, source, "refundStatus");
        copyIfPresent(module, source, "refundStatusDesc");
        return recordSellerOrder(accountId, source);
    }

    private boolean parseAndUpsert(Long accountId, Map<String, Object> source, LocalDateTime syncedAt) {
        try {
            XianyuSalesOrder order = orderParser.parse(accountId, source, syncedAt);
            if (order == null) {
                return false;
            }
            salesOrderMapper.upsert(order);
            return true;
        } catch (Exception exception) {
            log.warn("【账号{}】写入本地销售事实失败: {}", accountId, exception.getMessage());
            return false;
        }
    }

    private Map<String, Object> firstMutableMap(Object... values) {
        for (Object value : values) {
            Map<String, Object> result = mutableMap(value);
            if (!result.isEmpty()) {
                return result;
            }
        }
        return new HashMap<>();
    }

    private Map<String, Object> mutableMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return new HashMap<>();
        }
        Map<String, Object> result = new HashMap<>();
        map.forEach((key, item) -> {
            if (key != null) {
                result.put(key.toString(), item);
            }
        });
        return result;
    }

    private void copyFirstPresent(
            Map<String, Object> source,
            Map<String, Object> target,
            String targetKey,
            String... sourceKeys
    ) {
        Object existingValue = target.get(targetKey);
        if (existingValue != null && !existingValue.toString().isBlank()) {
            return;
        }
        for (String sourceKey : sourceKeys) {
            Object value = source.get(sourceKey);
            if (value != null && !value.toString().isBlank()) {
                target.put(targetKey, value);
                return;
            }
        }
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        Object value = source.get(key);
        if (value != null && !value.toString().isBlank()) {
            target.put(key, value);
        }
    }

    private void putIfNotBlank(Map<String, Object> target, String key, Object value) {
        if (value != null && !value.toString().isBlank()) {
            target.put(key, value);
        }
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
