-- 先执行 search-risk-20261112.sql；保留所有现有记录和账号状态。
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='user_info' AND column_name='ban_reason')=0,'ALTER TABLE user_info ADD COLUMN ban_reason VARCHAR(200) NULL','SELECT 1');
PREPARE risk_stmt FROM @ddl;
EXECUTE risk_stmt;
DEALLOCATE PREPARE risk_stmt;
ALTER TABLE search_risk_record MODIFY content TEXT NOT NULL;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='search_risk_record' AND column_name='scene')=0,'ALTER TABLE search_risk_record ADD COLUMN scene VARCHAR(32) NULL','SELECT 1');
PREPARE risk_stmt FROM @ddl;
EXECUTE risk_stmt;
DEALLOCATE PREPARE risk_stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='search_risk_record' AND column_name='subject_type')=0,'ALTER TABLE search_risk_record ADD COLUMN subject_type VARCHAR(16) NULL','SELECT 1');
PREPARE risk_stmt FROM @ddl;
EXECUTE risk_stmt;
DEALLOCATE PREPARE risk_stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='search_risk_record' AND column_name='subject_id')=0,'ALTER TABLE search_risk_record ADD COLUMN subject_id BIGINT NULL','SELECT 1');
PREPARE risk_stmt FROM @ddl;
EXECUTE risk_stmt;
DEALLOCATE PREPARE risk_stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='search_risk_record' AND column_name='business_type')=0,'ALTER TABLE search_risk_record ADD COLUMN business_type VARCHAR(100) NULL','SELECT 1');
PREPARE risk_stmt FROM @ddl;
EXECUTE risk_stmt;
DEALLOCATE PREPARE risk_stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='search_risk_record' AND column_name='business_id')=0,'ALTER TABLE search_risk_record ADD COLUMN business_id BIGINT NULL','SELECT 1');
PREPARE risk_stmt FROM @ddl;
EXECUTE risk_stmt;
DEALLOCATE PREPARE risk_stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='search_risk_record' AND column_name='content_hash')=0,'ALTER TABLE search_risk_record ADD COLUMN content_hash VARCHAR(64) NULL','SELECT 1');
PREPARE risk_stmt FROM @ddl;
EXECUTE risk_stmt;
DEALLOCATE PREPARE risk_stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='search_risk_record' AND column_name='ai_advice')=0,'ALTER TABLE search_risk_record ADD COLUMN ai_advice TEXT NULL','SELECT 1');
PREPARE risk_stmt FROM @ddl;
EXECUTE risk_stmt;
DEALLOCATE PREPARE risk_stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='search_risk_record' AND column_name='job_id')=0,'ALTER TABLE search_risk_record ADD COLUMN job_id BIGINT NULL','SELECT 1');
PREPARE risk_stmt FROM @ddl;
EXECUTE risk_stmt;
DEALLOCATE PREPARE risk_stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='search_risk_record' AND column_name='dedup_key')=0,'ALTER TABLE search_risk_record ADD COLUMN dedup_key VARCHAR(64) NULL','SELECT 1');
PREPARE risk_stmt FROM @ddl;
EXECUTE risk_stmt;
DEALLOCATE PREPARE risk_stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='search_risk_record' AND index_name='uk_risk_scan_dedup')=0,'CREATE UNIQUE INDEX uk_risk_scan_dedup ON search_risk_record(dedup_key)','SELECT 1');
PREPARE risk_stmt FROM @ddl;
EXECUTE risk_stmt;
DEALLOCATE PREPARE risk_stmt;
UPDATE search_risk_record SET scene='SEARCH',subject_type=IF(user_id IS NULL,'GUEST','APP'),subject_id=user_id WHERE scene IS NULL;
CREATE TABLE IF NOT EXISTS content_risk_policy (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,create_time DATETIME,update_time DATETIME,
 scene VARCHAR(32) NOT NULL UNIQUE,enabled TINYINT NOT NULL,action VARCHAR(16) NOT NULL
);
CREATE TABLE IF NOT EXISTS platform_warning (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,create_time DATETIME,update_time DATETIME,
 risk_id BIGINT NOT NULL,sender_id BIGINT NOT NULL,request_id VARCHAR(64) NOT NULL,
 subject_type VARCHAR(16) NOT NULL,subject_id BIGINT NOT NULL,title VARCHAR(100) NOT NULL,content TEXT NOT NULL,
 notice_type VARCHAR(16) NOT NULL,workspace_notice_id BIGINT NULL,read_at DATETIME NULL,confirmed_at DATETIME NULL,
 UNIQUE KEY uk_warning_request(sender_id,request_id), INDEX idx_warning_recipient(subject_type,subject_id,id),INDEX idx_warning_risk(risk_id)
);
CREATE TABLE IF NOT EXISTS content_risk_job (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,create_time DATETIME,update_time DATETIME,
 operator_id BIGINT,source VARCHAR(32),status VARCHAR(16),after_id BIGINT,cursor_id BIGINT,max_id BIGINT,
 limit_count INT,model_budget INT,checked_count INT,model_calls INT,error VARCHAR(255),
 started_at DATETIME,finished_at DATETIME,INDEX idx_risk_job_status(status,id)
);
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='search_risk_record' AND column_name='matched_details')=0,'ALTER TABLE search_risk_record ADD COLUMN matched_details TEXT NULL','SELECT 1');
PREPARE risk_stmt FROM @ddl;
EXECUTE risk_stmt;
DEALLOCATE PREPARE risk_stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='content_risk_job' AND column_name='phase')=0,'ALTER TABLE content_risk_job ADD COLUMN phase VARCHAR(32) NULL','SELECT 1');
PREPARE risk_stmt FROM @ddl;
EXECUTE risk_stmt;
DEALLOCATE PREPARE risk_stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='content_risk_job' AND column_name='scheduled')=0,'ALTER TABLE content_risk_job ADD COLUMN scheduled TINYINT NULL','SELECT 1');
PREPARE risk_stmt FROM @ddl;
EXECUTE risk_stmt;
DEALLOCATE PREPARE risk_stmt;
CREATE TABLE IF NOT EXISTS content_risk_schedule (
 id BIGINT PRIMARY KEY,create_time DATETIME,update_time DATETIME,enabled TINYINT NOT NULL,
 interval_minutes INT NOT NULL,batch_size INT NOT NULL,daily_budget INT NOT NULL,
 sources_json TEXT NOT NULL,cursors_json TEXT NOT NULL,source_index INT NOT NULL,
 budget_date VARCHAR(10),used_calls INT NOT NULL,active_job_id BIGINT,last_job_id BIGINT,
 retry_count INT NOT NULL,last_run_at DATETIME,next_run_at DATETIME,last_result VARCHAR(255)
);
INSERT IGNORE INTO content_risk_schedule(id,create_time,update_time,enabled,interval_minutes,batch_size,daily_budget,sources_json,cursors_json,source_index,used_calls,retry_count,last_result)
VALUES(1,NOW(),NOW(),0,60,10,20,'["RISKS"]','{}',0,0,0,'NOT_STARTED');


