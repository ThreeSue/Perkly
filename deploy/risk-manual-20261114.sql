-- 仅增量迁移；先备份；不改历史风险判断、不恢复已删词条、不启动AI。
CREATE TABLE IF NOT EXISTS risk_history_preview (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 create_time DATETIME,
 update_time DATETIME,
 operator_id BIGINT,
 expires_at DATETIME,
 snapshot MEDIUMTEXT,
 receipts MEDIUMTEXT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
SET @manual_ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='search_risk_record' AND column_name='detection_entry'),'SELECT 1','ALTER TABLE search_risk_record ADD COLUMN detection_entry VARCHAR(32)');
PREPARE manual_stmt FROM @manual_ddl; EXECUTE manual_stmt; DEALLOCATE PREPARE manual_stmt;
SET @manual_ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='search_risk_record' AND column_name='origin_type'),'SELECT 1','ALTER TABLE search_risk_record ADD COLUMN origin_type VARCHAR(32)');
PREPARE manual_stmt FROM @manual_ddl; EXECUTE manual_stmt; DEALLOCATE PREPARE manual_stmt;
SET @manual_ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='search_risk_record' AND column_name='origin_id'),'SELECT 1','ALTER TABLE search_risk_record ADD COLUMN origin_id BIGINT');
PREPARE manual_stmt FROM @manual_ddl; EXECUTE manual_stmt; DEALLOCATE PREPARE manual_stmt;
SET @manual_ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='search_risk_record' AND column_name='origin_field'),'SELECT 1','ALTER TABLE search_risk_record ADD COLUMN origin_field VARCHAR(100)');
PREPARE manual_stmt FROM @manual_ddl; EXECUTE manual_stmt; DEALLOCATE PREPARE manual_stmt;
SET @manual_ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='search_risk_record' AND column_name='content_version'),'SELECT 1','ALTER TABLE search_risk_record ADD COLUMN content_version VARCHAR(64)');
PREPARE manual_stmt FROM @manual_ddl; EXECUTE manual_stmt; DEALLOCATE PREPARE manual_stmt;
SET @manual_ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='search_risk_record' AND column_name='submitted_by_type'),'SELECT 1','ALTER TABLE search_risk_record ADD COLUMN submitted_by_type VARCHAR(32)');
PREPARE manual_stmt FROM @manual_ddl; EXECUTE manual_stmt; DEALLOCATE PREPARE manual_stmt;
SET @manual_ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='search_risk_record' AND column_name='submitted_by_id'),'SELECT 1','ALTER TABLE search_risk_record ADD COLUMN submitted_by_id BIGINT');
PREPARE manual_stmt FROM @manual_ddl; EXECUTE manual_stmt; DEALLOCATE PREPARE manual_stmt;
SET @manual_ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='search_risk_record' AND column_name='provenance_status'),'SELECT 1','ALTER TABLE search_risk_record ADD COLUMN provenance_status VARCHAR(32)');
PREPARE manual_stmt FROM @manual_ddl; EXECUTE manual_stmt; DEALLOCATE PREPARE manual_stmt;
SET @manual_ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='search_risk_record' AND column_name='duplicate_of_id'),'SELECT 1','ALTER TABLE search_risk_record ADD COLUMN duplicate_of_id BIGINT');
PREPARE manual_stmt FROM @manual_ddl; EXECUTE manual_stmt; DEALLOCATE PREPARE manual_stmt;
SET @manual_ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='search_risk_word' AND column_name='source'),'SELECT 1','ALTER TABLE search_risk_word ADD COLUMN source VARCHAR(40)');
PREPARE manual_stmt FROM @manual_ddl; EXECUTE manual_stmt; DEALLOCATE PREPARE manual_stmt;
SET @manual_ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='search_risk_word' AND column_name='source_version'),'SELECT 1','ALTER TABLE search_risk_word ADD COLUMN source_version VARCHAR(80)');
PREPARE manual_stmt FROM @manual_ddl; EXECUTE manual_stmt; DEALLOCATE PREPARE manual_stmt;
SET @manual_ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='search_risk_word' AND column_name='license'),'SELECT 1','ALTER TABLE search_risk_word ADD COLUMN license VARCHAR(100)');
PREPARE manual_stmt FROM @manual_ddl; EXECUTE manual_stmt; DEALLOCATE PREPARE manual_stmt;
SET @manual_ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='search_risk_word' AND column_name='source_key'),'SELECT 1','ALTER TABLE search_risk_word ADD COLUMN source_key VARCHAR(64)');
PREPARE manual_stmt FROM @manual_ddl; EXECUTE manual_stmt; DEALLOCATE PREPARE manual_stmt;
SET @manual_ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='search_risk_word' AND column_name='deleted'),'SELECT 1','ALTER TABLE search_risk_word ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0');
PREPARE manual_stmt FROM @manual_ddl; EXECUTE manual_stmt; DEALLOCATE PREPARE manual_stmt;
CREATE TABLE IF NOT EXISTS risk_word_state(id BIGINT PRIMARY KEY,revision BIGINT NOT NULL DEFAULT 0,initialized TINYINT NOT NULL DEFAULT 0,checksum VARCHAR(64));
INSERT IGNORE INTO risk_word_state(id,revision,initialized) VALUES(1,0,0);
SET @manual_ddl=IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='search_risk_word' AND index_name='uk_risk_word_source_key'),'SELECT 1','CREATE UNIQUE INDEX uk_risk_word_source_key ON search_risk_word(source_key)');
PREPARE manual_stmt FROM @manual_ddl; EXECUTE manual_stmt; DEALLOCATE PREPARE manual_stmt;
