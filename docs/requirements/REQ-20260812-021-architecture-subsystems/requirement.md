---
id: REQ-20260812-021
status: ready
owner: rokeyvvz0828
module: business/architecture
closure_status: accepted
closed_at: 2026-08-15T22:13:16+08:00
---

# 物理子系统与逻辑子系统基础信息管理

## 1. 背景与目标

旧系统分别维护逻辑子系统与物理子系统基本信息，并以“一个逻辑子系统包含多个物理子系统”的方式表达关系。当前 RDDMP 尚无对应领域模型、专用接口和页面。

本需求新增独立 `business/architecture` 业务模块，形成“建逻辑子系统—建物理子系统并关联—查询详情—受控维护—审计留痕”的最小闭环。逻辑/物理子系统领域规则、接口、表和前端页面全部归业务模块；平台只提供组织、用户、参数和操作审计等通用能力。V1 采用固定强类型表单，不接入已下线的业务表单元数据能力。

## 2. 用户、权限与数据范围

- 架构信息查看者：查看当前租户内相应资源的列表和详情。
- 架构信息维护者：在查看权限基础上，按独立动作权限创建、编辑或删除记录。
- 系统管理员：获得新菜单和八项动作权限的初始化授权，并维护组织、用户和参数基础数据。

服务端必须分别校验：

- `architecture:logical:list`
- `architecture:logical:list:create`
- `architecture:logical:list:update`
- `architecture:logical:list:delete`
- `architecture:physical:list`
- `architecture:physical:list:create`
- `architecture:physical:list:update`
- `architecture:physical:list:delete`

V1 数据范围为“具有相应权限的用户可访问当前租户全部子系统记录”，不按事业群或负责团队做二次隔离。前端按钮显隐不能替代服务端认证和 RBAC。

## 3. 菜单、页面与操作

- 新增一级菜单“架构管理”。
- 新增“逻辑子系统”和“物理子系统”两个独立页面。
- 菜单固定为：ID 600 的 `ArchitectureRoot` / `/architecture`；ID 601 的 `ArchitectureLogicalSubsystems` / `/architecture/logical-subsystems`；ID 602 的 `ArchitecturePhysicalSubsystems` / `/architecture/physical-subsystems`。
- V37 向超级管理员写入父目录、两个子菜单的 `sys_role_menu` 可见授权，以及八项 `sys_role_permission` 动作授权。
- `componentPath` 仅作为菜单元数据；页面由静态 router 装配。
- 两个页面均支持分页列表、筛选、详情、新增、编辑和软删除。
- 逻辑子系统按编号、简称、名称和事业群筛选。
- 物理子系统按编号、简称、名称、所属事业群、负责团队和所属逻辑子系统筛选。
- V1 不提供系统启用、停用、状态筛选或状态变更接口。

## 4. 字段规则

### 4.1 逻辑子系统

| 字段 | 必填 | 来源与规则 |
| --- | --- | --- |
| 系统编号 `code` | 是 | 手工录入；服务端转大写；2—32 位；仅允许字母、数字、连字符和下划线；租户内永久唯一 |
| 系统简称 `shortName` | 是 | 2—100 字符 |
| 系统名称 `name` | 是 | 2—200 字符；租户内永久唯一 |
| 事业群 `businessOrgId` | 是 | 当前租户有效组织，保存组织 ID |
| 部署平台 `deploymentPlatformCode` | 否 | `ARCH_DEPLOYMENT_PLATFORM` 受控参数代码 |
| 系统类型 `systemTypeCode` | 否 | `ARCH_SYSTEM_TYPE` 受控参数代码 |
| 系统归属 `systemOwnershipCode` | 否 | `ARCH_SYSTEM_OWNERSHIP` 受控参数代码 |
| 联系人 `contactUserId` | 是 | 当前租户有效用户，保存用户 ID |
| 联系电话 | 否 | 只读展示用户当前电话，不在子系统表重复存储 |
| 系统描述 `description` | 否 | 最长 2000 字符 |
| 备注 `remark` | 否 | 最长 1000 字符 |
| 创建/更新信息 | 自动 | 从认证上下文和数据库时间生成 |

### 4.2 物理子系统

| 字段 | 必填 | 来源与规则 |
| --- | --- | --- |
| 系统编号 `code` | 是 | 与逻辑子系统编号采用相同格式和永久唯一规则 |
| 系统简称 `shortName` | 是 | 2—100 字符 |
| 系统名称 `name` | 是 | 2—200 字符；租户内永久唯一 |
| 所属逻辑子系统 `logicalSubsystemId` | 是 | 当前租户未删除逻辑子系统，保存 ID |
| 所属事业群 `businessGroupName` | 否 | 简单文本；去除首尾空白；空白归一化为 `null`；最长 100 字符 |
| 负责团队 `responsibleTeamOrgId` | 是 | 当前租户有效组织，保存组织 ID |
| 负责团队名称快照 `responsibleTeamNameSnapshot` | 自动 | 保存时由服务端从组织读取，客户端不可提交；用于组织失效后的历史展示 |
| 系统运行时间 `runtimeCode` | 否 | `ARCH_RUNTIME` 受控参数代码 |
| 系统级别 `systemLevelCode` | 否 | `ARCH_SYSTEM_LEVEL` 受控参数代码 |
| 开发平台框架 `developmentFrameworkCode` | 否 | `ARCH_DEVELOPMENT_FRAMEWORK` 受控参数代码 |
| 系统负责人 `ownerUserId` | 否 | 当前租户有效用户，保存用户 ID |
| 联系人 `contactUserId` | 否 | 当前租户有效用户，保存用户 ID |
| 联系电话 | 否 | 有联系人时只读展示其当前电话，不在子系统表重复存储 |
| 系统描述 `description` | 否 | 最长 2000 字符 |
| 备注 `remark` | 否 | 最长 1000 字符 |
| 创建/更新信息 | 自动 | 从认证上下文和数据库时间生成 |

负责团队在创建和每次编辑时都必须仍为当前租户有效组织。组织后续改名时页面优先展示当前名称；组织被停用或删除时，历史记录仍使用快照可读并明确标记“负责团队已失效”，下一次编辑必须重新选择有效组织后才能保存。

## 5. 关联、唯一性、租户与删除

- 一个逻辑子系统可关联多个物理子系统；一个物理子系统只能关联一个逻辑子系统。
- 逻辑子系统仍被未删除物理子系统引用时，删除返回 409，不级联删除。
- 新增或修改物理关联先做当前租户活动逻辑记录初检；进入事务后，物理写入与逻辑删除统一锁定目标逻辑记录并保持一致锁顺序。
- 初检时不存在、跨租户或已删除的逻辑引用返回 400；初检有效但等待父锁后发现被并发软删除返回 409。
- 两类记录均软删除；编号和名称的租户内唯一键不包含 `deleted`，删除后不得复用。
- 所有查询、唯一性、关联、更新和删除显式限定认证用户的 `tenantId`。
- 请求 DTO、查询参数和页面不声明或展示 `tenant_id`；客户端附带的同名额外字段不能影响持久化租户。
- 新业务表 `tenant_id` 非空且没有数据库默认值；认证租户缺失返回 401，不以常量 1 兜底。
- 仅 `local` profile 且显式开启 Mock 的受信任初始化通道可读取数据集中的显式正数 `tenant_id`；必须先校验同租户根组织以及组织、用户、逻辑子系统引用，禁止缺省租户和生产启用。

## 6. 平台边界

- 架构领域代码、DTO、HTTP API、表和前端页面只存在于 `business/architecture`。
- 组织树复用已公开的 `com.ccb.system.org.OrganizationService`；架构模块只投影当前租户有效组织所需的最小字段。
- `platform/system` 新增无架构语义的 `SystemReferenceQuery`，提供当前租户用户安全查询、用户实时电话投影和白名单参数查询。
- `platform/system` 新增无架构语义的 `SystemOperationAudit`，将架构写操作写入现有 `sys_operation_log`。
- 权限由架构控制器使用 Spring Security `@PreAuthorize` 校验，不新增平台权限守卫。
- 不新增 `PublishedFormSchemaQuery`，不访问或写入 `biz_form_*`，不提供 `/api/architecture/form-schemas/**`。
- 架构模块不得导入 `com.ccb.system.internal.*`，不得直接写组织、用户、参数、权限或操作日志表。
- 根 POM、Boot POM、`governance/modules.yaml`、`MODULES.md` 和 CODEOWNERS 登记新模块及公开包。

## 7. HTTP 契约

资源根：

- `/api/architecture/logical-subsystems`
- `/api/architecture/physical-subsystems`
- `/api/architecture/options/{resource}`

两个资源提供分页查询、详情、新增、编辑和软删除。分页响应使用现有 `ApiResponse<PageResult<T>>`；`PageResult` 固定为 `{records,total,page,size}`，`page` 从 1 开始，`size` 默认 20、最大 100。

选项资源上下文只接受 `logical-subsystem` 或 `physical-subsystem`，分别校验对应 list 权限，不接受客户端权限码，也不以两项权限 OR 放行：

- `GET /organizations`：`PageResult<OrganizationOption>`，记录固定为 `{id,name,parentId,pathLabel}`。
- `GET /users`：`PageResult<UserOption>`，记录固定为 `{id,displayName,username,phone}`，`phone` 可为 `null`。
- `GET /parameters/{categoryCode}`：`ParameterOption[]`，记录固定为 `{code,label}`；逻辑和物理上下文分别限制到自己的三类参数。
- `GET /logical-subsystems`：仅物理上下文可用，返回 `{id,code,name}` 的分页选项。

响应使用 camelCase，选项不返回 `tenantId`、密码散列、头像对象键或其他管理字段。详情和列表可返回显示标签、`responsibleTeamValid` 和联系人实时电话；新增/编辑请求不得包含电话、团队名称快照、租户、状态、删除或审计字段。

模块内 404 Advice 只处理架构资源不存在/已删除和未知选项资源；400、401、403、409 继续使用现有全局语义，不修改 shared/boot 全局异常契约。

## 8. 初始化、Mock 与迁移顺序

- 2026-08-14 再次刷新 `origin/main` 后最高迁移仍为 V34，所有远端分支也没有 V35—V40。迁移版本按实际合入顺序分配，不能由尚未实施的需求文档长期占位。
- `V35__extend_operation_log_trace.sql`：向 `sys_operation_log` 追加可空 `trace_id` 和索引，保持既有写入兼容。
- `V36__create_architecture_subsystems.sql`：创建两个 `arch_` 表及唯一键、租户安全父子外键和查询索引。
- `V37__seed_architecture_subsystem_catalog.sql`：创建菜单、八项权限、超级管理员授权和六类参数目录/基础选项；不创建表单元数据。
- 原 REQ-20260814-022 的未落地迁移顺延为 V38/V39，REQ-20260814-021 的未落地迁移顺延为 V40；相应需求与 scope 文档同步更新。
- `mock/mock-data.json` 提供虚构逻辑/物理记录和有效关联；所有架构行显式填写租户，重复初始化幂等。
- 新增 Mock 数据不得使用旧系统截图中的真实名称、人员、手机号或业务记录。

## 9. 固定表单与前端体验

- 两个页面使用模块内固定 TypeScript 类型、固定表单分区和服务端校验，不依赖动态 schema、发布快照或运行时降级。
- 优先复用交付示范中心和 `web/src/components/ui` 的页面头、工具栏、数据表、表单抽屉、空状态、详情抽屉和语义主题变量。
- 桌面端使用服务端分页表格；小于 760px 切换为业务卡片，页面本身不得横向滚动。
- 手机验收覆盖 375×812、390×844、430×932，另覆盖至少 1280×800 桌面视口及明暗主题。
- 查看为主操作；移动端编辑、删除进入“更多”。
- 用户选择器防抖并服务端分页；参数按类别加载；组织选择显示层级路径。
- 负责团队失效时详情可读、表单明确提示并阻止原值直接保存。
- 提交中禁用重复提交；脏表单关闭前确认；409 保留输入；保存成功保留筛选和页码后刷新。
- 完整覆盖加载、初始空、筛选空、失败重试、无权限、只读、详情加载、提交中和删除中状态。

## 10. 审计

- 创建、编辑、删除成功时，在业务事务内通过 `SystemOperationAudit` 写入现有 `sys_operation_log`。
- 已认证失败尝试在业务事务回滚后以独立事务尽力记录；审计失败不得覆盖原业务错误。
- 审计记录包含租户、操作者、稳定动作码、对象路径、成功/失败、受限错误摘要和 trace ID，不保存表单正文、电话或其他敏感值。
- `trace_id` 为可空兼容列，既有日志写入方不需要同步修改。

## 11. 明确不实施

- 系统启用、停用、状态筛选和状态变更接口。
- 动态表单元数据、schema API、发布快照或扩展字段值。
- 编号自动生成、智能推导、智能补全和推荐。
- 新增申请、审批流程、批量导入导出和外部系统同步。
- 构件、业务领域、应用/产品、安全节点、文件传输节点等未准备主数据关系。
- 事业群或负责团队级数据隔离、跨租户共享和级联删除。

## 12. 验收标准

1. 管理员可从“架构管理”进入两个页面；查看和写动作均按八项权限服务端校验。
2. 两类资源完成分页、筛选、详情、新增、编辑、刷新重读和软删除闭环。
3. 编号大写归一化；非法、重复编号和名称被拒绝，软删除后仍不能复用。
4. 物理记录始终关联当前租户有效逻辑记录；并发创建与逻辑删除符合 400/409 锁定语义且无悬挂活动引用。
5. 物理所属事业群为可空文本；负责团队必选并保存服务端名称快照，组织失效后历史可读、下一次编辑必须重选。
6. 系统负责人和联系人均可空；有联系人时电话取平台当前值且请求、数据库不重复存储。
7. 逻辑分类和物理分类继续从六类白名单参数读取；组织、用户和选项响应不泄露管理或敏感字段。
8. 创建、编辑、删除成功和已认证失败尝试可在现有操作日志按 trace ID 复核。
9. 新库首次启动无需手工配置菜单、权限和参数；重复启动和 Mock 同步不产生重复数据。
10. 页面是固定强类型表单，代码、网络和数据库种子没有新增 `biz_form_*`、form-schema 或 `PublishedFormSchemaQuery` 依赖。
11. 桌面、三个手机视口和明暗主题无页面级横向滚动，页面状态明确，控制台无新增错误。
12. 页面、DTO、API 和权限中没有系统启停用入口或行为，`tenant_id` 不出现在页面和请求契约中。
13. `ccb-architecture`、前端模块、Boot 装配和治理登记一致；platform/system 不包含架构领域类型或规则。
14. 编码前 `origin/main` 必须仍以 V34 为最高迁移，V35—V37 和稳定 ID 均未被其他提交占用；出现冲突时重新分配而不是覆盖。

## 13. 验证与回退

- 后端：模块 CRUD、分页筛选、归一化、永久唯一、租户隔离、引用、负责团队失效、实时电话、权限、并发与审计测试。
- 平台：用户/参数安全查询与审计契约测试，以及现有 system 管理回归。
- 数据库：MySQL 空库和从 V34 已有库增量迁移到 V37、约束、种子幂等和现有日志兼容检查。
- Mock：profile/开关、显式租户、引用完整性、失败回滚和重复幂等测试。
- 前端：`npm --prefix web run build`，以及真实浏览器桌面/移动/权限/异常/组织失效 UAT。
- 治理：Maven reactor、Boot 装配、scope、governance、module-boundaries、Flyway 和 `git diff --check`。
- 应用回退按前端、业务模块、平台契约、装配和治理的依赖逆序执行；迁移表和历史审计保留。关闭入口只能使用后续补偿迁移隐藏菜单和撤销授权，不手工删除生产数据。

## 14. 当前准入状态

用户于 2026-08-14 批准设计修订 3、实施计划修订 2 及其最小 platform/system 公共能力变更，并确认审计采用 `SystemOperationAudit` 平台契约；随后明确要求修复开发入口与迁移前置阻塞。迁移改为占用主干实际连续的 V35—V37，未落地需求的版本同步顺延；开发入口兼容修复和迁移/ID 冲突重扫通过后，T0 方可关闭。

## 15. 关闭记录

- 2026-08-15：用户确认验收通过并要求关闭需求。
- 工程控制阶段为 `converged`，收敛门禁结果为 `pass`，未关闭反馈为 0。
- 为兼容仓库 Codex scope 门禁，头部 `status` 与 scope 中的 requirement status 继续保留为 `ready`；实际关闭状态以 `closure_status: accepted`、本节和最终 handoff 为准。
