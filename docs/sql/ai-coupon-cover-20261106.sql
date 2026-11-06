-- MySQL 8；在目标业务库执行。仅增量建表/字段，不替换已有业务数据。
-- 依赖此前 merchant-material-20261105.sql 已部署。脚本可重复执行。
SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS ai_settings (
  id bigint NOT NULL AUTO_INCREMENT, create_time datetime DEFAULT NULL, update_time datetime DEFAULT NULL,
  enabled bit(1) DEFAULT NULL, allowed_models text, default_daily_limit int DEFAULT NULL,
  estimated_unit_price decimal(12,6) DEFAULT NULL, PRIMARY KEY(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_merchant_policy (
  id bigint NOT NULL AUTO_INCREMENT, create_time datetime DEFAULT NULL, update_time datetime DEFAULT NULL,
  merchant_id bigint DEFAULT NULL, daily_limit int DEFAULT NULL, enabled bit(1) DEFAULT NULL,
  PRIMARY KEY(id), UNIQUE KEY auto_idx_uk_ai_merchant_policy(merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_daily_usage (
  id bigint NOT NULL AUTO_INCREMENT, create_time datetime DEFAULT NULL, update_time datetime DEFAULT NULL,
  merchant_id bigint DEFAULT NULL, day_key varchar(255) DEFAULT NULL,
  reserved_count int DEFAULT NULL, success_count int DEFAULT NULL, failed_count int DEFAULT NULL, uncertain_count int DEFAULT NULL,
  PRIMARY KEY(id), UNIQUE KEY auto_idx_uk_ai_daily_usage(merchant_id,day_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_image_task (
  id bigint NOT NULL AUTO_INCREMENT, create_time datetime DEFAULT NULL, update_time datetime DEFAULT NULL,
  merchant_id bigint DEFAULT NULL, actor_id bigint DEFAULT NULL, actor_kind varchar(255) DEFAULT NULL,
  request_id varchar(255) DEFAULT NULL, request_hash varchar(255) DEFAULT NULL, coupon_id bigint DEFAULT NULL,
  parent_task_id bigint DEFAULT NULL, parent_material_id bigint DEFAULT NULL, mode varchar(255) DEFAULT NULL,
  model varchar(255) DEFAULT NULL, status varchar(255) DEFAULT NULL, quota_day varchar(255) DEFAULT NULL,
  prompt text, draft_snapshot mediumtext, provider_result mediumtext, provider_request_id varchar(255) DEFAULT NULL,
  material_id bigint DEFAULT NULL, image_key varchar(64) DEFAULT NULL, image_count int DEFAULT NULL,
  duration_ms bigint DEFAULT NULL, estimated_cost decimal(12,6) DEFAULT NULL, error_message varchar(500) DEFAULT NULL,
  started_at datetime DEFAULT NULL, completed_at datetime DEFAULT NULL, next_attempt_at datetime DEFAULT NULL,
  PRIMARY KEY(id), UNIQUE KEY auto_idx_uk_ai_image_task(merchant_id,actor_kind,actor_id,request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_audit (
  id bigint NOT NULL AUTO_INCREMENT, create_time datetime DEFAULT NULL, update_time datetime DEFAULT NULL,
  actor_id bigint DEFAULT NULL, merchant_id bigint DEFAULT NULL, task_id bigint DEFAULT NULL,
  action varchar(255) DEFAULT NULL, detail text, PRIMARY KEY(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @ai_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='space_info' AND column_name='source'),
 'SELECT 1', 'ALTER TABLE space_info ADD COLUMN source varchar(16) NULL COMMENT ''UPLOAD或AI，历史空值表示上传''');
PREPARE ai_stmt FROM @ai_ddl; EXECUTE ai_stmt; DEALLOCATE PREPARE ai_stmt;
SET @ai_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='space_info' AND column_name='ai_task_id'),
 'SELECT 1', 'ALTER TABLE space_info ADD COLUMN ai_task_id bigint NULL COMMENT ''AI生成任务''');
PREPARE ai_stmt FROM @ai_ddl; EXECUTE ai_stmt; DEALLOCATE PREPARE ai_stmt;
SET @ai_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='space_info' AND column_name='parent_material_id'),
 'SELECT 1', 'ALTER TABLE space_info ADD COLUMN parent_material_id bigint NULL COMMENT ''参考原图素材''');
PREPARE ai_stmt FROM @ai_ddl; EXECUTE ai_stmt; DEALLOCATE PREPARE ai_stmt;
SET @ai_ddl = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='space_info' AND index_name='auto_idx_uk_space_ai_task'),
 'SELECT 1', 'ALTER TABLE space_info ADD UNIQUE INDEX auto_idx_uk_space_ai_task(ai_task_id)');
PREPARE ai_stmt FROM @ai_ddl; EXECUTE ai_stmt; DEALLOCATE PREPARE ai_stmt;

-- 默认关闭、每日10次、单价未配置；不覆盖运营已保存的设置。
INSERT INTO ai_settings(id,create_time,update_time,enabled,allowed_models,default_daily_limit,estimated_unit_price)
SELECT 1,NOW(),NOW(),b'0','["qwen-image-3.0"]',10,NULL
WHERE NOT EXISTS(SELECT 1 FROM ai_settings WHERE id=1);
-- 菜单及独立权限由启动时 WorkspaceInitializer 幂等注册；不自动授予既有岗位。
