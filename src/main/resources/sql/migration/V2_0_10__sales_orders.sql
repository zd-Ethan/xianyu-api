CREATE TABLE IF NOT EXISTS xianyu_sales_order (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    xianyu_account_id BIGINT NOT NULL,
    order_id VARCHAR(100) NOT NULL,
    xy_goods_id VARCHAR(100),
    platform_status VARCHAR(50) NOT NULL,
    raw_status VARCHAR(100),
    paid_at DATETIME,
    paid_date VARCHAR(10),
    quantity INTEGER NOT NULL DEFAULT 0,
    gross_amount_cents BIGINT NOT NULL DEFAULT 0,
    refunded_quantity INTEGER NOT NULL DEFAULT 0,
    refunded_amount_cents BIGINT NOT NULL DEFAULT 0,
    last_synced_at DATETIME NOT NULL,
    create_time DATETIME DEFAULT (datetime('now', 'localtime')),
    update_time DATETIME DEFAULT (datetime('now', 'localtime')),
    FOREIGN KEY (xianyu_account_id) REFERENCES xianyu_account(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_sales_order_account_order
ON xianyu_sales_order(xianyu_account_id, order_id);

CREATE INDEX IF NOT EXISTS idx_sales_order_paid_date
ON xianyu_sales_order(paid_date);

CREATE INDEX IF NOT EXISTS idx_sales_order_account_paid_date
ON xianyu_sales_order(xianyu_account_id, paid_date);

CREATE TABLE IF NOT EXISTS xianyu_sales_sync_state (
    xianyu_account_id BIGINT PRIMARY KEY,
    sync_status VARCHAR(20) NOT NULL DEFAULT 'never',
    last_started_at DATETIME,
    last_success_at DATETIME,
    last_error VARCHAR(500),
    synced_order_count INTEGER NOT NULL DEFAULT 0,
    update_time DATETIME DEFAULT (datetime('now', 'localtime')),
    FOREIGN KEY (xianyu_account_id) REFERENCES xianyu_account(id) ON DELETE CASCADE
);

CREATE TRIGGER IF NOT EXISTS update_xianyu_sales_order_time
AFTER UPDATE ON xianyu_sales_order
BEGIN
    UPDATE xianyu_sales_order SET update_time = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;

CREATE TRIGGER IF NOT EXISTS update_xianyu_sales_sync_state_time
AFTER UPDATE ON xianyu_sales_sync_state
BEGIN
    UPDATE xianyu_sales_sync_state SET update_time = CURRENT_TIMESTAMP WHERE xianyu_account_id = NEW.xianyu_account_id;
END;
