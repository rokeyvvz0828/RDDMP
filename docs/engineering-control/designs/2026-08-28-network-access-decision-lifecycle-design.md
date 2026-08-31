# 网络访问判定与关系生命周期工程设计

## 文档状态

- 需求编号：REQ-20260828-055
- 主题：network-access-decision-lifecycle
- 设计修订：1
- 状态：用户已审计通过，允许按 scope 受控实施
- 授权依据：用户于 2026-08-28 明确回复“可以，开始执行”

## 1. 来源与边界

| 来源 | 本设计使用方式 |
| --- | --- |
| 用户本轮请求 | 直接授权产出 spec 和 plan，并停止 |
| `temp/implementation/issues/10-network-decision-and-lifecycle.md` | 作为本批次候选范围证据 |
| `temp/spec.md` | 采用用户故事 98、99、106、108、109 作为业务语义证据 |
| `temp/CONTEXT.md` | 采用术语定义和快照语义，不替代正式需求 |
| `docs/original/tech/网络权限管理.md` | 作为原始材料证据，不自动引入自动回收、催办、报表、动态表单 |
| REQ-20260826-054 | 作为已完成前置和现有代码基线 |

本设计不把附件中的外部门禁语句、生成顺序、提醒回收和报表导出自动转成当前开发承诺。

## 2. 目标与成功定义

目标是在现有 `business/architecture` 网络访问能力上补齐两个闭环：

1. 网络访问判定：输入来源、目标、协议、端口和时间范围，输出“需要申请”或“不需要申请”。
2. 关系生命周期：开通、修改、续期和关闭都由访问申请及平台工作流驱动，关系事实不可直接编辑或删除。

成功信号：

1. 判定引擎失败保守，无法证明无需申请时一律要求申请。
2. 现有有效关系覆盖、显式免申请规则覆盖和严格不覆盖三类路径均可测试。
3. 关系修改、续期和关闭通过申请生成可追溯历史，不绕过流程。
4. 长期有效不用遥远日期占位。
5. 下线实例风险可见，且不能进入新申请。
6. 权限、租户隔离、审计、迁移、前端桌面/移动旅程均有证据。

## 3. 现状模型

现有 09 能力：

- 表：`arch_network_zone`、`arch_network_zone_subnet`、`arch_external_network_address`、`arch_network_access_application`、`arch_network_access_relation`。
- 后端：`NetworkAccessController`、`NetworkAccessService`、`NetworkAccessStore` 和 `NetworkAccessModels`。
- 前端：`web/src/modules/architecture/NetworkAccessPage.vue`、`api.ts`、`types.ts`。
- 权限：`architecture:network-access:view/apply/manage`。

现有缺口：

- 无独立判定 API 和判定响应模型。
- 无端口范围解析和覆盖判断。
- 无关系对来源/目标/协议/端口/时间的完整覆盖计算。
- 无“明确无需申请”的规则台账。
- 访问关系可通过现有管理入口直接关闭，不满足“只能通过新申请办理关闭”。
- 申请无动作类型，无法区分开通、修改、续期和关闭。
- 提交和审批仍是模块内状态机，未接入平台工作流。
- 关系列表无法投影历史快照中的已下线实例风险。

## 4. 系统边界

### 4.1 被控对象

- `business/architecture` 网络访问申请、判定、关系和规则。
- 前端架构模块网络访问页面。
- 平台基础设施中的 Flyway 追加迁移。
- 平台工作流只通过公开 gateway 和事件订阅接入，不修改 workflow 内部实现。

### 4.2 外部边界

- 不连接真实网络设备和外部网络平台。
- 不操作生产系统。
- 不读取或写入真实敏感数据。
- 不恢复动态表单元数据能力。

## 5. 领域模型

### 5.1 枚举

```text
AccessDecision = NEEDS_APPLICATION | NOT_REQUIRED
DecisionBasis = RELATION_COVERED | RULE_EXEMPT | STRICT_REQUIRED
ValidityType = LIMITED | LONG_TERM
NetworkAccessActionType = OPEN | MODIFY | RENEW | CLOSE
ApplicationStatus = DRAFT | RETURNED | IN_REVIEW | APPROVED | REJECTED | CANCELLED
RelationStatus = ACTIVE | CLOSED
ExemptionRuleStatus = ACTIVE | DISABLED
```

`APPROVED` 表示业务成功完成并已投影关系变更；工作流退回使用 `RETURNED`，拒绝使用 `REJECTED`，撤销和终止使用 `CANCELLED` 或终止原因字段。

### 5.2 判定请求模型

```text
NetworkAccessDecisionCommand
  source: NetworkEndpointCommand
  target: NetworkEndpointCommand
  protocol: AccessProtocol
  ports: String
  validityType: ValidityType
  validFrom: OffsetDateTime
  validUntil: OffsetDateTime?
```

### 5.3 判定响应模型

```text
NetworkAccessDecisionResult
  decision: AccessDecision
  needsApplication: boolean
  basis: DecisionBasis
  reasonCodes: List<String>
  coveringRelationNos: List<String>
  coveringRuleCodes: List<String>
```

`decision` 是唯一业务结论；`basis` 和原因只用于审计和解释。

### 5.4 生命周期模型

访问申请增加：

- `action_type`
- `target_relation_id`
- `validity_type`
- 工作流实例、版本、轮次、提交摘要、取消请求字段

访问关系增加：

- `validity_type`
- `source_application_id`
- `replaces_relation_id`
- `replaced_by_relation_id`
- `closed_application_id`
- `close_type`

新增历史表：

- `arch_network_access_application_history`
- `arch_network_access_workflow_round`
- `arch_network_access_workflow_receipt`

新增规则表：

- `arch_network_access_exemption_rule`

规则只表达“无需申请”，不表达真实防火墙策略。

## 6. 判定算法

### 6.1 总体流程

```text
normalize(command)
  -> invalid: NEEDS_APPLICATION

resolve endpoints
  -> unresolved or offline selected: NEEDS_APPLICATION

parse ports and validity
  -> invalid: NEEDS_APPLICATION

find covering active relations
  -> full coverage: NOT_REQUIRED, RELATION_COVERED

find covering active exemption rules
  -> full coverage: NOT_REQUIRED, RULE_EXEMPT

default
  -> NEEDS_APPLICATION, STRICT_REQUIRED
```

任一异常被捕获后返回 `NEEDS_APPLICATION` 并记录受限错误摘要，不向前端暴露堆栈。

### 6.2 端点覆盖

端点覆盖同时检查身份和地址证据：

- 内部端点：部署实例 ID 集合必须被关系快照或规则的来源/目标集合覆盖。
- 外部端点：外部地址 ID 和规范化地址值必须被覆盖。
- IPv4 单地址和 CIDR 使用 `NetworkCidr` 判断包含关系。
- 域名只做小写精确匹配。
- 历史快照无法解析端点成员时，该关系不能作为完整覆盖证据。

### 6.3 端口覆盖

新增 `NetworkPortRanges` 值对象：

- 支持 `80`、`443,8443`、`8000-8010`、混合列表。
- 端口范围规范化为不重叠闭区间。
- 请求端口集合必须是关系或规则端口集合的子集。
- 任意非法字符、越界、空值、范围反转或集合为空均为无效输入。

### 6.4 协议覆盖

- 默认精确匹配。
- 若现有枚举存在全协议值，只在关系或规则显式声明全协议时覆盖具体协议。
- 未知协议不参与推断，直接需要申请。

### 6.5 时间覆盖

- `LIMITED`：`validFrom` 和 `validUntil` 必填，且 `validUntil > validFrom`。
- `LONG_TERM`：`validFrom` 必填，`validUntil` 必须为空。
- 有限期请求只能被起止时间完整包含的关系或规则覆盖。
- 长期请求只能被长期关系或长期规则覆盖。
- 09 存量关系使用 `valid_until IS NULL` 推断长期，使用非空 `valid_until` 推断有限期；无法证明覆盖时不采用。

## 7. 生命周期设计

### 7.1 开通

1. 用户创建 `OPEN` 申请。
2. 服务端校验端点、协议、端口、有效期和申请原因。
3. 提交后启动平台工作流。
4. 工作流成功完成后，服务端创建新 `ACTIVE` 关系。

### 7.2 修改

1. 用户从目标 `ACTIVE` 关系发起 `MODIFY` 申请。
2. 申请必须指定新的端点、协议、端口或有效期。
3. 工作流成功完成后，通过同一事务关闭旧关系并创建替代关系。
4. 旧关系保留快照、关闭申请 ID 和替代关系 ID。

### 7.3 续期

1. 用户从目标 `ACTIVE` 关系发起 `RENEW` 申请。
2. 续期只允许改变有效期和说明，不改变端点、协议和端口。
3. 工作流成功完成后，关闭旧关系并创建续期关系。

### 7.4 关闭

1. 用户从目标 `ACTIVE` 关系发起 `CLOSE` 申请。
2. 关闭申请只提交关闭原因和目标关系。
3. 工作流成功完成后关闭目标关系，不创建新关系。

### 7.5 直接关闭入口

现有直接关闭关系的后端 API 和前端按钮需要废弃。候选兼容策略：

- 前端移除直接关闭按钮，改为“发起关闭申请”。
- 后端保留原路由但返回业务冲突错误，提示通过关闭申请办理，避免静默绕过流程。

此策略需用户审计确认。

## 8. 平台工作流接入

复用 CLB/DNS/CERT 网络工单的公开接入模式，但使用独立业务类型：

```text
workflow definition code: architecture.network-access-application
business type: architecture_network_access_application
```

新增组件：

- `NetworkAccessApplicationSubmissionService`
- `NetworkAccessWorkflowLifecycleConsumer`
- `NetworkAccessWorkflowPayloadFactory`

工作流事件处理：

- completed：调用生命周期投影，生成或关闭关系。
- returned：申请回到 `RETURNED`，允许修改后重提。
- rejected：申请变为 `REJECTED`，不改关系。
- terminated/cancelled：申请变为 `CANCELLED`，不改关系。

流程定义种子只提供基础草稿；真实提交前必须有已发布且已部署定义。

## 9. 下线风险投影

风险不写入关系主表，采用查询时投影：

1. 从关系来源和目标快照解析部署实例 ID。
2. 查询当前实例状态。
3. 存在非 `ACTIVE` 或缺失实例时，返回：
   - `hasOfflineEndpointRisk`
   - `offlineEndpointCount`
   - `offlineEndpointSummaries`
4. 列表展示风险标识；详情展示实例摘要。

新申请继续使用现有 active-only 实例选项，并在服务端二次校验，防止绕过前端提交下线实例。

## 10. HTTP 契约

```text
POST /api/architecture/network-access/decision
GET  /api/architecture/network-access-exemption-rules
POST /api/architecture/network-access-exemption-rules
PUT  /api/architecture/network-access-exemption-rules/{id}
POST /api/architecture/network-access-exemption-rules/{id}/enable
POST /api/architecture/network-access-exemption-rules/{id}/disable
POST /api/architecture/network-access-applications
POST /api/architecture/network-access-applications/{id}/submit
POST /api/architecture/network-access-applications/{id}/cancel
GET  /api/architecture/network-access-relations
GET  /api/architecture/network-access-relations/{id}
```

现有审批模拟入口如继续保留，仅限开发和管理权限；真实生命周期以工作流事件为准。

## 11. 前端设计

页面仍使用 `web/src/modules/architecture/NetworkAccessPage.vue`，保持 09 已验证的列表和详情结构。

新增交互：

- 顶部或申请页签内增加“访问判定”工作区。
- 判定表单复用申请端点选择器，保持桌面/移动端一致。
- 判定结果只显示“需要申请”或“不需要申请”，辅助原因用轻量说明和详情抽屉。
- “需要申请”时可带入判定输入创建 `OPEN` 申请。
- 关系列表操作从“关闭”改为“发起修改申请”“发起续期申请”“发起关闭申请”。
- 关系风险以状态标识和详情摘要展示。
- 管理权限用户可维护免申请规则；普通申请用户只能查看命中的判定结果。

前端必须提供加载、空、失败、无权限、提交中状态，并按 `design-h5.md` 和 `frontend-usability.md` 验收手机视口。

## 12. 数据与迁移

### V104

- 扩展 `arch_network_access_application`：
  - `action_type`
  - `target_relation_id`
  - `validity_type`
  - workflow round/instance/version/digest/cancel fields
- 扩展 `arch_network_access_relation`：
  - `validity_type`
  - `source_application_id`
  - `replaces_relation_id`
  - `replaced_by_relation_id`
  - `closed_application_id`
  - `close_type`
- 新增：
  - `arch_network_access_application_history`
  - `arch_network_access_workflow_round`
  - `arch_network_access_workflow_receipt`
  - `arch_network_access_exemption_rule`

### V105

- 补齐菜单和权限种子。
- 补齐免申请规则示例数据。
- 补齐访问申请工作流草稿定义。

V100-V103 不修改。

## 13. 权限与审计

| 行为 | 权限 |
| --- | --- |
| 查看判定、关系、规则 | `architecture:network-access:view` |
| 创建、提交、撤销申请 | `architecture:network-access:apply` |
| 管理免申请规则、管理审批模拟入口 | `architecture:network-access:manage` |

审计事件候选：

- `architecture.network-access.decision.evaluate`
- `architecture.network-access-application.create`
- `architecture.network-access-application.submit`
- `architecture.network-access-application.cancel`
- `architecture.network-access-application.workflow-complete`
- `architecture.network-access-relation.replace`
- `architecture.network-access-relation.close-by-application`
- `architecture.network-access-exemption-rule.create`
- `architecture.network-access-exemption-rule.update`
- `architecture.network-access-exemption-rule.enable`
- `architecture.network-access-exemption-rule.disable`

## 14. 测试传感器

- `NetworkPortRangesTest`
- `NetworkAccessDecisionServiceTest`
- `NetworkAccessLifecycleServiceTest`
- `NetworkAccessWorkflowLifecycleConsumerTest`
- `NetworkAccessStoreMySqlTest`
- `NetworkAccessControllerTest`
- `NetworkAccessPage` 前端构建和浏览器 UAT
- `node scripts/check-codex-scope.mjs`
- `git diff --check`

验证报告必须区分代码存在、自动化测试、真实服务启动和浏览器验收。

## 15. 已审计决策

1. 采用显式免申请规则台账作为“明确无需申请”的最小表达。
2. 修改/续期采用“关闭旧关系并创建替代关系”模型。
3. 原直接关闭接口按兼容策略保留但返回冲突错误。
4. 外部地址本批次只支持精确匹配，不支持通配符和泛域名规则。
5. 访问申请工作流定义使用独立代码 `architecture.network-access-application`。
