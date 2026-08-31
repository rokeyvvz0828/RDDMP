-- =============================================================================
-- V97：存量演示需求补齐系统子表行（主责 + 协同）
-- 存量需求的“选择系统子表行”下拉依赖 req_legacy_system_item；
-- V71 的存量演示需求未生成系统子表行，导致下拉无数据。
-- 本迁移按业务组为每个存量需求补一行主责 + 一行协同，幂等（已存在行则跳过）。
-- 仅追加，不修改历史迁移。
-- =============================================================================

-- 主责行
INSERT INTO req_legacy_system_item
    (id, tenant_id, requirement_id, system_role, system_code, system_name, owner_user_id, owner_user_name, remark, created_by, deleted)
SELECT 930000000020000 + lr.id * 10 + 1, lr.tenant_id, lr.id, '主责',
       CASE lr.business_group
           WHEN '零售一组' THEN 'S-RETAIL-LOAN'
           WHEN '零售二组' THEN 'S-CREDIT-CARD'
           WHEN '对公一组' THEN 'S-CORP-LOAN'
           WHEN '渠道一组' THEN CASE lr.id WHEN 5018 THEN 'S-CHNL-NETBANK' ELSE 'S-CHNL-MOBILE' END
           ELSE 'S-RETAIL-LOAN'
       END AS system_code,
       CASE lr.business_group
           WHEN '零售一组' THEN '零售贷款系统'
           WHEN '零售二组' THEN '信用卡系统'
           WHEN '对公一组' THEN '对公贷款系统'
           WHEN '渠道一组' THEN CASE lr.id WHEN 5018 THEN '渠道网银系统' ELSE '手机银行系统' END
           ELSE '零售贷款系统'
       END AS system_name,
       lr.created_by, u.display_name, '演示系统子表行（主责）', 1, 0
FROM req_legacy_requirement lr
LEFT JOIN sys_user u ON u.id = lr.created_by AND u.tenant_id = lr.tenant_id
WHERE lr.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM req_legacy_system_item si
      WHERE si.requirement_id = lr.id AND si.tenant_id = lr.tenant_id AND si.deleted = 0);

-- 协同行
INSERT INTO req_legacy_system_item
    (id, tenant_id, requirement_id, system_role, system_code, system_name, owner_user_id, owner_user_name, remark, created_by, deleted)
SELECT 930000000020000 + lr.id * 10 + 2, lr.tenant_id, lr.id, '协同',
       CASE lr.business_group
           WHEN '零售一组' THEN 'S-DATA-MID'
           WHEN '零售二组' THEN 'S-CHNL-MOBILE'
           WHEN '对公一组' THEN 'S-CORP-CORE'
           WHEN '渠道一组' THEN 'S-RETAIL-PAY'
           ELSE 'S-DATA-MID'
       END AS system_code,
       CASE lr.business_group
           WHEN '零售一组' THEN '数据中台'
           WHEN '零售二组' THEN '手机银行系统'
           WHEN '对公一组' THEN '对公核心系统'
           WHEN '渠道一组' THEN '零售支付系统'
           ELSE '数据中台'
       END AS system_name,
       NULL, NULL, '演示系统子表行（协同）', 1, 0
FROM req_legacy_requirement lr
WHERE lr.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM req_legacy_system_item si
      WHERE si.requirement_id = lr.id AND si.tenant_id = lr.tenant_id AND si.deleted = 0);
