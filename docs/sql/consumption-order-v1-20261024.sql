-- 可重复执行；不修改已有购券订单、持券状态和历史核销数据。
SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS consumption_order (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 order_no VARCHAR(40) NOT NULL,
 merchant_id BIGINT NOT NULL,
 store_id BIGINT NOT NULL,
 merchant_name VARCHAR(200) NULL,
 store_name VARCHAR(200) NOT NULL,
 operator_id BIGINT NOT NULL,
 operator_name VARCHAR(200) NULL,
 request_id VARCHAR(64) NOT NULL,
 customer_coupon_id BIGINT NOT NULL,
 coupon_code CHAR(64) NOT NULL COMMENT '内部核销使用，不返回查询接口',
 coupon_name VARCHAR(200) NOT NULL,
 coupon_type VARCHAR(32) NOT NULL,
 coupon_snapshot MEDIUMTEXT NOT NULL,
 status VARCHAR(16) NOT NULL COMMENT 'PENDING/COMPLETED/CANCELLED',
 consumption_amount BIGINT NULL COMMENT '整数分，内容类券不填写',
 discount_amount BIGINT NOT NULL COMMENT '整数分，内容兑换不折算金额优惠',
 payable_amount BIGINT NOT NULL COMMENT '本次应补款整数分',
 received_amount BIGINT NOT NULL DEFAULT 0 COMMENT '商家确认本次线下收到的整数分',
 payment_mode VARCHAR(16) NULL COMMENT 'OFFLINE/NONE，不代表系统实际扣款',
 remark VARCHAR(200) NOT NULL DEFAULT '',
 version INT NOT NULL DEFAULT 1,
 active_coupon_id BIGINT NULL COMMENT '待结算和已完成占用，取消时释放',
 redemption_id BIGINT NULL,
 created_at DATETIME NOT NULL,
 completed_at DATETIME NULL,
 UNIQUE KEY uk_consumption_order_no(order_no),
 UNIQUE KEY uk_consumption_request(operator_id,request_id),
 UNIQUE KEY uk_consumption_active_coupon(active_coupon_id),
 UNIQUE KEY uk_consumption_redemption(redemption_id),
 KEY idx_consumption_store(merchant_id,store_id,id),
 KEY idx_consumption_operator(operator_id,id),
 KEY idx_consumption_coupon(customer_coupon_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='到店消费账单，区别于购券订单';
SET @bill_ddl=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='coupon_redemption' AND column_name='consumption_order_id')=0,
 'ALTER TABLE coupon_redemption ADD COLUMN consumption_order_id BIGINT NULL COMMENT ''关联到店消费账单，历史流水可为空''','SELECT 1');
PREPARE bill_stmt FROM @bill_ddl; EXECUTE bill_stmt; DEALLOCATE PREPARE bill_stmt;
SET @bill_ddl=IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='coupon_redemption' AND index_name='uk_redemption_consumption_order')=0,
 'ALTER TABLE coupon_redemption ADD UNIQUE KEY uk_redemption_consumption_order(consumption_order_id)','SELECT 1');
PREPARE bill_stmt FROM @bill_ddl; EXECUTE bill_stmt; DEALLOCATE PREPARE bill_stmt;

SET @bill_ddl=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='consumption_order' AND column_name='confirmed_by')=0,
 'ALTER TABLE consumption_order ADD COLUMN confirmed_by BIGINT NULL','SELECT 1');
PREPARE bill_stmt FROM @bill_ddl; EXECUTE bill_stmt; DEALLOCATE PREPARE bill_stmt;
SET @bill_ddl=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='consumption_order' AND column_name='confirmed_by_name')=0,
 'ALTER TABLE consumption_order ADD COLUMN confirmed_by_name VARCHAR(200) NULL','SELECT 1');
PREPARE bill_stmt FROM @bill_ddl; EXECUTE bill_stmt; DEALLOCATE PREPARE bill_stmt;
