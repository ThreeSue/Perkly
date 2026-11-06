-- MySQL 8，先执行既有统一消费结算增量，再执行本脚本。
SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS wallet_activity_exclusion (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 create_time DATETIME NULL,
 update_time DATETIME NULL,
 activity_id BIGINT NULL,
 merchant_id BIGINT NULL,
 store_id BIGINT NULL,
 UNIQUE KEY uk_activity_exclusion(activity_id,merchant_id,store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS wallet_activity_contact (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 create_time DATETIME NULL,
 update_time DATETIME NULL,
 merchant_id BIGINT NULL,
 store_id BIGINT NULL,
 recipient_id BIGINT NULL,
 UNIQUE KEY uk_activity_contact(merchant_id,store_id,recipient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS wallet_activity_event (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 create_time DATETIME NULL,
 update_time DATETIME NULL,
 activity_id BIGINT NULL,
 activity_version INT NULL,
 kind VARCHAR(255) NULL,
 due_at DATETIME NULL,
 status VARCHAR(255) NULL,
 attempts INT NULL,
 next_attempt_at DATETIME NULL,
 payload MEDIUMTEXT NULL,
 gaps TEXT NULL,
 last_error VARCHAR(500) NULL,
 UNIQUE KEY uk_activity_event(activity_id,activity_version,kind),
 KEY idx_activity_event_due(status,next_attempt_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS wallet_activity_delivery (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 create_time DATETIME NULL,
 update_time DATETIME NULL,
 event_id BIGINT NULL,
 recipient_id BIGINT NULL,
 merchant_id BIGINT NULL,
 store_id BIGINT NULL,
 status VARCHAR(255) NULL,
 notice_id BIGINT NULL,
 attempts INT NULL,
 next_attempt_at DATETIME NULL,
 last_error VARCHAR(500) NULL,
 UNIQUE KEY uk_activity_delivery(event_id,recipient_id),
 KEY idx_activity_delivery_due(status,next_attempt_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
SET @activity_ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wallet_activity' AND column_name='activity_version'),
 'SELECT 1','ALTER TABLE wallet_activity ADD COLUMN activity_version INT NULL');
PREPARE activity_stmt FROM @activity_ddl; EXECUTE activity_stmt; DEALLOCATE PREPARE activity_stmt;
SET @activity_ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wallet_activity' AND column_name='audience_snapshot'),
 'SELECT 1','ALTER TABLE wallet_activity ADD COLUMN audience_snapshot MEDIUMTEXT NULL');
PREPARE activity_stmt FROM @activity_ddl; EXECUTE activity_stmt; DEALLOCATE PREPARE activity_stmt;
SET @activity_ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='workspace_notice' AND column_name='business_type'),
 'SELECT 1','ALTER TABLE workspace_notice ADD COLUMN business_type VARCHAR(255) NULL');
PREPARE activity_stmt FROM @activity_ddl; EXECUTE activity_stmt; DEALLOCATE PREPARE activity_stmt;
SET @activity_ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='workspace_notice' AND column_name='business_id'),
 'SELECT 1','ALTER TABLE workspace_notice ADD COLUMN business_id BIGINT NULL');
PREPARE activity_stmt FROM @activity_ddl; EXECUTE activity_stmt; DEALLOCATE PREPARE activity_stmt;
SET @activity_ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='workspace_notice' AND column_name='business_version'),
 'SELECT 1','ALTER TABLE workspace_notice ADD COLUMN business_version INT NULL');
PREPARE activity_stmt FROM @activity_ddl; EXECUTE activity_stmt; DEALLOCATE PREPARE activity_stmt;
SET @activity_ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='workspace_notice' AND column_name='detail_path'),
 'SELECT 1','ALTER TABLE workspace_notice ADD COLUMN detail_path VARCHAR(255) NULL');
PREPARE activity_stmt FROM @activity_ddl; EXECUTE activity_stmt; DEALLOCATE PREPARE activity_stmt;
SET @activity_ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='workspace_notice' AND column_name='action_path'),
 'SELECT 1','ALTER TABLE workspace_notice ADD COLUMN action_path VARCHAR(255) NULL');
PREPARE activity_stmt FROM @activity_ddl; EXECUTE activity_stmt; DEALLOCATE PREPARE activity_stmt;
