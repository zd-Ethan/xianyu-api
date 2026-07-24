package com.feijimiao.xianyuassistant.service.order;

import java.util.List;
import java.util.Map;

/** 闲鱼卖家订单分页结果，可区分空页和平台请求失败。 */
public record SellerOrderPage(
        boolean success,
        List<Map<String, Object>> items,
        boolean hasMore,
        String errorMessage
) {
    public static SellerOrderPage success(List<Map<String, Object>> items, boolean hasMore) {
        return new SellerOrderPage(true, items, hasMore, null);
    }

    public static SellerOrderPage failed(String errorMessage) {
        return new SellerOrderPage(false, List.of(), false, errorMessage);
    }
}
