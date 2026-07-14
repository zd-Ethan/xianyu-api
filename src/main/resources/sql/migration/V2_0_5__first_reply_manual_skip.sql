CREATE INDEX IF NOT EXISTS idx_chat_message_manual_reply_lookup
ON xianyu_chat_message(xianyu_account_id, xy_goods_id, s_id, content_type, sender_user_id);
