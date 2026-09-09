# 开发任务模块边界

## 远端基线移植补充

REQ-20260909-072 经用户确认以远端 dev-ivanh 为基线。远端物理系统已有项目归属，公开系统查询必须显式携带已授权 projectId，查询、计数、创建与详情均同时限定租户和项目；管理员也不能越过项目边界。仅适配本次新增公开查询及直接消费者，原参与人员能力和既有迁移不改变。

日期：2026-09-08。来源：已批准设计、实施计划及本次开发授权。治理例外只表示继续开发，不表示 CODEOWNERS 已审批或可以合并发布。

## 决策

- 新建 `business/development`，只依赖 common、infrastructure、security、system、attachment。不依赖 requirement 或 architecture。
- requirement 和 architecture 在各自 `integration` 包提供最小只读投影，数据库访问留在数据所属模块；boot 通过公开契约转换并装配。
- 不暴露新的来源全量 HTTP API，不扩展既有需求、架构页面权限，不改 shared 或平台认证实现。
- `req_legacy_requirement.project_id` 是 `req_project.id`，不能与平台 `pm_project.id` 比较。boot 先用 `ProjectAccessService` 校验平台 `projectRef`，需求模块再按同租户同项目编码解析自身项目；缺失或歧义失败关闭，禁止按名称或列表第一项兜底。
- 内部 `requireCurrent` 明确接收 `projectRef`，避免只按需求 id 读取时漏掉项目授权；返回开发域的 `projectId` 由 boot 转换为已校验的平台主键。来源摘要仍保留需求域身份。
- 人员、参数、审计、附件采用既有公开能力；系统/负责人/指定人员授权属于后续 development 消费者，不用项目访问替代实体授权。

## 证据与影响

项目主键关系见 `V129__legacy_project_association.sql`、`RequirementsView.vue` 的 `resolveTargetProject` 和 `JdbcProjectAccessService`。本次只新增只读生产者及装配，不修改历史表、迁移、公开消费者或原有页面。

## 回退

消费者尚未接入时可以回退新增装配与模块；消费者接入后先关闭消费者入口，保留已公开契约兼容。数据库和原模块行为不受 T1 影响。
