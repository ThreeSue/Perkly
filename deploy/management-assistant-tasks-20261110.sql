-- 管理端 AI 确认任务；上线前备份并在目标库执行。不包含密钥、会话原文或测试账号。
CREATE TABLE IF NOT EXISTS ai_management_task (
  id BIGINT NOT NULL AUTO_INCREMENT,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  token VARCHAR(64) NOT NULL,
  actor_id BIGINT NOT NULL,
  actor_kind VARCHAR(16) NOT NULL,
  session_id VARCHAR(64) NOT NULL,
  login_fingerprint VARCHAR(64) NOT NULL,
  state VARCHAR(20) NOT NULL,
  steps MEDIUMTEXT NOT NULL,
  previews MEDIUMTEXT NOT NULL,
  results MEDIUMTEXT NOT NULL,
  completed INT NOT NULL DEFAULT 0,
  expires_at BIGINT,
  PRIMARY KEY (id),
  UNIQUE KEY uk_management_task_token (token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
