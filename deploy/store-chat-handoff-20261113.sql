-- 增量迁移；不修改历史会话，不自动开启AI。先备份并在目标业务库执行。
CREATE TABLE IF NOT EXISTS support_chat_presence (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 actor_id BIGINT NOT NULL, store_id BIGINT NOT NULL,
 session_id VARCHAR(36) NOT NULL, expires_at BIGINT NOT NULL,
 UNIQUE KEY uk_chat_presence_actor_session(actor_id,session_id),
 KEY idx_chat_presence_online(actor_id,store_id,expires_at)
);
SET @chat_handoff_ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='support_chat_settings' AND column_name='auto_takeover_enabled'),'SELECT 1','ALTER TABLE support_chat_settings ADD COLUMN auto_takeover_enabled TINYINT(1) NOT NULL DEFAULT 1');
PREPARE chat_handoff_stmt FROM @chat_handoff_ddl;
EXECUTE chat_handoff_stmt;
DEALLOCATE PREPARE chat_handoff_stmt;
SET @chat_handoff_ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='support_store_chat' AND column_name='human_requested'),'SELECT 1','ALTER TABLE support_store_chat ADD COLUMN human_requested TINYINT(1) NOT NULL DEFAULT 0');
PREPARE chat_handoff_stmt FROM @chat_handoff_ddl;
EXECUTE chat_handoff_stmt;
DEALLOCATE PREPARE chat_handoff_stmt;
