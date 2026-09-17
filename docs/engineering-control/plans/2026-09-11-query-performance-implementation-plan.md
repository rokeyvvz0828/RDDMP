# 项目成员与流程定义查询性能优化实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 以增量 MyBatis Mapper、流程定义摘要读模型与低基数指标消除两个热点列表的可预见性能退化，并保持权限和 API 兼容。

**架构：** system 和 workflow 模块显式依赖 MyBatis-Plus starter，以 `@Mapper` 接口和 XML 承载两个只读查询簇。工作流定义表新增最新版本与配置状态摘要；两个工作流版本写服务通过共享投影器在本地事务中刷新摘要。服务层继续承担权限、范围、审计、头像 URL 处理和分页响应。

**技术栈：** Java 17、Spring Boot 3.4.4、MyBatis-Plus 3.5.12、MySQL 8.4、Flyway、Micrometer/Actuator、JUnit 5、Mockito、Testcontainers。

## 状态与来源

- 计划修订：1
- 设计修订：1
- 设计文档：`docs/engineering-control/designs/2026-09-11-query-performance-design.md`
- 状态：可移交

## 目标与全局约束

- 保持 `/api` 路径、请求参数、分页响应、成员角色字段、`requires_configuration` 和 `current_version` 的外部语义不变。
- Mapper 只读取数据且每个查询都接收、使用 tenant ID；服务层不得把认证、RBAC、项目可见范围或审计下沉至 XML。
- `latest_version_no` 用于最新草稿/发布版本摘要，不能替代 `current_version` 的已发布版本语义。
- 迁移使用新的 `V201__workflow_definition_query_summary.sql`；若执行时目标分支已含同版本迁移，停止并基于最新未占用版本重新编号，不覆盖任何已发布文件。
- 指标仅允许 `operation`、`scope_type`、`outcome` 标签；不记录 JSON、租户、用户、项目、定义、请求或业务编号。
- 不引入缓存、游标、Outbox、服务包迁移或其余 `JdbcTemplate` 迁移。

## 文件职责地图

| 路径 | 状态 | 职责与依据 |
| --- | --- | --- |
| `server/src/platform/system/pom.xml` | 现有 | 显式引入 MyBatis-Plus starter，当前只有根版本属性。 |
| `server/src/platform/system/src/main/java/com/ccb/system/project/ProjectMemberMapper.java` | candidate-new | 定义成员基础行与角色批量行的 tenant-bound Mapper 方法。 |
| `server/src/platform/system/src/main/resources/mapper/project/ProjectMemberMapper.xml` | candidate-new | 保存两个参数化 SQL 与结果映射。 |
| `server/src/platform/system/src/main/java/com/ccb/system/project/ProjectService.java` | 现有 | 保留 `member:read`、项目范围、头像 URL 和响应组装；替换 N+1 读取。 |
| `server/src/platform/workflow/pom.xml` | 现有 | 显式引入 MyBatis-Plus starter。 |
| `server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowDefinitionMapper.java` | candidate-new | 查询定义摘要、总数与条件范围。 |
| `server/src/platform/workflow/src/main/resources/mapper/workflow/WorkflowDefinitionMapper.xml` | candidate-new | 保存列表/总数 SQL，明确不选择 `definition_json`。 |
| `server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowDefinitionSummaryProjector.java` | candidate-new | 从已校验 JSON 计算摘要并更新 `wf_definition`。 |
| `WorkflowService.java`、`FlowableWorkflowService.java` | 现有 | 调用 Mapper 和摘要投影器，覆盖普通与 Flowable 版本写路径。 |
| `V201__workflow_definition_query_summary.sql` | candidate-new | 追加摘要列、索引和以最高版本为基准的回填。 |
| `server/src/platform/boot/src/main/resources/application.yml` | 现有 | 暴露 metrics 端点并启用两个计时器的直方图/分位数。 |
| `ProjectServiceTest.java`、`WorkflowServiceTest.java` | 现有 | 验证服务行为、权限边界、Mapper 委托与摘要调用。 |
| `WorkflowDefinitionSummaryMigrationMySqlTest.java` | candidate-new | 用 MySQL 8.4/Flyway 验证存量回填和 SQL 摘要规则。 |

## 任务依赖图与并行策略

`T1 -> {T2, T3} -> T4 -> T5`。

T2 仅写 system 模块，T3 写 workflow 与基础设施迁移，二者在 T1 模块依赖已验证后可并行。T4 同时改两个服务的指标和 boot 运行配置，必须等待 T2/T3；T5 只记录和验证，最后执行。

## 需求覆盖表

| 需求 | 任务 |
| --- | --- |
| R1：成员角色批量读取 | T1, T2, T4, T5 |
| R2：定义列表只读摘要 | T1, T3, T4, T5 |
| R3：摘要同步与回填 | T3, T4, T5 |
| R4：低基数计时指标 | T4, T5 |
| R5：租户、权限、分页和 API 不变 | T2, T3, T4, T5 |

### T1：启用并验证模块级 MyBatis Mapper 基线

#### 需求映射与前置事实

**需求映射：** R1, R2, R5  
**前置任务：** 无  
根 POM 仅声明 `mybatis-plus.version`，system/workflow POM 没有 starter 依赖；local profile 已配置 `classpath*:mapper/**/*.xml`。

#### 文件边界与接口

- 修改：`server/src/platform/system/pom.xml`、`server/src/platform/workflow/pom.xml`。
- 新建：两个模块的最小 `@Mapper` 接口和 XML 空查询基线，随后由 T2/T3 填充业务 SQL。
- 消费：`com.baomidou:mybatis-plus-spring-boot3-starter:${mybatis-plus.version}`、既有 `mybatis-plus.mapper-locations` 配置。
- 产出：两个模块均能扫描注解 Mapper 并加载 XML；不改变 JDBC 写路径。

#### 操作步骤、命令和预期信号

- [ ] 建立启动/扫描聚焦测试，断言 Mapper Bean 在 Spring 上下文可解析。
- [ ] 先运行对应模块测试并记录当前没有 Mapper starter 的基准事实。
- [ ] 在两个模块 POM 添加同版本 starter；创建接口和 XML 的最小可调用查询。
- [ ] 运行 `mvn -pl :ccb-system,:ccb-workflow -am test`，确认依赖解析、Mapper 扫描和既有测试通过。
- [ ] 运行 `git diff --check`，记录依赖树中无重复 MyBatis starter 或版本漂移。

#### 验收、证据与回滚

验收：两个模块独立测试均能启动并解析 Mapper；未更改任何 API、服务权限或数据库结构。  
回滚：移除 T1 POM、接口和 XML 基线增量。  
停止条件：starter 与既有 JDBC/Flowable 自动配置冲突，或需修改根 POM、组合根扫描策略之外的文件。  
升级条件：自动扫描无法保证模块边界，需要新增 boot 级全局配置或公共包契约。

### T2：以批量 Mapper 读取项目成员角色

#### 需求映射与前置事实

**需求映射：** R1, R5  
**前置任务：** T1  
`ProjectService.members` 已确认执行一次成员查询并对每行执行一次角色查询；服务前置执行 `member:read` 和项目可见范围校验。

#### 文件边界与接口

- 修改：`ProjectService.java`、`ProjectServiceTest.java`。
- 新建：`ProjectMemberMapper.java`、`ProjectMemberMapper.xml`。
- 消费：`members(projectId, user)` 的既有服务入口、`MinioStorageService.presignedUrl`。
- 产出：`listMembers(projectId, tenantId)` 与 `listRolesByMemberIds(memberIds, tenantId)`；Mapper 使用 `@Param` 和 XML `foreach`，服务按 `member_id` 分组。

#### 操作步骤、命令和预期信号

- [ ] 在 `ProjectServiceTest` 增加成员数为 0、1、3 的场景，断言角色 Mapper 最多调用一次、返回角色按既有 `r.id` 排序、缺少角色时返回空数组。
- [ ] 运行聚焦测试，记录旧实现对 3 个成员调用 3 次角色查询的基准。
- [ ] 将成员基础 SQL 与角色 SQL 移至 XML：两个 SELECT 都包含 `tenant_id = #{tenantId}`，角色集合为空时服务不调用第二个 Mapper。
- [ ] 在 `ProjectService.members` 保留权限检查与头像 URL 装饰，仅将读取与分组替换为 Mapper 调用。
- [ ] 运行 `mvn -pl :ccb-system -am -Dtest=ProjectServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`，再运行 `mvn -pl :ccb-system -am test`。

#### 验收、证据与回滚

验收：任意成员量为固定两次或更少数据库读取，返回字段、排序、权限失败行为不变。  
回滚：撤销 T2 Mapper 和 `ProjectService.members` 的委托增量，恢复已有 JDBC 查询。  
停止条件：Mapper 无法表达现有 tenant、deleted、组织或角色排序条件，或测试发现权限检查被绕过。  
升级条件：头像 URL 生成需要异步或缓存改造才能满足目标，超出本需求范围。

### T3：建立流程定义摘要读模型并迁移定义列表

#### 需求映射与前置事实

**需求映射：** R2, R3, R5  
**前置任务：** T1  
`WorkflowService.definitions` 从 `wf_version` 相关子查询读取完整 JSON，只为计算 `requires_configuration` 后移除。普通与 Flowable 服务分别写入版本、发布和取消发布路径。

#### 文件边界与接口

- 修改：`WorkflowService.java`、`FlowableWorkflowService.java`、`WorkflowServiceTest.java`。
- 新建：`WorkflowDefinitionMapper.java`、`WorkflowDefinitionMapper.xml`、`WorkflowDefinitionSummaryProjector.java`、`WorkflowDefinitionSummaryMigrationMySqlTest.java`、`V201__workflow_definition_query_summary.sql`。
- 消费：定义列表的 `PageQuery`、现有 `hasConfigurationPlaceholder` 两种占位语义、普通与 Flowable 版本事务。
- 产出：`latest_version_no`、`requires_configuration` 摘要列；定义列表/计数 Mapper；`refresh(tenantId, definitionId, scopeType, versionNo, definitionJson)` 同事务投影器。

#### 操作步骤、命令和预期信号

- [ ] 在工作流测试构造 TEMPLATE、无占位 PROJECT、审批占位 PROJECT、抄送占位 PROJECT、发布后取消发布复制草稿五种版本状态；先断言当前列表 SQL 含 JSON、摘要没有单一写入点。
- [ ] 创建 `V201`，新增非空默认摘要列和最小索引；以每定义最大 `version_no` 回填 `latest_version_no`，以 MySQL JSON 谓词回填两种与 Java 方法等价的占位模式。
- [ ] 实现投影器：使用 ObjectMapper 解析已校验 JSON，更新同 tenant/definition 的两列；普通与 Flowable 的 create、update、publish、unpublish/复制路径都在原事务中调用。
- [ ] 实现列表与 count XML；列表只读取 `wf_definition` 摘要列并保持 scope、project、状态、排序、offset/limit 与返回字段兼容。
- [ ] 用 Testcontainers MySQL/Flyway 对迁移前后数据断言：最高版本被选中、摘要与投影器一致、无 JSON 列出现在列表 Mapper SQL。
- [ ] 运行 `mvn -pl :ccb-workflow -am -Dtest=WorkflowServiceTest,WorkflowDefinitionSummaryMigrationMySqlTest -Dsurefire.failIfNoSpecifiedTests=false test`，再运行 `mvn -pl :ccb-workflow -am test`。

#### 验收、证据与回滚

验收：列表不选择 JSON，摘要在普通/Flowable 写路径与迁移回填一致，详情读取 JSON 与授权不变。  
回滚：回退 T3 应用代码；新列保留。发现回填偏差时新建补偿迁移而不修改 V201。  
停止条件：MySQL JSON 谓词无法与 Java 占位语义一致，或有无法分类的存量 JSON。  
升级条件：需要修改 Flowable 引擎表、改变公开模型字段或把版本摘要写入其他模块。

### T4：添加低基数查询计时与运行配置

#### 需求映射与前置事实

**需求映射：** R1, R2, R3, R4, R5  
**前置任务：** T2, T3  
boot 已引入 Actuator，但当前仅暴露 `health,info`；不存在现有 `MeterRegistry`、Timer 或慢查询阈值实现。

#### 文件边界与接口

- 修改：`ProjectService.java`、`WorkflowService.java`、`application.yml`、对应 System/Workflow 测试。
- 消费：Spring Boot Actuator 自动配置的 `MeterRegistry`。
- 产出：`ccb.project.members.query`、`ccb.workflow.definitions.query` Timer；固定标签 `operation`、`scope_type`、`outcome`；`metrics` endpoint 和这两个计时器的分位数/直方图配置。

#### 操作步骤、命令和预期信号

- [ ] 为成功和 Mapper 异常场景写服务测试，断言每次记录一个 Timer，错误仍抛出既有异常且 `outcome=error`。
- [ ] 在服务中以 `Timer.Sample` 包围仅目标列表查询与结果组装；禁止将标识符、关键字或 JSON 作为 tag/log 字段。
- [ ] 在 `application.yml` 仅扩展 management endpoint exposure 至 `metrics`，并为两个精确 metric 名配置 percentile histogram 与 p95/p99。
- [ ] 启动本地 boot，调用 `/actuator/metrics/ccb.project.members.query` 与 `/actuator/metrics/ccb.workflow.definitions.query`；检查可用标签集合。
- [ ] 运行 `mvn -pl :ccb-boot -am test` 和两个模块回归；确认 endpoint 扩展未暴露 env、configprops 或日志端点。

#### 验收、证据与回滚

验收：两个 Timer 有成功/错误记录，标签集合固定且无敏感标识；metrics 端点可达且其他未授权管理端点不暴露。  
回滚：移除 T4 Timer 代码和两个精确 metrics 配置，不影响 Mapper、摘要列或 API。  
停止条件：Actuator 配置要求暴露超出 `metrics` 的管理端点，或 Timer 注入破坏既有单元构造方式。  
升级条件：需求转向集中式日志、Prometheus、Redis 或外部告警系统。

### T5：组合回归、迁移审计与运行验收

#### 需求映射与前置事实

**需求映射：** R1, R2, R3, R4, R5  
**前置任务：** T2, T3, T4  
T2 提供成员批量查询，T3 提供摘要迁移和版本同步，T4 提供可观测性；本任务不新增产品代码。

#### 文件边界与接口

- 修改：`.ai-control/requirements/req-20260911-076-query-performance/*.json`。
- 消费：T1-T4 的模块代码、Mapper XML、Flyway、Actuator 与 API 响应。
- 产出：实际执行、独立观测与收敛证据。

#### 操作步骤、命令和预期信号

- [ ] 运行 `mvn -pl :ccb-system -am test`、`mvn -pl :ccb-workflow -am test`、`mvn test`；记录测试数、退出码和 Java/Byte Buddy 兼容扰动。
- [ ] 在隔离 MySQL 8.4 环境运行全部 Flyway 迁移，查询定义摘要并与版本 JSON 的 Java 投影结果逐条比对。
- [ ] 用本地 API 验证成员角色、流程定义列表字段、项目范围拒绝路径和分页总数；不输出账户、Token 或真实业务数据。
- [ ] 验证两个 Actuator 指标的可达性、标签和样本计数，检查 `metrics` 以外端点仍未暴露。
- [ ] 运行 `node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260911-076-query-performance/codex-task-scope.yaml --base origin/main --head HEAD --working-tree` 和 `git diff --check`，记录范围与格式证据。

#### 验收、证据与回滚

验收：R1-R5 都有自动化、迁移、运行和范围证据；无跨租户、API、指标基数或迁移回填偏差。  
回滚：按 T2/T3/T4 分别回退应用增量；保留 V201 并在必要时追加补偿迁移。  
停止条件：任何项目成员/定义数据跨越 tenant，或摘要与详情 JSON 不一致。  
升级条件：隔离环境不能运行 MySQL/Flyway，或生产量级需要真实执行计划才能判断索引。

## 集成检查

- `mvn -pl :ccb-system -am test`
- `mvn -pl :ccb-workflow -am test`
- `mvn test`
- Testcontainers MySQL 8.4 的摘要迁移对照测试
- 隔离本地 API：成员列表、流程定义列表、未授权项目范围、分页
- `/actuator/metrics/ccb.project.members.query` 与 `/actuator/metrics/ccb.workflow.definitions.query`
- `node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260911-076-query-performance/codex-task-scope.yaml --base origin/main --head HEAD --working-tree`
- `git diff --check`

## 控制模型种子

以下均为 `hypotheses-only`，由 `$model-engineering-system` 验证：被控边界是 MyBatis Mapper 扫描、成员角色查询、流程摘要投影、版本写路径、Flyway 回填、Actuator 指标；状态变量候选是成员查询次数、定义摘要一致性、列表 JSON 读取、Timer 标签集和迁移版本；传感器候选是 Mockito 交互测试、Mapper 集成测试、Testcontainers/Flyway、API 探针、Actuator 输出和范围检查；执行器候选是模块 starter、Mapper XML、摘要投影器、V201、Timer 与 endpoint 配置；扰动候选是历史异常 JSON、迁移版本冲突、Flowable 写路径遗漏、Byte Buddy/JDK 兼容和数据量级差异；时延候选是事务提交后才可观察的摘要、指标采样及 Flyway 回填耗时。

## 风险与用户批准

用户已确认首期范围及自动推进后续计划。高风险动作仅限平台模块依赖、追加 Flyway、流程版本摘要和 Actuator 指标暴露；不执行生产访问、全仓迁移或接口变更。若需越出当前范围，按用户授权边界暂停说明。
