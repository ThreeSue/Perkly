-- MySQL 8，部署到关闭 AutoTable 的环境前执行。旧平台行的归属保持 NULL。
SET NAMES utf8mb4;

SET @material_ddl = IF(EXISTS (SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'space_info' AND column_name = 'merchant_id'),
    'SELECT 1', 'ALTER TABLE space_info ADD COLUMN merchant_id bigint NULL COMMENT ''商家素材归属，空值为原平台文件''');
PREPARE material_stmt FROM @material_ddl;
EXECUTE material_stmt;
DEALLOCATE PREPARE material_stmt;

SET @material_ddl = IF(EXISTS (SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'space_info' AND column_name = 'uploader_id'),
    'SELECT 1', 'ALTER TABLE space_info ADD COLUMN uploader_id bigint NULL COMMENT ''商家素材上传人''');
PREPARE material_stmt FROM @material_ddl;
EXECUTE material_stmt;
DEALLOCATE PREPARE material_stmt;

SET @material_ddl = IF(EXISTS (SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'space_info' AND column_name = 'material_deleted'),
    'SELECT 1', 'ALTER TABLE space_info ADD COLUMN material_deleted bit(1) NULL COMMENT ''商家素材删除标记''');
PREPARE material_stmt FROM @material_ddl;
EXECUTE material_stmt;
DEALLOCATE PREPARE material_stmt;

SET @material_ddl = IF(EXISTS (SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'space_type' AND column_name = 'merchant_id'),
    'SELECT 1', 'ALTER TABLE space_type ADD COLUMN merchant_id bigint NULL COMMENT ''商家分类归属，空值为原平台分类''');
PREPARE material_stmt FROM @material_ddl;
EXECUTE material_stmt;
DEALLOCATE PREPARE material_stmt;

SET @material_ddl = IF(EXISTS (SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'space_info' AND index_name = 'auto_idx_space_info_merchant_id'),
    'SELECT 1', 'ALTER TABLE space_info ADD INDEX auto_idx_space_info_merchant_id (merchant_id)');
PREPARE material_stmt FROM @material_ddl;
EXECUTE material_stmt;
DEALLOCATE PREPARE material_stmt;

SET @material_ddl = IF(EXISTS (SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'space_type' AND index_name = 'auto_idx_space_type_merchant_id'),
    'SELECT 1', 'ALTER TABLE space_type ADD INDEX auto_idx_space_type_merchant_id (merchant_id)');
PREPARE material_stmt FROM @material_ddl;
EXECUTE material_stmt;
DEALLOCATE PREPARE material_stmt;
