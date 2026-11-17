-- Additive migration. Run before enabling the consumer. No historical figures are seeded.
CREATE TABLE IF NOT EXISTS sms_receipt (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 create_time DATETIME NULL,
 update_time DATETIME NULL,
 biz_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 phone_mask VARCHAR(16) NOT NULL,
 result VARCHAR(24) NOT NULL,
 provider_code VARCHAR(80) NULL,
 sent_at DATETIME NOT NULL,
 reported_at DATETIME NOT NULL,
 UNIQUE KEY uk_sms_receipt_biz (biz_id),
 KEY idx_sms_receipt_sent (sent_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
