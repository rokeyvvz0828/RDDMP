# 投产演练管理模型重构 工程设计

## 文档状态

- 修订：1
- 状态：已确认
- 用户确认依据：用户要求不询问意见，全部实施并由用户最终验收。

## 目标与成功信号

投产方案、演练环境、演练轮次和问题形成可追溯链路；旧时序菜单消失，当前项目切换后各列表只返回当前项目数据。

## 方案选择

采用“新增规范化表 + 追加回填 + 保留旧接口兼容”的方案。相比直接改造旧表，它能保留历史数据和旧调用；相比只在旧表上加字段，它能表达一个项目多个方案/环境及轮次步骤，并能对方案步骤和轮次步骤分别加项目归属与乐观锁。

## 架构边界与职责

- `ReleaseOperationsController`：HTTP 路由、权限声明和 DTO 适配。
- `ReleaseOperationsService`：项目成员授权、实体归属、必填校验、引用校验和事务编排。
- `ReleaseOperationsStore`：JdbcTemplate 参数化查询、逻辑删除、行版本和结果映射。
- `V127`：新表、旧数据默认方案/环境回填、菜单迁移和本地演示数据。
- Vue 投产页面：方案步骤、环境、轮次步骤、问题轮次选择和独立菜单页面状态。

## 数据和接口

新增 `rel_release_plan`、`rel_release_plan_item`、`rel_release_drill_environment`、`rel_release_drill_step`；给 `rel_release_drill_round` 增加 `release_plan_id`、`environment_id`，给 `rel_release_issue` 增加可空 `drill_round_id`。新接口均使用 `/api/release/operations/release-plans`、`/environments`、`drills` 和 `drill-rounds/{id}/steps`，分页问题接口返回 `drillRoundId/drillRoundName`。

方案页面用左右分栏选择方案、下方双类型步骤列表；环境页面使用列表与编辑弹窗；演练页面先维护轮次再维护步骤；问题编辑弹窗增加当前项目轮次选择。所有请求都携带当前项目 ID，页面组件 key 随项目 ref 改变。

## 错误、兼容与安全

缺少方案/环境、跨项目引用、重复名称、时间倒置、删除被引用实体和乐观锁冲突返回明确业务错误。旧 `/drill-plan` 和 `/timelines` 接口保留，不再被新菜单调用。后端不信任前端 projectId 或成员选择，所有实体二次查询确认租户、项目和有效状态；写入使用已有审计字段。

## 验证策略

执行后端 service/controller 安全测试、Flyway 检查、前端构建、治理/范围/差异检查，并在真实浏览器验证五项菜单、CRUD、引用约束、项目切换、刷新及 `1280`、`375x812`、`390x844`、`430x932` 视口。

## 风险与回退

V127 回填依赖旧表结构和本地演示用户；使用条件插入与 `NOT EXISTS`，不修改既有迁移。动态菜单有缓存时需刷新登录态或重启后端。应用回退保留 V127 数据，恢复旧入口可通过回退代码及菜单权限策略完成。
