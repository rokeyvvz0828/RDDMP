# 全量 MyBatis 分层迁移实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。经用户确认，先完成全部迁移实现，再统一执行模块级回归、独立观测和全量验收；不得降低任何验收门禁。

**目标：** 完成服务端 SQL 的 MyBatis Repository/Mapper 收敛并验证外部行为等价。

**架构：** 保持现有模块和包名，新增 Repository/Mapper 持久化边界；Service 保留业务、授权和事务；Controller 保留协议适配。

## 任务

### T1：建立全仓库 SQL 与分层基线

盘点所有 SQL 入口、事务注解、权限前置调用、动态条件和现有 Mapper，生成按模块的迁移清单；验收为清单覆盖所有生产 Java SQL 入口。

### T2：迁移平台模块

按 system、workflow、attachment、security、infrastructure 的模块边界建立 Repository/Mapper/XML，保留每个模块独立回退边界；全部迁移实现完成后，统一执行对应 Maven 测试以验证结果等价、权限、租户、事务和异常路径。

### T3：迁移业务模块

按 release、requirement、architecture、data-migration、test-management、ai 顺序迁移 Service 直接 SQL，保持公开 integration 契约和模块依赖不变；模块测试统一在实现完成后执行。

### T4：全量回归与接口验收

执行全量 Maven、MySQL/Flyway、权限边界、分页、写审计和核心 Controller 验收；记录失败并只对对应模块纠偏。

### T5：收敛与交付审计

执行 SQL 静态扫描、分层审计、差异检查和回滚演练；确认所有任务可追溯、无未关闭高优先级反馈后进入 converged。

## 停止条件

出现 API 响应变化、权限绕过、事务边界变化、跨模块依赖违规、无法回滚或集成环境不可用时停止当前模块并回到建模/规划阶段。
