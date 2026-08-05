-- Organization owner used by workflow assignee resolution.
ALTER TABLE sys_org
    ADD COLUMN leader_id BIGINT NULL AFTER org_name,
    ADD KEY idx_sys_org_leader (tenant_id, leader_id);
