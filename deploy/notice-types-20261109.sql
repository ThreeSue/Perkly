-- MySQL 8.0: execute against the application's selected schema. Idempotent, preserves history.
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='workspace_notice' AND column_name='notice_type')=0,
 'ALTER TABLE workspace_notice ADD COLUMN notice_type VARCHAR(16) NULL DEFAULT ''LIGHT'' COMMENT ''LIGHT轻通知 STRONG强提醒''', 'SELECT 1');
PREPARE notice_migration FROM @ddl; EXECUTE notice_migration; DEALLOCATE PREPARE notice_migration;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='workspace_notice_recipient' AND column_name='confirmed_at')=0,
 'ALTER TABLE workspace_notice_recipient ADD COLUMN confirmed_at DATETIME NULL COMMENT ''强提醒明确确认时间''', 'SELECT 1');
PREPARE notice_migration FROM @ddl; EXECUTE notice_migration; DEALLOCATE PREPARE notice_migration;
UPDATE workspace_notice SET notice_type='LIGHT' WHERE notice_type IS NULL;
