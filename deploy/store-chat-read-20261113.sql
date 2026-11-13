-- 聊天阅读进度增量迁移，不修改历史消息。部署新版后端前在目标业务库执行。
CREATE TABLE IF NOT EXISTS support_chat_read (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 chat_id BIGINT NOT NULL,
 reader_kind VARCHAR(16) NOT NULL,
 reader_id BIGINT NOT NULL,
 last_read_message_id BIGINT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_chat_reader(chat_id,reader_kind,reader_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
