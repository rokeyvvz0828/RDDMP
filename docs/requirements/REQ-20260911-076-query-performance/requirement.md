---
id: REQ-20260911-076
status: ready
owner: rokeyvvz0828
module: platform/system, platform/workflow
---

# 项目成员与流程定义查询性能优化

## 业务目标

降低项目成员列表和流程定义列表在成员、版本数据增长后的数据库负载，同时让查询耗时、慢查询和摘要读模型状态可被观测。调用方继续使用现有接口、权限和分页语义。

## 范围

### 本次实施

- 项目成员列表使用 MyBatis Mapper/XML 批量查询成员角色，消除按成员逐行查询角色的 N+1 访问。
- 流程定义列表使用 MyBatis Mapper/XML；列表不读取或传输完整 `definition_json`。
- 在 `wf_definition` 维护最新版本号及是否需要项目配置的摘要字段，完成存量回填并在定义写路径中同事务更新。
- 增加低基数的成员列表和流程定义列表计时指标、慢查询阈值配置与聚焦回归测试。

### 本次不实施

- 不拆分 `ProjectService`、`WorkflowService`，不迁移其余 `JdbcTemplate` 写操作或查询。
- 不引入 Caffeine、Redis、游标分页、Outbox、异步附件删除或新业务模块。
- 不修改公开 REST API、前端页面字段、JWT、RBAC、项目数据范围或附件删除行为。

## 现状与规则

- `ProjectService.members` 当前先查询成员，再为每名成员查询角色；权限由服务端 `member:read` 和项目访问校验执行。
- `WorkflowService.definitions` 当前读取最新版本完整 JSON，仅用于计算 `requires_configuration`，响应前又移除该字段。
- `wf_definition.current_version` 表示已发布版本；草稿或复制版本的最新版本需由本次新增摘要字段明确表达。
- 所有 Mapper 查询必须包含租户条件；服务层继续负责认证、RBAC、项目范围、审计和响应组装。

## 接口与数据

- API/事件契约：成员列表与流程定义列表的路径、参数、分页结果和响应字段保持不变。
- 数据 Owner：项目成员和角色为 platform/system；流程定义和版本为 platform/workflow；Flyway 文件为 platform/infrastructure。
- 数据库迁移与存量兼容：追加迁移新增 `latest_version_no`、`requires_configuration` 并由最新 `wf_version` 回填；回退应用代码时字段可保留，旧代码忽略新增列。
- 脱敏输入输出示例：指标标签仅含固定操作名、流程范围和结果，不含用户、租户、项目、流程编号或正文。

## 验收标准

1. 项目成员列表对任意成员数执行一次成员查询和一次角色批量查询，不再按成员逐行访问数据库；现有排序、角色顺序、头像 URL 与权限结果不变。
2. 流程定义列表 SQL 不选取 `definition_json`，响应仍返回正确的 `requires_configuration`、分页总数、范围和权限结果。
3. 定义创建、编辑、复制、发布等受影响版本写路径在同一事务内更新摘要；迁移后既有定义的摘要与最新版本 JSON 解析结果一致。
4. `/actuator/metrics` 可读取两个查询计时指标；标签不含高基数或敏感业务标识，查询异常仍保持既有 API 错误行为。
5. System 与 Workflow 聚焦测试、迁移集成测试、Maven 完整测试和本地 API 验证通过。

## 测试与发布

- 必须执行的测试：`mvn -pl :ccb-system -am test`、`mvn -pl :ccb-workflow -am test`、`mvn test`、Flyway MySQL 迁移测试、Mapper 查询和摘要同步聚焦测试。
- 上线验证：观察成员列表和流程定义列表 p95/p99、慢查询日志与指标标签；抽样对比摘要字段和最新流程版本的配置占位标识。
- 回退或补偿：回退应用提交即可恢复旧查询路径；新增摘要列保留。若迁移回填错误，追加补偿迁移重算摘要，禁止修改已发布脚本。
- 风险与人工复核人：platform/system、platform/workflow、platform/infrastructure Owner `rokeyvvz0828`；重点复核租户条件、JSON 回填、版本写路径和指标基数。
