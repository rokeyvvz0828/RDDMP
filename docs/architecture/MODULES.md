# RDDMP 模块与所有权

机器可读事实源是 `governance/modules.yaml`。本文只解释边界，不重复维护路径和依赖清单。

## 后端分层

| 层级 | 目录 | 责任 |
| --- | --- | --- |
| 组合根 | `server/src/platform/boot` | Spring Boot 启动、模块装配、统一 Web 与观测入口 |
| 平台 | `server/src/platform/infrastructure` | MySQL/Flyway、MinIO 与基础设施适配 |
| 平台 | `server/src/platform/file-preview` | 受控文件上传、预览源校验与 kkFileView 适配 |
| 平台 | `server/src/platform/security` | JWT、认证、会话、RBAC 与安全过滤器 |
| 平台 | `server/src/platform/system` | 组织、用户、角色、菜单、参数、站内消息通知和系统管理 |
| 平台 | `server/src/platform/workflow` | 流程模型、BPMN 编译、运行服务与监控 |
| 公共 | `server/src/shared/common` | 统一响应、分页、异常和 trace，不拥有业务数据 |
| 业务 | `server/src/modules/project` | 项目主数据、成员角色、归档状态和公开 Project Context 范围契约 |
| 业务 | `server/src/modules/ai` | AI 模型、路由、能力执行与审计接入 |

`boot` 可以组合全部模块；其他 platform/shared 不得反向依赖具体业务模块。system 和 workflow 是后续业务统一复用的平台能力；业务模块按清单依赖 common 与 platform 能力，不直接访问其他业务模块内部实现。

## 前端边界

当前前端仍是单 Vue 应用和按技术类型组织的目录。迁移到 `web/src/modules/<module>` 前，不为页面虚构目录边界：

- `web/src/api/{system,workflow,ai}.ts` 是相应后端域的请求入口。
- `web/src/components/workflow` 和 `WorkflowView.vue` 属于 workflow。
- `web/src/modules/delivery-showcase` 是纯前端虚构交付示范模块，使用本地 mock 数据沉淀列表、表单、详情、审批和可视化样式，不拥有后端业务数据。
- `web/src/components/ui`、router、stores、主题和通用类型属于前端公共能力。
- `web/src/api/file-preview.ts` 与 `UiFilePreview.vue` 提供统一文件预览契约，业务页面不得直接拼接 kkFileView 地址或提交任意外部 URL。
- `com.ccb.system.notification` 与 `web/src/api/notifications.ts` 提供租户隔离的站内消息发布和当前用户消息中心契约，业务模块不得直接写通知表。
- `com.ccb.system.model.UserDirectory` 由 system 提供租户内启用用户的最小只读摘要；project 等消费方不得读取 `sys_user`。
- `com.ccb.project.api.ProjectContextPort` 与 `web/src/api/projects.ts` 由 project 提供项目摘要、成员关系和项目动作范围；消费方不得读取 `pm_*` 私有表，也不得用该范围替代自身平台 RBAC。
- 其余业务页面的归属以 `modules.yaml` 中列出的精确路径为准。

公共前端能力变更需要说明现有页面回归范围。后续目录重构必须独立立项，保持路由、类型、接口和业务逻辑兼容。

## 契约原则

现阶段公开 Java 包按既有调用基线登记，避免为了治理改动原包名。新增跨模块能力应优先收敛为稳定 DTO/服务接口，并在 `modules.yaml` 登记；逐步缩小公开面，而不是扩大整个实现包的可见性。
