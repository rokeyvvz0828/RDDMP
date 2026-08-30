-- 公共附件长期绑定兼容性：TIMESTAMP 的上限约为 2038-01-19，无法保存绑定接口使用的 9999-12-31。
-- 仅追加、幂等调整列类型；临时附件的过期清理逻辑不变，DATETIME 仍可参与索引和时间比较。
SET @att_file_expires_at_type = (
    SELECT LOWER(DATA_TYPE)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'att_file'
      AND column_name = 'expires_at'
    LIMIT 1
);
SET @att_file_expires_at_sql = IF(
    @att_file_expires_at_type = 'timestamp',
    'ALTER TABLE att_file MODIFY COLUMN expires_at DATETIME NOT NULL',
    'SELECT 1'
);
PREPARE att_file_expires_at_stmt FROM @att_file_expires_at_sql;
EXECUTE att_file_expires_at_stmt;
DEALLOCATE PREPARE att_file_expires_at_stmt;
