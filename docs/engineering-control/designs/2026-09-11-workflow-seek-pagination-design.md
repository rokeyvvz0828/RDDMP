# 工作流高增长列表游标分页设计

## 目标与边界

为工作流待办、已办审计记录和实例历史提供基于 `(created_at, id)` 的向后游标读取。原页码接口保留给管理页面，定义列表、时间线、Flowable 引擎表和前端页面均不在本次范围。

## 方案选择

推荐增加三个 `/seek` 接口，而不是改变现有 `PageResult`。这让现有消费者继续获取 `total/page/size`，而高增长消费者获得不含总数的 `records/next_cursor/has_more`。另一方案是为所有分页响应增加可选游标字段，但会让两类分页语义混在同一契约中；完全替换旧接口则会破坏管理页面和外部调用方。

游标是 Base64URL 编码的 `createdAtEpochMillis:id`，仅表示已读取的排序位置。服务在解析失败、非正 ID 或多余字段时返回参数错误；每次请求仍重新计算租户、用户及项目范围，游标不能携带或放大授权。

## 数据与执行流

```text
认证用户 + 可选 cursor
  -> 现有可见范围 / 项目范围
  -> (created_at, id) seek predicate + size + 1
  -> records + next_cursor + has_more
```

待办排序使用 `wf_task.created_at, wf_task.id`，已办使用 `wf_task_action.created_at, wf_task_action.id`，实例使用 `wf_instance.created_at, wf_instance.id`，均为降序。迁移只追加与固定范围和排序配套的复合索引。

## 兼容、权限和失败处理

- 现有 `/instances`、`/inbox`、`/done` 保持不变，包括 `COUNT(*)` 语义。
- 新接口复用当前服务的 `InstanceVisibility`、`WorkflowProjectAccessGateway` 和节点名称投影。
- SQL 读取 `size + 1`；多出记录只用于计算是否有下一页，不返回给调用方。
- 不合法游标拒绝；无结果返回空记录、`has_more=false` 和空 `next_cursor`。

## 前端与移动端

本任务不改 UI：现有页面没有消费待办或已办 API，流程监控仍使用管理页传统分页。因此不引入新页面布局、组件或移动断点；后续消费方切换 seek 接口时必须遵守 `design-h5.md` 的列表卡片与视口验收规则。

## 验证与回退

测试首屏、续页、相同时间戳、非法游标、租户/项目范围和 SQL 不含 `OFFSET`、`COUNT(*)`。本地验证两个连续请求。回退应用代码即可，V204 的附加索引可保留。

批准依据：用户已确认后续优化方向，并明确授权后续确认自动通过。
