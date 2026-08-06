---
id: REQ-20260806-004
title: AI Control 账本目录分层
status: ready
owner: zhangwei
---

# AI Control 账本目录分层

## 目标

解决 `.ai-control` 中原始平台账本与各需求账本全部平铺、难以定位和容易误改的问题，建立可持续校验的分层目录。

## 目录规则

- `.ai-control/original/`：仅保存初始平台建设产生的无需求前缀历史账本。
- `.ai-control/requirements/<control-prefix>/`：每个需求或历史任务独立目录。
- 需求目录内使用 `design.json`、`state.json`、`control-plan.json`、`execution-Tn.json`、`observation-Tn.json`、`convergence.json` 等语义文件名。
- `.ai-control` 根目录只允许说明文档和上述两个目录，不再平铺 JSON。

## 实施范围

- 迁移全部现有账本文件，不修改其业务结论。
- 更新 Codex 指令、Skill、开发指引、任务范围模板和现有需求证据路径。
- 更新任务范围检查脚本以识别需求子目录。
- 增加账本目录结构与 JSON 可解析性检查，并纳入治理检查。

## 非实施范围

- 不改写历史执行结果、观测结论和收敛判断。
- 不修改业务代码、数据库、权限、接口或运行行为。

## 验收

1. `.ai-control` 根目录不存在 JSON 文件。
2. 10 个初始平台账本进入 `original`，其余历史账本按需求前缀进入独立目录。
3. 当前需求范围模板和检查脚本使用新目录规则。
4. 所有账本 JSON 可解析，目录结构检查和仓库治理检查通过。
5. 现有 REQ-20260806-001 至 003 的证据路径全部有效。

## 回退

按迁移映射将文件移回根目录，恢复脚本、模板和文档中的旧路径；无数据补偿。
