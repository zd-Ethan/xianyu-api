package com.feijimiao.xianyuassistant.service.impl;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsAutoDeliveryConfig;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsOrder;
import com.feijimiao.xianyuassistant.exception.BusinessException;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsAutoDeliveryConfigMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsOrderMapper;
import com.feijimiao.xianyuassistant.service.AccountService;
import com.feijimiao.xianyuassistant.service.OrderService;
import com.feijimiao.xianyuassistant.service.delivery.DeliveryContext;
import com.feijimiao.xianyuassistant.service.delivery.DeliveryStrategyResolver;
import com.feijimiao.xianyuassistant.service.order.DeliveryLockManager;
import com.feijimiao.xianyuassistant.service.order.OrderStatus;
import com.feijimiao.xianyuassistant.service.order.SellerOrderPage;
import com.feijimiao.xianyuassistant.service.sales.LocalSalesFactService;
import com.feijimiao.xianyuassistant.utils.XianyuApiCallUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * 订单服务实现
 */
@Slf4j
@Service
public class OrderServiceImpl implements OrderService {
    
    @Autowired
    private AccountService accountService;
    
    @Autowired
    private XianyuApiCallUtils xianyuApiCallUtils;

    @Autowired
    private XianyuGoodsOrderMapper orderMapper;

    @Autowired
    private XianyuGoodsAutoDeliveryConfigMapper autoDeliveryConfigMapper;

    @Autowired
    private DeliveryStrategyResolver deliveryStrategyResolver;

    @Autowired
    private DeliveryLockManager deliveryLockManager;

    @Autowired
    @Lazy
    private LocalSalesFactService localSalesFactService;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    
    @Override
    public String confirmShipment(Long accountId, String orderId) {
        return confirmShipmentToXianyu(accountId, orderId);
    }

    @Override
    public String consignDummyDelivery(Long accountId, String orderId, String tradeText, List<String> imageUrls) {
        try {
            log.info("【账号{}】开始调用闲鱼新发货API(虚拟发货): orderId={}", accountId, orderId);

            String cookieStr = accountService.getCookieByAccountId(accountId);
            if (cookieStr == null || cookieStr.isEmpty()) {
                log.error("【账号{}】未找到Cookie", accountId);
                return null;
            }

            String limitedText = tradeText;
            if (limitedText != null && limitedText.length() > 200) {
                limitedText = limitedText.substring(0, 200);
                log.info("【账号{}】发货内容超过200字，已截断", accountId);
            }

            List<String> limitedImages = new ArrayList<>();
            if (imageUrls != null && !imageUrls.isEmpty()) {
                int limit = Math.min(imageUrls.size(), 3);
                limitedImages = imageUrls.subList(0, limit);
                if (imageUrls.size() > 3) {
                    log.info("【账号{}】发货图片超过3张，已截断", accountId);
                }
            }

            String picListJson;
            if (limitedImages.isEmpty()) {
                picListJson = "[]";
            } else {
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < limitedImages.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append("\"").append(limitedImages.get(i)).append("\"");
                }
                sb.append("]");
                picListJson = sb.toString();
            }

            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("orderId", orderId);
            dataMap.put("tradeText", limitedText != null ? limitedText : "");
            dataMap.put("picList", picListJson);
            dataMap.put("newUnconsign", true);

            Map<String, String> extraHeaders = new HashMap<>();
            extraHeaders.put("idle_site_biz_code", "COMMONPRO");
            extraHeaders.put("Origin", "https://seller.goofish.com");
            extraHeaders.put("Referer", "https://seller.goofish.com/?site=COMMONPRO");

            XianyuApiCallUtils.ApiCallResult result = xianyuApiCallUtils.callApiWithRetry(
                    accountId,
                    "mtop.taobao.idle.logistics.merchant.consign.dummy",
                    dataMap,
                    cookieStr,
                    extraHeaders
            );

            if (!result.isSuccess()) {
                String errorMsg = result.getErrorMessage();
                log.error("【账号{}】❌ 闲鱼新发货API失败: {}", accountId, errorMsg);

                if (result.isTokenExpired()) {
                    return null;
                }

                if (errorMsg != null && errorMsg.contains("ORDER_ALREADY_DELIVERY")) {
                    log.info("【账号{}】订单已发货(ORDER_ALREADY_DELIVERY)，视为成功: orderId={}", accountId, orderId);
                    return "虚拟发货成功(已发货)";
                }

                return null;
            }

            Map<String, Object> responseData = result.extractData();
            if (responseData != null) {
                log.info("【账号{}】✅ 闲鱼新发货API成功: orderId={}", accountId, orderId);
                return "虚拟发货成功";
            } else {
                log.error("【账号{}】响应数据格式错误", accountId);
                return null;
            }

        } catch (Exception e) {
            log.error("【账号{}】调用闲鱼新发货API异常: orderId={}", accountId, orderId, e);
            return null;
        }
    }
    
    @Override
    public String confirmShipmentToXianyu(Long accountId, String orderId) {
        try {
            log.info("【账号{}】开始调用闲鱼API确认发货: orderId={}", accountId, orderId);
            
            String cookieStr = accountService.getCookieByAccountId(accountId);
            if (cookieStr == null || cookieStr.isEmpty()) {
                log.error("【账号{}】未找到Cookie", accountId);
                return null;
            }
            
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("orderId", orderId);
            dataMap.put("tradeText", "");
            dataMap.put("picList", new String[0]);
            dataMap.put("newUnconsign", true);
            
            log.info("【账号{}】data参数: {}", accountId, dataMap);
            
            XianyuApiCallUtils.ApiCallResult result = xianyuApiCallUtils.callApiWithRetry(
                    accountId, 
                    "mtop.taobao.idle.logistic.consign.dummy", 
                    dataMap, 
                    cookieStr
            );
            
            if (!result.isSuccess()) {
                String errorMsg = result.getErrorMessage();
                log.error("【账号{}】❌ 闲鱼API确认发货失败: {}", accountId, errorMsg);
                
                if (result.isTokenExpired()) {
                    return null;
                }

                if (errorMsg != null && errorMsg.contains("ORDER_ALREADY_DELIVERY")) {
                    log.info("【账号{}】订单已发货(ORDER_ALREADY_DELIVERY)，视为确认成功: orderId={}", accountId, orderId);
                    return "确认发货成功(已发货)";
                }
                
                return null;
            }
            
            Map<String, Object> responseData = result.extractData();
            if (responseData != null) {
                log.info("【账号{}】✅ 闲鱼API确认发货成功: orderId={}", accountId, orderId);
                return "确认发货成功";
            } else {
                log.error("【账号{}】响应数据格式错误", accountId);
                return null;
            }
            
        } catch (Exception e) {
            log.error("【账号{}】调用闲鱼API确认发货异常: orderId={}", accountId, orderId, e);
            return null;
        }
    }

    @Override
    public String getOrderDetail(Long accountId, String orderId) {
        try {
            log.info("【账号{}】开始获取订单详情: orderId={}", accountId, orderId);

            String cookieStr = accountService.getCookieByAccountId(accountId);
            if (cookieStr == null || cookieStr.isEmpty()) {
                log.error("【账号{}】未找到Cookie", accountId);
                return null;
            }

            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("tid", orderId);

            XianyuApiCallUtils.ApiCallResult result = xianyuApiCallUtils.callApiWithRetry(
                    accountId,
                    "mtop.taobao.idle.trade.merchant.full.info",
                    dataMap,
                    cookieStr
            );

            if (!result.isSuccess()) {
                log.warn("【账号{}】获取订单详情失败: orderId={}, error={}", accountId, orderId, result.getErrorMessage());
                return null;
            }

            Map<String, Object> responseData = result.extractData();
            if (responseData == null) {
                log.warn("【账号{}】订单详情响应数据为空: orderId={}", accountId, orderId);
                return null;
            }

            localSalesFactService.recordOrderDetail(accountId, orderId, responseData);
            String json = objectMapper.writeValueAsString(responseData);
            log.info("【账号{}】获取订单详情成功: orderId={}", accountId, orderId);

            updateOrderDetailFromApi(accountId, orderId, responseData);

            return json;
        } catch (Exception e) {
            log.error("【账号{}】获取订单详情异常: orderId={}", accountId, orderId, e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private void updateOrderDetailFromApi(Long accountId, String orderId, Map<String, Object> responseData) {
        try {
            XianyuGoodsOrder order = orderMapper.selectByAccountIdAndOrderId(accountId, orderId);
            if (order == null) {
                log.debug("【账号{}】本地无此订单记录，跳过更新: orderId={}", accountId, orderId);
                return;
            }

            Object moduleObj = responseData.get("module");
            if (!(moduleObj instanceof Map)) return;
            Map<String, Object> module = (Map<String, Object>) moduleObj;

            String buyerUserName = null;
            Object merchantBuyerVO = module.get("merchantBuyerVO");
            if (merchantBuyerVO instanceof Map) {
                Map<String, Object> buyer = (Map<String, Object>) merchantBuyerVO;
                Object userNick = buyer.get("userNick");
                if (userNick instanceof String) buyerUserName = (String) userNick;
            }

            String orderCreateTime = null;
            String paySuccessTime = null;
            String consignTime = null;
            Object merchantCommonData = module.get("merchantCommonData");
            if (merchantCommonData instanceof Map) {
                Map<String, Object> commonData = (Map<String, Object>) merchantCommonData;
                Object ct = commonData.get("createTime");
                if (ct instanceof String) orderCreateTime = (String) ct;
                Object pt = commonData.get("paySuccessTime");
                if (pt instanceof String) paySuccessTime = (String) pt;
                Object ct2 = commonData.get("consignTime");
                if (ct2 instanceof String) consignTime = (String) ct2;
                Object os = commonData.get("orderStatus");
                if (os instanceof String) {
                    String platformStatus = OrderStatus.normalizePlatformStatus((String) os);
                    orderMapper.updateOrderStatus(order.getId(), platformStatus);
                    if ((OrderStatus.PLATFORM_SHIPPED.equals(platformStatus) || OrderStatus.PLATFORM_COMPLETED.equals(platformStatus))
                            && !OrderStatus.isAlreadyDelivered(order.getState())) {
                        orderMapper.updateState(order.getId(), OrderStatus.DELIVERY_SUCCESS);
                        order.setState(OrderStatus.DELIVERY_SUCCESS);
                    }
                }
            }

            String goodsTitle = null;
            Object merchantItemVO = module.get("merchantItemVO");
            if (merchantItemVO instanceof Map) {
                Map<String, Object> merchantItem = (Map<String, Object>) merchantItemVO;
                Object title = merchantItem.get("title");
                if (title instanceof String) goodsTitle = (String) title;
            }

            String totalPrice = null;
            Integer buyNum = null;
            Object merchantPriceVO = module.get("merchantPriceVO");
            if (merchantPriceVO instanceof Map) {
                Map<String, Object> priceVO = (Map<String, Object>) merchantPriceVO;
                Object tp = priceVO.get("totalPrice");
                if (tp instanceof String) totalPrice = (String) tp;
                Object bn = priceVO.get("buyNum");
                if (bn instanceof String) {
                    try { buyNum = Integer.parseInt((String) bn); } catch (Exception e) { buyNum = 1; }
                } else if (bn instanceof Number) {
                    buyNum = ((Number) bn).intValue();
                }
            }

            orderMapper.updateOrderDetail(order.getId(), buyerUserName, orderCreateTime, paySuccessTime, consignTime, null, goodsTitle, totalPrice, buyNum);
            log.info("【账号{}】从API更新订单详情成功: orderId={}", accountId, orderId);
        } catch (Exception e) {
            log.warn("【账号{}】更新订单详情失败: orderId={}", accountId, orderId, e);
        }
    }

    @Override
    public String getOrderDetailFromLocal(Long accountId, String orderId) {
        try {
            XianyuGoodsOrder order = orderMapper.selectByAccountIdAndOrderId(accountId, orderId);
            if (order == null) {
                log.warn("【账号{}】本地未找到订单: orderId={}", accountId, orderId);
                return null;
            }
            Map<String, Object> result = new HashMap<>();
            result.put("orderId", order.getOrderId());
            result.put("orderStatus", order.getOrderStatus());
            result.put("xyGoodsId", order.getXyGoodsId());
            result.put("goodsTitle", order.getGoodsTitle());
            result.put("buyerUserName", order.getBuyerUserName());
            result.put("content", order.getContent());
            result.put("state", order.getState());
            result.put("failReason", order.getFailReason());
            result.put("confirmState", order.getConfirmState());
            result.put("createTime", order.getCreateTime());
            result.put("skuName", order.getSkuName());
            result.put("orderCreateTime", order.getOrderCreateTime());
            result.put("paySuccessTime", order.getPaySuccessTime());
            result.put("consignTime", order.getConsignTime());
            result.put("totalPrice", order.getTotalPrice());
            result.put("buyNum", order.getBuyNum());
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("【账号{}】获取本地订单详情异常: orderId={}", accountId, orderId, e);
            return null;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> queryPendingOrders(Long accountId) {
        return querySellerOrders(accountId, "NOT_SHIP", 1, 20);
    }

    @Override
    public List<Map<String, Object>> querySellerOrders(Long accountId, String queryCode, Integer pageNumber, Integer pageSize) {
        SellerOrderPage page = querySellerOrderPage(accountId, queryCode, pageNumber, pageSize);
        return page.success() ? page.items() : List.of();
    }

    @Override
    @SuppressWarnings("unchecked")
    public SellerOrderPage querySellerOrderPage(Long accountId, String queryCode, Integer pageNumber, Integer pageSize) {
        try {
            String cookieStr = accountService.getCookieByAccountId(accountId);
            if (cookieStr == null || cookieStr.isEmpty()) {
                log.error("【账号{}】未找到Cookie", accountId);
                return SellerOrderPage.failed("账号 Cookie 不存在或已失效");
            }

            int safePageNumber = pageNumber != null && pageNumber > 0 ? pageNumber : 1;
            int safePageSize = pageSize != null && pageSize > 0 ? pageSize : 20;
            String safeQueryCode = queryCode == null || queryCode.isBlank() ? "NOT_SHIP" : queryCode;

            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("pageNumber", safePageNumber);
            dataMap.put("rowsPerPage", safePageSize);
            dataMap.put("orderIds", "");
            dataMap.put("queryCode", safeQueryCode);
            dataMap.put("orderSearchParam", "{}");

            Map<String, String> extraHeaders = new HashMap<>();
            extraHeaders.put("idle_site_biz_code", "COMMONPRO");
            extraHeaders.put("Origin", "https://seller.goofish.com");
            extraHeaders.put("Referer", "https://seller.goofish.com/?site=COMMONPRO");

            Map<String, String> extraQueryParams = new HashMap<>();
            extraQueryParams.put("type", "json");
            extraQueryParams.put("valueType", "string");

            XianyuApiCallUtils.ApiCallResult result = xianyuApiCallUtils.callApiWithRetry(
                    accountId,
                    "mtop.taobao.idle.trade.merchant.sold.get",
                    dataMap,
                    cookieStr,
                    extraHeaders,
                    extraQueryParams
            );

            if (!result.isSuccess()) {
                log.warn("【账号{}】查询卖家订单失败: queryCode={}, error={}", accountId, safeQueryCode, result.getErrorMessage());
                return SellerOrderPage.failed(result.getErrorMessage());
            }

            Map<String, Object> responseData = result.extractData();
            if (responseData == null) {
                return SellerOrderPage.failed("平台订单响应为空");
            }

            Object moduleObj = responseData.get("module");
            if (!(moduleObj instanceof Map)) {
                return SellerOrderPage.failed("平台订单响应缺少 module");
            }
            Map<String, Object> module = (Map<String, Object>) moduleObj;

            Object itemsObj = module.get("items");
            if (itemsObj == null) {
                return SellerOrderPage.failed("平台订单响应缺少 items");
            }
            if (!(itemsObj instanceof List)) {
                return SellerOrderPage.failed("平台订单响应的 items 格式错误");
            }

            List<Map<String, Object>> items = (List<Map<String, Object>>) itemsObj;
            return SellerOrderPage.success(items, resolveHasMore(module, safePageNumber, safePageSize, items.size()));
        } catch (Exception e) {
            log.error("【账号{}】查询卖家订单异常: queryCode={}", accountId, queryCode, e);
            return SellerOrderPage.failed(e.getMessage() == null ? "查询卖家订单异常" : e.getMessage());
        }
    }

    /** 根据平台分页字段判断是否还有下一页，缺失时退化为页大小判断。 */
    boolean resolveHasMore(Map<String, Object> module, int pageNumber, int pageSize, int itemCount) {
        for (String key : List.of("hasMore", "hasNext")) {
            Object value = module.get(key);
            if (value instanceof Boolean bool) {
                return bool;
            }
            if (value != null) {
                String normalizedValue = value.toString().trim();
                if ("true".equalsIgnoreCase(normalizedValue) || "1".equals(normalizedValue)) {
                    return true;
                }
                if ("false".equalsIgnoreCase(normalizedValue) || "0".equals(normalizedValue)) {
                    return false;
                }
                throw new IllegalArgumentException("无法识别平台分页字段 " + key + ": " + normalizedValue);
            }
        }

        for (String key : List.of("totalPage", "totalPages", "pageCount")) {
            Integer totalPages = parsePositiveInteger(module.get(key));
            if (totalPages != null) {
                return pageNumber < totalPages;
            }
        }

        Integer totalCount = parsePositiveInteger(module.get("totalCount"));
        if (totalCount != null) {
            return (long) pageNumber * pageSize < totalCount;
        }
        // 平台可能自行限制每页条数；无分页元数据时继续请求，直到返回空页。
        return itemCount > 0;
    }

    private Integer parsePositiveInteger(Object value) {
        if (value instanceof Number number) {
            return Math.max(number.intValue(), 0);
        }
        if (value == null) {
            return null;
        }
        try {
            return Math.max(Integer.parseInt(value.toString()), 0);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Override
    public Map<String, Object> getOrderDetailMap(Long accountId, String orderId) {
        try {
            String cookieStr = accountService.getCookieByAccountId(accountId);
            if (cookieStr == null || cookieStr.isEmpty()) {
                return null;
            }

            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("tid", orderId);

            XianyuApiCallUtils.ApiCallResult result = xianyuApiCallUtils.callApiWithRetry(
                    accountId,
                    "mtop.taobao.idle.trade.merchant.full.info",
                    dataMap,
                    cookieStr
            );

            if (!result.isSuccess()) {
                log.warn("【账号{}】获取订单详情失败: orderId={}, error={}", accountId, orderId, result.getErrorMessage());
                return null;
            }

            Map<String, Object> responseData = result.extractData();
            if (responseData != null) {
                localSalesFactService.recordOrderDetail(accountId, orderId, responseData);
            }
            return responseData;
        } catch (Exception e) {
            log.warn("【账号{}】获取订单详情异常: orderId={}", accountId, orderId, e);
            return null;
        }
    }

    @Override
    public String consignDummyDeliveryWithConfig(Long accountId, String xyGoodsId, String orderId) {
        log.info("【账号{}】带配置凭证发货: xyGoodsId={}, orderId={}", accountId, xyGoodsId, orderId);

        String deliveryLockKey = deliveryLockManager.key(accountId, orderId, null);
        if (!deliveryLockManager.tryLock(deliveryLockKey)) {
            throw new BusinessException(89283, "订单正在发货中，请稍后再试");
        }

        try {
            XianyuGoodsOrder existing = orderMapper.selectByAccountIdAndOrderId(accountId, orderId);
            if (existing != null && OrderStatus.isDeliveryBlockedPlatformStatus(existing.getOrderStatus())) {
                throw new BusinessException(89282, OrderStatus.getDeliveryBlockedReason(existing.getOrderStatus()));
            }
            if (existing != null && OrderStatus.isAlreadyDelivered(existing.getState())) {
                return "订单已发货成功";
            }
            if (existing != null && OrderStatus.isDelivering(existing.getState())) {
                throw new BusinessException(89283, "订单正在发货中，请稍后再试");
            }
            if (existing != null) {
                if (orderMapper.markDelivering(existing.getId()) == 0) {
                    throw new BusinessException(89283, "订单状态已变化，请刷新后再试");
                }
            }

            XianyuGoodsAutoDeliveryConfig deliveryConfig = autoDeliveryConfigMapper.findByAccountIdAndGoodsIdNoSku(accountId, xyGoodsId);
            if (deliveryConfig == null) {
                String failMsg = "商品无发货配置，请先配置自动发货";
                log.warn("【账号{}】{}: xyGoodsId={}", accountId, failMsg, xyGoodsId);
                markConfigDeliveryFailure(existing, null, failMsg);
                throw new BusinessException(89282, failMsg);
            }

            int deliveryMode = deliveryConfig.getDeliveryMode() != null ? deliveryConfig.getDeliveryMode() : 1;
            int deliveryCount = resolveConfigDeliveryCount(deliveryConfig, existing != null ? existing.getBuyNum() : null);

            DeliveryContext ctx = DeliveryContext.builder()
                    .recordId(existing != null ? existing.getId() : null)
                    .accountId(accountId)
                    .xyGoodsId(xyGoodsId)
                    .orderId(orderId)
                    .deliveryConfig(deliveryConfig)
                    .build();

            List<String> contentList = new ArrayList<>();
            for (int i = 0; i < deliveryCount; i++) {
                String itemContent = deliveryStrategyResolver.resolve(deliveryMode, ctx);
                if (itemContent == null) {
                    String failMsg = resolveDeliveryContentFailMessage(deliveryMode);
                    log.warn("【账号{}】发货内容解析失败: {}", accountId, failMsg);
                    markConfigDeliveryFailure(existing, null, failMsg);
                    return null;
                }
                contentList.add(itemContent);
            }
            String content = String.join("\n", contentList);
            List<String> imageUrls = parseImageUrls(deliveryConfig.getAutoDeliveryImageUrl());

            log.info("【账号{}】带配置凭证发货: orderId={}, deliveryMode={}, count={}, contentLen={}, imageCount={}",
                    accountId, orderId, deliveryMode, deliveryCount, content.length(), imageUrls.size());
            String result = consignDummyDelivery(accountId, orderId, content, imageUrls);

            if (result != null) {
                upsertConfigDeliverySuccess(existing, accountId, xyGoodsId, orderId, content);
                return result;
            }

            markConfigDeliveryFailure(existing, content, "凭证发货API失败");
            return null;
        } finally {
            deliveryLockManager.unlock(deliveryLockKey);
        }
    }

    private int resolveConfigDeliveryCount(XianyuGoodsAutoDeliveryConfig deliveryConfig, Integer buyNum) {
        int safeBuyNum = buyNum != null && buyNum > 0 ? buyNum : 1;
        Integer multiQuantityDelivery = deliveryConfig.getMultiQuantityDelivery();
        boolean enabled = multiQuantityDelivery == null || multiQuantityDelivery == 1;
        return enabled ? safeBuyNum : 1;
    }

    private String resolveDeliveryContentFailMessage(int deliveryMode) {
        if (deliveryMode == 1) {
            return "未配置发货内容";
        }
        if (deliveryMode == 2) {
            return "卡密库存不足，无可用卡密";
        }
        return "未知的发货模式: " + deliveryMode;
    }

    private List<String> parseImageUrls(String imageUrlStr) {
        List<String> imageUrls = new ArrayList<>();
        if (imageUrlStr == null || imageUrlStr.trim().isEmpty()) {
            return imageUrls;
        }
        for (String url : imageUrlStr.split(",")) {
            String trimmed = url.trim();
            if (!trimmed.isEmpty()) {
                imageUrls.add(trimmed);
            }
        }
        return imageUrls;
    }

    private void markConfigDeliveryFailure(XianyuGoodsOrder existing, String content, String failReason) {
        if (existing != null && !OrderStatus.isAlreadyDelivered(existing.getState())) {
            orderMapper.updateStateContentAndFailReason(existing.getId(), OrderStatus.DELIVERY_FAILED, content, failReason);
        }
    }

    private void upsertConfigDeliverySuccess(XianyuGoodsOrder existing, Long accountId, String xyGoodsId, String orderId, String content) {
        if (existing != null) {
            orderMapper.updateStateContentAndFailReason(existing.getId(), OrderStatus.DELIVERY_SUCCESS, content, null);
            orderMapper.updateConfirmState(accountId, orderId);
            orderMapper.updateOrderStatus(existing.getId(), OrderStatus.PLATFORM_SHIPPED);
            log.info("【账号{}】凭证发货成功，已更新订单状态: orderId={}", accountId, orderId);
            return;
        }

        XianyuGoodsOrder record = new XianyuGoodsOrder();
        record.setXianyuAccountId(accountId);
        record.setXyGoodsId(xyGoodsId);
        record.setOrderId(orderId);
        record.setOrderStatus(OrderStatus.PLATFORM_SHIPPED);
        record.setPnmId("api_" + orderId);
        record.setContent(content);
        record.setState(OrderStatus.DELIVERY_SUCCESS);
        record.setConfirmState(1);
        orderMapper.insert(record);
        log.info("【账号{}】凭证发货成功，已新建订单记录: orderId={}", accountId, orderId);
    }
}
