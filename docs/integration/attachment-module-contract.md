# 持久附件模块契约

`ccb-attachment` 负责附件元数据和 MinIO 对象生命周期。调用方只能依赖 `com.ccb.attachment.model.AttachmentPort`，通过业务类型、业务主键、服务端租户和认证用户访问附件；对象键永远不进入 HTTP 响应或前端请求。

项目附件固定使用业务类型 `PROJECT`，项目服务先校验项目成员、负责人和项目权限，再调用附件端口。预览地址由 `FilePreviewUrlProvider` 生成 kkFileView 短时地址，下载地址由 MinIO 生成短时预签名地址。

上传允许常见办公文档、图片、文本和压缩包，单文件最大 100MB。删除先清理对象，再将元数据逻辑删除；上传元数据落库失败时清理已写对象。Flyway `V45__persistent_project_attachments.sql` 只追加，不修改历史迁移。
