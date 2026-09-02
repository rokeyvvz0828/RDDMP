# 测试管理公告板实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施；用户已确认设计和实施授权。

**目标：** 交付四大测试大类内可持久化、可授权、可审计的项目级公告板。

**架构：** V124 建立公告和附件映射；测试管理服务通过平台附件公开契约绑定、删除和鉴权附件，控制器提供受保护 API。Vue 页面复用全局项目上下文和现有 UI 组件，在 dashboard 动态路由前注册专页。

**技术栈：** Java 17、Spring Boot、JdbcTemplate、MySQL 8.4/Flyway、Vue 3、TypeScript、Element Plus、平台附件公开契约。

## 全局约束

- 只修改当前任务范围已授权路径；V124 仅追加，平台附件实现保持只读。
- 不重复实现项目选择器；仅新增 wangEditor 官方 Vue 3 组件依赖，不访问生产环境或敏感数据。
- 写操作执行认证、RBAC、租户/项目/大类范围校验，并写公告审计；单个附件不超过 50MB。

### T1：公告持久化、附件策略与 API

**需求映射：** A1—A5

**前置任务：** 无

**文件：**
- 新建：`server/src/platform/infrastructure/src/main/resources/db/migration/V124__test_management_announcement.sql`
- 修改：`server/src/modules/test-management/pom.xml`、`governance/modules.yaml`
- 新建：`server/src/modules/test-management/src/main/java/com/ccb/testmanagement/announcement/TestAnnouncementService.java`
- 新建：`server/src/modules/test-management/src/main/java/com/ccb/testmanagement/announcement/TestAnnouncementAttachmentPolicy.java`
- 新建：`server/src/modules/test-management/src/main/java/com/ccb/testmanagement/web/TestAnnouncementController.java`
- 测试：`server/src/modules/test-management/src/test/java/com/ccb/testmanagement/announcement/TestAnnouncementServiceTest.java`

**接口：** 消费 `AttachmentGateway`、`AttachmentAccessPolicy` 和 `AuthUser`；产出 `/api/test-management/announcements/{domain}` 的 current/list/detail/create/update/pin/delete 契约。

- [ ] 先增加服务测试：项目/大类隔离、当前公告置顶兜底、标题正文校验、编辑时间、跨范围拒绝和净化 HTML。
- [ ] 追加 V124，创建公告、附件映射、审计、菜单细粒度权限与最少管理授权。
- [ ] 实现受限 HTML、事务保存、附件绑定/移除、平台访问策略和控制器 RBAC。
- [ ] 运行 `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -Dnet.bytebuddy.experimental=true -pl :ccb-test-management -am test`，预期零失败。

**回滚：** 回退本模块代码，保留 V124 和数据。

**停止条件：** 平台公开附件契约无法绑定或没有可用附件元数据查询能力。

### T2：公告板页面、富文本编辑与路由

**需求映射：** A1—A6

**前置任务：** T1

**文件：**
- 新建：`web/src/modules/test-management/announcement/TestAnnouncementPage.vue`
- 新建：`web/src/modules/test-management/announcement/announcement.css`
- 修改：`web/src/modules/test-management/api.ts`、`web/src/router/index.ts`

**接口：** 消费 T1 API、`useProjectContextStore`、`web/src/api/attachments.ts` 和只读 UI 组件；产出 dashboard 专页。

- [ ] 在通配 dashboard 路由前注册专页；未选全局项目不请求公告。
- [ ] 实现阅读 TAB、管理 TAB 权限显隐、筛选/分页、640px 查看抽屉、720px 编辑窗、删除确认和提交/错误状态。
- [ ] 安装 wangEditor 官方 Vue 3 组件，配置成熟工具栏、临时图片/文件上传、50MB 前置校验、编辑附件移除与未保存关闭确认；渲染只使用服务端净化 HTML。
- [ ] 运行 `npm --prefix web run build`，预期 TypeScript 与 Vite 构建成功。

**回滚：** 删除专页路由，保留原动态列表；后端数据保留。

**停止条件：** 页面需要改动公共项目选择器、UI 组件或附件 API。

### T3：本地集成与独立验收

**需求映射：** A1—A6

**前置任务：** T1、T2

**文件：**
- 新建：`.ai-control/requirements/req-20260831-057-test-management-configuration/announcement-*.json`

- [ ] 对本地 MySQL 执行 V124 后验证 API 的未选项目、读取、管理权限、创建、编辑、置顶和删除路径。
- [ ] 用真实浏览器验证桌面与手机视口，不重复项目选择器，覆盖当前公告兜底、空态、管理筛选、编辑未保存确认与附件下载。
- [ ] 运行 Flyway、治理、差异检查并记录实际退出码和任何环境阻塞。

**回滚：** 回退应用代码；V124 和受控附件保留。

## 依赖与采样

T1 → T2 → T3 串行，避免迁移/API/页面契约漂移。每个任务后运行局部自动化检查，T3 使用 API 与浏览器两种独立传感器；高风险点为 V124、菜单权限和附件策略。
