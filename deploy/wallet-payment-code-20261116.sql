-- MySQL 8.0. Apply to the same schema as wallet_bill and wallet_transaction.
CREATE TABLE IF NOT EXISTS wallet_payment_code (
    id BIGINT NOT NULL AUTO_INCREMENT,
    create_time DATETIME NULL,
    update_time DATETIME NULL,
    token_hash VARCHAR(64) NOT NULL,
    customer_id BIGINT NOT NULL,
    expires_at DATETIME NOT NULL,
    bill_id BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_wallet_payment_code_hash (token_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='wallet_bill' AND column_name='payment_channel')=0,
    'ALTER TABLE wallet_bill ADD COLUMN payment_channel VARCHAR(24) NULL', 'SELECT 1');
PREPARE payment_migration FROM @ddl; EXECUTE payment_migration; DEALLOCATE PREPARE payment_migration;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='wallet_bill' AND column_name='paid_at')=0,
    'ALTER TABLE wallet_bill ADD COLUMN paid_at DATETIME NULL', 'SELECT 1');
PREPARE payment_migration FROM @ddl; EXECUTE payment_migration; DEALLOCATE PREPARE payment_migration;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='wallet_transaction' AND column_name='payment_channel')=0,
    'ALTER TABLE wallet_transaction ADD COLUMN payment_channel VARCHAR(24) NULL', 'SELECT 1');
PREPARE payment_migration FROM @ddl; EXECUTE payment_migration; DEALLOCATE PREPARE payment_migration;
