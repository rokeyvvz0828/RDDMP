# 配置管理角色菜单权限设计

## 设计结论

配置管理由一个父目录和六个真实子菜单组成。子菜单的 `permission_code` 使用各自查看权限，角色权限配置仍通过动作权限勾选；保存角色权限时，现有平台逻辑自动补齐动作所属菜单及父目录。

| 子菜单 | 路由 | 查看权限 | 写权限 |
| --- | --- | --- | --- |
| 投产窗口 | `/release/windows` | `release:window:view` | `release:window:create`, `release:window:update` |
| 版本申请 | `/release/applications` | `release:application:view` | `create`, `update`, `submit`, `withdraw`, `cancel` |
| 投产基线 | `/release/production-baseline` | `release:baseline:view` | `release:baseline:update` |
| 生产版本 | `/release/production-versions` | `release:production-version:view` | 无 |
| 统计分析 | `/release/analytics` | `release:analytics:view` | 无 |
| 审批流程配置 | `/release/workflow-bindings` | `release:workflow-config:view` | `release:workflow-config:update` |

## 数据迁移

V45 将菜单 600 转为目录，新增 610 至 615 六个子菜单，保留现有动作权限 ID 并迁移其 `menu_id`。原 `release:production:view/update` 改为投产基线权限，新增生产版本查看权限 6016，并向原投产查看角色补授该权限，从而保持存量角色能力。

## 前端行为

后端动态菜单是唯一菜单来源，不再前端强制注入配置管理。静态 Vue 路由带查看权限元数据，路由守卫在刷新和直接输入地址时校验权限；`/release` 跳转到当前角色第一个可访问子菜单。模块内页签只展示有权菜单，组件只请求有权限的数据，写按钮按动作权限显隐。

## 后端行为

投产基线和生产版本接口使用拆分后的权限。投产窗口列表作为版本申请、投产基线和统计分析的必要选项数据，可由这些菜单的查看/创建权限读取；窗口详情及写操作仍仅允许投产窗口自身权限。

## 风险与回退

Flyway 为追加迁移，不能编辑或降版本；迁移前备份本地数据库，异常时恢复备份，已发布环境通过后续补偿迁移修正。前端和 Java 代码可按本需求范围独立回退。
