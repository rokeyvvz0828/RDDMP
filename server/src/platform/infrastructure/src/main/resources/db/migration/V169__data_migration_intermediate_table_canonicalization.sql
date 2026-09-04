-- V169: 中间表模型唯一化。
-- 测试节点不保留 dm_intermediate_table 存量；应用已统一使用
-- dm_target_table(table_category='INTERMEDIATE') + dm_target_table_field。
-- 删除前必须断言旧表为空，非空时以 SIGNAL 失败，禁止静默丢失数据。

DELIMITER $$
CREATE PROCEDURE dm_v169_assert_intermediate_empty()
BEGIN
    DECLARE row_count BIGINT DEFAULT 0;
    SELECT COUNT(*) INTO row_count FROM dm_intermediate_table;
    IF row_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V169 失败：dm_intermediate_table 非空，禁止删除，请先处理测试数据';
    END IF;
END$$
DELIMITER ;

CALL dm_v169_assert_intermediate_empty();
DROP PROCEDURE dm_v169_assert_intermediate_empty;
DROP TABLE dm_intermediate_table;
