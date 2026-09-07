# REQ-066 实施准入记录

日期：2026-09-06。

## 用户授权与旧改动归档

用户已明确要求“先提交旧有改动然后进入开发”。这授权归档已确认来源的旧改动及推进本需求实施准备；不代替仓库规定的数据库/权限专项 Owner 审批。

旧改动已在 dev-ivanh 分组提交，未推送、未合并：

- d49d9c8：REQ-063 本地开发脚本开启种子流程定义发布。
- f817c1e：REQ-064 T7 跨模块物理子系统项目隔离，包含两个测试文件。
- fd2abd3：REQ-021 两个历史 T11 账本原样归档，保留 partial 结论。

本轮验证：

- node scripts/check-development-entry.mjs --require-plugin：通过。
- node --check scripts/dev.mjs：通过。
- 两个历史 JSON 解析：通过。
- git diff --check 及提交前暂存差异检查：通过，有 LF/CRLF 提示。
- mvn -pl :ccb-data-migration,:ccb-test-management -am test：沙箱内依赖访问失败，经审批在沙箱外重试成功；数据迁移 13 项、测试管理 21 项，均无失败/错误/跳过，9 模块反应堆成功。
- 未重跑旧 T11 的前端或架构测试，不提升历史结论。

## 当前准入事实

- 当前普通检出：C:/Users/missa/source/RDDMP，分支 dev-ivanh；不是隔离工作树。
- 旧改动均已提交，剩余未跟踪内容只有 REQ-066 文档和当前前缀账本。
- 现行 codex-task-scope.yaml 仍为 documentation-only，本记录不扩大可写路径。
- 正式控制状态为 control-state.json 的 baseline；不能用开发前 state.json 的 planning 当作可执行阶段。
- 尚无数据库/权限 Owner 专项审批记录，不能填写 owner_approved=true。

## 首批实现落点与待补范围

按已批准的修订2计划串行实施 T1—T6，不创建新看板页面或路由。

1. T1：PhysicalSubsystemService、物理子系统模型/请求/响应、人员关系存储、物理子系统页面与表单、API/type、参与资格测试。
2. T2：PlanGenerationService、PlanStore、相关 DTO、计划创建/任务分派交互及默认分工测试。
3. T3：PlanEngine、PlanExecutionService、PlanQueryService、PlanBlockService、PlanTimeService、PlanController 及授权测试。前后端均需取消管理员执行豁免，管理可见性与执行资格分开。
4. T4：资源申请、部署单元选项、关联工单、报告、附件策略与通知收件人的一致授权。复用平台公开接口，不更改平台实现或其他业务模块。
5. T5：PlanDetailPage 现有 dashboard 页签原位替换为当前计划四列看板，服务端过滤并返回授权范围内计数。
6. T6：真实 MySQL 迁移和越权回归、Maven 全量、前端构建及四视口/明暗主题真实浏览器验收。

上述具体文件仍须在当前模型/控制计划中逐项确认，并写入实施 scope 后才能编辑；不得凭目录通配符顺手修改共享能力。

## 数据库审批候选

本轮只读盘点最高已有迁移为 V158，拟追加：

`server/src/platform/infrastructure/src/main/resources/db/migration/V159__subsystem_participation.sql`

该路径尚未授权或创建；实施前再次检查版本冲突。拟变更仅为架构模块系统参与关系及必要任务兼容字段/索引，使用 tenant_id + project_id + subsystem_id 约束归属。负责人隐含参与；已有任务分工保留，不批量扩大系统或任务名单。无效存量资格需显式待处理并禁止执行。

不修改已发布迁移，不访问生产，不直接读取平台私有用户/项目表。数据库或权限变更上线前必须人工专项复核及全量回归。安全回退保留新增关系和审计，暂停受影响写入口，不恢复旧版宽权限。

## 开始业务代码前待确认

- 模块/数据库 Owner 对本次权限收紧、存量人员策略与上述追加迁移的审批。
- 是否建立隔离工作树；若明确不使用，则在当前目录独立需求分支实施，不直接在 dev-ivanh 开发。
- 审批后修订现行 scope，完成 baseline → modeling → planning 门禁，再进入 T1 执行。
## 当前实施状态（2026-09-06 更新）
用户已批准在当前目录独立需求分支开发，不使用 worktree。此前待授权及仅文档描述作为历史记录，不再代表当前准入状态。当前分支为 feat/REQ-20260906-066-subsystem-participation，正式控制阶段见当前前缀 control-state.json。仅 scope 精确路径可写；其余路径不得写入。

## T1实际门禁反馈（2026-09-06 17:00）
已实现参与关系第一切片，15项聚焦测试和前端构建通过，但当前任务仍为partial。
实际脚本将V159归为平台变更，专项Issue与Owner审批不能延期；当前没有这两项证据，禁止填报通过。
Flyway格式检查另外被仓库原有V84_1命名阻断，不修改已发布迁移绕过检查。
本轮实现保留待审，尚未提交业务代码、推送、合并或部署。后续T2-T6不推进。

## 2026-09-07 Owner审批与Issue豁免
用户明确确认：“已获得Owner审批，Issue已获豁免，直接开始执行变更”。据此记录Owner已审批、Issue豁免，不虚构Issue编号或审批人身份。此授权取代上一节缺少审批导致的暂停；原业务规则、精确写范围、测试与禁止生产访问约束不变。V84_1历史命名检查失败单独披露，不修改历史迁移或治理脚本。
