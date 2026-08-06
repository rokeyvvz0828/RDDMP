# 参与 RDDMP 开发

首次参与项目，请先阅读 [Codex 协同开发文件清单与操作指引](CODEX-DEVELOPMENT-GUIDE.md)，了解仓库 Skill、分层 `AGENTS.md`、需求范围和验证证据的使用方式。

## 日常流程

1. 从 `docs/requirements/TEMPLATE.md` 创建 `docs/requirements/REQ-YYYYMMDD-NNN/requirement.md`，经评审后将状态改为 `ready`。
2. 从 `docs/requirements/codex-task-scope.template.yaml` 创建同目录 `codex-task-scope.yaml`，明确模块、Owner、可写/只读/禁止路径、`.ai-control` 任务前缀、风险和测试。
3. 创建短生命周期分支：`feat|fix|hotfix|docs|chore/REQ-YYYYMMDD-NNN-short-name`。
4. 阅读根目录及目标目录最近的 `AGENTS.md`，按 `.agents/skills/rddmp-delivery-engineer/SKILL.md` 实施。
5. 运行治理检查、目标模块测试、后端测试及前端构建；只把证据写入 `.ai-control/` 当前任务前缀记录。
6. 创建 PR，关联需求编号，说明范围、Codex 参与、权限/数据/迁移影响、测试、发布和回退。

## 常用检查

```bash
node scripts/check-all-governance.mjs
mvn test
npm --prefix web run build
```

单任务范围检查：

```bash
node scripts/check-codex-scope.mjs \
  --scope docs/requirements/REQ-YYYYMMDD-NNN/codex-task-scope.yaml \
  --base origin/main --head HEAD --working-tree
```

正式规则见 `docs/governance/README.md`。不要直接推送 `main`，不要提交密钥、生产数据、`target/`、`node_modules/` 或本地环境文件。
