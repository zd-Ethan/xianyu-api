CREATE TABLE IF NOT EXISTS xianyu_delivery_template (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    xianyu_account_id INTEGER,
    name TEXT NOT NULL,
    description TEXT,
    enabled INTEGER DEFAULT 1,
    delivery_mode INTEGER DEFAULT 1,
    auto_delivery_content TEXT,
    kami_config_ids TEXT,
    kami_delivery_template TEXT,
    auto_delivery_image_url TEXT,
    auto_confirm_shipment INTEGER DEFAULT 0,
    multi_quantity_delivery INTEGER DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_delivery_template_account_id
ON xianyu_delivery_template(xianyu_account_id);

CREATE INDEX IF NOT EXISTS idx_delivery_template_enabled
ON xianyu_delivery_template(enabled);

CREATE TRIGGER IF NOT EXISTS update_xianyu_delivery_template_time
AFTER UPDATE ON xianyu_delivery_template
BEGIN
    UPDATE xianyu_delivery_template SET update_time = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;
