-- REQ-20260906-066：只保存显式参与人，负责人从系统记录实时合并，不扩大历史任务名单。
CREATE TABLE arch_subsystem_participant (
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    subsystem_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, project_id, subsystem_id, user_id),
    KEY idx_arch_subsystem_participant_user (tenant_id, project_id, user_id, subsystem_id),
    CONSTRAINT fk_arch_subsystem_participant_system FOREIGN KEY (tenant_id, project_id, subsystem_id)
        REFERENCES arch_physical_subsystem (tenant_id, project_id, id) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='物理子系统显式参与人员';

CREATE TABLE arch_subsystem_participant_audit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    subsystem_id BIGINT NOT NULL,
    actor_user_id BIGINT NOT NULL,
    before_user_ids JSON NOT NULL,
    after_user_ids JSON NOT NULL,
    reason VARCHAR(500) NOT NULL,
    trace_id VARCHAR(128) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_arch_subsystem_participant_audit (tenant_id, project_id, subsystem_id, id),
    CONSTRAINT fk_arch_subsystem_participant_audit_system FOREIGN KEY (tenant_id, project_id, subsystem_id)
        REFERENCES arch_physical_subsystem (tenant_id, project_id, id) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统参与人员变更审计';
