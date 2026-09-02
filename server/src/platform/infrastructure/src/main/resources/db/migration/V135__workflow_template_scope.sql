-- 将存量租户全局流程明确为可执行的平台流程，并为不可执行结构模板预留独立范围。

UPDATE wf_definition
SET scope_type = 'PLATFORM', project_id = NULL
WHERE scope_type = 'GLOBAL';

ALTER TABLE wf_definition
    MODIFY COLUMN scope_type VARCHAR(16) NOT NULL DEFAULT 'PLATFORM'
        COMMENT '流程范围：PLATFORM平台流程、TEMPLATE全局模板、PROJECT项目流程';
