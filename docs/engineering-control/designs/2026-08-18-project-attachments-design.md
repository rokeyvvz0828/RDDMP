# 项目详情附件与在线预览 工程设计

## 设计状态

- 修订：2
- 状态：已确认
- 用户确认：用户确认采用“可复用持久附件平台 + 项目附件页签”方案。

## 目标与边界

项目详情提供可持久化的项目附件维护能力。附件模块拥有对象存储和附件元数据；项目模块拥有项目访问授权和业务绑定入口；前端只消费业务接口和统一预览组件。

本次补充确认项目级自定义分类：分类由附件平台按租户和项目隔离维护，附件使用可空分类引用，空值统一呈现为“未分类”。

现有 `file-preview` 继续只处理临时预览文件，本设计通过新增公开 `FilePreviewUrlProvider` 复用 kkFileView URL 生成，不改变临时预览接口。

## 用户路径

项目成员打开项目详情 -> 进入“项目附件”页签 -> 查看附件列表 -> 具备更新权限时选择文件上传 -> 上传成功后刷新列表 -> 点击预览获取短时 kkFileView 地址 -> 点击下载获取短时 MinIO 地址 -> 具备更新权限时删除。

## 方案与取舍

采用独立 `ccb-attachment` 平台模块。它比把表和对象操作塞入 `ProjectService` 多一个模块，但可以为后续发布、需求和测试业务复用，避免项目模块直接读取对象键和重复实现生命周期。项目模块通过公开 `AttachmentPort` 调用，附件模块不依赖项目模块。

## 后端契约

`AttachmentPort` 只暴露业务元数据和短时操作地址，不暴露对象键：

- `uploadAndBind(businessType, businessId, tenantId, uploaderId, MultipartFile)` -> `AttachmentItem`
- `uploadAndBind(..., categoryId, tenantId, uploaderId, MultipartFile)` -> `AttachmentItem`
- `listCategories(businessType, businessId, tenantId)` / `createCategory(...)` / `updateCategory(...)`
- `list(businessType, businessId, tenantId)` -> `List<AttachmentItem>`
- `preview(attachmentId, businessType, businessId, tenantId)` -> `AttachmentLink`
- `download(attachmentId, businessType, businessId, tenantId)` -> `AttachmentLink`
- `delete(attachmentId, businessType, businessId, tenantId)` -> void

项目接口固定使用 `businessType=PROJECT` 和 `businessId=projectId`。项目 `ProjectService` 在调用前执行现有项目权限和实体授权检查；附件服务再次校验租户、业务类型、业务主键和附件状态。

## 数据模型

V61 已新增 `sys_attachment`；本次 V82 新增 `sys_attachment_category`，并在附件表增加可空 `category_id`。分类唯一/索引覆盖租户、业务类型、业务主键和删除状态；对象键仅服务端使用。

上传顺序为校验 -> 写 MinIO -> 写已绑定元数据；元数据写入失败时删除刚写入的对象并返回失败。删除顺序为逻辑删除 -> 删除对象，删除对象失败保留逻辑状态和审计错误，接口不恢复已删除业务记录。

## 权限、审计和错误

- 列表、预览、下载：`project:project:list` + 项目成员/超级管理员范围。
- 上传、删除：`project:project:list:update` + 项目负责人/超级管理员实体授权。
- 服务端从认证用户取得租户和上传人，不信任表单字段。
- 上传成功、预览、下载、删除均记录附件操作审计。
- 文件为空、超限、扩展名不允许、项目不可见、附件不存在和预览服务失败均返回统一中文错误。

## 前端交互

在项目风险页签后增加“项目附件”页签。列表使用统一数据表和空/加载/失败状态；上传使用 `el-upload` 手动提交，上传中禁用重复操作。预览调用业务预览接口得到短时地址后打开 `UiFilePreview`，不将地址长期写入项目状态。下载使用短时地址创建临时链接。移动端使用单列信息和可滚动操作区，禁止页面横向溢出。

附件工具栏提供新建分类和上传分类选择；已有附件的更新权限成员可直接切换分类，未选择分类显示为“未分类”。

## 分类布局优化

为降低分类导航、文件检索和维护操作之间的视觉干扰，项目附件页采用“分类侧栏 + 附件浏览区”的工作区布局：左侧固定展示全部附件、未分类和项目自定义分类，右侧展示当前分类、分类下拉筛选、文件名检索和附件列表。上传统一归入未分类，当前页面不提供上传时指定分类或已有附件改分类入口。附件列表按文件信息、所属分类和操作分列，预览、下载和删除统一使用带 Tooltip 的图标按钮。移动端将侧栏改为可横向浏览的分类导航，筛选和上传控件纵向排列，附件操作独占一行，避免页面级横向溢出。

## 验证与回退

验证覆盖附件单元测试、项目权限测试、迁移检查、前端构建、治理检查和真实浏览器上传/刷新/预览/下载/删除。回退应用时不删除 V61/V82；可以关闭项目附件入口，保留已绑定附件与分类作为只读数据。
