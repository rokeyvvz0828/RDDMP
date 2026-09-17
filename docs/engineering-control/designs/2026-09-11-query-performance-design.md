# 项目成员与流程定义查询性能优化工程设计

## 文档状态

- 修订：1
- 状态：已确认
- 用户确认依据：用户确认首期“性能热点修复、读模型摘要与可观测性”设计。

## 目标与成功信号

将项目成员列表的数据库查询数从 `1 + N` 收敛为固定两次，将流程定义列表改为只读取摘要而不读取完整定义 JSON，并以低基数计时指标提供后续优化的真实依据。

## 使用者与场景

项目管理员查看成员与角色分配时，成员数增加不得线性放大 SQL 次数。流程管理员查看模板或项目流程时，列表读取不得随 JSON 画布大小放大网络和 JVM 反序列化成本；进入详情页时才读取完整定义。

## 必须需求与验收条件

| ID | 要求 | 验收 | 反例 |
| --- | --- | --- | --- |
| R1 | 成员角色必须批量读取。 | 成员列表只执行成员查询和角色查询各一次，响应保持现有排序与字段。 | 每个成员额外执行一次角色查询。 |
| R2 | 定义列表只读摘要。 | 列表 Mapper SQL 不含 `definition_json`；`requires_configuration` 与最新版本模型一致。 | 列表传输 JSON 后再删除。 |
| R3 | 摘要读模型必须一致。 | 迁移回填及所有受影响的版本写路径同事务更新 `latest_version_no`、`requires_configuration`。 | 新草稿、复制版本或发布后的摘要滞后。 |
| R4 | 性能结果可观测。 | Actuator 读取到成员/定义查询计时指标；标签不含租户、用户、项目或业务编号。 | 用高基数 ID 作为指标标签。 |
| R5 | 安全与契约保持不变。 | Mapper 始终按 tenant 查询；既有服务层权限、审计、分页和 API 响应不变。 | 通过 Mapper 绕过项目访问或扩大接口。 |

## 不变量与约束

- 保持 `com.ccb.*` 包名、Maven artifact、REST API 和前端 `WorkflowDefinition`、成员 DTO 兼容。
- Mapper 只承载数据访问；认证、RBAC、项目范围、审计和对象存储 URL 仍在服务层。
- Flyway 迁移只追加，面向 MySQL 8.4；不得修改既有迁移。
- 不记录或暴露定义 JSON、用户、租户、项目 ID 等敏感或高基数指标标签。

## 非目标

- 不做全仓或全服务 `JdbcTemplate` 迁移，不拆分超大服务。
- 不引入缓存、Redis、游标分页、Outbox、异步附件删除或外部观测系统。
- 不改变 `current_version` 已有语义，也不删除历史流程版本。

## 方案比较与选择

1. **选定：增量 Mapper + 摘要字段。** 两个热点查询先使用 MyBatis XML，流程定义表保存最新版本和配置标识摘要。读写路径清晰、可测且可回退。
2. **备选：仅将 N+1 改为 JdbcTemplate 批量查询。** 拒绝：能减查询次数，但延续 SQL 与业务规则混杂，不能建立 Mapper 基线。
3. **备选：列表 JOIN 版本表后在 Java 解析 JSON。** 拒绝：减少相关子查询但仍读取大字段，未解决传输和解析成本。
4. **备选：全量 MyBatis 迁移。** 拒绝：影响面过大，无法将性能回归与迁移错误隔离。

## 架构边界与组件职责

- `ProjectMemberMapper` 与 XML：查询成员基础行、按成员 ID 集合查询角色行；所有参数绑定且必须带租户条件。
- `ProjectMemberQueryService`：复用现有服务鉴权后调用 Mapper、按成员分组角色、生成头像 URL，保持旧响应形状。
- `WorkflowDefinitionMapper` 与 XML：查询定义摘要与计数，不选择 JSON。
- `WorkflowDefinitionSummaryService`：基于完整 JSON 计算 `requires_configuration`，在创建、编辑、复制、发布等版本变更的同一事务刷新 `latest_version_no` 与摘要标识。
- `QueryPerformanceMetrics`：记录固定名称、固定低基数标签的计时器，不承担缓存或业务决策。

## 接口、数据和状态流

成员读取路径：认证与项目范围校验 -> 成员 Mapper -> 角色 Mapper `member_id IN (...)` -> 内存按 `member_id` 分组 -> 头像 URL 装饰 -> 原响应。

定义读取路径：认证与范围校验 -> 定义摘要 Mapper -> 读取 `requires_configuration` -> 原分页响应。详情接口继续从 `wf_version` 读取 `definition_json`。

版本写入路径：解析并校验 JSON -> 保存版本或状态变更 -> 使用同一 JSON 计算摘要 -> 更新同定义的 `latest_version_no`、`requires_configuration` -> 提交事务。`latest_version_no` 指向最新版本而非仅已发布的 `current_version`，避免草稿版本摘要滞后。

迁移路径：追加两列和索引 -> 用每定义的最高 `version_no` 回填 `latest_version_no` -> 由 MySQL JSON 谓词识别两种现有占位模式并回填 `requires_configuration` -> 迁移集成测试对照 Java 解析结果。

## 错误、降级与恢复

- 空成员列表不执行无效 `IN ()` 查询，直接返回空列表。
- 缺失版本或无效 JSON 的回填摘要为 `false`，并在迁移验证中报告异常定义；不以错误摘要放宽流程执行校验。
- Mapper 查询异常继续交由既有异常模型返回；指标以固定 `outcome=error` 记录后重新抛出。
- 应用回退不删除新列；发现摘要不一致时追加补偿迁移重算，禁止回改已发布迁移。

## 安全、性能、兼容性与运维

- Mapper 参数使用绑定变量；集合参数使用 MyBatis `foreach`，不拼接外部输入。
- 需要的索引以实际执行计划和迁移集成结果决定，最小候选为版本查询的 `(tenant_id, definition_id, version_no)` 覆盖路径；不依据猜测新增冗余索引。
- 指标名为 `ccb.project.members.query`、`ccb.workflow.definitions.query`，标签只允许 `operation`、`scope_type`、`outcome`。
- API p95/p99 通过 Actuator 指标观察；慢 SQL 阈值记录为本地配置，不增加外部系统依赖。

## 验证策略

| 需求 | 信号 | 方法 |
| --- | --- | --- |
| R1 | SQL 次数、分组结果、权限 | Mapper/服务单测和本地 API 探针。 |
| R2-R3 | 列表 SQL、回填和写后摘要 | MySQL Flyway 集成测试、版本生命周期测试、定义详情对照。 |
| R4 | 指标名称、标签和值 | Spring Boot 指标测试与本地 Actuator 探针。 |
| R5 | API、租户和分页兼容 | System/Workflow 回归测试、鉴权负例和 `git diff --check`。 |

## 假设、未知项与决策记录

- 假设：MyBatis-Plus 已由父 POM 管理且可在平台模块以 `@Mapper` 与 XML 增量启用；若启动验证失败，先补最小模块配置，不迁移其他查询。
- 未知项：历史 JSON 是否存在未被现有 Java 方法识别的占位结构；迁移集成测试将以 Java 方法对照，若发现则阻塞发布并补充规则。
- D1：`latest_version_no` 独立于 `current_version`。原因：前者服务列表摘要，后者保留已发布版本语义。
- D2：指标标签不使用实体标识。原因：保护隐私、避免监控存储高基数。

## 风险与回退原则

最大风险是流程摘要在某个版本写路径遗漏更新。所有版本写路径必须经同一摘要服务，并以生命周期测试覆盖。数据库结构采用仅新增字段的可兼容策略；功能回退只回退应用提交，摘要错误使用后续补偿迁移修复。
