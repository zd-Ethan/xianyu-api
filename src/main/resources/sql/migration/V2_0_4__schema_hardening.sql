-- Move one-time schema/data repairs out of the always-run schema bootstrap.

DROP INDEX IF EXISTS idx_auto_delivery_config_unique;

UPDATE xianyu_goods_auto_delivery_config
SET sku_id = NULL
WHERE sku_id = '';

CREATE UNIQUE INDEX IF NOT EXISTS idx_auto_delivery_config_unique_v2
ON xianyu_goods_auto_delivery_config(xianyu_account_id, xy_goods_id, COALESCE(sku_id, ''));

CREATE TABLE IF NOT EXISTS xianyu_first_reply_record (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    xianyu_account_id BIGINT NOT NULL,
    xianyu_goods_id BIGINT,
    xy_goods_id VARCHAR(100) NOT NULL,
    buyer_user_id VARCHAR(100) NOT NULL,
    buyer_user_name VARCHAR(256),
    s_id VARCHAR(100),
    pnm_id VARCHAR(100),
    reply_content TEXT,
    reply_image_url TEXT,
    state TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT (datetime('now', 'localtime')),
    update_time DATETIME DEFAULT (datetime('now', 'localtime')),
    FOREIGN KEY (xianyu_account_id) REFERENCES xianyu_account(id)
);

CREATE INDEX IF NOT EXISTS idx_first_reply_record_account_id ON xianyu_first_reply_record(xianyu_account_id);
CREATE INDEX IF NOT EXISTS idx_first_reply_record_xy_goods_id ON xianyu_first_reply_record(xy_goods_id);
CREATE INDEX IF NOT EXISTS idx_first_reply_record_buyer_user_id ON xianyu_first_reply_record(buyer_user_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_first_reply_record_unique
ON xianyu_first_reply_record(xianyu_account_id, xy_goods_id, buyer_user_id);

CREATE INDEX IF NOT EXISTS idx_chat_message_first_reply_lookup
ON xianyu_chat_message(xianyu_account_id, xy_goods_id, sender_user_id, content_type);

CREATE TRIGGER IF NOT EXISTS update_xianyu_first_reply_record_time
AFTER UPDATE ON xianyu_first_reply_record
BEGIN
    UPDATE xianyu_first_reply_record SET update_time = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;
