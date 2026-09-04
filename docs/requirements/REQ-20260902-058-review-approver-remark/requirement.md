---
id: REQ-20260902-058
status: ready
owner: rokeyvvz0828
module: business/requirement
---

# 需求管理评审选人范围与存量交付件评审增强

## 业务目标

1. **新建项目差异「提交评审」选人与「流转」一致**：审批人列表放开为需求模块全部启用用户，不再仅限少数有评审权限的用户。
2. **存量工作量表/软需文档「提交评审」参考新建项目差异**：提交时选择审批人（可多选、全部用户）+ 填写评审报告文档名称；评审由被选审批人或 PMO/管理员确认。
3. **评审备注**：评审记录新增「评审备注」字段；工作量表/软需文档列表与「评审记录」弹窗均展示评审备注。

## 范围

### 本次实施

- 后端 `RequirementDifferenceService.reviewers()`：审批人列表改为全部启用用户（与差异流转 `userOptions()` 口径一致）。
- 后端 `RequirementLegacyEnhanceService`：
  - `submitDeliverableReview` 增加审批人列表与评审报告文档名称，提交时记录到交付件；
  - `reviewDeliverable` 允许被选审批人或 PMO/管理员评审，写入评审备注；
  - `deliverables` 查询返回最近一次评审备注；`reviewRecords` 返回评审备注。
- 后端 `RequirementLegacyController`：提交评审接口接收 `approverIds`/`reportDocName`。
- 新增 Flyway 迁移 `V146__requirement_review_approver_and_remark.sql`：
  - `req_workload`/`req_soft_doc` 增加 `review_approver_ids`、`review_approver_names`、`review_report_name`；
  - `req_review_record` 增加 `remark`（评审备注）。
- 前端：
  - 差异「提交评审」与交付件「提交评审」弹窗统一支持多人选择（全部用户）+ 评审报告文档名称；
  - 评审弹窗增加「评审备注」输入；
  - 评审记录弹窗增加「评审备注」列；
  - 工作量表/软需文档列表增加「评审备注」列与「评审记录」入口。

### 本次不实施

- 存量交付件评审不接入平台工作流引擎，仍为直接确认并写评审记录。
- 不实现文件实际上传/预览。
- 不改动存量需求头阶段流转。

## 现状与规则

- 新建项目差异提交评审原审批人仅限 `requirement:diff:review` 权限、统筹角色或管理员（演示数据 2 人）；流转可选全部用户。
- 存量交付件原提交评审为确认弹窗，不选人；评审仅限 PMO/管理员，评审记录含评审意见（`comment`）但无评审备注。
- 权限：提交评审需 `requirement:legacy:update`；评审确认允许被选审批人（提交时选择的用户）或 PMO/管理员；服务端仍做 RBAC 与数据范围校验。
- 数据库列新增沿用 information_schema 判断的追加式迁移模式，不改历史脚本。

## 验收标准（由用户测试）

1. 新建项目差异提交评审弹窗可选任意启用用户（与流转一致）。
2. 存量工作量表/软需文档提交评审弹窗可选多人并填写评审报告文档名称；被选审批人或 PMO 可执行评审。
3. 评审弹窗可填写「评审备注」；评审记录弹窗与工作量/软需列表展示评审备注。

## 测试与发布

- 测试由用户在本地执行（用户 2026-09-02 明确：测试由用户负责，Codex 不再执行测试）。
- 回退：回退本需求提交；V146 为追加式迁移，可单独停用。
- 风险与人工复核人：数据库迁移、评审权限调整需模块 Owner `rokeyvvz0828` 复核。

需求已与用户确认（2026-09-02，评审备注方案 C：列表与评审记录两处均加），头部 `status` 为 `ready`。
