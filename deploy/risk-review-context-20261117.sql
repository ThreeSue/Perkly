-- 新增检测结果、客服接待和优惠券上下文。不改历史归因和巡检开关。
SET @risk_ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='search_risk_record' AND column_name='detection_result'),'SELECT 1','ALTER TABLE search_risk_record ADD COLUMN detection_result VARCHAR(32) NULL');
PREPARE risk_stmt FROM @risk_ddl; EXECUTE risk_stmt; DEALLOCATE PREPARE risk_stmt;
SET @risk_ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='search_risk_record' AND column_name='chat_context'),'SELECT 1','ALTER TABLE search_risk_record ADD COLUMN chat_context JSON NULL');
PREPARE risk_stmt FROM @risk_ddl; EXECUTE risk_stmt; DEALLOCATE PREPARE risk_stmt;
SET @risk_ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='search_risk_record' AND column_name='coupon_context'),'SELECT 1','ALTER TABLE search_risk_record ADD COLUMN coupon_context JSON NULL');
PREPARE risk_stmt FROM @risk_ddl; EXECUTE risk_stmt; DEALLOCATE PREPARE risk_stmt;
