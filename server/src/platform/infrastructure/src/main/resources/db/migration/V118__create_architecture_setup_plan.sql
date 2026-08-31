-- REQ-20260830-056：搭建计划、计划目标快照、环节、任务、参与人与检查项。
-- 生成计划时从已发布模板版本复制结构并保存模板版本与目标快照；执行状态与进度由系统计算。

CREATE TABLE arch_setup_plan (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    plan_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '计划编号，SP+id',
    name VARCHAR(300) NOT NULL,
    environment_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'NOT_STARTED' COMMENT 'NOT_STARTED/IN_PROGRESS/COMPLETED/CANCELLED',
    template_id BIGINT NOT NULL,
    template_version_no INT NOT NULL,
    plan_owner_user_id BIGINT NOT NULL,
    planned_start TIMESTAMP NULL,
    planned_end TIMESTAMP NULL,
    actual_start TIMESTAMP NULL COMMENT '由执行事件聚合',
    actual_end TIMESTAMP NULL COMMENT '最近一次完成事件聚合',
    cancelled TINYINT NOT NULL DEFAULT 0,
    cancel_reason VARCHAR(1000) NULL,
    cancelled_by BIGINT NULL,
    cancelled_at TIMESTAMP NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_setup_plan_tenant_id (tenant_id, id),
    UNIQUE KEY uk_arch_setup_plan_no (tenant_id, plan_no),
    KEY idx_arch_setup_plan_env (tenant_id, environment_id, status, id),
    KEY idx_arch_setup_plan_owner (tenant_id, plan_owner_user_id, status),
    KEY idx_arch_setup_plan_template (tenant_id, template_id),
    CONSTRAINT fk_arch_setup_plan_env
        FOREIGN KEY (tenant_id, environment_id)
        REFERENCES arch_environment (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_arch_setup_plan_template
        FOREIGN KEY (tenant_id, template_id)
        REFERENCES arch_plan_template (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_setup_plan_status CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_arch_setup_plan_template_version CHECK (template_version_no > 0),
    CONSTRAINT chk_arch_setup_plan_cancelled CHECK (cancelled IN (0, 1)),
    CONSTRAINT chk_arch_setup_plan_row_version CHECK (row_version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='搭建计划';

CREATE TABLE arch_plan_target (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    plan_id BIGINT NOT NULL,
    target_type VARCHAR(32) NOT NULL COMMENT 'PHYSICAL_SUBSYSTEM/DEPLOYMENT_UNIT',
    target_id BIGINT NOT NULL,
    target_no VARCHAR(64) NULL,
    target_name VARCHAR(300) NOT NULL,
    target_snapshot_json JSON NULL COMMENT '生成时目标关联信息快照',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/REMOVED',
    added_reason VARCHAR(1000) NULL,
    removed_reason VARCHAR(1000) NULL,
    removed_by BIGINT NULL,
    removed_at TIMESTAMP NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_plan_target_tenant_id (tenant_id, id),
    UNIQUE KEY uk_arch_plan_target_plan_target (tenant_id, plan_id, target_type, target_id),
    KEY idx_arch_plan_target_plan (tenant_id, plan_id, status),
    CONSTRAINT fk_arch_plan_target_plan
        FOREIGN KEY (tenant_id, plan_id)
        REFERENCES arch_setup_plan (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_plan_target_type CHECK (target_type IN ('PHYSICAL_SUBSYSTEM', 'DEPLOYMENT_UNIT')),
    CONSTRAINT chk_arch_plan_target_status CHECK (status IN ('ACTIVE', 'REMOVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='搭建计划目标快照';

CREATE TABLE arch_plan_stage (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    plan_id BIGINT NOT NULL,
    stage_no INT NOT NULL,
    name VARCHAR(200) NOT NULL,
    sort_no INT NOT NULL DEFAULT 0,
    owner_user_id BIGINT NOT NULL,
    planned_start TIMESTAMP NULL,
    planned_end TIMESTAMP NULL,
    actual_start TIMESTAMP NULL COMMENT '由执行事件聚合',
    actual_end TIMESTAMP NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'NOT_STARTED' COMMENT 'NOT_STARTED/IN_PROGRESS/COMPLETED/CANCELLED',
    cancelled TINYINT NOT NULL DEFAULT 0,
    cancel_reason VARCHAR(1000) NULL,
    cancelled_by BIGINT NULL,
    cancelled_at TIMESTAMP NULL,
    snapshot_json JSON NULL COMMENT '生成时该环节结构快照',
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_plan_stage_tenant_id (tenant_id, id),
    UNIQUE KEY uk_arch_plan_stage_no (tenant_id, plan_id, stage_no),
    KEY idx_arch_plan_stage_plan (tenant_id, plan_id, status, sort_no),
    CONSTRAINT fk_arch_plan_stage_plan
        FOREIGN KEY (tenant_id, plan_id)
        REFERENCES arch_setup_plan (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_plan_stage_status CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_arch_plan_stage_no CHECK (stage_no > 0),
    CONSTRAINT chk_arch_plan_stage_cancelled CHECK (cancelled IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='搭建计划环节';

CREATE TABLE arch_plan_task (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    plan_id BIGINT NOT NULL,
    stage_id BIGINT NOT NULL,
    task_no INT NOT NULL,
    name VARCHAR(300) NOT NULL,
    target_type VARCHAR(32) NULL COMMENT 'PHYSICAL_SUBSYSTEM/DEPLOYMENT_UNIT；计划级任务为 NULL',
    target_id BIGINT NULL,
    target_no VARCHAR(64) NULL,
    target_name VARCHAR(300) NULL,
    task_template_id BIGINT NULL,
    task_template_version_no INT NULL,
    dimension VARCHAR(32) NULL COMMENT '生成时维度 NONE/PHYSICAL_SUBSYSTEM/DEPLOYMENT_UNIT',
    snapshot_json JSON NULL COMMENT '生成时任务模板快照（名称与检查项定义）',
    owner_user_id BIGINT NOT NULL,
    planned_start TIMESTAMP NULL,
    planned_end TIMESTAMP NULL,
    actual_start TIMESTAMP NULL COMMENT '由执行事件聚合',
    actual_end TIMESTAMP NULL COMMENT '最近一次完成事件聚合',
    status VARCHAR(24) NOT NULL DEFAULT 'NOT_STARTED' COMMENT 'NOT_STARTED/WAITING_PRECEDING/IN_PROGRESS/BLOCKED/COMPLETED/CANCELLED',
    waived_all TINYINT NOT NULL DEFAULT 0 COMMENT '全部检查项均被取消',
    cancelled TINYINT NOT NULL DEFAULT 0,
    cancel_reason VARCHAR(1000) NULL,
    cancelled_by BIGINT NULL,
    cancelled_at TIMESTAMP NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_plan_task_tenant_id (tenant_id, id),
    UNIQUE KEY uk_arch_plan_task_no (tenant_id, stage_id, task_no),
    KEY idx_arch_plan_task_plan (tenant_id, plan_id, status),
    KEY idx_arch_plan_task_stage (tenant_id, stage_id, status),
    KEY idx_arch_plan_task_target (tenant_id, target_type, target_id, status),
    KEY idx_arch_plan_task_owner (tenant_id, owner_user_id, status),
    CONSTRAINT fk_arch_plan_task_plan
        FOREIGN KEY (tenant_id, plan_id)
        REFERENCES arch_setup_plan (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_arch_plan_task_stage
        FOREIGN KEY (tenant_id, stage_id)
        REFERENCES arch_plan_stage (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_plan_task_target_type CHECK (target_type IS NULL OR target_type IN ('PHYSICAL_SUBSYSTEM', 'DEPLOYMENT_UNIT')),
    CONSTRAINT chk_arch_plan_task_dimension CHECK (dimension IS NULL OR dimension IN ('NONE', 'PHYSICAL_SUBSYSTEM', 'DEPLOYMENT_UNIT')),
    CONSTRAINT chk_arch_plan_task_status CHECK (status IN ('NOT_STARTED', 'WAITING_PRECEDING', 'IN_PROGRESS', 'BLOCKED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_arch_plan_task_waived CHECK (waived_all IN (0, 1)),
    CONSTRAINT chk_arch_plan_task_cancelled CHECK (cancelled IN (0, 1)),
    CONSTRAINT chk_arch_plan_task_row_version CHECK (row_version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='搭建计划任务';

CREATE TABLE arch_plan_task_participant (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_plan_task_participant_tenant_id (tenant_id, id),
    UNIQUE KEY uk_arch_plan_task_participant_user (tenant_id, task_id, user_id),
    KEY idx_arch_plan_task_participant_task (tenant_id, task_id),
    CONSTRAINT fk_arch_plan_task_participant_task
        FOREIGN KEY (tenant_id, task_id)
        REFERENCES arch_plan_task (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务参与人';

CREATE TABLE arch_plan_check_item (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    check_no INT NOT NULL,
    name VARCHAR(500) NOT NULL,
    sort_no INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/COMPLETED/CANCELLED',
    remark VARCHAR(2000) NULL,
    completed_by BIGINT NULL,
    completed_at TIMESTAMP NULL,
    cancelled TINYINT NOT NULL DEFAULT 0,
    cancel_reason VARCHAR(1000) NULL,
    cancelled_by BIGINT NULL,
    cancelled_at TIMESTAMP NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_plan_check_item_tenant_id (tenant_id, id),
    UNIQUE KEY uk_arch_plan_check_item_no (tenant_id, task_id, check_no),
    KEY idx_arch_plan_check_item_task (tenant_id, task_id, status),
    CONSTRAINT fk_arch_plan_check_item_task
        FOREIGN KEY (tenant_id, task_id)
        REFERENCES arch_plan_task (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_plan_check_item_status CHECK (status IN ('PENDING', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_arch_plan_check_item_cancelled CHECK (cancelled IN (0, 1)),
    CONSTRAINT chk_arch_plan_check_item_row_version CHECK (row_version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='检查项';
