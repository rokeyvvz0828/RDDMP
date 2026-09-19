-- =============================================================================
-- 存量需求「需求状态及备注」按 6 个阶段收敛受控取值
-- -----------------------------------------------------------------------------
-- 目标：编辑页「需求状态」只保留按当前阶段收敛后的口径，旧口径取值全部清理：
--   1 需求提出：需求提出、需求终止
--   2 需求对接：需求分析、需规编制、需规评审、需求终止
--   3 工作量评估：工作量评估、需求终止
--   4 立项：立项、需求终止
--   5 软需：软需编写、软需评审、需求终止
--   6 投产：已投产、需求终止
-- 规则：需求状态默认为空；需求已落库的存量行按"当前阶段候选的第一个"归一，
--       不在当前阶段候选内的旧值（业需修订/业需评审通过/立项中/软需编制/软需评审通过等）全部删除；
--       「需求终止」在任何阶段保留不动，空值保持为空。
-- 范围：只归一化 req_legacy_requirement.requirement_status 与列注释；
--       不改表结构、不改历史阶段日志与修改记录（保留审计原貌）、不影响接口契约。
-- 幂等：所有 UPDATE 都限定"旧取值/非本阶段取值"，可安全重跑。
-- =============================================================================

-- 1) 需求提出：旧值与跨阶段值统一为「需求提出」
UPDATE req_legacy_requirement SET requirement_status = '需求提出'
WHERE deleted = 0 AND current_stage = 'PROPOSE'
  AND requirement_status IS NOT NULL AND requirement_status <> ''
  AND requirement_status NOT IN ('需求提出', '需求终止');

-- 2) 需求对接：旧值与跨阶段值统一为「需求分析」
UPDATE req_legacy_requirement SET requirement_status = '需求分析'
WHERE deleted = 0 AND current_stage = 'DOCKING'
  AND requirement_status IS NOT NULL AND requirement_status <> ''
  AND requirement_status NOT IN ('需求分析', '需规编制', '需规评审', '需求终止');

-- 3) 工作量评估：旧值与跨阶段值统一为「工作量评估」
UPDATE req_legacy_requirement SET requirement_status = '工作量评估'
WHERE deleted = 0 AND current_stage = 'WORKLOAD'
  AND requirement_status IS NOT NULL AND requirement_status <> ''
  AND requirement_status NOT IN ('工作量评估', '需求终止');

-- 4) 立项：旧值与跨阶段值统一为「立项」
UPDATE req_legacy_requirement SET requirement_status = '立项'
WHERE deleted = 0 AND current_stage = 'PROJECT'
  AND requirement_status IS NOT NULL AND requirement_status <> ''
  AND requirement_status NOT IN ('立项', '需求终止');

-- 5) 软需：旧值与跨阶段值统一为「软需编写」
UPDATE req_legacy_requirement SET requirement_status = '软需编写'
WHERE deleted = 0 AND current_stage = 'SOFT'
  AND requirement_status IS NOT NULL AND requirement_status <> ''
  AND requirement_status NOT IN ('软需编写', '软需评审', '需求终止');

-- 6) 投产：旧值与跨阶段值统一为「已投产」
UPDATE req_legacy_requirement SET requirement_status = '已投产'
WHERE deleted = 0 AND current_stage = 'LAUNCH'
  AND requirement_status IS NOT NULL AND requirement_status <> ''
  AND requirement_status NOT IN ('已投产', '需求终止');

-- 7) 兜底：阶段为空或不在 6 个阶段内的存量行，旧取值统一为「需求提出」
UPDATE req_legacy_requirement SET requirement_status = '需求提出'
WHERE deleted = 0
  AND (current_stage IS NULL OR current_stage NOT IN ('PROPOSE', 'DOCKING', 'WORKLOAD', 'PROJECT', 'SOFT', 'LAUNCH'))
  AND requirement_status IS NOT NULL AND requirement_status <> ''
  AND requirement_status NOT IN ('需求提出', '需求分析', '需规编制', '需规评审', '工作量评估', '立项', '软需编写', '软需评审', '已投产', '需求终止');

-- 列注释同步为新口径（需求终止在任何阶段均可选）
ALTER TABLE req_legacy_requirement
    MODIFY COLUMN requirement_status VARCHAR(32) NULL
        COMMENT '需求状态：需求提出/需求分析/需规编制/需规评审/工作量评估/立项/软需编写/软需评审/已投产/需求终止（按当前阶段受控，默认为空）';
