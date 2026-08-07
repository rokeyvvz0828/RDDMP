---
id: REQ-20260807-011
status: ready
owner: rokeyvvz0828
module: workflow
---

# Workflow 菜单可见性修复

## 业务目标

让已交付的 workflow 功能在当前工程的顶级导航中可见，并确保直接访问 `/workflow` 时进入流程定义页面。

## 现状与原因

- `web/src/router/index.ts` 已有 `workflow/:section`，但缺少 `/workflow` 根路径重定向。
- 本地数据库的 workflow 根菜单 `id=200` 当前 `parent_id=100`，被挂在系统目录下；仓库初始化设计要求它是顶级目录。
- workflow 菜单、管理员角色绑定和 Flyway V5-V27 均存在，后端业务实现无需补充。

## 范围

- 新增 `/workflow` 到 `/workflow/definitions` 的前端重定向。
- 通过追加式 Flyway 迁移将既有租户的 workflow 根菜单恢复为顶级菜单。
- 验证菜单树、后端 workflow 测试和前端构建。

## 不实施

- 不修改 workflow 服务、控制器、模型、Flowable 运行逻辑或现有 Flyway 脚本。
- 不绕过后端认证、角色菜单绑定和数据权限。
- 不调整当前工程模块目录和包路径。

## 验收标准

1. 登录后 `/auth/routes` 返回 `id=200` 作为顶级 workflow 目录，并包含 definitions、inbox、monitor 子菜单。
2. 访问 `/workflow` 自动进入 `/workflow/definitions`。
3. Flyway V28 成功应用，workflow 后端测试和前端生产构建通过。
4. 既有工程功能和工作区未产生无关变更。

## 回退

回退本需求提交即可移除路由重定向和 V28；数据库回退需使用经审批的补偿迁移，不修改已发布迁移脚本。
