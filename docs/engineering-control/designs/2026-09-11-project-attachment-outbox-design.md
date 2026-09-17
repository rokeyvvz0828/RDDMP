# 项目附件删除 Outbox 异步清理设计

## 目标与边界

项目删除只在数据库事务内软删除 `sys_attachment` 并记录对象删除意图；对象存储调用移到提交后的定时工作器。上传、预览、下载、单附件删除、`att_file` 的既有临时清理器和消息中间件均不在本次范围。

## 方案选择

推荐使用单表 Outbox 加租约工作器。事务内执行 `INSERT ... SELECT` 将每个对象键写入 Outbox，再软删除附件；唯一键确保重复调用没有重复活跃事件。工作器原子领取事件，进行对象删除，完成或按失败状态重试。相比在 `afterCommit` 直接删除，该方案跨进程失败可恢复；相比新增消息系统，依赖更小且适合当前单体部署。

## 数据与执行流

```text
项目删除事务
  -> sys_attachment: deleted=1
  -> att_project_cleanup_outbox: PENDING
  -> commit
定时工作器
  -> claim PROCESSING + lease
  -> MinIO delete (事务外)
  -> DONE | RETRY
```

`att_project_cleanup_outbox` 保存 tenant、attachment、object key、状态、尝试次数、租约、截断错误和时间戳。领取条件为 `PENDING/RETRY` 或租约到期的 `PROCESSING`。事件唯一键 `(tenant_id, attachment_id)` 使项目删除重试具备幂等性。

## 兼容、权限和失败处理

- `ProjectService.delete` 继续先执行现有操作权限和项目实体授权，继续审计项目删除。
- `AttachmentPort` 新增内部批量登记方法；除了项目删除，没有 REST API 变化。
- 工作器在网络 I/O 期间不持有项目事务。成功可能面对对象已不存在，存储适配器现有删除语义决定是否作为成功；其他异常写入截断错误并进入 RETRY。
- 中断后过期租约回收，避免 `PROCESSING` 永久卡住；同一事件领取操作以条件更新保护。

## 验证与回退

测试项目删除不触发存储删除、Outbox 记录与软删除原子性、成功、失败重试、过期租约和幂等登记。回退应用代码时表与未完成事件保留，升级恢复后继续处理。

批准依据：用户已确认后续优化方向，并明确授权后续确认自动通过。
