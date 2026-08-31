# CLB、DNS 与证书工单实施计划

适用需求：`REQ-20260823-051`。每项任务以命令与证据收口，先集成契约再集成实现，
最终由独立观测确认。

## T1 数据库迁移 V100-V101

- 产物：`V100__create_architecture_network_work_orders.sql`（4 张表 + CHECK/索引/FK）、
  `V101__seed_architecture_network_work_orders.sql`（菜单 808、权限 8081-8083、角色 113、
  兼容映射、流程草稿 900000000000032/033、身份冲突守卫）。
- 证据：`node scripts/check-flyway-migrations.mjs`；
  `mvn -pl :ccb-architecture -am -Dtest=NetworkWorkOrderMySqlTest,NetworkWorkflowIntegrationMySqlTest -Dsurefire.failIfNoSpecifiedTests=false -Dapi.version=1.44 test`。
- 边界：只追加，不改 V1-V86；不写其他模块表。

## T2 后端领域与持久化

- 产物：`com.ccb.architecture.network.model.NetworkWorkOrderModels`（kind/动作/状态/
  工单/历史/轮次/回执/三类载荷 DTO）、`persistence.NetworkWorkOrderStore`（JDBC，
  行版本与状态守卫、回执幂等、轮次绑定）。
- 证据：领域测试 `NetworkWorkOrderServiceTest`、`NetworkWorkOrderMySqlTest`。

## T3 服务编排与工作流

- 产物：`service.NetworkWorkOrderService`（状态机、per-kind 校验、摘要、结果登记）、
  `service.NetworkWorkOrderSubmissionService`（提交/取消工作流协调）、
  `integration.NetworkWorkflowLifecycleConsumer`（STARTED/APPROVED/RETURNED/REJECTED/
  TERMINATED 幂等消费）、`integration.NetworkAttachmentAccessPolicy`。
- 证据：`NetworkWorkOrderServiceTest`、`NetworkWorkOrderSubmissionServiceTest`、
  `NetworkWorkflowLifecycleConsumerTest`、`NetworkWorkflowIntegrationMySqlTest`。

## T4 HTTP 边界

- 产物：`web.NetworkWorkOrderController`（三级权限、数据范围、审计包装、附件黑名单）。
- 证据：`NetworkWorkOrderControllerTest`（含越权 403/404）。

## T5 前端

- 产物：`NetworkWorkOrderListPage/FormPage/DetailPage.vue`、`network.ts`、
  `networkTypes.ts`、`networkUtils.ts`，`api.ts/types.ts/utils.ts/architecture.css`
  增量，`router/index.ts` 四段路由。
- 证据：`npm --prefix web run build`；桌面/移动浏览器 UAT（manual）。

## T6 全量验证与收敛

- 命令：`mvn -pl :ccb-architecture -am test -Dapi.version=1.44`、`mvn test
  -Dapi.version=1.44`、`npm --prefix web run build`、`node scripts/check-all-governance.mjs`、
  `check-module-boundaries.mjs`、`check-flyway-migrations.mjs`、
  `check-codex-scope.mjs --scope .../codex-task-scope.yaml --working-tree`、
  `git diff --check`。
- 观测：独立复跑关键场景，核对状态机、审计、附件授权与权限矩阵；记录
  execution/observation 与收敛判定；不把静态检查当作运行证据。

## 采样点

- T1 后：迁移空库/增量库成功，V90 守卫在身份冲突库上失败关闭。
- T3 后：消费者回执幂等；提交未发布流程返回明确错误。
- T5 后：前端构建通过，四视口无溢出。
- T6 后：全量测试与治理检查通过，风险逐项复核。
