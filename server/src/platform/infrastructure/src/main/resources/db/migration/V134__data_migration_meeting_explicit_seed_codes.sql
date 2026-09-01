-- V104: 演示显式提供 meeting_code 的 seed 范式（REQ-20260820-031 T26）
-- 背景：V103 通过 UPDATE 回填为存量 dm_meeting 行提供了 MEET-{meeting_id}；但应用侧推荐
--       新建会议时显式提供业务编号（例如 MEET-DEMO-KICKOFF），避免依赖兜底策略。
-- 目的：在 dev/评估环境中演示"显式提供 meeting_code"的正确 INSERT 模式；三行使用固定主键
--       30001-30003（远离 mock seed 20001-20010），配合 uk_dm_meeting_active_code 保证活动域唯一。
-- 幂等：INSERT IGNORE；若目标项目/租户不存在或编号冲突，静默跳过，不影响迁移成功。

INSERT IGNORE INTO dm_meeting
    (meeting_id, meeting_code, tenant_id, project_id, granularity, meeting_source, meeting_title,
     meeting_content, meeting_conclusion, keywords, deleted, created_by, created_at)
SELECT seed.meeting_id,
       seed.meeting_code,
       COALESCE(ctx.tenant_id, 1),
       COALESCE(ctx.project_id, 1),
       seed.granularity,
       'MEETING_MINUTES',
       seed.meeting_title,
       seed.meeting_content,
       seed.meeting_conclusion,
       JSON_ARRAY('demo', 'v104', 'explicit-code'),
       0,
       COALESCE(ctx.created_by, 1),
       CURRENT_TIMESTAMP(6)
FROM (
    SELECT 1 AS k
) dummy
LEFT JOIN (
    SELECT tenant_id, project_id, created_by
    FROM dm_meeting
    ORDER BY meeting_id ASC
    LIMIT 1
) ctx ON 1 = 1
CROSS JOIN (
    SELECT 30001 AS meeting_id,
           'MEET-DEMO-KICKOFF' AS meeting_code,
           'PROJECT' AS granularity,
           'V104 演示：显式编号 - 项目启动会' AS meeting_title,
           '本行由 V104 演示显式提供 meeting_code 的正确用法；应用层新建会议时应显式指定编号或依赖 MeetingService.create 自动生成 MEET-{id} 兜底。' AS meeting_content,
           '推荐范式：显式业务编号优先，兜底自动生成' AS meeting_conclusion
    UNION ALL
    SELECT 30002, 'MEET-DEMO-REVIEW', 'COMPONENT',
           'V104 演示：显式编号 - 组件评审会',
           '与 REPORT.doc_code / ISSUE.issue_code 同构：MEETING 通过 meeting_code 参与统一回收站信封 asset_code 的展示与排序。',
           '业务编号列已内建，跨模块信封统一'
    UNION ALL
    SELECT 30003, 'MEET-DEMO-RETRO', 'TABLE',
           'V104 演示：显式编号 - 表复盘会',
           '统一回收站列表按业务编号升序；分页由 ContentRecycleBinService.list(page,size) 提供，跨来源合并后按 asset_code ASC 全局排序。',
           '统一排序：asset_code ASC 跨类型一致'
) seed;
