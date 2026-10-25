-- 游客入驻及审批后营业码增量。可重复执行，不修改已有商家或账号。
CREATE TABLE IF NOT EXISTS merchant_activation (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 application_no VARCHAR(40) NOT NULL,
 phone VARCHAR(30) NOT NULL COMMENT '短信验证的联系人手机号；不依赖user_info',
 request_id VARCHAR(64) NOT NULL,
 code_hash VARCHAR(64) NULL COMMENT '随机营业码的SHA256摘要',
 code_cipher TEXT NULL COMMENT 'AES-GCM加密后供本人查询的营业码',
 expires_at DATETIME NULL,
 activated_at DATETIME NULL,
 account_id BIGINT NULL,
 UNIQUE KEY uk_activation_application(application_no),
 UNIQUE KEY uk_activation_phone_request(phone,request_id),
 UNIQUE KEY uk_activation_code(code_hash),
 KEY idx_activation_phone(phone,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
