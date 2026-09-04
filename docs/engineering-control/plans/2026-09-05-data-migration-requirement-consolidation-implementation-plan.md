# REQ-031/REQ-064 归并与 schema 4 收敛实施计划

> 执行要求：使用 `$control-engineering` 在 REQ-031 既有高保证账本内纠正。原 schema 5 状态必须先保留快照；不得通过直接改完成标志绕过门禁。

**目标：** 将 REQ-064 作为数据迁移模型治理增量并入 REQ-031，收缩写入边界，并把 REQ-031 活动账本转换为可由当前控制脚本验证的 schema 4。

**架构：** REQ-031 是唯一活动需求和控制前缀；REQ-064 的需求、scope 与独立账本保留为历史证据。转换只改变治理元数据，不修改业务代码、数据库、权限、审计、附件或外部访问行为。

**技术栈：** JSON 兼容 YAML、control-engineering schema 4、Node.js 治理检查、Maven/JUnit、Vue/Vite、Flyway。

## 全局约束

- 只修改数据迁移模块、数据迁移命名的精确 Flyway 文件、两份需求及其工程控制文档和账本。
- 不修改 `server/src/modules/requirement`、`server/src/modules/architecture`、`server/src/platform` 实现、公共前端组件、治理脚本或 control-engineering 插件。
- 保留用户和历史任务已有修改；不提交、不推送、不发布。
- 已接受反馈只能在原失败检查和必要回归通过后关闭。

### T45：需求和范围逻辑归并

**需求映射：** REQ-031 R2/R3/R5/R7/R8，REQ-064 R1-R5

**前置任务：** 无

**文件：**

- 修改：`docs/requirements/REQ-20260820-031-data-migration-asset-library-v3/requirement.md`
- 修改：`docs/requirements/REQ-20260820-031-data-migration-asset-library-v3/codex-task-scope.yaml`
- 修改：`docs/requirements/REQ-20260903-064-data-migration-intermediate-table-canonicalization/requirement.md`
- 修改：`docs/requirements/REQ-20260903-064-data-migration-intermediate-table-canonicalization/codex-task-scope.yaml`

**接口：** REQ-031 成为唯一活动 scope；REQ-064 通过 `merged_into` 指向 REQ-031。

- [ ] 将 REQ-064 加入 REQ-031 `merged_requirements`，把 R1-R5、V169、回退和历史证据写入 REQ-031。
- [ ] 将 REQ-031 `writable_paths` 收缩到数据迁移代码、命名迁移、数据迁移文档和两个账本目录。
- [ ] 保留 `.gitignore` 与 `backend-dev.sh` 仅用于覆盖分支既有历史差异，本任务不得修改二者。
- [ ] 运行 REQ-031 scope 检查，预期全部当前差异可归属且无非数据迁移新增修改。

**回滚：** 恢复本任务开始前的四份需求/scope 文件；REQ-064 历史账本不删除。

**停止条件：** 范围检查出现需求管理、架构、平台实现、公共组件或控制脚本的新改动。

**升级条件：** 必须修改非数据迁移所有权文件才能完成归并。

### T46：schema 4 转换与阻塞反馈复验

**需求映射：** REQ-031 工程控制账本规范

**前置任务：** T45

**文件：**

- 新建：`.ai-control/requirements/req-20260820-031-data-migration-asset-library-v3/schema5-state-snapshot.json`
- 修改：`.ai-control/requirements/req-20260820-031-data-migration-asset-library-v3/state.json`
- 新建：`.ai-control/requirements/req-20260820-031-data-migration-asset-library-v3/schema4-conversion.json`
- 新建：`.ai-control/requirements/req-20260820-031-data-migration-asset-library-v3/execution-T46.json`
- 新建：`.ai-control/requirements/req-20260820-031-data-migration-asset-library-v3/observation-T46.json`

**接口：** 活动 `state.json` 使用 schema 4；快照保存转换前 schema 5 全量内容。

- [ ] 将活动状态从错误的 `converged` 恢复为 `correcting`，记录转换原因并移除 schema 5 专属顶层派生字段。
- [ ] 运行问题清理、Issue XLSX、权限/项目范围、前端构建和中间表 MySQL 迁移传感器。
- [ ] 仅依据复验证据关闭 F-0010/F-0011/F-0012/F-0013/F-0016，并将 T2/T4/T19/T20 设置为 `verified`。
- [ ] 记录零严重误差采样并进入 `verifying`。

**回滚：** 用 `schema5-state-snapshot.json` 恢复转换前账本；不回退业务代码或数据库。

**停止条件：** 任一反馈的原失败行为仍可复现，或 schema 4 命令拒绝账本结构。

**升级条件：** 需要修改 control-engineering 脚本或非数据迁移模块才能通过门禁。

### T47：最终收敛验收

**需求映射：** REQ-031 R1-R8、REQ-064 R1-R5

**前置任务：** T46

**文件：**

- 修改：`.ai-control/requirements/req-20260820-031-data-migration-asset-library-v3/convergence.json`
- 修改：`.ai-control/requirements/req-20260820-031-data-migration-asset-library-v3/state.json`

**接口：** `control_loop.py gate` 返回 0，账本通过脚本从 `verifying` 转移到 `converged`。

- [ ] 运行聚焦 Maven、前端构建、Flyway、治理、scope 和 `git diff --check`。
- [ ] 生成最新 ConvergenceReport，明确范围、限制、残余风险和未执行验证。
- [ ] 通过 `record-artifact`、`gate` 和 `transition` 完成收敛，不手工设置最终阶段。

**回滚：** 删除本次新增收敛证据并恢复转换快照；业务实现不变。

**停止条件：** 任一 P0/P1、未关闭已接受反馈、未验证任务或范围越界仍存在。

**升级条件：** 门禁只能通过修改插件、其他模块或弱化验收标准。
