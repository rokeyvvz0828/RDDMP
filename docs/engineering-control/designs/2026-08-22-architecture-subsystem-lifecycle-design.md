# 架构子系统全生命周期设计

## 状态与来源

- 设计修订：2
- 状态：已批准
- 需求：`REQ-20260822-048`
- 来源：外部 01 开发票据、现有 architecture/workflow 实现，以及用户于 2026-08-22 的逐项确认。
- 覆盖决定：逻辑子系统同样走工单；审批人不得修改业务字段；物理归属迁移采用“新建目标记录并下线旧记录”；AI 只留接口不调用真实服务。
- 修订 2：按用户明确的“全局递增序号”统一编号分配域；租户隔离不再被误解为每租户重新起号。

## 1. 选择的方案

采用“发布主记录 + 强类型变更申请 + 平台工作流 + 策略化编号 + 引用检查 SPI”的纵向方案。

选择原因：

- 发布主记录继续服务稳定查询，不被草稿或审批中数据污染。
- 强类型申请表可执行字段约束、快照差异、原子级联发布和数据库唯一性，不重新引入已下线的动态表单。
- 复用 `WorkflowBusinessGateway` 和 `WorkflowLifecycleConsumer`，业务模块只拥有申请及发布语义。
- 编号分配与格式化封装在策略后，首期实现简单、并发可锁定，后续可替换而不侵入申请服务。
- 引用检查以 architecture 公开 SPI 扩展，当前没有未来业务模块时仍能运行，外部检查失败时 fail-closed。

未选择：

- 继续直接 CRUD：无法满足审批发布、排他锁和历史。
- 审批人修改发布值：会破坏提交快照与审批事实的一致性，已被用户否决。
- 物理记录直接改父级：编号嵌入父序号，会导致身份与归属不一致。
- 调用 `business/ai`：当前无公开契约，且用户要求首期不调用真实 AI。

## 2. 边界与依赖

```text
Vue architecture pages
        |
        v
architecture HTTP / RBAC / entity authorization
        |
        +--> change application service --> typed application tables
        |           |                         numbering/locks/history
        |           +--> WorkflowBusinessGateway (public)
        |           +--> suggestion providers (local + no-op AI)
        |           +--> reference checker registry (public SPI)
        |
        +--> published query service --> arch logical/physical masters
                                      ^
                                      |
                         APPROVED lifecycle consumer
```

边界内：`server/src/modules/architecture/**`、`web/src/modules/architecture/**`、V82—V84、architecture 契约与治理登记。

边界外且只读：workflow/security/system/shared 公共实现、其他业务模块、业务表单元数据、真实 AI、生产系统。

模块调整：

- `business/architecture.allowed_dependencies` 增加 `platform/workflow`。
- architecture POM 增加 `ccb-workflow`。
- architecture 公开包设为 `com.ccb.architecture.integration`，仅承载引用检查 SPI 和中性值对象。
- workflow 只通过 `com.ccb.workflow.integration` 消费，不访问其表或内部包。

## 3. 领域状态

### 3.1 发布主记录

```text
ACTIVE --下线工单批准--> OFFLINE --重启工单批准--> ACTIVE
   |                         |
   +--作废工单批准----------+--作废工单批准--> VOIDED

VOIDED 为不可逆终态
```

### 3.2 变更申请

```text
DRAFT --提交--> IN_REVIEW --批准--> APPROVED
                    |  |\--拒绝--> REJECTED
                    |  \--退回--> RETURNED --重提(新轮次)--> IN_REVIEW
                    \--终止确认--> CANCELLED

DRAFT/RETURNED --取消--> CANCELLED
```

- `IN_REVIEW`、`RETURNED` 对已有目标持有排他锁；新增申请通过值保留和编号保留防冲突。
- 退回终止当前 workflow instance，但保留业务申请、目标锁和值/编号保留。
- 拒绝和取消释放未发布编号、值保留和目标锁。
- 审批中取消先记录取消请求并调用 `terminate`；收到当前轮次 `TERMINATED` 后才终态化。

### 3.3 工单类型

| 目标 | 类型 | 发布动作 |
| --- | --- | --- |
| 逻辑 | `CREATE` | 创建逻辑，可原子创建 0..N 个物理 |
| 逻辑 | `UPDATE` | 修改单个逻辑，不级联 |
| 逻辑 | `OFFLINE` / `REACTIVATE` / `VOID` | 单目标状态变化 |
| 物理 | `CREATE` | 在已发布活动逻辑下创建一个物理 |
| 物理 | `UPDATE` | 修改单个物理，不允许改父级 |
| 物理 | `OFFLINE` / `REACTIVATE` / `VOID` | 单目标状态变化 |
| 物理 | `REPLACE` | 在目标逻辑下新建物理并下线旧物理，保存替换关系 |

## 4. 数据设计

### 4.1 发布表扩展

`arch_logical_subsystem` 追加：

- `number_sequence INT NULL`：内部四位序号，既有编号不改写；全系统唯一，不按租户重新起号。
- `status VARCHAR(16)`：`ACTIVE/OFFLINE/VOIDED`，既有记录回填 `ACTIVE`。
- `sort_no INT`：稳定排序。
- `row_version BIGINT`：乐观并发。

`arch_physical_subsystem` 追加：

- `number_slot VARCHAR(1) NULL`：`1..9,A..Z`；与逻辑内部序号共同形成新编号。
- `english_name VARCHAR(200) NULL`：非空时租户内永久唯一。
- `status VARCHAR(16)`、`row_version BIGINT`。

迁移按 `tenant_id, created_at, id` 的全局稳定顺序为既有逻辑分配内部序号，按 `tenant_id, logical_subsystem_id, created_at, id` 为所有既有物理历史分配槽位；只补元数据，不更新 `code`。全系统逻辑历史超出 9999 或任一逻辑物理历史超出 35 时，用 SQL 预检查令迁移失败。

### 4.2 申请与历史表

- `arch_subsystem_change_application`：目标种类、操作类型、目标 ID、状态、申请人、原因、当前业务轮次、当前工作流定义/版本/实例、数据摘要、取消请求、行版本和审计。
- `arch_subsystem_logical_draft`：每个逻辑类申请一行强类型逻辑草稿，保存保留编号和提交快照版本。
- `arch_subsystem_physical_draft`：物理草稿行；逻辑 CREATE 为 0..N 行，其他物理申请为一行；含稳定 `line_no`、保留槽位和来源目标。
- `arch_subsystem_change_history`：不可变业务事件、前后状态、轮次、快照/差异摘要、操作者和时间。
- `arch_subsystem_change_lock`：`tenant + target_kind + target_id` 唯一，保护已发布目标。
- `arch_subsystem_value_reservation`：活动申请中的编号/名称/英文名归一化值唯一保留；终态释放。
- `arch_subsystem_replacement`：旧物理、新物理、批准申请和时间，不可变。
- `arch_subsystem_workflow_round`：每轮 definition/version/instance/digest/status/时间。
- `arch_subsystem_workflow_receipt`：`tenant + event_id + subscriber_key` 唯一，记录 `PROCESSED/IGNORED`。

不使用通用 JSON 作为业务事实。历史事件可保存受控 JSON 快照，当前可编辑字段始终落强类型列。

### 4.3 编号状态

- `arch_subsystem_number_namespace`：全局分配域的命名空间与 `next_ordinal`；逻辑使用 `LOGICAL`，物理使用 `PHYSICAL:<logicalSequence>`。业务申请仍保存 `tenant_id`，编号 namespace 不按租户分裂。
- `arch_subsystem_number_recycled`：被拒绝/取消的最小可复用序号。
- `arch_subsystem_number_reservation`：当前申请活动保留，唯一约束命名空间和序号。

事务分配算法：

1. `SELECT ... FOR UPDATE` 锁命名空间。
2. 先取 `recycled` 最小值并删除；否则取 `next_ordinal` 并递增。
3. 校验逻辑上限 9999、物理上限 35。
4. 写活动 reservation 和草稿保留字段。
5. 批准时写主记录后消费 reservation；拒绝/取消时写 recycled 并删除 reservation；退回不处理。

策略接口：

```java
public interface SubsystemNumberStrategy {
    NumberReservation reserve(NumberRequest request);
    void release(NumberReservation reservation);
}
```

首期实现 `FixedPrefixIncrementalSubsystemNumberStrategy`，负责 `A%04d` 和 `W%04d<slot>` 格式。事务和持久化锁由编号存储协作者提供，策略不依赖 HTTP。

## 5. 服务与接口

### 5.1 工单 REST

- `GET /api/architecture/subsystem-change-applications`：本人或全部分页。
- `POST /api/architecture/subsystem-change-applications`：创建草稿。
- `GET /api/architecture/subsystem-change-applications/{id}`：详情、快照、差异、历史和工作流上下文。
- `PUT /api/architecture/subsystem-change-applications/{id}`：仅本人 `DRAFT/RETURNED`。
- `POST .../{id}/submit`：分配/保留编号、取锁、启动新流程轮次。
- `POST .../{id}/cancel`：草稿/退回同步取消，审批中终止后确认。
- `GET .../suggestions`：本地建议与空 AI 建议。

批准、退回、拒绝继续调用平台工作流任务决定 API；业务详情通过工作流公开前端 API 展示当前任务。

### 5.2 主记录兼容接口

- GET 列表/详情保留，响应增加状态、内部关系摘要和可发起动作提示。
- POST/PUT/DELETE 保留路由但统一返回 409，错误码 `ARCHITECTURE_WORK_ORDER_REQUIRED`，并返回工单路由提示；不执行校验后写库或审计成功事件。

### 5.3 引用检查 SPI

```java
public interface SubsystemReferenceChecker {
    String checkerKey();
    ReferenceCheckResult check(long tenantId, SubsystemKind kind, long subsystemId,
                               ReferenceCheckOperation operation);
}
```

`SubsystemReferenceGuard` 汇总所有 Spring 实现：

- 内部逻辑—物理检查始终运行。
- 外部 provider 为空是健康状态。
- 任一 provider 返回有效引用则拒绝。
- 任一 provider 抛错、超时或返回不可判定则拒绝作废；错误写审计但不泄露内部异常。

### 5.4 建议接口

`SubsystemSuggestionProvider` 返回 `field/value/source/explanation`。`DeterministicSubsystemSuggestionProvider` 只读受控参数和当前草稿；`NoopAiSubsystemSuggestionProvider` 始终返回空列表。前端仅在字段未被用户修改且用户点击“采用”后写入本地表单。

## 6. 工作流设计

- 流程编码：`architecture.subsystem.change`。
- 业务类型：`architecture_subsystem_change`。
- subscriber key：`architecture.subsystem.change.lifecycle.v1`。
- 单个 `ROLE` 审批节点，角色 ID 110 `ARCHITECTURE_MANAGER`，模式 `ANY`，动作仅 `APPROVE/RETURN/REJECT`。
- 预置 definition ID `900000000000030`、version ID `900000000000031`，迁移前再次扫描冲突。
- V84 以 `DRAFT` 预置定义和版本，不伪造 Flowable `deployment_id`；使用平台既有发布入口编译并部署后才允许 `startByCode`，未发布时提交事务整体回滚。
- `WorkflowBusinessContext`：module `architecture`、business key 为申请 ID 字符串、business round、业务详情 action path、提交快照 SHA-256 digest。
- `STARTED` 只确认当前轮次；`RETURNED` 进入 RETURNED；`REJECTED` 终态并释放；`APPROVED` 原子发布；`TERMINATED` 只完成已登记的取消请求。
- 重复或非当前 instance/round/digest 事件记为 `IGNORED`，不能改变申请。

## 7. 权限与目录迁移

- 菜单 803：`ArchitectureSubsystemChanges` / `/architecture/subsystem-change-applications`。
- 权限 8031/8032/8033：`architecture:view`、`architecture:apply`、`architecture:manage`。
- 角色 110：`ARCHITECTURE_MANAGER`，拥有三级权限和三个架构菜单；另绑定平台既有 workflow 根菜单 200 与收件箱 202，以复用现有当前任务/决定 API，不授予定义、监控或已办菜单；本地 tenant 1 的 admin 同时绑定该角色，便于真实流程验收。
- 旧逻辑/物理 read 权限映射 view；旧 create/update/delete 任一权限映射 apply；超级管理员和新架构管理员拥有 manage。
- 发布主数据和选项 GET 同时兼容旧 list 与新 view/apply/manage；旧 POST/PUT/DELETE 即使由新 apply/manage 调用也仍进入无副作用工单 409。
- 服务端不接受客户端提交权限码、租户或申请人 ID。

## 8. 前端信息架构

- `/architecture/logical-subsystems`：发布逻辑列表/详情，只读；操作为发起更新、下线、重新启用、作废。
- `/architecture/physical-subsystems`：发布物理列表/详情，只读；操作为发起新增、更新、下线、重新启用、作废、替换。
- `/architecture/subsystem-change-applications`：工单列表。
- `/architecture/subsystem-change-applications/new`：全页新建。
- `/architecture/subsystem-change-applications/:id`：详情、编辑和审批统一入口。

全页表单：页头显示类型、状态和“待生成/已保留”编号；逻辑区域在上，物理动态卡片在下；底部使用受控粘性操作栏。移动端卡片纵向排列，弹层高度不超过视口，页面本身不横向滚动。

## 9. 错误与并发

- 400：字段、状态转换、父级、槽位容量或引用规则错误。
- 401/403：认证、三级权限、申请归属、管理权限或工作流任务权限错误。
- 404：当前租户下申请/主记录不存在。
- 409：目标锁、值保留、行版本、旧写接口、流程轮次或发布事实冲突。
- 503：外部引用检查器不可判定；作废 fail-closed。
- 所有关键成功和已认证失败写现有操作审计，包含 trace ID、申请 ID、轮次和状态变化；敏感字段不进入日志。

## 10. 验证设计

1. 编号策略单元测试：格式、槽位映射、容量、最小复用。
2. MySQL 测试：V81→V84 和空库迁移、既有编号不改写、并发分配/锁、唯一约束、级联原子性。
3. 服务/MockMvc：状态机、权限矩阵、本人数据范围、旧写 409、建议无 AI、引用 fail-closed。
4. 工作流：真实固定定义启动、退回新轮次、自审批、拒绝/取消、幂等和乱序事件、批准发布。
5. 前端构建与真实浏览器：四个视口、明暗主题、全状态、网络、控制台和几何。
6. 治理：scope、module boundaries、Flyway、全治理、Boot 装配、`git diff --check`。

## 11. 回退

- 应用按前端路由/页面、architecture 工作流消费者和服务、模块依赖/治理的逆序回退。
- V82—V84、已分配编号、申请、发布和审计历史全部保留；不逆向删除。
- 如需关闭能力，后续补偿迁移隐藏菜单并撤销角色授权，旧 GET 可继续提供只读服务。
- 未批准且活动的工单在回退前停止新提交；恢复时按保存的轮次和 reservation 继续处理，不能手工改状态。

## 12. 已批准关键决定

1. 管理权限包含申请和批准，可完成自申请自审批，但仍必须走流程。
2. 新建逻辑工单可带也可不带一批物理子系统，批准时原子发布。
3. 逻辑编号 `A+4 位`；物理编号 `W+逻辑 4 位序号+1 位 1-Z 槽位`。
4. 拒绝、取消或未发布作废的保留编号可复用；已发布编号永久不可复用。
5. 审批人只做批准、退回、拒绝，不调整业务字段。
6. 下线可恢复，作废不可逆；引用检查异常拒绝作废。
7. 物理归属不直接修改，使用新建目标记录并下线旧记录。
8. AI 只预留接口，不调用真实 AI。
9. 使用独立工单页和全页表单，桌面展开卡片、移动纵向卡片。
