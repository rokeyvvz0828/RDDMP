# 持久附件模块接入规范

## 边界

`ccb-attachment` 拥有持久附件元数据、MinIO 对象身份、TEMP/BOUND/DELETED 状态、业务绑定、访问控制、操作日志和清理重试。业务模块只保存附件 ID，不保存或返回对象键，也不得直接写 `att_*` 表。

## 生命周期

1. 当前登录用户通过 `POST /api/attachments` 上传，平台返回 TEMP 附件 ID。
2. 业务数据保存成功后，业务服务调用 `AttachmentGateway.bind` 绑定业务类型和业务主键。浏览器无绑定接口。
3. TEMP 附件仅上传人可读、预览、下载和删除。
4. BOUND 附件必须由对应业务模块提供唯一 `AttachmentAccessPolicy`。策略缺失、重复、抛错或明确拒绝时一律拒绝访问。
5. 已绑定附件只能由业务服务在完成业务状态授权后调用 `deleteBound`；删除先保留逻辑证据，再异步清理对象。
6. 超期 TEMP 和待清理 DELETED 对象由幂等批任务处理，存储故障进入 RETRY，不删除仍处于 BOUND 的对象。

## 公共契约

- `com.ccb.attachment.integration.AttachmentGateway`
- `AttachmentBindingCommand`、`AttachmentItem`
- `AttachmentAccessPolicy`、`AttachmentOperation`
- `web/src/api/attachments.ts`

预览继续复用 `FilePreviewUrlProvider` 和 kkFileView 的可信源校验；不支持预览的业务材料仍可在授权后取得短时下载地址。
