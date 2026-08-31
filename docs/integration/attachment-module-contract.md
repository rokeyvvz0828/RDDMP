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

## 项目附件分类

项目服务通过 `AttachmentPort` 维护项目附件分类，不直接访问附件表：

- `listCategories` 和 `createCategory` 只接受服务端传入的租户、业务类型和项目 ID。
- `list` 支持可选 `categoryId` 筛选；未传表示全部分类，传 `0` 表示未分类，正数只匹配当前项目内的自定义分类。
- 项目上传接口接受可选 `categoryId`；分类不属于当前项目时拒绝，未传或传空值表示“未分类”。
- `updateCategory` 只更新同一项目内附件的分类引用，传空值可将附件重新放回“未分类”。
- 分类名称由项目更新权限成员维护，分类名称在同一项目内不可重复；“未分类”是系统保留展示名称，不创建实体分类记录。

