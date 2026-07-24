-- 仅迁移本平台已经记录、具有稳定订单号和明确已付款状态的订单。
INSERT INTO xianyu_sales_order (
    xianyu_account_id,
    order_id,
    xy_goods_id,
    platform_status,
    raw_status,
    paid_at,
    paid_date,
    quantity,
    gross_amount_cents,
    refunded_quantity,
    refunded_amount_cents,
    last_synced_at
)
SELECT
    source.xianyu_account_id,
    trim(source.order_id),
    source.xy_goods_id,
    source.order_status,
    source.order_status,
    COALESCE(source.pay_success_time, source.order_create_time, source.create_time),
    substr(COALESCE(source.pay_success_time, source.order_create_time, source.create_time), 1, 10),
    COALESCE(NULLIF(source.buy_num, 0), 1),
    CAST(
        MAX(
            CAST(
                REPLACE(
                    REPLACE(
                        REPLACE(COALESCE(source.total_price, '0'), ',', ''),
                        '¥',
                        ''
                    ),
                    '￥',
                    ''
                ) AS REAL
            ),
            0
        ) * 100 AS INTEGER
    ),
    CASE WHEN source.order_status = 'refunded'
        THEN COALESCE(NULLIF(source.buy_num, 0), 1) ELSE 0 END,
    CASE WHEN source.order_status = 'refunded'
        THEN CAST(
            MAX(
                CAST(
                    REPLACE(
                        REPLACE(
                            REPLACE(COALESCE(source.total_price, '0'), ',', ''),
                            '¥',
                            ''
                        ),
                        '￥',
                        ''
                    ) AS REAL
                ),
                0
            ) * 100 AS INTEGER
        ) ELSE 0 END,
    datetime('now', 'localtime')
FROM xianyu_goods_order source
WHERE source.id = (
    SELECT MAX(candidate.id)
    FROM xianyu_goods_order candidate
    WHERE candidate.xianyu_account_id = source.xianyu_account_id
      AND trim(candidate.order_id) = trim(source.order_id)
      AND candidate.order_status IN (
          'pending_ship', 'shipped', 'completed', 'refunding', 'refunded'
      )
)
  AND source.order_id IS NOT NULL
  AND trim(source.order_id) <> ''
  AND source.order_status IN (
      'pending_ship', 'shipped', 'completed', 'refunding', 'refunded'
  )
  AND COALESCE(source.pay_success_time, source.order_create_time, source.create_time) IS NOT NULL
ON CONFLICT(xianyu_account_id, order_id) DO NOTHING;
