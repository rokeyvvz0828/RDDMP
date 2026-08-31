-- REQ-20260830-056：计划模板流程图编排——环节时间范围、环节模板依赖、任务模板依赖、计划环节依赖。
-- 只追加，不修改 V1-V110。

ALTER TABLE arch_plan_template_stage
    ADD COLUMN start_offset_days INT NULL COMMENT '环节相对计划开始的偏移天数（模板配置，生成计划时计算环节计划开始）' AFTER sort_no,
    ADD COLUMN duration_days INT NULL COMMENT '环节持续天数（模板配置，生成计划时计算环节计划结束）' AFTER start_offset_days,
    ADD CONSTRAINT chk_arch_plan_template_stage_offset CHECK (start_offset_days IS NULL OR start_offset_days >= 0),
    ADD CONSTRAINT chk_arch_plan_template_stage_duration CHECK (duration_days IS NULL OR duration_days > 0);

CREATE TABLE arch_plan_template_stage_dependency (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    template_id BIGINT NOT NULL,
    stage_id BIGINT NOT NULL COMMENT '后续环节模板',
    predecessor_stage_id BIGINT NOT NULL COMMENT '前置环节模板',
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_plan_template_stage_dep_tenant_id (tenant_id, id),
    UNIQUE KEY uk_arch_plan_template_stage_dep_pair (tenant_id, stage_id, predecessor_stage_id),
    KEY idx_arch_plan_template_stage_dep_stage (tenant_id, stage_id),
    KEY idx_arch_plan_template_stage_dep_pred (tenant_id, predecessor_stage_id),
    CONSTRAINT fk_arch_plan_template_stage_dep_stage
        FOREIGN KEY (tenant_id, stage_id)
        REFERENCES arch_plan_template_stage (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_arch_plan_template_stage_dep_pred
        FOREIGN KEY (tenant_id, predecessor_stage_id)
        REFERENCES arch_plan_template_stage (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT chk_arch_plan_template_stage_dep_distinct CHECK (stage_id <> predecessor_stage_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='计划模板环节间依赖（流程图连线）';

CREATE TABLE arch_task_template_dependency (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    template_id BIGINT NOT NULL,
    stage_id BIGINT NOT NULL,
    task_template_id BIGINT NOT NULL COMMENT '后续任务模板',
    predecessor_task_template_id BIGINT NOT NULL COMMENT '前置任务模板（同一环节内）',
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_task_template_dep_tenant_id (tenant_id, id),
    UNIQUE KEY uk_arch_task_template_dep_pair (tenant_id, task_template_id, predecessor_task_template_id),
    KEY idx_arch_task_template_dep_task (tenant_id, task_template_id),
    KEY idx_arch_task_template_dep_pred (tenant_id, predecessor_task_template_id),
    CONSTRAINT fk_arch_task_template_dep_task
        FOREIGN KEY (tenant_id, task_template_id)
        REFERENCES arch_task_template (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_arch_task_template_dep_pred
        FOREIGN KEY (tenant_id, predecessor_task_template_id)
        REFERENCES arch_task_template (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT chk_arch_task_template_dep_distinct CHECK (task_template_id <> predecessor_task_template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='同一环节内任务模板依赖（流程图连线）';

CREATE TABLE arch_plan_stage_dependency (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    plan_id BIGINT NOT NULL,
    stage_id BIGINT NOT NULL COMMENT '后续环节',
    predecessor_stage_id BIGINT NOT NULL COMMENT '前置环节',
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_plan_stage_dep_tenant_id (tenant_id, id),
    UNIQUE KEY uk_arch_plan_stage_dep_pair (tenant_id, stage_id, predecessor_stage_id),
    KEY idx_arch_plan_stage_dep_stage (tenant_id, stage_id),
    KEY idx_arch_plan_stage_dep_pred (tenant_id, predecessor_stage_id),
    CONSTRAINT fk_arch_plan_stage_dep_stage
        FOREIGN KEY (tenant_id, stage_id)
        REFERENCES arch_plan_stage (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_arch_plan_stage_dep_pred
        FOREIGN KEY (tenant_id, predecessor_stage_id)
        REFERENCES arch_plan_stage (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT chk_arch_plan_stage_dep_distinct CHECK (stage_id <> predecessor_stage_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='计划环节间依赖（生成时从模板依赖落库）';
