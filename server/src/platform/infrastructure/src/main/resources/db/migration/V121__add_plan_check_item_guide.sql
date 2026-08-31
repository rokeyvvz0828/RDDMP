-- REQ-20260830-056：检查项增加“检查指标及内容”说明字段（UAT 检查表铺底数据支持）。
-- 只追加列，不动既有数据；JSON 结构中的检查项对象同步支持 guide 字段（旧数据兼容）。

ALTER TABLE arch_plan_check_item
    ADD COLUMN guide VARCHAR(2000) NULL COMMENT '检查指标及内容（含检查要求备注）；生成时从任务模板检查项复制' AFTER name;
