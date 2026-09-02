-- =============================================================================
-- V132：需求管理术语脱敏统一（蒙商→同业、金科→我方）
-- -----------------------------------------------------------------------------
-- 背景：需求管理平台面向通用同业场景，用户可见「蒙商」「金科」统一替换为
-- 「同业」「我方」（含组合词，如「蒙商银行」→「同业银行」）。
-- 范围：仅更新 req_* 存量数据文本/枚举值与数据库列注释；
--       不重命名字段、不修改历史脚本、不影响接口契约。
-- 追加式迁移：本文件只在既有 V131 之后执行一次。
-- =============================================================================

-- 1) 新建项目需求差异清单（req_difference）：文本与枚举值
UPDATE req_difference SET
    jinke_practice = REPLACE(REPLACE(jinke_practice, '金科', '我方'), '蒙商', '同业'),
    monshang_practice = REPLACE(REPLACE(monshang_practice, '金科', '我方'), '蒙商', '同业'),
    difference_type = REPLACE(REPLACE(difference_type, '金科', '我方'), '蒙商', '同业'),
    difference_desc = REPLACE(REPLACE(difference_desc, '金科', '我方'), '蒙商', '同业'),
    monshang_dept = REPLACE(REPLACE(monshang_dept, '金科', '我方'), '蒙商', '同业'),
    monshang_analyst = REPLACE(REPLACE(monshang_analyst, '金科', '我方'), '蒙商', '同业'),
    jinke_analyst = REPLACE(REPLACE(jinke_analyst, '金科', '我方'), '蒙商', '同业'),
    monshang_confirm_dept = REPLACE(REPLACE(monshang_confirm_dept, '金科', '我方'), '蒙商', '同业'),
    jinke_confirmer = REPLACE(REPLACE(jinke_confirmer, '金科', '我方'), '蒙商', '同业'),
    solution = REPLACE(REPLACE(solution, '金科', '我方'), '蒙商', '同业'),
    decision_conclusion = REPLACE(REPLACE(decision_conclusion, '金科', '我方'), '蒙商', '同业'),
    review_comment = REPLACE(REPLACE(review_comment, '金科', '我方'), '蒙商', '同业')
WHERE deleted = 0
  AND (jinke_practice LIKE '%金科%' OR jinke_practice LIKE '%蒙商%'
    OR monshang_practice LIKE '%金科%' OR monshang_practice LIKE '%蒙商%'
    OR difference_type LIKE '%金科%' OR difference_type LIKE '%蒙商%'
    OR difference_desc LIKE '%金科%' OR difference_desc LIKE '%蒙商%'
    OR monshang_dept LIKE '%金科%' OR monshang_dept LIKE '%蒙商%'
    OR monshang_analyst LIKE '%金科%' OR monshang_analyst LIKE '%蒙商%'
    OR jinke_analyst LIKE '%金科%' OR jinke_analyst LIKE '%蒙商%'
    OR monshang_confirm_dept LIKE '%金科%' OR monshang_confirm_dept LIKE '%蒙商%'
    OR jinke_confirmer LIKE '%金科%' OR jinke_confirmer LIKE '%蒙商%'
    OR solution LIKE '%金科%' OR solution LIKE '%蒙商%'
    OR decision_conclusion LIKE '%金科%' OR decision_conclusion LIKE '%蒙商%'
    OR review_comment LIKE '%金科%' OR review_comment LIKE '%蒙商%');

-- 2) 存量常态化需求（req_legacy_requirement）：文档名/负责人/归属/变更结论等
UPDATE req_legacy_requirement SET
    legacy_doc_name = REPLACE(REPLACE(legacy_doc_name, '金科', '我方'), '蒙商', '同业'),
    requirement_no = REPLACE(REPLACE(requirement_no, '金科', '我方'), '蒙商', '同业'),
    requirement_name = REPLACE(REPLACE(requirement_name, '金科', '我方'), '蒙商', '同业'),
    content_summary = REPLACE(REPLACE(content_summary, '金科', '我方'), '蒙商', '同业'),
    monshang_ba = REPLACE(REPLACE(monshang_ba, '金科', '我方'), '蒙商', '同业'),
    monshang_architect = REPLACE(REPLACE(monshang_architect, '金科', '我方'), '蒙商', '同业'),
    jinke_contact = REPLACE(REPLACE(jinke_contact, '金科', '我方'), '蒙商', '同业'),
    need_jinke_arch_decision = REPLACE(REPLACE(need_jinke_arch_decision, '金科', '我方'), '蒙商', '同业'),
    jinke_architect = REPLACE(REPLACE(jinke_architect, '金科', '我方'), '蒙商', '同业'),
    unified_managed = REPLACE(REPLACE(unified_managed, '金科', '我方'), '蒙商', '同业'),
    soft_doc_name = REPLACE(REPLACE(soft_doc_name, '金科', '我方'), '蒙商', '同业'),
    owner_conglomerate = REPLACE(REPLACE(owner_conglomerate, '金科', '我方'), '蒙商', '同业'),
    owner_system = REPLACE(REPLACE(owner_system, '金科', '我方'), '蒙商', '同业'),
    coord_conglomerate = REPLACE(REPLACE(coord_conglomerate, '金科', '我方'), '蒙商', '同业'),
    coord_system = REPLACE(REPLACE(coord_system, '金科', '我方'), '蒙商', '同业'),
    change_info = REPLACE(REPLACE(change_info, '金科', '我方'), '蒙商', '同业'),
    change_conclusion_status = REPLACE(REPLACE(change_conclusion_status, '金科', '我方'), '蒙商', '同业'),
    workload_change = REPLACE(REPLACE(workload_change, '金科', '我方'), '蒙商', '同业'),
    remark = REPLACE(REPLACE(remark, '金科', '我方'), '蒙商', '同业')
WHERE deleted = 0
  AND (legacy_doc_name LIKE '%金科%' OR legacy_doc_name LIKE '%蒙商%'
    OR requirement_no LIKE '%金科%' OR requirement_no LIKE '%蒙商%'
    OR requirement_name LIKE '%金科%' OR requirement_name LIKE '%蒙商%'
    OR content_summary LIKE '%金科%' OR content_summary LIKE '%蒙商%'
    OR monshang_ba LIKE '%金科%' OR monshang_ba LIKE '%蒙商%'
    OR monshang_architect LIKE '%金科%' OR monshang_architect LIKE '%蒙商%'
    OR jinke_contact LIKE '%金科%' OR jinke_contact LIKE '%蒙商%'
    OR need_jinke_arch_decision LIKE '%金科%' OR need_jinke_arch_decision LIKE '%蒙商%'
    OR jinke_architect LIKE '%金科%' OR jinke_architect LIKE '%蒙商%'
    OR unified_managed LIKE '%金科%' OR unified_managed LIKE '%蒙商%'
    OR soft_doc_name LIKE '%金科%' OR soft_doc_name LIKE '%蒙商%'
    OR owner_conglomerate LIKE '%金科%' OR owner_conglomerate LIKE '%蒙商%'
    OR owner_system LIKE '%金科%' OR owner_system LIKE '%蒙商%'
    OR coord_conglomerate LIKE '%金科%' OR coord_conglomerate LIKE '%蒙商%'
    OR coord_system LIKE '%金科%' OR coord_system LIKE '%蒙商%'
    OR change_info LIKE '%金科%' OR change_info LIKE '%蒙商%'
    OR change_conclusion_status LIKE '%金科%' OR change_conclusion_status LIKE '%蒙商%'
    OR workload_change LIKE '%金科%' OR workload_change LIKE '%蒙商%'
    OR remark LIKE '%金科%' OR remark LIKE '%蒙商%');

-- 3) 系统清单（req_system）：归属机构「蒙商银行」→「同业银行」，介绍文本替换
UPDATE req_system SET
    system_name = REPLACE(REPLACE(system_name, '金科', '我方'), '蒙商', '同业'),
    conglomerate = REPLACE(REPLACE(conglomerate, '金科', '我方'), '蒙商', '同业'),
    introduction = REPLACE(REPLACE(introduction, '金科', '我方'), '蒙商', '同业')
WHERE deleted = 0
  AND (system_name LIKE '%金科%' OR system_name LIKE '%蒙商%'
    OR conglomerate LIKE '%金科%' OR conglomerate LIKE '%蒙商%'
    OR introduction LIKE '%金科%' OR introduction LIKE '%蒙商%');

-- 4) 统一改动记录（req_change_log）：历史快照中的旧词一并替换，保持可追溯一致
UPDATE req_change_log SET
    old_value = REPLACE(REPLACE(old_value, '金科', '我方'), '蒙商', '同业'),
    new_value = REPLACE(REPLACE(new_value, '金科', '我方'), '蒙商', '同业')
WHERE deleted = 0
  AND (old_value LIKE '%金科%' OR old_value LIKE '%蒙商%'
    OR new_value LIKE '%金科%' OR new_value LIKE '%蒙商%');

-- 5) 列注释同步（只改注释，不改列名/类型/默认值）
ALTER TABLE req_difference
    MODIFY COLUMN jinke_practice TEXT NULL COMMENT '我方做法',
    MODIFY COLUMN monshang_practice TEXT NULL COMMENT '同业作法',
    MODIFY COLUMN monshang_dept VARCHAR(128) NULL COMMENT '同业分析部门',
    MODIFY COLUMN monshang_analyst VARCHAR(64) NULL COMMENT '同业分析人',
    MODIFY COLUMN jinke_analyst VARCHAR(64) NULL COMMENT '我方分析人',
    MODIFY COLUMN monshang_confirm_dept VARCHAR(128) NULL COMMENT '同业确认部门',
    MODIFY COLUMN jinke_confirmer VARCHAR(64) NULL COMMENT '我方确认人';

ALTER TABLE req_legacy_requirement
    MODIFY COLUMN monshang_ba VARCHAR(64) NULL COMMENT '同业 BA',
    MODIFY COLUMN monshang_architect VARCHAR(64) NULL COMMENT '同业架构',
    MODIFY COLUMN jinke_contact VARCHAR(128) NULL COMMENT '我方对接人及电话',
    MODIFY COLUMN need_jinke_arch_decision VARCHAR(4) NULL COMMENT '是否需要我方架构决策：是/否',
    MODIFY COLUMN jinke_architect VARCHAR(64) NULL COMMENT '我方架构人员',
    MODIFY COLUMN unified_managed VARCHAR(4) NULL COMMENT '是否纳入同业统一管理：是/否',
    MODIFY COLUMN involve_cooperation VARCHAR(4) NULL COMMENT '是否涉及我方引入组件协同：是/否',
    MODIFY COLUMN change_conclusion_status VARCHAR(32) NULL COMMENT '变更结论及状态：审核通过/评估工作量/同业立项完成';
