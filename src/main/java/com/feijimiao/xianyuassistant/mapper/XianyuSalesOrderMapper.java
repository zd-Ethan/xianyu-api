package com.feijimiao.xianyuassistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.feijimiao.xianyuassistant.entity.XianyuSalesOrder;
import com.feijimiao.xianyuassistant.entity.bo.SalesDailyBO;
import com.feijimiao.xianyuassistant.entity.bo.SalesSummaryBO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface XianyuSalesOrderMapper extends BaseMapper<XianyuSalesOrder> {

    @Insert("""
            INSERT INTO xianyu_sales_order (
                xianyu_account_id, order_id, xy_goods_id, platform_status, raw_status,
                paid_at, paid_date, quantity, gross_amount_cents, refunded_quantity,
                refunded_amount_cents, last_synced_at
            ) VALUES (
                #{xianyuAccountId}, #{orderId}, #{xyGoodsId}, #{platformStatus}, #{rawStatus},
                #{paidAt}, #{paidDate}, #{quantity}, #{grossAmountCents}, #{refundedQuantity},
                #{refundedAmountCents}, #{lastSyncedAt}
            )
            ON CONFLICT(xianyu_account_id, order_id) DO UPDATE SET
                xy_goods_id = COALESCE(excluded.xy_goods_id, xianyu_sales_order.xy_goods_id),
                platform_status = CASE WHEN #{platformStatusProvided}
                    THEN excluded.platform_status ELSE xianyu_sales_order.platform_status END,
                raw_status = CASE WHEN #{platformStatusProvided}
                    THEN excluded.raw_status ELSE xianyu_sales_order.raw_status END,
                paid_at = COALESCE(excluded.paid_at, xianyu_sales_order.paid_at),
                paid_date = COALESCE(excluded.paid_date, xianyu_sales_order.paid_date),
                quantity = CASE WHEN #{quantityProvided}
                    THEN excluded.quantity ELSE xianyu_sales_order.quantity END,
                gross_amount_cents = CASE WHEN #{grossAmountProvided}
                    THEN excluded.gross_amount_cents ELSE xianyu_sales_order.gross_amount_cents END,
                refunded_quantity = CASE
                    WHEN #{refundedQuantityProvided} THEN excluded.refunded_quantity
                    WHEN #{platformStatusProvided}
                        AND excluded.platform_status = 'refunded'
                        AND NOT #{refundedAmountProvided}
                        AND xianyu_sales_order.refunded_quantity = 0
                        AND xianyu_sales_order.refunded_amount_cents = 0
                        THEN xianyu_sales_order.quantity
                    WHEN #{platformStatusProvided}
                        AND excluded.platform_status = 'refunded'
                        AND #{refundedAmountProvided}
                        AND xianyu_sales_order.gross_amount_cents > 0
                        AND excluded.refunded_amount_cents >= xianyu_sales_order.gross_amount_cents
                        THEN xianyu_sales_order.quantity
                    ELSE xianyu_sales_order.refunded_quantity
                END,
                refunded_amount_cents = CASE
                    WHEN #{refundedAmountProvided} THEN excluded.refunded_amount_cents
                    WHEN #{platformStatusProvided}
                        AND excluded.platform_status = 'refunded'
                        AND NOT #{refundedQuantityProvided}
                        AND xianyu_sales_order.refunded_quantity = 0
                        AND xianyu_sales_order.refunded_amount_cents = 0
                        THEN xianyu_sales_order.gross_amount_cents
                    WHEN #{platformStatusProvided}
                        AND excluded.platform_status = 'refunded'
                        AND #{refundedQuantityProvided}
                        AND xianyu_sales_order.quantity > 0
                        AND excluded.refunded_quantity >= xianyu_sales_order.quantity
                        THEN xianyu_sales_order.gross_amount_cents
                    ELSE xianyu_sales_order.refunded_amount_cents
                END,
                last_synced_at = excluded.last_synced_at
            """)
    int upsert(XianyuSalesOrder order);

    @Delete("DELETE FROM xianyu_sales_order WHERE xianyu_account_id = #{accountId}")
    int deleteByAccountId(@Param("accountId") Long accountId);

    @Select("""
            <script>
            SELECT order_id
            FROM xianyu_sales_order
            WHERE xianyu_account_id = #{accountId}
              AND order_id IN
              <foreach collection="orderIds" item="orderId" open="(" separator="," close=")">
                #{orderId}
              </foreach>
            </script>
            """)
    List<String> selectExistingOrderIds(
            @Param("accountId") Long accountId,
            @Param("orderIds") List<String> orderIds
    );

    /** 查询需要低频核对退款状态的本地订单。 */
    @Select("""
            SELECT order_id
            FROM xianyu_sales_order
            WHERE xianyu_account_id = #{accountId}
              AND platform_status IN ('pending_ship', 'shipped', 'completed', 'refunding')
            ORDER BY last_synced_at ASC, id ASC
            LIMIT #{limit}
            """)
    List<String> selectOrderIdsForStatusRefresh(
            @Param("accountId") Long accountId,
            @Param("limit") int limit
    );

    /** 推进订单核对时间，避免单个失败订单长期阻塞后续轮询。 */
    @Update("""
            UPDATE xianyu_sales_order
            SET last_synced_at = #{attemptedAt}
            WHERE xianyu_account_id = #{accountId}
              AND order_id = #{orderId}
            """)
    int markStatusRefreshAttempt(
            @Param("accountId") Long accountId,
            @Param("orderId") String orderId,
            @Param("attemptedAt") String attemptedAt
    );

    @Select("""
            <script>
            WITH net_sales AS (
                SELECT paid_date,
                    CASE WHEN platform_status IN ('pending_ship', 'shipped', 'completed', 'refunding', 'refunded', 'refund_closed')
                        THEN MAX(quantity - refunded_quantity, 0) ELSE 0 END AS net_quantity,
                    CASE WHEN platform_status IN ('pending_ship', 'shipped', 'completed', 'refunding', 'refunded', 'refund_closed')
                        THEN MAX(gross_amount_cents - refunded_amount_cents, 0) ELSE 0 END AS net_amount_cents
                FROM xianyu_sales_order
                WHERE paid_date IS NOT NULL
                <if test="accountId != null">AND xianyu_account_id = #{accountId}</if>
            )
            SELECT
                COALESCE(SUM(net_quantity), 0) AS totalQuantity,
                COALESCE(SUM(CASE WHEN paid_date = #{today} THEN net_quantity ELSE 0 END), 0) AS todayQuantity,
                COALESCE(SUM(CASE WHEN paid_date BETWEEN #{last7Start} AND #{today} THEN net_quantity ELSE 0 END), 0) AS last7DaysQuantity,
                COALESCE(SUM(CASE WHEN paid_date BETWEEN #{last30Start} AND #{today} THEN net_quantity ELSE 0 END), 0) AS last30DaysQuantity,
                COALESCE(SUM(CASE WHEN paid_date BETWEEN #{monthStart} AND #{monthEnd} THEN net_quantity ELSE 0 END), 0) AS monthQuantity,
                COALESCE(SUM(net_amount_cents), 0) AS totalAmountCents,
                COALESCE(SUM(CASE WHEN paid_date BETWEEN #{monthStart} AND #{monthEnd} THEN net_amount_cents ELSE 0 END), 0) AS monthAmountCents
            FROM net_sales
            </script>
            """)
    SalesSummaryBO selectSummary(
            @Param("accountId") Long accountId,
            @Param("today") String today,
            @Param("last7Start") String last7Start,
            @Param("last30Start") String last30Start,
            @Param("monthStart") String monthStart,
            @Param("monthEnd") String monthEnd
    );

    @Select("""
            <script>
            SELECT paid_date AS date,
                COALESCE(SUM(CASE WHEN platform_status IN ('pending_ship', 'shipped', 'completed', 'refunding', 'refunded', 'refund_closed')
                    THEN MAX(quantity - refunded_quantity, 0) ELSE 0 END), 0) AS quantity,
                COALESCE(SUM(CASE WHEN platform_status IN ('pending_ship', 'shipped', 'completed', 'refunding', 'refunded', 'refund_closed')
                    THEN MAX(gross_amount_cents - refunded_amount_cents, 0) ELSE 0 END), 0) AS amountCents
            FROM xianyu_sales_order
            WHERE paid_date BETWEEN #{startDate} AND #{endDate}
            <if test="accountId != null">AND xianyu_account_id = #{accountId}</if>
            GROUP BY paid_date
            ORDER BY paid_date
            </script>
            """)
    List<SalesDailyBO> selectDailySales(
            @Param("accountId") Long accountId,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate
    );
}
