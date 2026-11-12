-- 执行前备份。仅新增字段；旧已上线活动保持原状态，不伪造商家确认。
-- 可重复执行，兼容 AUTO_TABLE_ENABLE=false 的已有数据库；不改写已有活动。
SET @stamp_confirmation_ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='stamp_campaign' AND column_name='confirmations'),
  'SELECT 1', 'ALTER TABLE stamp_campaign ADD COLUMN confirmations MEDIUMTEXT NULL');
PREPARE stamp_confirmation_stmt FROM @stamp_confirmation_ddl;
EXECUTE stamp_confirmation_stmt;
DEALLOCATE PREPARE stamp_confirmation_stmt;
