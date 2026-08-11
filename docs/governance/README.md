# RDDMP 正式研发规约

本目录是项目通用规约的唯一正式来源：

- [Codex 协同开发文件清单与操作指引](../../CODEX-DEVELOPMENT-GUIDE.md)：研发人员如何使用 Skill、AGENTS、需求范围和项目证据参与开发。
- [项目研发规约](PROJECT-RULES.md)：架构、模块、数据、质量与发布。
- [Codex Coding 规约](CODEX-CODING-RULES.md)：Codex 准入、数据边界、任务范围与完成报告。
- [移动端适配工程手册](../../design-h5.md)：移动端页面、列表、弹框、流程图、表单和图表的工程级适配规则。
- [GitHub 协作规约](GITHUB-RULES.md)：分支、PR、审批和 CI。

长期模块边界只维护在 `governance/modules.yaml`；单任务范围只维护在对应需求目录的 `codex-task-scope.yaml`；执行与验证证据维护在 `.ai-control/`。`AGENTS.md` 和仓库 Skill 只引用或摘要这些事实源，不另建一套规则。

规约变更必须通过独立 Issue/需求、Owner 审批和治理检查。能自动检查的规则应进入 `scripts/` 与 GitHub Actions。

供应链准入参数维护在 `governance/dependency-review-config.yml`；Flyway 历史保护由 `scripts/check-flyway-migrations.mjs` 执行。不要在 Workflow 中复制一套不同阈值。
