---
id: REQ-20260814-023
status: ready
owner: rokeyvvz0828
module: governance
---

# 治理变更关联正式需求

## 目标

将规约、模块边界、CODEOWNERS、CI、仓库 Skill 和公共能力变更的追踪依据统一为已批准需求编号，不再要求单独创建 GitHub Issue。

## 范围

- 修改 GitHub 协作规约中的治理变更追踪要求。
- 同步 Codex 编码规约、开发指引和 PR 模板中的相关摘要。
- 保留 `public_capability_change.issue` 兼容字段；该字段继续填写 `REQ-...` 需求编号，暂不实施全仓结构迁移。

## 验收标准

1. 正式规约明确要求关联状态为 `ready` 的需求编号及任务范围文件。
2. Codex 开发指引和 PR 模板不再要求填写治理 Issue。
3. 仓库中除兼容字段名和 GitHub 模板配置外，不再出现“关联 Issue”或“治理 Issue”文案。
4. 治理检查和差异检查通过，或明确记录与本次无关的既有阻塞。

## 非范围

- 不迁移历史任务范围中的 `public_capability_change.issue` 字段。
- 不修改 GitHub Issue 功能或仓库 Ruleset。
- 不修改产品代码、数据库或运行配置。

## 回退

恢复四处文案即可，不涉及数据补偿。
