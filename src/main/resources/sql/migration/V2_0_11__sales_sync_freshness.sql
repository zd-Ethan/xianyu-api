ALTER TABLE xianyu_sales_sync_state
ADD COLUMN last_incremental_success_at DATETIME;

ALTER TABLE xianyu_sales_sync_state
ADD COLUMN last_full_success_at DATETIME;

ALTER TABLE xianyu_sales_sync_state
ADD COLUMN data_quality_error_count INTEGER NOT NULL DEFAULT 0;

UPDATE xianyu_sales_sync_state
SET last_incremental_success_at = last_success_at
WHERE last_success_at IS NOT NULL;
