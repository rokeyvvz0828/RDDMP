---
id: REQ-20260824-052
status: "ready"
closure_status: accepted
closed_at: "2026-08-25T15:43:34+08:00"
owner: rokeyvvz0828
module: business/architecture
source_issue: 03-environment-and-resource-request
---

# 具体环境与资源申请

## 业务目标

环境类型由系统字典统一维护，环境管理人员维护具体环境；申请人可以在一个已发布物理子系统和一个
启用具体环境下发起资源申请，选择资源申请联系人，并按现有资源登记表口径连续填写该物理子系统下
多个启用部署单元的资源容量、技术栈、附加需求和备注。物理子系统信息、系统等级、灾备模式和部署单元信息
均从主数据带出并形成提交快照，通过平台工作流完成提交、退回、批准、拒绝和取消。

本需求把 Wayfinder 03 票据转换为仓库正式实施批次。外部 `temp/spec.md`、
`temp/CONTEXT.md` 与 `temp/implementation/issues/03-environment-and-resource-request.md`
只作为需求来源和术语证据；用户于 2026-08-24 直接下达“实现03：具体环境与资源申请”
作为本批次开工授权。实际机器、IP、环境部署实例和实际资源分配属于后续 04，不在本批次
中实现或伪造。

## 范围

### 本次实施

- 环境类型：不在架构模块内自建维护入口；由系统字典 `ARCH_ENVIRONMENT_TYPE` 维护，具体环境只引用启用字典项的 `config_key`。
- 具体环境：新增、修改、停用、重新启用和无业务引用删除；停用只阻止新资源申请，不自动取消既有申请。
- 具体环境详情：明确分区展示基础信息、申请态资源汇总和实际态资源汇总；申请态由资源申请明细计算，实际态在本批次仅展示为空态并标明来源等待 04。
- 资源申请工单：固定选择一个物理子系统、一个具体环境和一个资源申请联系人，可连续添加多条部署单元登记明细，不再维护来源任务号。
- 资源登记明细：每条明细选择部署单元；部署物理子系统编号、物理子系统简称、物理子系统名称、系统等级、所属事业群、部署平台和灾备模式从物理子系统只读带出，部署单元名称、关联部署单元名称、部署单元简述和部署单元类型从部署单元模型只读带出。资源申请不维护农信业务连续性等级、项目组收集系统等级，也不在部署单元明细层维护所属事业群、部署平台和灾备模式。`DB` 仅填写数据库存储需求、数据库和数据库版本；`AP/WB/PL` 填写除 `DB` 专属字段外的服务器类型、文件/CBS/本地盘容量、网络分区、CPU、内存、AP/WEB 组数、生产节点数、边车资源、JDK、中间件、操作系统、NFT/FSever/jobexecutor 附加需求和备注；同一部署单元允许重复明细以表达不同规格。
- 工作流：资源申请支持草稿、提交、审批中、退回、批准、拒绝、取消和历史追踪；提交、审批中取消与生命周期事件复用平台工作流公开契约。
- 服务端校验：失效物理子系统、停用/作废部署单元、停用环境不得用于新申请；前端级联只改善体验，不替代后端校验。
- 前端：新增“具体环境”和“资源申请”入口，桌面端表格、移动端卡片、表单弹层/页面、加载/空/失败/无权限/提交中状态。

### 本次不实施

- 资源下发办理结果登记、机器标识、IP、环境部署实例、实际资源分配与差异原因；这些属于 04。
- 搭建计划、环节、任务、检查项，以及从搭建任务发起资源工单的真实任务关联。
- 环境资源总账或人工维护实际节点数。
- 网络访问申请、CLB/DNS/证书、加密机入池与架构决策。
- 动态表单元数据、多租户业务概念、外部资源自动下发或任何外部运维动作。

## 业务规则

- 环境类型编码来自系统字典 `ARCH_ENVIRONMENT_TYPE` 的 `config_key`，启用字典项可用于新建具体环境；停用或删除字典项后不能再用于新建具体环境，历史具体环境保留原 `typeCode`。
- 具体环境编码和名称在租户内唯一；已有资源申请引用的具体环境不能删除，只能停用。
- 资源申请创建和更新时必须固定一个物理子系统和一个具体环境；明细部署单元必须属于该物理子系统。
- 物理子系统必须为 `ACTIVE` 且未删除；部署单元必须为 `ACTIVE`；具体环境必须为 `ACTIVE`。
- 服务器类型来自系统字典 `ARCH_SERVER_TYPE`，内置 `容器`、`物理机`、`虚拟机`，默认 `容器`；灾备模式来自系统字典 `ARCH_DISASTER_RECOVERY_MODE`，内置 `主备`、`双活`、`冷备`、`无灾备`，并由物理子系统维护后带出。
- JDK、中间件和产品化操作系统分别来自系统字典 `ARCH_JDK_VERSION`、`ARCH_MIDDLEWARE`、`ARCH_OPERATING_SYSTEM`，资源申请只选择启用字典项，不自由录入。
- CPU、内存、数据库存储、文件存储、CBS、本地盘和边车容量为非负整数；AP/WEB 组数和生产环境节点数为非负整数，允许数据库存储类明细节点数为 0。`DB` 明细至少填写数据库存储需求、数据库或数据库版本之一；`AP/WB/PL` 明细至少填写一项非 `DB` 资源容量、组数、节点数或 NFT/FSever/jobexecutor 附加需求。
- 同一部署单元需要不同规格时直接新增或复制明细，服务端允许同一工单内重复部署单元。
- 申请态资源汇总按当前资源申请明细计算：CPU/内存按单节点配额乘以生产环境节点数后叠加总边车资源，存储按数据库存储、文件存储、CBS 和本地盘合计；取消/拒绝工单不计入当前申请态汇总。
- 物理子系统编号、简称、名称、系统等级、所属事业群、部署平台和灾备模式来自所选物理子系统；部署单元名称、关联部署单元名称、简述和类型来自部署单元主数据，申请级快照保留提交时带出的物理子系统信息，明细快照只保留部署单元信息和资源规格。
- 工作流批准只表示资源申请已审批通过，等待后续 04 登记实际下发；批准不会创建环境部署实例或实际资源分配。
- 写操作记录操作审计；业务历史保留事件、状态前后值、操作者、轮次和快照。

## API 与数据

- API：
  - `GET /api/architecture/environment-types`（只读字典选项，维护入口在系统字典）
  - `GET/POST /api/architecture/environments`
  - `GET /api/architecture/environments/{id}`
  - `PUT /api/architecture/environments/{id}`
  - `POST /api/architecture/environments/{id}/deactivate|reactivate|delete`
  - `GET/POST /api/architecture/resource-requests`
  - `GET /api/architecture/resource-requests/{id}`
  - `PUT /api/architecture/resource-requests/{id}`
  - `POST /api/architecture/resource-requests/{id}/submit|cancel`
- 数据 Owner：`business/architecture` 拥有具体环境和资源申请数据；环境类型由 `platform/system` 字典拥有，架构模块通过 `SystemReferenceQuery` 读取。
- 数据库迁移：追加 `V91__create_architecture_environment_resource_requests.sql` 与
  `V92__seed_architecture_environment_resource_requests.sql`；用户于 2026-08-25 确认环境类型改由系统字典维护后追加
  `V93__move_environment_type_to_system_dictionary.sql`；用户于 2026-08-25 要求按现有登记表重写资源申请后追加
  `V94__expand_resource_request_registration_items.sql` 扩展资源申请明细；
  `V95__refine_resource_request_registration_ownership.sql` 将服务器类型、灾备模式迁入字典，补齐物理子系统和部署单元模型字段，资源申请改为联系人选择并移除来源任务号和明细确认人；
  `V96__refine_resource_request_resource_catalogs.sql` 将技术栈字段迁入系统字典、资源容量字段改为整数，并移除资源申请明细层旧等级与物理字段，不修改 V1-V95。
- 权限：
  - `architecture:environment:view/manage`
  - `architecture:resource-request:view/apply/manage`
  - 资源申请 apply 用户只能查看和操作本人申请；manage 用户可查看和办理全部申请。

## 验收标准

1. 管理人员可以在系统字典维护环境类型，并在具体环境页面引用启用字典项；被资源申请引用的具体环境删除失败，停用后不能用于新申请。
2. 具体环境详情同时展示基础信息、申请态资源汇总和实际态资源分区；实际态不由本批次人工录入。
3. 申请人创建资源申请时必须选择一个物理子系统和具体环境，明细只能选择该物理子系统下启用部署单元。
4. 每条资源登记明细按部署单元类型区分字段：`DB` 只填写数据库存储需求、数据库、数据库版本；`AP/WB/PL` 填写非 `DB` 资源字段；附加需求默认折叠；同一部署单元重复明细可保存。
5. 资源申请支持草稿、更新、提交工作流、退回、批准、拒绝、取消和历史追踪；批准不会创建实际实例。
6. 失效物理子系统、停用部署单元、停用环境在服务端均被拒绝；权限不足返回 403。
7. 架构模块聚焦测试、Flyway 迁移检查、前端构建、范围检查和真实浏览器桌面/移动旅程按证据等级如实记录。

## 测试与发布

- 必须执行：
  - `mvn -pl :ccb-architecture -am -Dtest=EnvironmentResourceServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - `mvn -pl :ccb-architecture -am test`
  - `npm --prefix web run build`
  - `node scripts/check-flyway-migrations.mjs`
  - `node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260824-052-environment-resource-request/codex-task-scope.yaml --base HEAD --head HEAD --working-tree`
  - `git diff --check`
- 上线验证：空库和既有库迁移到 V96；在系统字典维护 `ARCH_ENVIRONMENT_TYPE`、`ARCH_SERVER_TYPE`、`ARCH_DISASTER_RECOVERY_MODE`、`ARCH_JDK_VERSION`、`ARCH_MIDDLEWARE`、`ARCH_OPERATING_SYSTEM` 后，以环境管理/资源申请/资源办理角色完成具体环境维护、登记表明细资源申请创建、提交、退回、批准、拒绝、取消；普通查看角色验证 403；桌面和移动视口检查页面无横向溢出。
- 回退或补偿：应用代码按前端、服务、迁移登记逆序回退；已执行迁移保留数据，通过后续补偿迁移隐藏菜单、撤销权限或恢复兼容结构，不在生产手工删除表或数据。

## 关闭记录

- 关闭时间：2026-08-25T15:43:34+08:00。
- 用户验收授权：用户于 2026-08-25 明确要求“可以，关闭此需求并提交、合并、推送”。
- 收敛结论：具体环境、资源申请、登记表字段、字典归属、工作流提交/审批入口、系统等级带出、整数容量字段和边车占比修正已完成；本地 Flyway 已迁移到 V96，后端和前端运行可达。
- 已通过验证：`EnvironmentResourceServiceTest` 聚焦测试、`npm --prefix web run build`、`node scripts/check-flyway-migrations.mjs`、已提交范围的 `node scripts/check-codex-scope.mjs --base HEAD~1 --head HEAD`、`git diff --check`；8080 健康接口与 5173 前端入口可达。
- 已接受残余风险：完整架构模块测试受本地 Testcontainers/Docker 识别问题影响未形成通过结论；全仓治理检查失败项来自历史 `.ai-control` 旧账本；带 `--working-tree` 的范围检查受本工作区既有未跟踪 `.dsh/` 与 `req-20260812-021` 文件影响；真实浏览器桌面/移动 UAT 尚未执行。
- 集成边界：提交落在需求分支 `feat/REQ-20260824-052-environment-resource-request`，按本任务范围合并回任务分支 `dev-ivanh` 并推送；不直接推送 `main`。
