-- MySQL 8 增量，运行前选择项目库。空字段按历史兼容规则读取。
SET NAMES utf8mb4;
SET @settlement_ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wallet_bill' AND column_name='consumption_order_id'),
 'SELECT 1','ALTER TABLE wallet_bill ADD COLUMN consumption_order_id BIGINT NULL');
PREPARE settlement_stmt FROM @settlement_ddl; EXECUTE settlement_stmt; DEALLOCATE PREPARE settlement_stmt;
SET @settlement_ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wallet_bill' AND column_name='version'),
 'SELECT 1','ALTER TABLE wallet_bill ADD COLUMN version INT NULL');
PREPARE settlement_stmt FROM @settlement_ddl; EXECUTE settlement_stmt; DEALLOCATE PREPARE settlement_stmt;
SET @settlement_ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wallet_bill' AND column_name='coupon_discount'),
 'SELECT 1','ALTER TABLE wallet_bill ADD COLUMN coupon_discount BIGINT NULL');
PREPARE settlement_stmt FROM @settlement_ddl; EXECUTE settlement_stmt; DEALLOCATE PREPARE settlement_stmt;
SET @settlement_ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wallet_bill' AND column_name='remaining_amount'),
 'SELECT 1','ALTER TABLE wallet_bill ADD COLUMN remaining_amount BIGINT NULL');
PREPARE settlement_stmt FROM @settlement_ddl; EXECUTE settlement_stmt; DEALLOCATE PREPARE settlement_stmt;
SET @settlement_ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wallet_bill' AND column_name='payment_mode'),
 'SELECT 1','ALTER TABLE wallet_bill ADD COLUMN payment_mode VARCHAR(32) NULL');
PREPARE settlement_stmt FROM @settlement_ddl; EXECUTE settlement_stmt; DEALLOCATE PREPARE settlement_stmt;
SET @settlement_ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wallet_bill' AND column_name='payment_request_id'),
 'SELECT 1','ALTER TABLE wallet_bill ADD COLUMN payment_request_id VARCHAR(64) NULL');
PREPARE settlement_stmt FROM @settlement_ddl; EXECUTE settlement_stmt; DEALLOCATE PREPARE settlement_stmt;
SET @settlement_ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wallet_bill' AND column_name='paid_amount'),
 'SELECT 1','ALTER TABLE wallet_bill ADD COLUMN paid_amount BIGINT NULL');
PREPARE settlement_stmt FROM @settlement_ddl; EXECUTE settlement_stmt; DEALLOCATE PREPARE settlement_stmt;
SET @settlement_ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wallet_bill' AND column_name='settlement_snapshot'),
 'SELECT 1','ALTER TABLE wallet_bill ADD COLUMN settlement_snapshot MEDIUMTEXT NULL');
PREPARE settlement_stmt FROM @settlement_ddl; EXECUTE settlement_stmt; DEALLOCATE PREPARE settlement_stmt;
SET @settlement_ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='consumption_order' AND column_name='settlement_bill_id'),
 'SELECT 1','ALTER TABLE consumption_order ADD COLUMN settlement_bill_id BIGINT NULL');
PREPARE settlement_stmt FROM @settlement_ddl; EXECUTE settlement_stmt; DEALLOCATE PREPARE settlement_stmt;
SET @settlement_ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wallet_activity' AND column_name='enabled'),
 'SELECT 1','ALTER TABLE wallet_activity ADD COLUMN enabled BIT(1) NULL');
PREPARE settlement_stmt FROM @settlement_ddl; EXECUTE settlement_stmt; DEALLOCATE PREPARE settlement_stmt;
SET @settlement_ddl=IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='wallet_bill' AND index_name='uk_wallet_bill_payment'),
 'SELECT 1','ALTER TABLE wallet_bill ADD UNIQUE INDEX uk_wallet_bill_payment (customer_id,payment_request_id)');
PREPARE settlement_stmt FROM @settlement_ddl; EXECUTE settlement_stmt; DEALLOCATE PREPARE settlement_stmt;
SET @settlement_ddl=IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='wallet_bill' AND index_name='auto_idx_wallet_bill_consumption_order_id'),
 'SELECT 1','ALTER TABLE wallet_bill ADD UNIQUE INDEX auto_idx_wallet_bill_consumption_order_id (consumption_order_id)');
PREPARE settlement_stmt FROM @settlement_ddl; EXECUTE settlement_stmt; DEALLOCATE PREPARE settlement_stmt;
