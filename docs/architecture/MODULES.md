# RDDMP 模块与所有权

机器可读事实源是 `governance/modules.yaml`。本文只解释边界，不重复维护路径和依赖清单。

## 后端分层

| 层级 | 目录 | 责任 |
| --- | --- | --- |
| 组合根 | `server/src/platform/boot` | Spring Boot 启动、模块装配、统一 Web 与观测入口 |
| 平台 | `server/src/platform/infrastructure` | MySQL/Flyway、MinIO 与基础设施适配 |
| 平台 | `server/src/platform/file-preview` | 受控文件上传、预览源校验与 kkFileView 适配 |
| 平台 | `server/src/platform/attachment` | 持久业务附件、绑定授权、下载预览与孤立文件清理 |
| 平台 | `server/src/platform/security` | JWT、认证、会话、RBAC 与安全过滤器 |
| 平台 | `server/src/platform/system` | 组织、用户、角色、菜单、参数、站内消息通知和系统管理 |
| 平台 | `server/src/platform/workflow` | 流程模型、BPMN 编译、运行服务与监控 |
| 公共 | `server/src/shared/common` | 统一响应、分页、异常和 trace，不拥有业务数据 |
| 业务 | `server/src/modules/ai` | AI 模型、路由、能力执行与审计接入 |
| 业务 | `server/src/modules/release` | 投产窗口、版本申请、审批关联、投产基线、生产版本和统计分析 |
| 业务 | `server/src/modules/requirement` | 需求管理平台：新建项目差异清单、存量需求阶段、系统清单、基线与统一改动记录 |
| 业务 | `server/src/modules/architecture` | 逻辑子系统、物理子系统、关联关系与变更工单全生命周期（审批、确定性编号、引用检查）、部署单元、架构规范文档与架构决策事项/结论替代链 |

`boot` 可以组合全部模块；其他 platform/shared 不得反向依赖具体业务模块。system 和 workflow 是后续业务统一复用的平台能力；业务模块按清单依赖 common 与 platform 能力，不直接访问其他业务模块内部实现。

## 前端边界

当前前端仍是单 Vue 应用和按技术类型组织的目录。迁移到 `web/src/modules/<module>` 前，不为页面虚构目录边界：

- `web/src/api/{system,workflow,ai}.ts` 是相应后端域的请求入口。
- `web/src/api/requirements.ts`、`web/src/types/requirements.ts` 与 `web/src/views/RequirementsView.vue` 属于需求管理业务域。
- `web/src/components/workflow` 和 `WorkflowView.vue` 属于 workflow。
- `web/src/modules/delivery-showcase` 是纯前端虚构交付示范模块，使用本地 mock 数据沉淀列表、表单、详情、审批和可视化样式，不拥有后端业务数据。
- `web/src/modules/release` 与 `web/src/api/release.ts` 属于配置管理业务模块；仅项目、物理子系统和交付单元选择源可以临时使用前端 Mock，业务状态必须来自 `ccb-release`。
- `web/src/modules/architecture` 属于架构管理业务模块，承载逻辑子系统和物理子系统页面、类型与 API，不写入前端公共目录。
- `web/src/components/ui`、router、stores、主题和通用类型属于前端公共能力。
- `web/src/api/file-preview.ts` 与 `UiFilePreview.vue` 提供统一文件预览契约，业务页面不得直接拼接 kkFileView 地址或提交任意外部 URL。
- `com.ccb.attachment.model` 提供持久附件公开契约，业务模块只能通过 `AttachmentPort` 访问附件，不得读取附件表或对象键。
- `com.ccb.system.notification` 与 `web/src/api/notifications.ts` 提供租户隔离的站内消息发布和当前用户消息中心契约，业务模块不得直接写通知表。
- `business/architecture` 对外仍只公开 `com.ccb.architecture.integration`；该包提供子系统外部引用检查的中性 SPI，异常或无法判定必须按 `INDETERMINATE` 失败关闭，首期不接入真实 AI。架构模块通过 `com.ccb.workflow.integration` 接入固定审批流程（子系统变更审批、决策结论发布审批），通过 `com.ccb.attachment.integration`/`com.ccb.attachment.model`（AttachmentPort）访问附件，不访问 workflow/attachment 内部实现或表。REQ-20260823-050 增加架构规范（`architecture-standard`）与架构决策（`architecture-decision`）两个附件业务策略。
- 其余业务页面的归属以 `modules.yaml` 中列出的精确路径为准。

公共前端能力变更需要说明现有页面回归范围。后续目录重构必须独立立项，保持路由、类型、接口和业务逻辑兼容。

## 契约原则

现阶段公开 Java 包按既有调用基线登记，避免为了治理改动原包名。新增跨模块能力应优先收敛为稳定 DTO/服务接口，并在 `modules.yaml` 登记；逐步缩小公开面，而不是扩大整个实现包的可见性。
