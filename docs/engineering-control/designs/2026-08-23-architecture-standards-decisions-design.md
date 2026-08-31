# 架构规范与架构决策设计

## 状态与来源

- 设计修订：1
- 状态：本任务内形成并执行
- 需求：`REQ-20260823-050`
- 来源：`temp/spec.md` User Stories 120-131、`temp/CONTEXT.md` 领域词汇、外部票据 13、现有 architecture/workflow/attachment 实现，以及用户 2026-08-23「实施 13，使用 worktree」开工授权。

## 1. 选择的方案

### 1.1 架构规范

采用「受控类别 + 文档主记录 + 发布版本快照 + 平台附件」的纵向方案：

- 类别使用平台 `sys_dict_type`（`ARCH_STANDARD_CATEGORY`）与 `sys_config` 参数项维护，业务代码只按字典编码读取，不写死可管理分类。
- 文档主记录承载当前内容（DRAFT/PUBLISHED/OFFLINE），每次发布追加不可变版本快照，满足「发布与维护」和追溯要求；PDF 等文件只作为平台附件（`architecture-standard` 业务类型）承载，不建独立文档模块。
- 查阅与附件访问都受服务端 RBAC 与实体授权约束。

未选择：独立部署公告/网络规划模块（超出范围且被规格 122 禁止）；把网络分区/IP 段以文档替代（结构化数据不属于本批次）；直接 CRUD 无版本记录（无法追溯发布历史）。

### 1.2 架构决策

采用「事项主记录 + 材料/评审强类型子表 + 首次处理期限计算 + 平台工作流发布门禁 + 结论不可变 + 替代链推导」的纵向方案：

- 事项提交允许材料不完整（规格 123/124），方案/影响/争议按类别追加补齐并留痕。
- 首次处理为业务动作（review 权限），受理时间 + 7 自然日计算首次处理期限并持久化，逾期为独立计算标识。
- 评审以强类型记录落地（异步/会议、参与人、过程材料、关键意见、正式结论、理由），行动项单独子表，发布后继续跟踪。
- 正式结论发布复用平台工作流 `architecture.decision.review`：业务详情页保存发布准备（结论引用、替代/部分修订目标、类型必须确定），启动工作流实例，仅 `APPROVED` 生命周期事件在同一事务写入不可变结论与替代关系并完成事项（规格 128/129/130 语义）。
- 结论有效状态（EFFECTIVE/SUPERSEDED/PARTIALLY_SUPERSEDED）由替代关系推导，提供有效列表与完整链路查询（规格 131）。

未选择：首次处理走工作流（规格只要求记录首次处理与期限，业务动作 + 权限校验已满足且避免双流程状态机）；把结论与评审合并一张表（发布前评审可调整、发布后不可变，分开承载更清晰的不可变语义）；取消/拒绝事项状态（规格未定义，不引入）。

## 2. 边界与依赖

```text
Vue architecture pages (standards / decisions)
        |
        v
architecture HTTP / RBAC / entity authorization / audit
        |
        +--> standard service --> arch_standard_document + versions
        |        +--> AttachmentPort (platform/attachment public)
        |
        +--> decision service --> arch_decision_matter/materials/reviews/... 
        |        +--> WorkflowBusinessGateway (public)
        |        +--> ArchitectureDecisionWorkflowLifecycleConsumer (APPROVED/RETURNED)
        |        +--> SystemReferenceQuery (users / parameter options)
        +--> standard & decision attachment access policies (public SPI impl)
```

边界内：`server/src/modules/architecture/**`、`web/src/modules/architecture/**`、`web/src/router/index.ts`、V87/V88、architecture 契约与治理登记。

边界外且只读：workflow/security/system/attachment/file-preview/shared 公共实现、其他业务模块、生产系统。

模块调整（公共能力变更，用户已批准）：

- `business/architecture.allowed_dependencies` 增加 `platform/attachment`；architecture POM 增加 `ccb-attachment`。
- architecture 对外公开包保持 `com.ccb.architecture.integration`，新增附件访问策略与引用检查 SPI 同类的中性实现。
- 附件通过 `AttachmentPort`/`AttachmentGateway` 公开契约访问，不读附件表或对象键；工作流只通过 `com.ccb.workflow.integration` 消费。

## 3. 领域状态

### 3.1 架构规范文档

```text
DRAFT --发布--> PUBLISHED --下线--> OFFLINE --重新发布--> PUBLISHED
  ^                                                          |
  +------------------编辑并发布（版本自增）------------------+
```

- 版本快照每次发布追加，不可改写；删除仅允许从未发布的草稿。

### 3.2 决策事项

```text
SUBMITTED --首次处理:要求补充--> RETURNED_FOR_INFO --提出人补充并重提--> SUBMITTED
   |--首次处理:确认受理/确定评审方式--> IN_REVIEW --记录评审与结论--> IN_REVIEW(结论就绪)
   |--发布准备(类型+结论+替代目标)--> 启动工作流 --> APPROVED 事件 --> PUBLISHED(完成)
   \--发布工作流 RETURNED 事件 --> RETURNED_FOR_INFO（结论准备保留可调整）
```

- 首次处理期限 = 受理时间 + 7 自然日；逾期独立计算，不覆盖状态。
- 结论发布前必须确定事项类型（规格 125）。
- `PUBLISHED` 为完成态；已发布结论无任何修改/删除入口。

### 3.3 结论有效状态（推导）

```text
EFFECTIVE              无任何后续替代/部分修订引用
SUPERSEDED             被任一后续结论「替代」
PARTIALLY_SUPERSEDED   仅被后续结论「部分修订」
```

- 替代关系只允许指向已发布结论；同一对（新结论, 旧结论）唯一。
- 结论的发布/替代/部分修订全部通过工作流 APPROVED 事件在同一事务落库。

## 4. 数据模型（V87）

| 表 | 说明 |
| --- | --- |
| `arch_standard_document` | 规范文档主记录：标题、类别、摘要、正文、状态、当前版本、发布人/时间、行版本 |
| `arch_standard_document_version` | 发布版本快照（不可变） |
| `arch_decision_matter` | 事项主记录：编号 AD-年份-序号、标题、问题、类型、状态、受理时间、首次处理期限/结果/操作人、评审方式、提出人 |
| `arch_decision_material` | 协作补齐材料：类别（方案/影响/争议/其他）、内容、操作人、时间 |
| `arch_decision_review` | 评审记录：方式（异步/会议）、时间、关键意见、过程材料摘要、正式结论、理由 |
| `arch_decision_review_participant` | 评审参与人 |
| `arch_decision_action_item` | 行动项：内容、责任人、状态 |
| `arch_decision_conclusion` | 已发布结论（不可变）：内容、理由、发布人/时间 |
| `arch_decision_supersession` | 替代/部分修订关系 |
| `arch_decision_number_sequence` | 事项编号租户内序列（行锁） |
| `arch_decision_workflow_receipt` / `arch_decision_workflow_round` | 工作流事件回执与轮次（幂等） |

约束要点：编号唯一且不可复用（发布/取消不留空位给后续？——按 `AD-年份-序号` 递增且永久占用）；替代关系唯一；结论行无更新路径；所有写操作保留操作人/时间。

## 5. 权限与审计

| 权限 | 覆盖 |
| --- | --- |
| `architecture:standard:view` | 规范列表/详情/版本/附件预览 |
| `architecture:standard:manage` | 规范创建/编辑/发布/下线/删除草稿/附件维护 |
| `architecture:decision:view` | 事项/材料/评审/结论/替代链查询 |
| `architecture:decision:propose` | 本人事项创建/编辑/补充/重提 |
| `architecture:decision:review` | 首次处理、评审记录、结论与行动项记录 |
| `architecture:decision:manage` | 发布准备、启动/终止发布工作流、结论发布 |

- 关键写操作（提交、首次处理、评审、发布准备、结论发布）统一走 `SystemOperationAudit` 记录成功/失败与 trace。
- 附件访问策略：`architecture-standard`（view 权限可读，manage 可写删）、`architecture-decision`（propose 及以上可上传本人或全部事项材料，发布后结论附件只读）。

## 6. 测试接缝

- 领域单元测试：期限计算与逾期、编号分配、状态机、结论有效状态推导、替代链、发布前校验。
- 工作流消费者测试：APPROVED 原子发布、RETURNED 退回、重复/乱序事件幂等、摘要/轮次校验。
- MockMvc：权限矩阵、403/409、DTO 白名单、审计。
- 真实 MySQL/Flyway 集成：V87/V88 空库与增量迁移、存储断言、编号并发、工作流全链路。
- 前端构建 + 四个视口真实浏览器验收（1280x800、375x812、390x844、430x932，明暗主题）。
