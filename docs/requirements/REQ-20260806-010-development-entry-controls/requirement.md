---
id: REQ-20260806-010
status: ready
owner: rokeyvvz0828
module: governance
---

# 项目研发入口与业务前端设计准入

## 业务目标

让后续研发任务在进入编码前完成项目契约阅读、工程控制插件安装和 Skill 准入；让业务功能优先复用交付示范中心已经验证的前端结构与样式，降低页面风格分裂和重复实现。

## 范围

### 本次实施

- 在根目录研发入口中强制说明首次阅读、插件安装、每次任务 Skill 和前端设计准入。
- 在正式前端契约中声明交付示范中心优先原则和新样式例外记录要求。
- 增加开发入口检查脚本，校验契约文件、仓库 Skill、交付示范中心和可检测的 Codex 插件状态。
- 安装并启用 `control-engineering@control-engineering-local` 插件。

### 本次不实施

- 不将外部插件源码复制到 RDDMP 仓库。
- 不修改业务页面、后端业务逻辑、数据库和运行时 API。
- 不以脚本替代 Owner 审批、需求评审、PR Required Checks 或人工验收。

## 现状与规则

- 当前已有根 `AGENTS.md`、`CODEX-DEVELOPMENT-GUIDE.md`、仓库 `rddmp-delivery-engineer` Skill 和交付示范中心。
- 外部 `control-engineering-skills` 以 Codex 插件形式提供闭环研发 Skill。
- 规则例外必须可审计，记录于需求设计或当前 `.ai-control` 任务证据。

## 接口与数据

- 新增本地检查命令：`node scripts/check-development-entry.mjs [--require-plugin]`。
- 不新增业务 API、数据库表、字段或迁移。
- 插件安装状态由 Codex CLI 查询，不写入业务数据。

## 验收标准

1. 根入口、首次启动手册、前端目录规则和正式 UI 契约均明确两条准入要求。
2. 开发入口检查能验证入口文件、仓库 Skill、交付示范中心和当前可用 Codex 插件。
3. 指定插件安装结果为 `installed=true enabled=true`。
4. 治理检查、脚本语法检查和现有前端构建通过，且不修改业务代码逻辑。

## 测试与发布

- 必须执行：`node scripts/check-development-entry.mjs --require-plugin`、`node scripts/check-all-governance.mjs`、`git diff --check`、`npm --prefix web run build`。
- 上线验证：新建 Codex 任务后重新读取入口文件并确认插件可被发现；业务页面任务检查交付示范中心复用记录。
- 回退：回退本需求提交即可移除入口检查和文档约束；外部插件按 Codex CLI 单独卸载，不影响业务运行。
- 风险与人工复核人：规则属于治理和公共契约变更，由项目 Owner `rokeyvvz0828` 复核。
