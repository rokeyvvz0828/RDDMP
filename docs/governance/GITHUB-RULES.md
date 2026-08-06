# RDDMP GitHub 协作与合并规约

## 分支与 PR

- 每个需求使用独立工作区和短生命周期分支：`feat|fix|hotfix|docs|chore/REQ-YYYYMMDD-NNN-short-name`。
- 一个 PR 只交付一个细粒度需求，必须引用需求编号和任务范围文件。
- PR 说明目标模块、Codex 参与范围、公共能力、数据库、权限、审计、测试、发布与回退影响。
- 不直接推送 `main`，不强制推送，不自行批准自己的 PR，不绕过 Required Checks 或 CODEOWNERS。

## 所有权与审批

`governance/modules.yaml` 定义模块 Owner 和依赖边界，`.github/CODEOWNERS` 将目录映射为 GitHub 审批责任。公共能力、认证安全、数据库迁移、治理规则和发布工作流至少需要对应 Owner 审批；高风险变更不得由提交人自审。

主分支 Ruleset 应启用 PR、至少一名非作者审批、CODEOWNERS、对话解决、线性历史、限制强推，以及以下 Required Checks：

- 治理结构、仓库 Skill、Codex 任务范围和模块依赖检查。
- Maven 测试与打包、前端类型检查和生产构建。
- 密钥扫描、依赖漏洞扫描；发布时增加镜像扫描和软件物料清单。

## 规约变更

规约、模块边界、CODEOWNERS、CI 或仓库 Skill 变更必须关联治理 Issue，更新唯一事实源并同步必要摘要。个人聊天记录、临时提示词和本地配置不构成团队规则。
