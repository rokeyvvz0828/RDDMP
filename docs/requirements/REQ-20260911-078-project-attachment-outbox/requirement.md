---
id: REQ-20260911-078
status: ready
owner: rokeyvvz0828
module: platform/system, platform/attachment
---

# 项目附件删除 Outbox 异步清理

## 业务目标

项目删除不再在数据库事务内逐项调用对象存储；事务内原子记录项目附件删除意图，提交后由可重试的 Outbox 工作器清理对象，从而缩短事务并提供失败恢复路径。

## 范围

### 本次实施

- 扩展项目附件公开端口，支持按项目范围批量登记删除意图。
- 在同一事务内将 `sys_attachment` 软删除并写入每个对象的 Outbox 事件。
- 新增带租约的定时工作器，在事务提交后删除对象；成功完成事件，失败退回重试，进程中断后的过期租约可重新领取。
- 追加 Outbox 表及唯一约束、状态和扫描索引；新增项目删除与工作器聚焦测试。

### 本次不实施

- 不修改上传、预览、下载、单附件删除或 `att_file` 临时附件清理行为。
- 不新增消息中间件、跨服务事件总线、管理界面或人工重放 API。
- 不改变项目删除权限、审计、软删除和其他关联数据清理顺序。

## 现状与规则

- 项目附件使用 `sys_attachment`，`ProjectService.delete` 当前分页列出并逐条调用 `AttachmentPort.delete`；该调用直接执行对象存储删除。
- 现有 `att_file` 清理器针对另一套持久附件模型，不能复用其数据表。
- 对象存储删除可安全重试；Outbox 事件必须保存对象键的最小副本，不能依赖已软删除的展示查询。

## 接口与数据

- API/事件契约：对外 REST 接口不变；仅 `AttachmentPort` 增加项目内部批量登记方法。
- 数据 Owner：项目删除为 platform/system，附件与 Outbox 为 platform/attachment，迁移为 platform/infrastructure。
- 数据库迁移与存量兼容：V205 新建 `att_project_cleanup_outbox`；回退应用时待处理事件保留，升级后继续清理。
- 脱敏输入输出示例：Outbox 仅记录 tenant、attachment、object key、状态、次数和截断错误；日志不包含对象存储凭据或文件内容。

## 验收标准

1. 删除含多附件项目时，项目事务内不调用 `MinioStorageService.delete`，而是软删除附件并写入对应 Outbox 事件。
2. 成功工作器执行一次对象删除并将事件标记为 `DONE`；失败事件保留可重试状态、次数和截断错误。
3. 同一附件不会产生重复未完成事件；过期 `PROCESSING` 租约可被后续运行重新领取。
4. 项目删除既有认证、项目范围、审计与关联数据软删除行为不变；无附件项目仍可删除。
5. System、Attachment 聚焦测试、迁移检查、模块回归、范围检查和本地异步清理验证通过，或如实记录环境限制。

## 测试与发布

- 必须执行的测试：`mvn -pl :ccb-system -am test`、`mvn -pl :ccb-attachment -am test`、`mvn test`、Flyway V205 迁移检查。
- 上线验证：观察 Outbox 待处理/重试/过期租约数量、对象存储失败率、项目删除事务耗时和重复清理日志。
- 回退或补偿：回退应用提交不删除事件；恢复后工作器继续处理。错误事件可通过数据库状态及审计排查，禁止直接删除对象键记录。
- 风险与人工复核人：platform/system、platform/attachment、platform/infrastructure Owner `rokeyvvz0828`；重点复核事务原子性、重复领取、重试风暴与对象存储异常处理。
