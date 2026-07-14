CREATE TABLE IF NOT EXISTS xianyu_delivery_template_binding (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    xianyu_account_id BIGINT NOT NULL,
    xy_goods_id VARCHAR(100) NOT NULL,
    template_id BIGINT NOT NULL,
    enabled INTEGER DEFAULT 1,
    create_time DATETIME DEFAULT (datetime('now', 'localtime')),
    update_time DATETIME DEFAULT (datetime('now', 'localtime')),
    FOREIGN KEY (xianyu_account_id) REFERENCES xianyu_account(id),
    FOREIGN KEY (template_id) REFERENCES xianyu_delivery_template(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_delivery_template_binding_goods
ON xianyu_delivery_template_binding(xianyu_account_id, xy_goods_id);

CREATE INDEX IF NOT EXISTS idx_delivery_template_binding_template_id
ON xianyu_delivery_template_binding(template_id);

CREATE UNIQUE INDEX IF NOT EXISTS idx_delivery_template_binding_unique
ON xianyu_delivery_template_binding(xianyu_account_id, xy_goods_id);

CREATE TRIGGER IF NOT EXISTS update_xianyu_delivery_template_binding_time
AFTER UPDATE ON xianyu_delivery_template_binding
BEGIN
    UPDATE xianyu_delivery_template_binding SET update_time = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;
