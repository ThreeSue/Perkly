-- 执行前备份；不预装默认敏感词，不修改用户状态。
CREATE TABLE IF NOT EXISTS search_risk_word (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, create_time DATETIME, update_time DATETIME,
 word VARCHAR(100) NOT NULL, mode VARCHAR(16) NOT NULL, enabled TINYINT NOT NULL
);
CREATE TABLE IF NOT EXISTS search_risk_record (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, create_time DATETIME, update_time DATETIME,
 user_id BIGINT NULL, content VARCHAR(100) NOT NULL, matched_words TEXT,
 source VARCHAR(255), mode VARCHAR(16), status VARCHAR(16), operator_id BIGINT,
 reason VARCHAR(200), handled_at DATETIME,
 INDEX idx_search_risk_status(status,id), INDEX idx_search_risk_user(user_id,id)
);
CREATE TABLE IF NOT EXISTS search_risk_audit (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, create_time DATETIME, update_time DATETIME,
 operator_id BIGINT, target_id BIGINT, action VARCHAR(32), detail TEXT
);
