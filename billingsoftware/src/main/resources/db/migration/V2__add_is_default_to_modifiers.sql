-- Safe addition of is_default to tbl_modifiers (idempotent if column already exists)
SET @col_exists = (
    SELECT COUNT(*) 
    FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() 
      AND TABLE_NAME = 'tbl_modifiers' 
      AND COLUMN_NAME = 'is_default'
);

SET @sql = IF(@col_exists = 0, 
    'ALTER TABLE `tbl_modifiers` ADD COLUMN `is_default` BOOLEAN NOT NULL DEFAULT FALSE', 
    'SELECT 1'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
