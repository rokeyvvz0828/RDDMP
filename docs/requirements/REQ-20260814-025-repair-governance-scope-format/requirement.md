---
id: REQ-20260814-025
status: ready
owner: rokeyvvz0828
module: governance
---

# 修复历史任务范围格式门禁

## 目标

修复历史任务范围和账本与当前治理规则不兼容的问题：将 `REQ-20260810-018`、`REQ-20260811-019` 和 `REQ-20260811-020` 的任务范围等价转换为 JSON 兼容 YAML；统一相关账本 `topic` 与目录前缀；规范旧纠偏证据文件名；并将 `REQ-20260810-018` 的公共能力追踪值改为正式需求编号。

## 验收标准

1. 三份任务范围转换前后的字段和值保持一致，唯一语义纠正为 `REQ-20260810-018` 的 `public_capability_change.issue=REQ-20260810-018`。
2. 文件可以由 JSON 解析器读取。
3. `node scripts/check-all-governance.mjs` 不再因该文件格式失败。
4. `req-...` 账本目录中的 `topic` 与目录名一致，旧纠偏证据使用允许的 `observation-correction.json` 文件名。
5. 不修改产品代码、历史执行结论或需求目标。

## 回退

恢复原 YAML 表达；不会影响产品数据和运行状态。
