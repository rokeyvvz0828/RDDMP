---
id: REQ-20260806-002
title: System 与 Workflow 平台模块归位
status: ready
owner: zhangwei
---

# System 与 Workflow 平台模块归位

## 目标

基础框架中的系统管理和工作流由后续业务统一复用，应作为平台能力维护。将 `ccb-system` 和 `ccb-workflow` 从 `server/src/modules` 迁移到 `server/src/platform`，使 `modules` 仅承载可扩展业务模块。

## 实施范围

- 将 `server/src/modules/system` 迁移为 `server/src/platform/system`。
- 将 `server/src/modules/workflow` 迁移为 `server/src/platform/workflow`。
- 同步 Maven 聚合、模块治理清单、架构文档、CODEOWNERS 和研发模板。
- 保持 Maven artifact、Java 包名、类内容、依赖版本、接口和运行逻辑不变。

## 非实施范围

- 不合并或拆分 Maven artifact。
- 不修改 `com.ccb.system`、`com.ccb.workflow` 包名。
- 不修改数据库、Flyway、前端、权限或业务行为。
- 不追溯改写历史工程控制记录和历史实施计划中的旧路径。

## 验收标准

1. `server/src/modules` 下不再存在 `system` 和 `workflow`。
2. 根 Maven reactor 从 `server/src/platform/system` 和 `server/src/platform/workflow` 加载原 artifact。
3. 当前架构事实源不再把 system/workflow 登记为 business 模块。
4. `mvn test`、模块边界检查和仓库治理检查通过。
5. 后端健康检查与迁移前保持一致。

## 风险与回退

目录迁移可能导致 Maven 聚合、IDE 索引、CODEOWNERS 或治理路径失配。回退时将两个目录移回 `server/src/modules`，恢复对应聚合和治理路径；无需数据库补偿。
