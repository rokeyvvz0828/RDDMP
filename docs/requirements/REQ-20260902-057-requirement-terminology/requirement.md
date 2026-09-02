---
id: REQ-20260902-057
status: ready
owner: rokeyvvz0828
module: business/requirement
---

# 需求管理术语脱敏统一（蒙商→同业、金科→我方）

## 业务目标

需求管理平台（新建项目差异清单、存量需求、系统清单、Excel 导入模板与演示数据）当前使用客户专有术语「蒙商」「金科」。为使平台可面向通用同业场景展示，将用户可见的「蒙商」统一替换为「同业」、「金科」统一替换为「我方」（含组合词），同时保持内部字段标识符、数据库列名与接口 JSON 键不变。

## 范围

### 本次实施

- 前端 `web/src/views/RequirementsView.vue`：标签、占位符、表单文案中的「蒙商/金科」全部替换为「同业/我方」。
- 后端 `RequirementEnums.java`：差异类型枚举、变更结论状态枚举、字段标签中的「蒙商/金科」替换；枚举值与标签口径一致。
- 后端 `RequirementImportService.java`：Excel 导入表头映射与模板示例值替换为新术语；保留旧表头兼容，旧模板文件仍可导入。
- 测试 `RequirementImportServiceTest.java`：表头断言更新为新术语，并新增旧表头兼容用例。
- `mock/mock-data.json`：演示数据中的差异类型枚举值与文本替换为新术语。
- 新增 Flyway 迁移 `V145__requirement_terminology_rename.sql`（追加式，不改已发布脚本）：更新 `req_*` 存量数据（差异、存量需求、系统清单、变更日志等）与数据库列注释。
- 建立本需求的任务范围、设计/计划文档与 `.ai-control` 任务账本。

### 本次不实施

- 不重命名内部字段标识符（`monshang_*`、`jinke_*`）、数据库列名或接口 JSON 键（用户已确认方案 A）。
- 不修改已发布 Flyway 脚本（V67/V71/V75/V84_1/V130 等保持原样）。
- 不修改历史文档：`docs/original/**`、原需求 `REQ-20260816-001` 的 `requirement.md` 与设计文档保持原样（作为历史证据）。
- 不新增、删除或调整任何业务功能与流程。

## 现状与规则

- 术语分布：前端 18 处、`RequirementEnums.java` 19 处、`RequirementImportService.java` 15 处、测试 3 处、`mock/mock-data.json` 6 处、已发布迁移 V67/V71/V75/V84_1/V130 含数据与注释。
- 替换规则：`蒙商 → 同业`、`蒙商银行 → 同业银行`、`金科 → 我方`；差异类型 `金科有-蒙商无 → 我方有-同业无`、`金科有-蒙商手工 → 我方有-同业手工`、`蒙商有-金科无 → 同业有-我方无`；变更结论 `蒙商立项完成 → 同业立项完成`。
- 不变量：字段标识符、列名、API 键、Java 包名、模块边界与既有业务逻辑不变。
- 历史文档作为证据不修改；新旧术语并存仅限导入兼容（旧表头仍可解析，模板展示新表头）。

## 接口与数据

- 接口契约：请求/响应字段名与现有版本完全一致，不产生破坏性变更。
- 数据：新增迁移仅做 `UPDATE ... REPLACE` 与 `ALTER TABLE ... MODIFY COLUMN ... COMMENT`，追加式迁移、可回退（回退本需求提交即可；迁移不回滚历史脚本）。
- 系统清单 `req_system`：`conglomerate` 中「蒙商银行」更新为「同业银行」，`introduction` 中「蒙商/金科」按规则替换。

## 验收标准

1. 运行时代码与数据中不再出现「蒙商/金科」旧词（`rg` 扫描需求管理运行面为 0 命中），页面、枚举、导入模板均展示「同业/我方」。
2. 差异类型下拉与存量数据一致：`我方有-同业无 / 我方有-同业手工 / 同业有-我方无 / 双方作法有差异`。
3. 旧表头 Excel 仍可导入，新表头模板可下载；`RequirementImportServiceTest` 新旧表头用例均通过。
4. 新增 V145 迁移通过 Flyway 校验；迁移后存量数据与列注释无旧词。
5. `mvn -pl :ccb-requirement -am test`、`npm --prefix web run build`、`node scripts/check-all-governance.mjs` 与任务范围检查通过。

## 测试与发布

- 必须执行的测试：`mvn -pl :ccb-requirement -am test`、`npm --prefix web run build`、`node scripts/check-all-governance.mjs`、`node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260902-057-requirement-terminology/codex-task-scope.yaml`。
- 上线验证：本地 MySQL 未启动时，数据迁移的实际执行留待运行环境可用后补验；SQL 结构通过 Flyway 检查脚本校验。
- 回退：回退本需求提交；新增 V145 迁移可按 Flyway 回退策略处理，历史脚本不受影响。
- 风险与人工复核人：数据库迁移与前端/后端联动修改需模块 Owner `rokeyvvz0828` 复核。

需求已与用户确认（2026-09-02，用户选择方案 A：不改字段标识符），头部 `status` 为 `ready`，同目录 `codex-task-scope.yaml` 已启用。
