# 项目详情附件与在线预览 实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 建立可复用持久附件能力，并在项目详情提供上传、列表、预览、下载和删除页签。

**架构：** 新增 `ccb-attachment` 平台模块管理附件表和 MinIO 对象，通过公开 `AttachmentPort` 被项目模块调用。项目模块负责项目范围和既有权限校验；前端使用项目附件 API 和 `UiFilePreview`，不拼接存储地址。

**技术栈：** Spring Boot 3.4、JDK 17、Spring JDBC、MySQL/Flyway、MinIO、kkFileView、Vue 3、TypeScript、Element Plus。

## 全局约束

- 只追加 V45，不修改已发布迁移。
- 只使用服务端认证用户的租户和用户 ID；不信任客户端对象键、租户、项目或上传人。
- 不改变临时 `file-preview` 上传接口语义。
- 业务模块不直接读取附件表和 MinIO 对象键。
- 不修改 `web/src/components/ui`，复用现有 `UiFilePreview`。
- 保护工作区既有项目风险和项目管理改动。

### T1：附件平台与公开契约

**需求映射：** R2, R3, R5

**前置任务：** 无

**文件：**

- 新建：`server/src/platform/attachment/pom.xml`
- 新建：`server/src/platform/attachment/src/main/java/com/ccb/attachment/model/AttachmentItem.java`
- 新建：`server/src/platform/attachment/src/main/java/com/ccb/attachment/model/AttachmentLink.java`
- 新建：`server/src/platform/attachment/src/main/java/com/ccb/attachment/model/AttachmentPort.java`
- 新建：`server/src/platform/attachment/src/main/java/com/ccb/attachment/service/AttachmentService.java`
- 新建：`server/src/platform/attachment/src/test/java/com/ccb/attachment/service/AttachmentServiceTest.java`
- 修改：`pom.xml`、`server/src/platform/boot/pom.xml`、`server/src/platform/file-preview/.../model/FilePreviewUrlProvider.java`、`KkFileViewUrlBuilder.java`
- 新建：`server/src/platform/infrastructure/src/main/resources/db/migration/V45__persistent_project_attachments.sql`

**接口：**

- 产出 `AttachmentPort` 的上传绑定、列表、预览、下载和删除方法。
- 产出 `FilePreviewUrlProvider`，由 `KkFileViewUrlBuilder` 实现。

- [ ] 建立空文件、超限、类型、租户和对象清理失败的测试基线。
- [ ] 实现对象写入、元数据写入、短时下载地址和预览地址。
- [ ] 追加 V45 中文注释、索引和软删除字段。
- [ ] 运行 `mvn -pl :ccb-attachment -am test` 和 `node scripts/check-flyway-migrations.mjs`。

**回滚：** 删除新增附件模块代码并按部署流程保留/回退 V45，不修改历史迁移。

**停止条件：** 无法保证元数据与对象存储的一致性，或需要修改基础 MinIO 公共实现。

**升级条件：** 需要新增独立附件权限节点或改变既有 file-preview 业务语义。

### T2：项目附件业务接口

**需求映射：** R1, R2, R3, R4, R5

**前置任务：** T1

**文件：**

- 修改：`server/src/platform/system/pom.xml`
- 修改：`server/src/platform/system/src/main/java/com/ccb/system/project/ProjectController.java`
- 修改：`server/src/platform/system/src/main/java/com/ccb/system/project/ProjectService.java`
- 修改：`server/src/platform/system/src/test/java/com/ccb/system/project/ProjectServiceTest.java`
- 新建：`docs/integration/attachment-module-contract.md`
- 修改：`governance/modules.yaml`、`docs/architecture/MODULES.md`

**接口：**

- 新增 `GET/POST/DELETE /api/project/{projectId}/attachments` 和 `GET .../{attachmentId}/preview|download`。
- 项目服务对所有入口执行项目读取/更新权限和成员/负责人范围，再传入服务端租户与用户信息调用 `AttachmentPort`。

- [ ] 测试项目成员读写边界、非成员拒绝、超级管理员和跨租户附件 ID 拒绝。
- [ ] 实现中文错误、审计动作和项目删除时附件逻辑删除处理。
- [ ] 运行 `mvn -pl :ccb-attachment,:ccb-system -am test`。

**回滚：** 恢复项目接口和 Maven 依赖；保留 V45 元数据，入口可关闭。

**停止条件：** 项目服务无法在不读取附件表的前提下完成项目授权。

**升级条件：** 发现现有项目权限编码无法表达附件上传/删除要求。

### T3：项目详情附件页签

**需求映射：** R1, R2, R3, R5

**前置任务：** T2

**文件：**

- 新建：`web/src/api/attachments.ts`、`web/src/types/attachments.ts`
- 修改：`web/src/api/project.ts`、`web/src/types/project.ts`、`web/src/views/ProjectView.vue`、`web/src/styles.css`
- 只读复用：`web/src/components/ui/UiFilePreview.vue`

**接口：**

- 消费项目附件列表、上传、预览地址、下载地址和删除 API。
- 产出项目附件页签、上传状态、附件操作和预览弹窗状态。

- [ ] 增加页签并在项目详情加载后展示列表。
- [ ] 上传成功刷新列表，失败保留页面并显示中文错误，防止重复提交。
- [ ] 预览请求短时 URL 后打开 `UiFilePreview`；下载通过临时链接，不保存 URL。
- [ ] 桌面和 375px 检查列表、按钮、空/加载/失败和横向滚动。
- [ ] 运行 `npm --prefix web run build`。

**回滚：** 恢复附件 API、类型、页签和样式修改，不动公共预览组件。

**停止条件：** 现有预览组件无法承载后端短时地址，或移动端出现无法滚动/溢出。

**升级条件：** 需要改变公共 UI 组件契约。

### T4：集成验收与控制证据

**需求映射：** R1-R5

**前置任务：** T1, T2, T3

**文件：** `.ai-control/requirements/req-20260818-032-project-attachments/*.json`

- [ ] 运行 `node scripts/check-flyway-migrations.mjs`、`node scripts/check-all-governance.mjs` 和 `git diff --check`。
- [ ] 启动 MySQL、MinIO、kkFileView、后端和前端，执行成员上传/刷新/预览/下载/删除路径。
- [ ] 使用非成员和跨租户数据验证 403/拒绝，检查服务端无对象键泄露。
- [ ] 记录执行、观察、反馈和收敛证据；范围检查受 `licon` 历史任务文件影响时如实记录。

**回滚：** 保留证据，关闭附件入口并按部署回退 V45 应用兼容策略。

**停止条件：** 出现跨项目访问、对象键泄露、预览白屏或附件元数据/对象不一致。

## 依赖与采样

任务串行 `T1 -> T2 -> T3 -> T4`。T1 采样模块测试和迁移；T2 采样项目权限测试；T3 采样构建和 DOM；T4 使用 API、数据库状态、浏览器桌面/375px 三类传感器做最终验收。
