# 工作流高增长列表游标分页实施计划

> 执行要求：使用 `$control-engineering` 按任务实施；本计划是候选控制输入，导入后仍须完成需求定标、系统建模、受控执行与独立观测。

**目标：** 新增待办、已办、实例历史的稳定游标接口，消除高增长读取的深页 `OFFSET` 和总数扫描。

**架构：** `WorkflowCursorCodec` 只负责编解码和输入校验；服务复用既有授权范围构造器并追加 seek 谓词；控制器发布三个新增接口；V204 仅追加索引。

**技术栈：** Java 17、Spring MVC、JdbcTemplate、MySQL 8.4、JUnit 5、Flyway。

## 全局约束

不修改现有页码接口、`PageResult` 或前端；保持所有租户和项目范围条件；游标不包含业务、用户、租户或权限数据；每次最多读取 `size + 1`；迁移只追加。

---

### T1：游标契约和待办/已办 seek 读取

**需求映射：** R1, R2, R3

**前置任务：** 无

**文件：**
- 新建：`server/src/platform/workflow/src/main/java/com/ccb/workflow/model/WorkflowCursorPage.java`
- 新建：`server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowCursorCodec.java`
- 修改：`server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowService.java`
- 修改：`server/src/platform/workflow/src/main/java/com/ccb/workflow/web/WorkflowController.java`
- 测试：`server/src/platform/workflow/src/test/java/com/ccb/workflow/service/WorkflowCursorPaginationTest.java`

**接口：**
- 消费：`AuthUser`、现有 `InstanceVisibility`、`WorkflowNodeLabelResolver`。
- 产出：`GET /api/workflows/inbox/seek?cursor=&size=` 和 `/done/seek?cursor=&size=`，响应 `WorkflowCursorPage<Map<String,Object>>`。

- [ ] 建立服务测试，断言查询 SQL 包含降序 seek 谓词和 `LIMIT size+1`，不含 `OFFSET`/`COUNT(*)`。
- [ ] 实现不可变 `WorkflowCursorPage(records,nextCursor,hasMore)` 与 Base64URL 时间/ID codec；无效格式抛出统一 BAD_REQUEST。
- [ ] 为 `inboxSeek`、`doneSeek` 复用当前 from/where 与投影，移除多出记录后生成下一游标。
- [ ] 在控制器绑定可选 `cursor` 和限定 `size`，不改变旧方法。
- [ ] 运行：`mvn -pl :ccb-workflow -am -Dtest=WorkflowCursorPaginationTest -Dsurefire.failIfNoSpecifiedTests=false test`。

**回滚：** 删除新增方法、契约和路由；旧页码路径不受影响。

**停止条件：** seek 条件无法与当前范围 SQL 共存，或测试发现跨租户/项目记录。

**升级条件：** 需要修改公共分页模型或已有客户端响应。

### T2：实例历史 seek 读取与复合索引

**需求映射：** R1, R2, R3, R4

**前置任务：** T1

**文件：**
- 修改：`server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowMonitorService.java`
- 修改：`server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowService.java`
- 修改：`server/src/platform/workflow/src/main/java/com/ccb/workflow/web/WorkflowController.java`
- 新建：`server/src/platform/infrastructure/src/main/resources/db/migration/V204__workflow_seek_pagination_indexes.sql`
- 测试：`server/src/platform/workflow/src/test/java/com/ccb/workflow/service/WorkflowCursorPaginationTest.java`

**接口：**
- 消费：现有实例筛选、`WorkflowProjectAccessGateway` 和 `WorkflowCursorCodec`。
- 产出：`GET /api/workflows/instances/seek`，保留实例筛选参数并新增 cursor 响应。

- [ ] 扩展测试，覆盖同时间戳 ID 续页、项目范围和附带筛选。
- [ ] 在 `WorkflowMonitorService` 按现有过滤条件构造实例 seek SQL，保持节点名称投影。
- [ ] 从 `WorkflowService` 委托并在控制器发布接口；不修改 `/instances` 原方法。
- [ ] 添加 V204：任务、动作、实例的租户/固定过滤/创建时间/ID 复合索引。
- [ ] 运行：`mvn -pl :ccb-workflow -am test`，再执行本地 Flyway 迁移验证。

**回滚：** 回退新实例 seek 方法；V204 索引保留。

**停止条件：** MySQL 迁移发现版本占用或索引无法覆盖当前稳定排序。

**升级条件：** 查询计划表明需要改变既有数据模型或项目权限聚合。

### T3：接口与回归观测

**需求映射：** R1, R2, R3, R4, R5

**前置任务：** T1, T2

**文件：**
- 修改：`.ai-control/requirements/req-20260911-077-workflow-seek-pagination/*.json`

- [ ] 运行 Workflow 聚焦模块测试、`mvn test`、`git diff --check` 与任务范围检查。
- [ ] 调用本地受保护接口完成首屏和续页，并检查非法游标的统一错误。
- [ ] 记录命令退出码、迁移版本、接口状态、范围审计和环境限制。

**回滚：** 使用 T1/T2 回滚边界。

**停止条件：** 任何游标重复、跳过、越权或迁移失败。

**升级条件：** 本地认证/迁移环境无法在多次独立尝试后提供验证证据。
