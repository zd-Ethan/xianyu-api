package com.feijimiao.xianyuassistant.mapper;

import com.feijimiao.xianyuassistant.entity.XianyuGoodsOrder;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

/**
 * 商品订单Mapper
 */
@Mapper
public interface XianyuGoodsOrderMapper {

    @Insert("INSERT INTO xianyu_goods_order (xianyu_account_id, xianyu_goods_id, xy_goods_id, pnm_id, order_id, order_status, buyer_user_id, buyer_user_name, sid, content, state, fail_reason, confirm_state, goods_title, sku_name, order_create_time, pay_success_time, consign_time, total_price, buy_num) " +
            "VALUES (#{xianyuAccountId}, #{xianyuGoodsId}, #{xyGoodsId}, #{pnmId}, #{orderId}, #{orderStatus}, #{buyerUserId}, #{buyerUserName}, #{sid}, #{content}, #{state}, #{failReason}, #{confirmState}, #{goodsTitle}, #{skuName}, #{orderCreateTime}, #{paySuccessTime}, #{consignTime}, #{totalPrice}, #{buyNum})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(XianyuGoodsOrder record);
    
    @Select("SELECT * FROM xianyu_goods_order WHERE xianyu_account_id = #{accountId} ORDER BY create_time DESC")
    List<XianyuGoodsOrder> selectByAccountId(@Param("accountId") Long accountId);

    @Select("SELECT * FROM xianyu_goods_order WHERE id = #{id} LIMIT 1")
    XianyuGoodsOrder selectById(@Param("id") Long id);
    
    @Delete("DELETE FROM xianyu_goods_order WHERE xianyu_account_id = #{accountId}")
    int deleteByAccountId(@Param("accountId") Long accountId);
    
    @Select("<script>" +
            "SELECT r.*, " +
            "COALESCE(g.title, r.goods_title) as goods_title " +
            "FROM xianyu_goods_order r " +
            "LEFT JOIN xianyu_goods g ON r.xy_goods_id = g.xy_good_id " +
            "WHERE 1=1 " +
            "<if test='accountId != null'>" +
            "AND r.xianyu_account_id = #{accountId} " +
            "</if>" +
            "<if test='xyGoodsId != null and xyGoodsId != \"\"'>" +
            "AND r.xy_goods_id = #{xyGoodsId} " +
            "</if>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (g.title LIKE '%' || #{keyword} || '%' OR r.goods_title LIKE '%' || #{keyword} || '%' OR r.sku_name LIKE '%' || #{keyword} || '%' OR r.buyer_user_name LIKE '%' || #{keyword} || '%' OR r.content LIKE '%' || #{keyword} || '%') " +
            "</if>" +
            "ORDER BY r.create_time DESC " +
            "LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    @Results({
        @Result(property = "id", column = "id"),
        @Result(property = "xianyuAccountId", column = "xianyu_account_id"),
        @Result(property = "xianyuGoodsId", column = "xianyu_goods_id"),
        @Result(property = "xyGoodsId", column = "xy_goods_id"),
        @Result(property = "pnmId", column = "pnm_id"),
        @Result(property = "orderId", column = "order_id"),
        @Result(property = "orderStatus", column = "order_status"),
        @Result(property = "buyerUserId", column = "buyer_user_id"),
        @Result(property = "buyerUserName", column = "buyer_user_name"),
        @Result(property = "sid", column = "sid"),
        @Result(property = "content", column = "content"),
        @Result(property = "state", column = "state"),
        @Result(property = "failReason", column = "fail_reason"),
        @Result(property = "confirmState", column = "confirm_state"),
        @Result(property = "createTime", column = "create_time"),
        @Result(property = "goodsTitle", column = "goods_title"),
        @Result(property = "skuName", column = "sku_name"),
        @Result(property = "orderCreateTime", column = "order_create_time"),
        @Result(property = "paySuccessTime", column = "pay_success_time"),
        @Result(property = "consignTime", column = "consign_time"),
        @Result(property = "totalPrice", column = "total_price"),
        @Result(property = "buyNum", column = "buy_num")
    })
    List<XianyuGoodsOrder> selectByAccountIdWithPage(
            @Param("accountId") Long accountId,
            @Param("xyGoodsId") String xyGoodsId,
            @Param("keyword") String keyword,
            @Param("limit") int limit,
            @Param("offset") int offset);
    
    @Select("<script>" +
            "SELECT COUNT(*) FROM xianyu_goods_order r " +
            "LEFT JOIN xianyu_goods g ON r.xy_goods_id = g.xy_good_id " +
            "WHERE 1=1 " +
            "<if test='accountId != null'>" +
            "AND r.xianyu_account_id = #{accountId} " +
            "</if>" +
            "<if test='xyGoodsId != null and xyGoodsId != \"\"'>" +
            "AND r.xy_goods_id = #{xyGoodsId} " +
            "</if>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (g.title LIKE '%' || #{keyword} || '%' OR r.goods_title LIKE '%' || #{keyword} || '%' OR r.sku_name LIKE '%' || #{keyword} || '%' OR r.buyer_user_name LIKE '%' || #{keyword} || '%' OR r.content LIKE '%' || #{keyword} || '%') " +
            "</if>" +
            "</script>")
    long countByAccountId(@Param("accountId") Long accountId, @Param("xyGoodsId") String xyGoodsId, @Param("keyword") String keyword);
    
    @Update("UPDATE xianyu_goods_order SET state = #{state} WHERE id = #{id}")
    int updateState(@Param("id") Long id, @Param("state") Integer state);
    
    @Update("UPDATE xianyu_goods_order SET state = #{state}, content = #{content} WHERE id = #{id}")
    int updateStateAndContent(@Param("id") Long id, @Param("state") Integer state, @Param("content") String content);

    @Update("UPDATE xianyu_goods_order SET state = #{state}, content = #{content}, fail_reason = #{failReason} WHERE id = #{id}")
    int updateStateContentAndFailReason(@Param("id") Long id, @Param("state") Integer state, @Param("content") String content, @Param("failReason") String failReason);

    @Update("UPDATE xianyu_goods_order SET state = 2, fail_reason = NULL WHERE id = #{id} AND (state IS NULL OR state NOT IN (1, 2))")
    int markDelivering(@Param("id") Long id);

    @Update("UPDATE xianyu_goods_order SET order_status = #{orderStatus} WHERE id = #{id}")
    int updateOrderStatus(@Param("id") Long id, @Param("orderStatus") String orderStatus);

    @Update("<script>" +
            "UPDATE xianyu_goods_order SET state = 1, fail_reason = NULL " +
            "WHERE (state IS NULL OR state != 1) " +
            "AND order_status IN ('shipped', 'completed') " +
            "<if test='accountId != null'>" +
            "AND xianyu_account_id = #{accountId} " +
            "</if>" +
            "</script>")
    int reconcileDeliveredStates(@Param("accountId") Long accountId);

    @Update("UPDATE xianyu_goods_order SET buyer_user_id = #{buyerUserId}, buyer_user_name = #{buyerUserName}, sid = #{sid}, order_status = #{orderStatus} WHERE id = #{id}")
    int updateEventContext(@Param("id") Long id, @Param("buyerUserId") String buyerUserId, @Param("buyerUserName") String buyerUserName, @Param("sid") String sid, @Param("orderStatus") String orderStatus);
    
    @Select("SELECT * FROM xianyu_goods_order WHERE xianyu_account_id = #{accountId} AND xy_goods_id = #{xyGoodsId} AND order_id = #{orderId} LIMIT 1")
    XianyuGoodsOrder selectByOrderId(@Param("accountId") Long accountId, @Param("xyGoodsId") String xyGoodsId, @Param("orderId") String orderId);

    @Select("SELECT * FROM xianyu_goods_order WHERE xianyu_account_id = #{accountId} AND order_id = #{orderId} LIMIT 1")
    XianyuGoodsOrder selectByAccountIdAndOrderId(@Param("accountId") Long accountId, @Param("orderId") String orderId);

    @Select("SELECT COUNT(*) FROM xianyu_goods_order WHERE xianyu_account_id = #{accountId} AND order_id = #{orderId} AND state = 1 AND id != #{excludeId}")
    int countSuccessfulSameOrder(@Param("accountId") Long accountId, @Param("orderId") String orderId, @Param("excludeId") Long excludeId);
    
    @Update("UPDATE xianyu_goods_order SET confirm_state = 1 WHERE xianyu_account_id = #{accountId} AND order_id = #{orderId}")
    int updateConfirmState(@Param("accountId") Long accountId, @Param("orderId") String orderId);
    
    @Select("SELECT * FROM xianyu_goods_order WHERE xianyu_account_id = #{accountId} AND pnm_id = #{pnmId}")
    XianyuGoodsOrder selectByPnmId(@Param("accountId") Long accountId, @Param("pnmId") String pnmId);

    @Select("SELECT COUNT(*) FROM xianyu_goods_order WHERE date(create_time) = date('now', '-1 day', 'localtime')")
    int countYesterdayOrders();

    @Select("SELECT COUNT(*) FROM xianyu_goods_order WHERE state = 1")
    int countDeliverySuccess();

    @Select("SELECT COUNT(*) FROM xianyu_goods_order WHERE state = -1")
    int countDeliveryFail();

    @Select("SELECT COUNT(*) FROM xianyu_goods_order")
    int countAllOrders();

    @Select("SELECT COUNT(*) FROM xianyu_goods_order WHERE date(create_time) = #{date}")
    int countOrdersByDate(@Param("date") String date);

    @Select("SELECT " +
            "COUNT(*) AS orderCount, " +
            "SUM(CASE WHEN state = 1 THEN 1 ELSE 0 END) AS deliverySuccessCount, " +
            "SUM(CASE WHEN state = -1 THEN 1 ELSE 0 END) AS deliveryFailCount " +
            "FROM xianyu_goods_order WHERE date(create_time) = #{date}")
    Map<String, Object> selectDataPanelStatsByDate(@Param("date") String date);

    @Select("<script>" +
            "SELECT r.*, COALESCE(g.title, r.goods_title) as goods_title " +
            "FROM xianyu_goods_order r " +
            "LEFT JOIN xianyu_goods g ON r.xy_goods_id = g.xy_good_id " +
            "WHERE 1=1 " +
            "<if test='accountId != null'>" +
            "AND r.xianyu_account_id = #{accountId} " +
            "</if>" +
            "<if test='xyGoodsId != null and xyGoodsId != \"\"'>" +
            "AND r.xy_goods_id = #{xyGoodsId} " +
            "</if>" +
            "<if test='orderStatus != null and orderStatus == 2'>" +
            "AND r.state IN (0, 2) " +
            "</if>" +
            "<if test='orderStatus != null and orderStatus != 2'>" +
            "AND r.state = #{orderStatus} " +
            "</if>" +
            "<if test='platformStatus != null and platformStatus != \"\"'>" +
            "AND r.order_status = #{platformStatus} " +
            "</if>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (g.title LIKE '%' || #{keyword} || '%' OR r.goods_title LIKE '%' || #{keyword} || '%' OR r.sku_name LIKE '%' || #{keyword} || '%' OR r.buyer_user_name LIKE '%' || #{keyword} || '%' OR r.content LIKE '%' || #{keyword} || '%') " +
            "</if>" +
            "ORDER BY r.create_time DESC " +
            "LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    @Results({
        @Result(property = "id", column = "id"),
        @Result(property = "xianyuAccountId", column = "xianyu_account_id"),
        @Result(property = "xianyuGoodsId", column = "xianyu_goods_id"),
        @Result(property = "xyGoodsId", column = "xy_goods_id"),
        @Result(property = "pnmId", column = "pnm_id"),
        @Result(property = "orderId", column = "order_id"),
        @Result(property = "orderStatus", column = "order_status"),
        @Result(property = "buyerUserId", column = "buyer_user_id"),
        @Result(property = "buyerUserName", column = "buyer_user_name"),
        @Result(property = "sid", column = "sid"),
        @Result(property = "content", column = "content"),
        @Result(property = "state", column = "state"),
        @Result(property = "failReason", column = "fail_reason"),
        @Result(property = "confirmState", column = "confirm_state"),
        @Result(property = "createTime", column = "create_time"),
        @Result(property = "goodsTitle", column = "goods_title"),
        @Result(property = "skuName", column = "sku_name"),
        @Result(property = "orderCreateTime", column = "order_create_time"),
        @Result(property = "paySuccessTime", column = "pay_success_time"),
        @Result(property = "consignTime", column = "consign_time"),
        @Result(property = "totalPrice", column = "total_price"),
        @Result(property = "buyNum", column = "buy_num")
    })
    List<XianyuGoodsOrder> selectByConditionWithPage(
            @Param("accountId") Long accountId,
            @Param("xyGoodsId") String xyGoodsId,
            @Param("orderStatus") Integer orderStatus,
            @Param("platformStatus") String platformStatus,
            @Param("keyword") String keyword,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Select("<script>" +
            "SELECT COUNT(*) FROM xianyu_goods_order r " +
            "LEFT JOIN xianyu_goods g ON r.xy_goods_id = g.xy_good_id " +
            "WHERE 1=1 " +
            "<if test='accountId != null'>" +
            "AND r.xianyu_account_id = #{accountId} " +
            "</if>" +
            "<if test='xyGoodsId != null and xyGoodsId != \"\"'>" +
            "AND r.xy_goods_id = #{xyGoodsId} " +
            "</if>" +
            "<if test='orderStatus != null and orderStatus == 2'>" +
            "AND r.state IN (0, 2) " +
            "</if>" +
            "<if test='orderStatus != null and orderStatus != 2'>" +
            "AND r.state = #{orderStatus} " +
            "</if>" +
            "<if test='platformStatus != null and platformStatus != \"\"'>" +
            "AND r.order_status = #{platformStatus} " +
            "</if>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (g.title LIKE '%' || #{keyword} || '%' OR r.goods_title LIKE '%' || #{keyword} || '%' OR r.sku_name LIKE '%' || #{keyword} || '%' OR r.buyer_user_name LIKE '%' || #{keyword} || '%' OR r.content LIKE '%' || #{keyword} || '%') " +
            "</if>" +
            "</script>")
    long countByCondition(@Param("accountId") Long accountId, @Param("xyGoodsId") String xyGoodsId, @Param("orderStatus") Integer orderStatus, @Param("platformStatus") String platformStatus, @Param("keyword") String keyword);

    @Select("SELECT COUNT(*) FROM xianyu_goods_order WHERE state = 1 AND date(create_time) = #{date}")
    int countDeliverySuccessByDate(@Param("date") String date);

    @Select("SELECT COUNT(*) FROM xianyu_goods_order WHERE state = -1 AND date(create_time) = #{date}")
    int countDeliveryFailByDate(@Param("date") String date);

    @Select("SELECT " +
            "date(create_time) AS date, " +
            "SUM(CASE WHEN state = 1 THEN 1 ELSE 0 END) AS deliverySuccessCount, " +
            "SUM(CASE WHEN state = -1 THEN 1 ELSE 0 END) AS deliveryFailCount " +
            "FROM xianyu_goods_order " +
            "WHERE date(create_time) >= #{startDate} AND date(create_time) <= #{endDate} " +
            "GROUP BY date(create_time)")
    List<Map<String, Object>> selectDeliveryTrendByDateRange(@Param("startDate") String startDate, @Param("endDate") String endDate);

    @Update("UPDATE xianyu_goods_order SET sku_name = #{skuName} WHERE id = #{id}")
    int updateSkuName(@Param("id") Long id, @Param("skuName") String skuName);

    @Update("UPDATE xianyu_goods_order SET buyer_user_name = #{buyerUserName}, order_create_time = #{orderCreateTime}, pay_success_time = #{paySuccessTime}, consign_time = #{consignTime}, sku_name = #{skuName}, goods_title = #{goodsTitle}, total_price = #{totalPrice}, buy_num = #{buyNum} WHERE id = #{id}")
    int updateOrderDetail(@Param("id") Long id, @Param("buyerUserName") String buyerUserName, @Param("orderCreateTime") String orderCreateTime, @Param("paySuccessTime") String paySuccessTime, @Param("consignTime") String consignTime, @Param("skuName") String skuName, @Param("goodsTitle") String goodsTitle, @Param("totalPrice") String totalPrice, @Param("buyNum") Integer buyNum);

}
