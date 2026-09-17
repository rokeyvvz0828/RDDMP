# 项目附件删除 Outbox 异步清理实施计划

> 执行要求：使用 `$control-engineering` 按任务实施；本计划是候选控制输入，导入后仍须完成需求定标、系统建模、受控执行与独立观测。

**目标：** 将项目删除的对象存储删除移出数据库事务，并以可重试 Outbox 保证最终清理。

**架构：** `AttachmentPort` 提供一次按项目批量登记的端口；适配器在当前事务内软删除并写事件；独立附件工作器通过租约领取、在事务外删对象，再写最终状态。

**技术栈：** Java 17、Spring Transaction/Scheduling、JdbcTemplate、MinIO 适配、MySQL 8.4、Flyway、JUnit 5。

## 全局约束

不改项目删除权限或审计；不在项目事务内调用 `MinioStorageService.delete`；新表和索引仅追加；Outbox 记录不含凭据和文件内容；失败可重试且可从过期租约恢复。

---

### T1：事务内批量登记项目附件清理

**需求映射：** R1, R3, R4

**前置任务：** 无

**文件：**
- 修改：`server/src/platform/attachment/src/main/java/com/ccb/attachment/model/AttachmentPort.java`
- 修改：`server/src/platform/attachment/src/main/java/com/ccb/attachment/service/ProjectAttachmentPortAdapter.java`
- 修改：`server/src/platform/system/src/main/java/com/ccb/system/project/ProjectService.java`
- 修改：`server/src/platform/system/src/test/java/com/ccb/system/project/ProjectServiceTest.java`

**接口：**
- 消费：项目、租户和 `AttachmentPort`。
- 产出：`enqueueBusinessDeletion("PROJECT", projectId, tenantId)`，在调用事务内写 Outbox 并软删除全部附件。

- [ ] 扩展项目删除测试，验证不再逐页调用 `list/delete`，而是一次登记端口调用。
- [ ] 在端口和适配器实现按业务范围的 `INSERT ... SELECT` 事件登记与软删除，保证重复调用无重复事件。
- [ ] 用单一端口调用替换 `ProjectService.delete` 中的分页循环；保留剩余关联数据处理和审计顺序。
- [ ] 运行：`mvn -pl :ccb-system -am -Dtest=ProjectServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`。

**回滚：** 恢复原端口实现与项目删除调用；不要删除已登记事件。

**停止条件：** 无法确保插入事件和附件软删除处于同一事务，或项目范围条件丢失。

**升级条件：** 其他附件业务消费者需要在同一变更中改为 Outbox。

### T2：Outbox 表与可租约重试工作器

**需求映射：** R1, R2, R3

**前置任务：** T1

**文件：**
- 新建：`server/src/platform/infrastructure/src/main/resources/db/migration/V205__project_attachment_cleanup_outbox.sql`
- 新建：`server/src/platform/attachment/src/main/java/com/ccb/attachment/service/ProjectAttachmentCleanupOutboxService.java`
- 测试：`server/src/platform/attachment/src/test/java/com/ccb/attachment/service/ProjectAttachmentCleanupOutboxServiceTest.java`

**接口：**
- 消费：`att_project_cleanup_outbox`、`MinioStorageService.delete`。
- 产出：定时批量 `PENDING/RETRY/expired PROCESSING -> PROCESSING -> DONE|RETRY` 状态流转。

- [ ] 添加 V205 表、唯一键和领取扫描索引。
- [ ] 编写成功、失败重试和过期租约重新领取的单元测试。
- [ ] 实现边界为：短事务领取，事务外网络删除，短事务写完成或重试；错误限制到表字段上限。
- [ ] 运行：`mvn -pl :ccb-attachment -am -Dtest=ProjectAttachmentCleanupOutboxServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`，再验证 V205 本地迁移。

**回滚：** 停止工作器或回退其代码；事件保留可由升级版本恢复。

**停止条件：** 存储删除错误不能区分为可重试，或租约领取无法避免并发重复工作。

**升级条件：** 需要跨节点事件广播、死信队列或人工重放 UI。

### T3：集成验证与收敛证据

**需求映射：** R1, R2, R3, R4, R5

**前置任务：** T1, T2

**文件：**
- 修改：`.ai-control/requirements/req-20260911-078-project-attachment-outbox/*.json`

- [ ] 运行 System、Attachment、完整 Maven、Flyway、diff 与范围检查。
- [ ] 在本地受保护项目删除路径验证先提交元数据、后执行工作器；模拟对象存储失败并观察重试状态。
- [ ] 记录真实命令、接口状态、范围审计与环境限制。

**回滚：** 使用 T1/T2 回滚边界。

**停止条件：** 任何同步对象删除残留、无 Outbox 的附件软删除、重复未完成事件或不可恢复 PROCESSING。

**升级条件：** 本地对象存储或事务环境连续无法提供验证证据。
