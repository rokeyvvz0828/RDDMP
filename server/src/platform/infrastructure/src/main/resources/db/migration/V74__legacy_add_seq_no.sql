-- V42：存量需求加序号字段（Excel 映射"序号"列）
ALTER TABLE req_legacy_requirement ADD COLUMN seq_no INT NULL COMMENT '序号（原则上不能重复）' AFTER id;

-- 回填：按 id 生成默认序号
UPDATE req_legacy_requirement SET seq_no = id WHERE seq_no IS NULL;
