-- =============================================================================
-- REQ-20260919-078（部分）：需求数据 project_id 统一为项目管理主键 pm_project.id
-- 背景：需求模块此前用自建台账 req_project.id（前端在无台账时还会回退写 pm_project.id），
--       两种口径混用导致"该存量需求未关联项目"等问题。
-- 处理：按 project_code 把需求数据里的 req_project.id 映射为 pm_project.id；
--       已是 pm_project.id 的行保持不变。幂等，可重复执行。
-- 说明：用户确认当前均为测试数据，不做字段级兼容，仅做口径归一。
-- =============================================================================

UPDATE req_difference d
JOIN req_project ledger ON ledger.id = d.project_id AND ledger.tenant_id = d.tenant_id AND ledger.deleted = 0
JOIN pm_project platform ON platform.project_code COLLATE utf8mb4_unicode_ci = ledger.project_code COLLATE utf8mb4_unicode_ci
    AND platform.tenant_id = ledger.tenant_id AND platform.deleted = 0
SET d.project_id = platform.id
WHERE d.tenant_id = 1 AND d.deleted = 0 AND d.project_id <> platform.id;

UPDATE req_legacy_requirement r
JOIN req_project ledger ON ledger.id = r.project_id AND ledger.tenant_id = r.tenant_id AND ledger.deleted = 0
JOIN pm_project platform ON platform.project_code COLLATE utf8mb4_unicode_ci = ledger.project_code COLLATE utf8mb4_unicode_ci
    AND platform.tenant_id = ledger.tenant_id AND platform.deleted = 0
SET r.project_id = platform.id
WHERE r.tenant_id = 1 AND r.deleted = 0 AND r.project_id <> platform.id;

UPDATE req_baseline b
JOIN req_project ledger ON ledger.id = b.project_id AND ledger.tenant_id = b.tenant_id AND ledger.deleted = 0
JOIN pm_project platform ON platform.project_code COLLATE utf8mb4_unicode_ci = ledger.project_code COLLATE utf8mb4_unicode_ci
    AND platform.tenant_id = ledger.tenant_id AND platform.deleted = 0
SET b.project_id = platform.id
WHERE b.tenant_id = 1 AND b.deleted = 0 AND b.project_id <> platform.id;

UPDATE req_import_batch i
JOIN req_project ledger ON ledger.id = i.project_id AND ledger.tenant_id = i.tenant_id AND ledger.deleted = 0
JOIN pm_project platform ON platform.project_code COLLATE utf8mb4_unicode_ci = ledger.project_code COLLATE utf8mb4_unicode_ci
    AND platform.tenant_id = ledger.tenant_id AND platform.deleted = 0
SET i.project_id = platform.id
WHERE i.tenant_id = 1 AND i.deleted = 0 AND i.project_id IS NOT NULL AND i.project_id <> platform.id;
