---
id: REQ-20260818-032
status: ready
owner: rokeyvvz0828
module: platform/attachment + platform/system + frontend/application
---

# 项目详情附件与在线预览

## 业务目标

项目成员在项目详情中集中维护项目附件，能够上传、查看列表、在线预览、下载和删除文件；文件保存到 MinIO，预览复用 kkFileView，访问范围遵循项目成员和超级管理员规则。

## 范围

### 本次实施

- 新增可复用 `ccb-attachment` 平台模块，管理附件元数据、MinIO 对象和业务绑定。
- 新增项目附件 HTTP 契约，支持列表、上传、预览地址、下载地址和受控删除。
- 项目详情新增“项目附件”页签，复用统一文件预览组件。
- 项目成员和超级管理员可查看、预览、下载项目附件；项目负责人或具备项目更新权限的用户可上传和删除。
- 追加 Flyway 迁移，所有新增表和字段提供中文注释。
- 记录附件操作审计，客户端不接收或提交对象存储键。

### 本次不实施

- 不修改现有临时 `file-preview` 上传接口的生命周期语义。
- 不实现附件版本管理、全文检索、批量打包下载、分片上传或外部分享链接。
- 不开放匿名上传，不允许前端提交任意 MinIO、kkFileView 或外部 URL。
- 不改变项目成员的既有可见范围和项目权限模型。

## 现状与规则

- 当前项目详情页签包含概览、计划、风险、成员、角色和设置；项目访问由 `ProjectService.requireProjectAccess` 服务端校验。
- 当前 `file-preview` 仅用于临时上传预览，不保存业务附件元数据；本需求新增独立附件能力。
- 附件业务类型固定使用 `PROJECT`，业务主键为项目 ID，租户 ID 和上传人由服务端认证上下文确定。
- 读取、预览和下载复用项目读取权限；上传和删除复用项目更新权限及项目负责人实体授权；超级管理员遵循现有例外规则。
- 上传成功后直接生成已绑定项目的附件记录，避免项目详情中产生无法归属的临时文件。

## 接口与数据

- `GET /api/project/{projectId}/attachments`：返回当前项目附件元数据列表。
- `POST /api/project/{projectId}/attachments`：multipart 字段 `file`，上传并绑定项目。
- `GET /api/project/{projectId}/attachments/{attachmentId}/preview`：返回短时 kkFileView 预览地址。
- `GET /api/project/{projectId}/attachments/{attachmentId}/download`：返回短时 MinIO 下载地址。
- `DELETE /api/project/{projectId}/attachments/{attachmentId}`：校验项目更新权限后逻辑删除元数据并清理对象。
- `platform/attachment` 公开 `AttachmentPort` 和 `AttachmentItem`，业务模块不得读取附件表或对象键。
- 数据库追加 `V45__persistent_project_attachments.sql`，包含租户、业务类型、业务主键、文件信息、对象键、上传人、状态、审计时间和删除标记。

## 验收标准

1. 项目详情出现“项目附件”页签，项目成员可以看到附件列表和空状态。
2. 具备项目更新权限的用户上传允许类型文件后，列表显示文件名、类型、大小、上传人和上传时间，刷新后仍存在。
3. 点击预览请求新的短时地址并打开 `UiFilePreview`，不向前端暴露对象键；预览服务不可用时显示可恢复错误。
4. 点击下载获取短时地址并下载；删除后列表移除，服务端对象和元数据按受控逻辑删除。
5. 非项目成员无法读取、预览、下载或删除其他项目附件；跨租户 ID 不能访问附件。
6. 附件服务单元测试、项目权限测试、Flyway 检查、前端构建和桌面/375px 浏览器验收通过。

## 测试与发布

- 必须执行：`mvn -pl :ccb-attachment,:ccb-system -am test`、`npm --prefix web run build`、`node scripts/check-flyway-migrations.mjs`、`node scripts/check-all-governance.mjs`、`git diff --check`。
- 上线验证：启动 MySQL、MinIO、kkFileView 和后端，使用项目成员完成上传/刷新/预览/下载/删除，使用非成员验证拒绝。
- 回退或补偿：回退应用代码后保留 V45 数据；不执行生产 DROP，必要时关闭附件入口并保留已绑定附件只读。
- 风险与人工复核人：附件对象和数据库元数据双写失败、跨项目访问、kkFileView 无法读取宿主机 MinIO 地址；复核人 rokeyvvz0828。
