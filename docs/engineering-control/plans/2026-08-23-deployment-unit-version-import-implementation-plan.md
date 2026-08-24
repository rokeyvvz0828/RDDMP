# 部署单元版本与初始化导入 — 实施计划（REQ-20260823-049）

## 任务拆分与依赖

| 任务 | 目标 | 写入范围 | 验收检查 | 回退 |
| --- | --- | --- | --- | --- |
| T1 迁移与种子 | V85 建表（unit/version/number_seq/import_batch/import_item），V86 菜单 804/805、权限 8041/8042、角色 111 与映射 | 两个 SQL 文件 | 空库迁移至 V86 幂等；既有迁移不被修改 | 保留迁移，补偿迁移关闭入口 |
| T2 后端领域 | 编号服务、Store、生命周期服务、引用 SPI、导入服务、控制器、选项 | `server/src/modules/architecture/src/main/**` | 领域测试 + MySQL 测试通过 | 逆序回退服务代码 |
| T3 后端测试 | 编号/生命周期/导入领域测试与 MySQL 集成测试 | `server/src/modules/architecture/src/test/**` | `mvn -pl :ccb-architecture -am test` | 回退测试 |
| T4 前端 | types/api/utils、DeploymentUnitPage、DeploymentUnitImportPage、路由 | `web/src/modules/architecture/**`、`web/src/router/index.ts` | `npm --prefix web run build` | 回退页面与路由 |
| T5 契约与验证 | architecture-module-contract.md、MODULES.md 描述同步；治理/范围/Flyway/模块边界/全量 Maven 检查 | 文档 + 全仓检查 | 全部检查命令 exit 0 | 回退文档 |

依赖：T1 → T2 → T3；T2 → T4；T4+T3 → T5。

## 关键实现要点

- 编号：`arch_deployment_unit_number_seq` 行锁分配，格式 `D<物理编号><%03d>`；
  唯一索引 `uk_arch_deployment_unit_code` 兜底。
- 版本：创建=版本 1；更新 ACTIVE 追加版本 N+1；版本无更新/删除 API。
- 作废：引用 SPI 聚合 + fail-closed。
- 导入：POI 解析 → 预览批次 → 确认逐行事务；行级失败明细；批次级回滚；
  `(physical, name)` 幂等跳过。
- 权限：写操作 `@PreAuthorize("hasAuthority('architecture:deployment-unit:manage')")`；
  查询兼容既有三级权限。

## 采样点

- T1 后：迁移执行采样（空库 → V86，含 V86 种子断言）。
- T2 后：领域与 MySQL 聚焦测试采样。
- T3+T4 后：模块全量测试 + 前端构建采样。
- T5 后：全仓 Maven、治理、范围、Flyway、模块边界采样 + 独立观测。

## 停止与升级条件

- 停止：既有 V1-V84 被修改；跨模块直接写表；版本行出现更新路径；作废检查绕过引用
  守卫；前端绕过权限只靠隐藏。
- 升级：POI 版本与既有模块不一致；MySQL 约束与设计冲突；前端公共组件缺失能力。
