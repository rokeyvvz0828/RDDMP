---
id: REQ-20260823-051
status: ready
owner: rokeyvvz0828
module: business/architecture
source_issue: 11-clb-dns-certificate-work-orders
---

# CLB、DNS 与证书工单

## 业务目标

为网络访问域的三类专项办理对象提供申请、跟踪与办理结果登记能力：CLB 开通/调整工单、
DNS 域名新增/变更/注销工单、SSL/外联证书申请/续期/吊销工单。平台只管理申请内容、
办理过程和办理结果，不建设 CLB 实例/监听器/后端池/健康检查、DNS 解析平台、证书私钥库
或自动生成/部署能力；实际 DNS、CLB、证书动作在线下或外部系统完成，由办理人员登记结果
与凭证。

本需求以仓库治理和本文件为编码准入基准。外部 Wayfinder 票据（
`temp/implementation/issues/11-clb-dns-certificate-work-orders.md`）与 `temp/spec.md`
（User Stories 110-113）、`temp/CONTEXT.md` 作为需求来源；用户于 2026-08-23 直接下达
“实施 11「CLB、DNS 与证书工单」”任务并确认模块落点为 `business/architecture`，作为本
批次开工授权。

## 范围

### 本次实施

- 在 `business/architecture` 模块内新增 `com.ccb.architecture.network` 子域包，提供
  CLB、DNS、证书三类网络专项工单：创建/保存草稿、提交、退回重提、取消、审批、
  办理结果登记、操作历史与查询。
- 三类工单共享一个工单引擎（状态机、工作流轮次、事件回执、历史快照），但各自持有
  独立字段契约与校验：
  - CLB：`clbName`（必填）、`purpose`（必填）、`description`（可选）；动作 `OPEN/ADJUST`。
  - DNS：`domainName`（必填）、`purpose`（必填）、`description`（可选）；动作
    `ADD/CHANGE/REMOVE`。
  - CERT：`certType`（`SSL`/`EXTERNAL` 必填）、`subjectName`（必填）、`purpose`（必填）、
    `description`（可选）；动作 `APPLY/RENEW/REVOKE`。
- 办理结果登记：`resultStatus`（`SUCCESS/FAILED`）、`resultDescription`、凭证附件；
  网络办理人员（`architecture:network-work-order:manage`）在审批中或完成后登记，
  结果随工单详情展示并进入历史。
- 附件：申请材料与办理凭证复用平台附件能力（业务类型 `architecture_network_work_order`），
  继承工单实体授权（查看/下载需可读该工单，删除仅草稿/退回且限本人或管理）；
  证书工单禁止上传私钥类文件（服务端扩展名黑名单）。本批次为
  `business/architecture` 登记 `platform/attachment` 依赖（`governance/modules.yaml`
  与 `server/src/modules/architecture/pom.xml`），仅使用其公开 `integration` 契约。
- 权限：新增三级权限 `architecture:network-work-order:view/apply/manage` 与角色
  `NETWORK_MANAGER`（网络办理人员）；`view/apply` 只见本人工单，`manage` 见租户全部；
  全部写操作服务端认证、RBAC、租户隔离、实体授权与操作审计。
- 前端：网络专项工单列表（类型页签/状态筛选/分页）、新建/编辑表单（按类型渲染固定
  强类型字段与附件上传）、详情页（快照、办理结果、业务历史、工作流审批与申请人操作），
  桌面与移动视口全状态。

### 本次不实施

- CLB 实例、监听器、后端池、健康检查等完整资源管理。
- DNS 解析管理平台或任何自动 DNS 操作。
- 证书生成、私钥保存、证书自动部署或证书生命周期管理。
- 网络分区、外部网络地址、网络访问申请/关系、访问判定（issue 09/10，后续批次）。
- 加密机入池工单（issue 12，后续批次）。
- 任务关联工单、通知集成之外的平台能力扩展；租户概念扩展、动态表单元数据、AI 建议。

## 现状与规则

- 当前入口：`business/architecture` 已实现逻辑/物理子系统主数据、变更工单（
  `com.ccb.architecture.change`）、固定审批流程（V84 预置草稿，平台发布后启用）与
  三级权限（V83 预置）。网络专项工单复用其工单+工作流模式，不重复建设流程状态机。
- 业务规则：
  - 状态机：`DRAFT → IN_REVIEW → COMPLETED/REJECTED`；`IN_REVIEW → RETURNED →
    IN_REVIEW`（新轮次）；`DRAFT/RETURNED → CANCELLED`（同步取消）；
    `IN_REVIEW → CANCELLED`（登记取消请求并等待 `TERMINATED` 事件确认）。
  - 草稿编辑仅限本人且状态为 `DRAFT/RETURNED`；提交固化工单快照与 SHA-256 摘要并启动
    工作流新轮次；批准事件到达后工单进入 `COMPLETED`（无主记录发布动作，不自动执行
    任何外部配置）；退回/拒绝事件分别进入 `RETURNED/REJECTED`。
  - 办理结果可重复登记（以 `rowVersion` 防并发覆盖），每次登记写入历史事件；
    登记结果不改变工单状态。
  - 附件 id 列表随工单保存（申请材料与凭证分开存储），详情返回附件摘要；
    前端通过平台附件接口下载/预览，附件模块按 `NetworkAttachmentAccessPolicy` 授权。
  - 证书工单附件扩展名黑名单：`key/pem/pfx/p12/jks/keystore`，命中即 400，
    防止私钥进入平台存储。
- 角色、权限、数据范围和审计：
  - 新增菜单 808「网络专项工单」（父菜单 800，路由
    `/architecture/network-work-orders`）、权限 8081/8082/8083
    （`architecture:network-work-order:view/apply/manage`）；新角色 113
    `NETWORK_MANAGER`（网络办理人员）拥有三者；本地管理员（用户 1）加入角色 113 并
    直接授权；存量角色兼容映射：持有 `architecture:view` 的存量角色获得 8081，
    持有 `architecture:apply` 的存量角色获得 8082（与 V83 口径一致）。
  - 查询列表/详情要求 `view/apply/manage` 任一且遵守数据范围；创建/编辑/提交/取消
    要求 `apply/manage` 且限本人；办理结果登记与审批动作要求 `manage`。
  - 服务端校验认证、RBAC、当前租户、实体存在性、状态机、行版本与附件授权；
    租户标识只从认证上下文注入，HTTP DTO 不接受/返回 `tenantId`。
  - 关键写操作（create/update/submit/cancel/result）记录平台操作审计
    （operation_code `architecture.network-work-order.*`）；业务状态变化写入不可变
    历史事件（快照与差异 JSON），工作流任务动作走 `wf_audit_event`。
- 外部系统、附件或敏感信息：无外部系统调用；测试数据全部虚构，不含生产数据、私钥
  或真实证书。

## 接口与数据

- API 契约（全部 `{ code, data, message, traceId }`，`/api` 前缀，JSON camelCase）：
  - `GET /api/architecture/network-work-orders`（查询 `kind?,status?,limit,offset`；
    `view/apply` 返回本人，`manage` 返回当前租户全部；响应
    `NetworkWorkOrderSummary[]`，无 total，客户端以返回长度判断分页）。
  - `GET /api/architecture/network-work-orders/{id}`（详情：工单主数据 + 解析后的
    kind 专属业务载荷 + 附件 id 列表 + 办理结果 + 不可变历史）。
  - `POST /api/architecture/network-work-orders`（创建草稿；请求
    `{kind, actionType, payload, reason?}`）。
  - `PUT /api/architecture/network-work-orders/{id}`（更新草稿；
    `{rowVersion, reason?, payload}`；仅本人 `DRAFT/RETURNED`）。
  - `POST /api/architecture/network-work-orders/{id}/submit`（`{rowVersion}`）。
  - `POST /api/architecture/network-work-orders/{id}/cancel`（`{rowVersion}`）。
  - `POST /api/architecture/network-work-orders/{id}/handling-result`（`manage`；
    `{rowVersion, resultStatus, resultDescription?, resultAttachmentIds[]}`；
    状态 `IN_REVIEW` 或 `COMPLETED`）。
  - 错误码：400 字段/状态/附件黑名单；401 未认证；403 权限/归属/任务处理人；
    404/code `40400` 当前租户资源不存在；409 行版本、状态机、流程实例已结束。
- 数据 Owner：`business/architecture`（`arch_` 前缀表）。
- 数据库迁移与存量兼容：仅追加
  `V100__create_architecture_network_work_orders.sql` 与
  `V101__seed_architecture_network_work_orders.sql`；不修改 V1-V99。V100 建立
  `arch_network_work_order`（主表，含 kind 专属 JSON 载荷、附件 id 列表、办理结果、
  工作流上下文、行版本）、`arch_network_work_order_history`、`arch_network_workflow_round`
  与 `arch_network_workflow_receipt`；V101 追加菜单 808、权限 8081-8083、角色 113 及
  兼容映射，并预置草稿流程 `architecture.network.work-order`（定义
  `900000000000032`、版本 `900000000000033`，审批节点 ROLE 113、ANY、空处理人 ERROR、
  动作 `APPROVE/RETURN/REJECT`，必须经平台既有发布入口生成 Flowable deployment 后才可
  提交）。迁移对稳定 ID 身份冲突失败关闭；存量数据无需改写。
- 脱敏输入输出示例：
  - CLB：`{"kind":"CLB","actionType":"OPEN","payload":{"clbName":"渠道接入CLB","purpose":"渠道流量接入","description":"演示数据"},"reason":"新环境开通"}`
  - DNS：`{"kind":"DNS","actionType":"ADD","payload":{"domainName":"demo.example.test","purpose":"演示域名","description":null},"reason":"环境搭建"}`
  - CERT：`{"kind":"CERT","actionType":"APPLY","payload":{"certType":"SSL","subjectName":"demo.example.test","purpose":"演示证书","description":null},"reason":"上线准备"}`

## 验收标准

1. 申请人（`apply/manage`）可创建并保存 CLB/DNS/证书三类草稿，字段契约与动作集按
   类型校验；非法类型/动作/必填缺失返回 400 且不落库。
2. 本人草稿/退回工单可编辑、提交；提交后进入 `IN_REVIEW` 并启动固定审批流程新轮次，
   工单快照与摘要固化；非本人、非草稿状态或行版本过期分别返回 403/409。
3. 网络办理人员（`manage`）可在审批中登记/更新办理结果（含凭证附件），完成后仍可
   补登；每次登记留痕；`COMPLETED` 表示外部配置已办理并登记，平台不执行任何外部动作。
4. 审批动作只能通过平台工作流任务完成（批准→`COMPLETED`、退回→`RETURNED`、
   拒绝→`REJECTED`、取消→`TERMINATED` 事件确认后 `CANCELLED`）；`view/apply` 角色
   无审批入口，直接调用审批相关接口返回 403。
5. 附件继承工单实体授权：申请材料与凭证只有可读工单的用户可下载/预览；草稿/退回外
   删除附件返回 403/409；证书工单上传私钥类文件返回 400 且不绑定。
6. 公开 API、真实 MySQL/Flyway、真实工作流集成、越权与敏感数据测试，以及桌面端和
   移动端主要旅程（列表、创建、编辑、提交、审批、结果登记、附件、历史）通过验收。

## 测试与发布

- 必须执行的测试：
  - 领域测试：状态机（提交/退回/拒绝/完成/取消与轮次递增）、per-kind 校验与动作集、
    摘要计算、办理结果登记并发、附件扩展名黑名单。
  - MySQL 集成测试（Testcontainers + 真实迁移）：V89 表结构与约束、V90 种子与身份
    冲突失败关闭、租户隔离、行版本并发、回执幂等、工作流轮次持久化。
  - `mvn -pl :ccb-architecture -am test`、`mvn test`、`npm --prefix web run build`。
  - `node scripts/check-all-governance.mjs`、`check-module-boundaries.mjs`、
    `check-flyway-migrations.mjs`、`check-codex-scope.mjs`、`git diff --check`。
- 上线验证：空库与既有库迁移至 V90；发布固定流程
  （`POST /api/workflows/definitions/900000000000032/publish`）；以申请人创建三类工单
  并提交，以网络办理人员完成退回/批准/结果登记，核对状态、历史、附件与审计；普通
  查看角色验证 403；真实浏览器桌面/移动视口 UAT。
- 回退或补偿：按前端、服务、迁移登记逆序回退应用代码；保留 V100-V101 与工单数据；
  通过后续补偿迁移隐藏入口和撤销授权，不执行生产逆向删除。
- 风险与人工复核人：数据库迁移与权限变更需模块 Owner 复核；V100-V101 与在途
  REQ-20260823-049 的 V96-V97 无编号冲突，但合并顺序需保证 049 先于本批次入库，
  否则 Flyway 会因 out-of-order 拒绝补位迁移；审批流程必须发布后才能提交工单。
- 运行时验收发现平台存量缺陷（非本批次引入）：`V36__persistent_attachments.sql` 将
  `att_file.expires_at` 定义为 `TIMESTAMP`，而平台 `AttachmentService.bind` 写入
  `'9999-12-31 23:59:59'`；mysql-connector-j 8/9 默认在会话级强制
  `STRICT_TRANS_TABLES`，任何环境执行附件绑定都会 Data truncation（500）。
  本批次只在隔离测试库将该列改为 `DATETIME` 完成验证；正式修复（改 V36 列类型或
  修正 bind SQL）需平台 Owner 另行立项。运行时验收证据：隔离 MySQL 8.4 空库迁移
  V1-V90、真实 Flowable 审批、附件绑定/私钥拒绝、权限矩阵与审计，见
  `.ai-control/requirements/req-20260823-051-clb-dns-certificate-work-orders/observation-T6.json`。
