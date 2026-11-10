-- 门店客服增量迁移：先备份，在目标业务库执行；不修改历史消息，不自动授予岗位权限。
-- 前置：旧 support_store_chat/support_chat_message 已存在。重复执行安全。
CREATE TABLE IF NOT EXISTS support_chat_settings (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 store_id BIGINT NOT NULL, ai_enabled TINYINT(1) NOT NULL DEFAULT 0,
 version INT NOT NULL DEFAULT 0, faqs TEXT,
 UNIQUE KEY uk_chat_settings_store(store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS support_chat_ai_job (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 chat_id BIGINT NOT NULL, message_id BIGINT NOT NULL, chat_version BIGINT NOT NULL,
 round INT NOT NULL, state VARCHAR(20) NOT NULL, lease_until BIGINT,
 UNIQUE KEY uk_chat_ai_message(message_id), KEY idx_chat_ai_job_state(state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @support_chat_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='support_store_chat' AND column_name='state'), 'SELECT 1', 'ALTER TABLE support_store_chat ADD COLUMN `state` VARCHAR(16) DEFAULT ''WAITING''');
PREPARE support_chat_stmt FROM @support_chat_ddl;
EXECUTE support_chat_stmt;
DEALLOCATE PREPARE support_chat_stmt;

SET @support_chat_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='support_store_chat' AND column_name='round'), 'SELECT 1', 'ALTER TABLE support_store_chat ADD COLUMN `round` INT DEFAULT 1');
PREPARE support_chat_stmt FROM @support_chat_ddl;
EXECUTE support_chat_stmt;
DEALLOCATE PREPARE support_chat_stmt;

SET @support_chat_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='support_store_chat' AND column_name='version'), 'SELECT 1', 'ALTER TABLE support_store_chat ADD COLUMN `version` BIGINT DEFAULT 0');
PREPARE support_chat_stmt FROM @support_chat_ddl;
EXECUTE support_chat_stmt;
DEALLOCATE PREPARE support_chat_stmt;

SET @support_chat_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='support_store_chat' AND column_name='assignee_id'), 'SELECT 1', 'ALTER TABLE support_store_chat ADD COLUMN `assignee_id` BIGINT NULL');
PREPARE support_chat_stmt FROM @support_chat_ddl;
EXECUTE support_chat_stmt;
DEALLOCATE PREPARE support_chat_stmt;

SET @support_chat_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='support_store_chat' AND column_name='latest_question_id'), 'SELECT 1', 'ALTER TABLE support_store_chat ADD COLUMN `latest_question_id` BIGINT NULL');
PREPARE support_chat_stmt FROM @support_chat_ddl;
EXECUTE support_chat_stmt;
DEALLOCATE PREPARE support_chat_stmt;

SET @support_chat_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='support_store_chat' AND column_name='summary'), 'SELECT 1', 'ALTER TABLE support_store_chat ADD COLUMN `summary` TEXT NULL');
PREPARE support_chat_stmt FROM @support_chat_ddl;
EXECUTE support_chat_stmt;
DEALLOCATE PREPARE support_chat_stmt;

SET @support_chat_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='support_chat_message' AND column_name='round'), 'SELECT 1', 'ALTER TABLE support_chat_message ADD COLUMN `round` INT DEFAULT 1');
PREPARE support_chat_stmt FROM @support_chat_ddl;
EXECUTE support_chat_stmt;
DEALLOCATE PREPARE support_chat_stmt;

SET @support_chat_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='support_chat_message' AND column_name='card_snapshot'), 'SELECT 1', 'ALTER TABLE support_chat_message ADD COLUMN `card_snapshot` MEDIUMTEXT NULL');
PREPARE support_chat_stmt FROM @support_chat_ddl;
EXECUTE support_chat_stmt;
DEALLOCATE PREPARE support_chat_stmt;

SET @support_chat_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='support_chat_message' AND column_name='reference_json'), 'SELECT 1', 'ALTER TABLE support_chat_message ADD COLUMN `reference_json` TEXT NULL');
PREPARE support_chat_stmt FROM @support_chat_ddl;
EXECUTE support_chat_stmt;
DEALLOCATE PREPARE support_chat_stmt;

SET @support_chat_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='support_chat_message' AND column_name='command'), 'SELECT 1', 'ALTER TABLE support_chat_message ADD COLUMN `command` VARCHAR(30) NULL');
PREPARE support_chat_stmt FROM @support_chat_ddl;
EXECUTE support_chat_stmt;
DEALLOCATE PREPARE support_chat_stmt;

SET @support_chat_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='support_chat_message' AND column_name='command_version'), 'SELECT 1', 'ALTER TABLE support_chat_message ADD COLUMN `command_version` BIGINT NULL');
PREPARE support_chat_stmt FROM @support_chat_ddl;
EXECUTE support_chat_stmt;
DEALLOCATE PREPARE support_chat_stmt;

SET @support_chat_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='support_chat_message' AND column_name='card_type'), 'SELECT 1', 'ALTER TABLE support_chat_message ADD COLUMN `card_type` VARCHAR(30) NULL');
PREPARE support_chat_stmt FROM @support_chat_ddl;
EXECUTE support_chat_stmt;
DEALLOCATE PREPARE support_chat_stmt;

SET @support_chat_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='support_chat_message' AND column_name='card_ref_id'), 'SELECT 1', 'ALTER TABLE support_chat_message ADD COLUMN `card_ref_id` BIGINT NULL');
PREPARE support_chat_stmt FROM @support_chat_ddl;
EXECUTE support_chat_stmt;
DEALLOCATE PREPARE support_chat_stmt;

-- 启动新版应用时 WorkspaceInitializer 幂等注册 chat:reply/chat:manage 权限目录。
-- 管理员须显式授权门店岗位/员工 chat:view + chat:reply；配置人员另授 chat:manage。
-- OWNER 身份不获得客服访问权。旧会话默认 WAITING，AI 开关默认关闭。
