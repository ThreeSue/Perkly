-- SMS verification usage; additive and safe to repeat.
CREATE TABLE IF NOT EXISTS sms_usage_policy (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 create_time DATETIME NULL,
 update_time DATETIME NULL,
 daily_limit INT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT IGNORE INTO sms_usage_policy (id, daily_limit, create_time, update_time) VALUES (1, 5, NOW(), NOW());

CREATE TABLE IF NOT EXISTS sms_usage_gate (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 create_time DATETIME NULL,
 update_time DATETIME NULL,
 phone_key VARCHAR(64) NOT NULL,
 day_key CHAR(10) NOT NULL,
 attempts INT NOT NULL,
 next_send_at BIGINT NOT NULL,
 UNIQUE KEY uk_sms_gate_phone (phone_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sms_usage_event (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 create_time DATETIME NULL,
 update_time DATETIME NULL,
 day_key VARCHAR(10) NOT NULL,
 kind VARCHAR(16) NOT NULL,
 scene VARCHAR(32) NOT NULL,
 result VARCHAR(24) NOT NULL,
 phone_mask VARCHAR(16) NULL,
 send_id BIGINT NULL,
 biz_id VARCHAR(128) NULL,
 provider_code VARCHAR(80) NULL,
 provider_request_id VARCHAR(128) NULL,
 actor_type VARCHAR(24) NULL,
 actor_id BIGINT NULL,
 verified_account_id BIGINT NULL,
 previous_limit INT NULL,
 next_limit INT NULL,
 KEY idx_sms_event_day_kind (day_key, kind),
 KEY idx_sms_event_send (send_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
