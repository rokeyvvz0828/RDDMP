-- =============================================================================
-- REQ-20260919-077/078：让需求相关种子流程可以在本地被"发布（部署）"
-- 背景：需求流程定义由历史迁移直接以 PUBLISHED 状态插入，从未部署到 Flowable
--       （deployment_id 为空），业务启动流程时报"流程定义未发布、未部署或不存在"。
--       本地启动器 LocalSeededWorkflowPublisher 只发布 DRAFT 状态的 PLATFORM 流程，
--       因此这里把两条需求流程回退为 DRAFT，由启动器在启动时真正发布并写回 deployment_id；
--       对应的编码已加入 application-local.yml 的 definition-codes 默认列表。
-- 幂等：仅处理未部署的定义，重复执行无副作用。
-- =============================================================================

UPDATE wf_definition
SET status = 'DRAFT'
WHERE tenant_id = 1 AND deleted = 0
  AND scope_type = 'PLATFORM'
  AND code IN ('requirement.diff.review', 'requirement.legacy.deliverable.review')
  AND deployment_id IS NULL;
