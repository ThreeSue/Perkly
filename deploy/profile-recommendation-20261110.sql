-- 用户画像 1.0：先备份，在独立测试库验证后手动执行。无历史自动导入。
CREATE TABLE IF NOT EXISTS profile_snapshot (
 customer_id BIGINT PRIMARY KEY, revision BIGINT NOT NULL,
 evidence_json LONGTEXT NOT NULL, interpretation_json TEXT NOT NULL,
 model_version VARCHAR(100) NOT NULL, prompt_version VARCHAR(80) NOT NULL,
 cutoff DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL
);
CREATE TABLE IF NOT EXISTS profile_job (
 customer_id BIGINT PRIMARY KEY, state VARCHAR(20) NOT NULL,
 attempts INT NOT NULL DEFAULT 0, requested_version BIGINT NOT NULL,
 running_version BIGINT NULL, requested_at DATETIME(3) NOT NULL,
 next_attempt_at DATETIME(3) NOT NULL, lease_until DATETIME(3) NULL,
 error_code VARCHAR(80) NULL,
 INDEX idx_profile_job_due(state,next_attempt_at)
);
CREATE TABLE IF NOT EXISTS profile_history (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, customer_id BIGINT NOT NULL,
 revision BIGINT NOT NULL, cutoff DATETIME(3) NOT NULL, snapshot_json LONGTEXT NOT NULL,
 UNIQUE KEY uk_profile_history_version(customer_id,revision)
);
CREATE TABLE IF NOT EXISTS profile_correction (
 customer_id BIGINT PRIMARY KEY, excluded_labels TEXT NOT NULL, updated_at DATETIME(3) NOT NULL
);

