-- 跨商家消费集章增量，2026-11-06。先执行统一消费结算及活动参与自动通知增量。
-- 在目标业务库执行；可重复运行；不删除历史数据。
SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS stamp_campaign (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 create_time DATETIME DEFAULT NULL, update_time DATETIME DEFAULT NULL,
 activity_id BIGINT, goals MEDIUMTEXT, reward_template_id BIGINT, reward_rule_version INT, reward_snapshot MEDIUMTEXT, reward_stock BIGINT, granted_count BIGINT,
 UNIQUE KEY auto_idx_uk_stamp_campaign_0 (activity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS completed_visit (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 create_time DATETIME DEFAULT NULL, update_time DATETIME DEFAULT NULL,
 source_type VARCHAR(255), source_id BIGINT, customer_id BIGINT, merchant_id BIGINT, store_id BIGINT, completed_at DATETIME,
 UNIQUE KEY auto_idx_uk_completed_visit_0 (source_type,source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS stamp_visit_match (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 create_time DATETIME DEFAULT NULL, update_time DATETIME DEFAULT NULL,
 visit_id BIGINT, activity_id BIGINT, activity_version INT, status VARCHAR(255), last_error VARCHAR(500),
 UNIQUE KEY auto_idx_uk_stamp_visit_match_0 (visit_id,activity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS customer_stamp (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 create_time DATETIME DEFAULT NULL, update_time DATETIME DEFAULT NULL,
 activity_id BIGINT, customer_id BIGINT, merchant_id BIGINT, visit_id BIGINT,
 UNIQUE KEY auto_idx_uk_customer_stamp_0 (activity_id,customer_id,merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS stamp_progress (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 create_time DATETIME DEFAULT NULL, update_time DATETIME DEFAULT NULL,
 activity_id BIGINT, customer_id BIGINT, stamp_count INT, reward_status VARCHAR(255), reward_coupon_id BIGINT, attempts INT, next_attempt_at DATETIME, completed_at DATETIME, last_error VARCHAR(500),
 UNIQUE KEY auto_idx_uk_stamp_progress_0 (activity_id,customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS stamp_reward_attempt (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 create_time DATETIME DEFAULT NULL, update_time DATETIME DEFAULT NULL,
 progress_id BIGINT, status VARCHAR(255), message VARCHAR(500)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS stamp_stock_entry (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 create_time DATETIME DEFAULT NULL, update_time DATETIME DEFAULT NULL,
 activity_id BIGINT, request_id VARCHAR(255), operator_id BIGINT, amount BIGINT,
 UNIQUE KEY auto_idx_uk_stamp_stock_entry_0 (activity_id,request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
SET @stamp_sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wallet_activity' AND COLUMN_NAME='kind')=0,'ALTER TABLE wallet_activity ADD COLUMN kind VARCHAR(255) DEFAULT NULL','SELECT 1');
PREPARE stamp_stmt FROM @stamp_sql;
EXECUTE stamp_stmt;
DEALLOCATE PREPARE stamp_stmt;
SET @stamp_sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='coupon_template' AND COLUMN_NAME='issue_channel')=0,'ALTER TABLE coupon_template ADD COLUMN issue_channel VARCHAR(32) DEFAULT NULL','SELECT 1');
PREPARE stamp_stmt FROM @stamp_sql;
EXECUTE stamp_stmt;
DEALLOCATE PREPARE stamp_stmt;
ALTER TABLE stamp_visit_match MODIFY last_error VARCHAR(500) DEFAULT NULL;
-- 历史 kind=NULL 继续代表储值折扣；issue_channel=NULL 继续代表 PUBLIC。
