CREATE TABLE IF NOT EXISTS xianyu_reply_template (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    xianyu_account_id BIGINT,
    name VARCHAR(120) NOT NULL,
    description TEXT,
    enabled INTEGER DEFAULT 1,
    create_time DATETIME DEFAULT (datetime('now', 'localtime')),
    update_time DATETIME DEFAULT (datetime('now', 'localtime')),
    FOREIGN KEY (xianyu_account_id) REFERENCES xianyu_account(id)
);

CREATE INDEX IF NOT EXISTS idx_reply_template_account_id ON xianyu_reply_template(xianyu_account_id);
CREATE INDEX IF NOT EXISTS idx_reply_template_enabled ON xianyu_reply_template(enabled);

CREATE TABLE IF NOT EXISTS xianyu_reply_template_keyword_rule (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    template_id BIGINT NOT NULL,
    keyword VARCHAR(200) NOT NULL,
    match_mode INTEGER DEFAULT 1,
    is_fallback INTEGER DEFAULT 0,
    create_time DATETIME DEFAULT (datetime('now', 'localtime')),
    FOREIGN KEY (template_id) REFERENCES xianyu_reply_template(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_reply_template_rule_template_id ON xianyu_reply_template_keyword_rule(template_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_reply_template_rule_unique
ON xianyu_reply_template_keyword_rule(template_id, keyword, match_mode)
WHERE is_fallback = 0;

CREATE TABLE IF NOT EXISTS xianyu_reply_template_keyword_content (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    template_rule_id BIGINT NOT NULL,
    reply_text TEXT,
    reply_image_url TEXT,
    create_time DATETIME DEFAULT (datetime('now', 'localtime')),
    FOREIGN KEY (template_rule_id) REFERENCES xianyu_reply_template_keyword_rule(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_reply_template_content_rule_id
ON xianyu_reply_template_keyword_content(template_rule_id);

CREATE TABLE IF NOT EXISTS xianyu_reply_template_binding (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    xianyu_account_id BIGINT NOT NULL,
    xy_goods_id VARCHAR(100) NOT NULL,
    template_id BIGINT NOT NULL,
    sort_order INTEGER DEFAULT 0,
    enabled INTEGER DEFAULT 1,
    create_time DATETIME DEFAULT (datetime('now', 'localtime')),
    update_time DATETIME DEFAULT (datetime('now', 'localtime')),
    FOREIGN KEY (xianyu_account_id) REFERENCES xianyu_account(id),
    FOREIGN KEY (template_id) REFERENCES xianyu_reply_template(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_reply_template_binding_goods
ON xianyu_reply_template_binding(xianyu_account_id, xy_goods_id);
CREATE INDEX IF NOT EXISTS idx_reply_template_binding_template_id
ON xianyu_reply_template_binding(template_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_reply_template_binding_unique
ON xianyu_reply_template_binding(xianyu_account_id, xy_goods_id, template_id);

CREATE TRIGGER IF NOT EXISTS update_xianyu_reply_template_time
AFTER UPDATE ON xianyu_reply_template
BEGIN
    UPDATE xianyu_reply_template SET update_time = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;

CREATE TRIGGER IF NOT EXISTS update_xianyu_reply_template_binding_time
AFTER UPDATE ON xianyu_reply_template_binding
BEGIN
    UPDATE xianyu_reply_template_binding SET update_time = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;
