# 需求管理术语脱敏统一（蒙商→同业、金科→我方）实施计划

## 状态与来源
- 计划修订：1
- 设计修订：1
- 设计文档：docs/engineering-control/designs/2026-09-02-requirement-terminology-design.md
- 状态：可移交
- 批准：用户 2026-09-02 选择方案 A

## 目标与全局约束

**目标：** 需求管理模块所有用户可见「蒙商/金科」替换为「同业/我方」，存量数据与注释同步更新，接口与字段标识符不变。

**全局约束：**
- 只修改任务范围 `codex-task-scope.yaml` 的 `writable_paths`。
- 不修改已发布 Flyway 脚本；新增 V145 追加式迁移。
- 不重命名 `monshang_*`/`jinke_*` 标识符、列名与 API 键。
- 不修改历史文档（`docs/original/**`、原需求与设计文档）。
- 保持 Java 包名与业务逻辑。

## 文件职责地图

| 文件 | 状态 | 职责 |
| --- | --- | --- |
| `web/src/views/RequirementsView.vue` | existing | 前端标签/占位/表单文案替换 |
| `server/.../support/RequirementEnums.java` | existing | 枚举值与字段标签替换 |
| `server/.../service/RequirementImportService.java` | existing | 表头映射、示例值替换、旧表头兼容 |
| `server/.../RequirementImportServiceTest.java` | existing | 表头断言更新 + 旧表头用例 |
| `mock/mock-data.json` | existing | 演示数据替换 |
| `server/.../db/migration/V145__requirement_terminology_rename.sql` | candidate-new | 存量数据与列注释迁移 |

## 任务依赖图与并行策略

单一任务 T1（各文件耦合为一个可验收结果，串行执行内部步骤）。

### T1：术语替换（运行面 + 数据迁移）

**需求映射：** R1-R4

**文件：**
- 修改：`web/src/views/RequirementsView.vue`、`RequirementEnums.java`、`RequirementImportService.java`、`RequirementImportServiceTest.java`、`mock/mock-data.json`
- 新建：`V145__requirement_terminology_rename.sql`

**操作步骤：**
1. 基线采样：`rg -c '蒙商|金科'` 记录各运行面文件当前命中数。
2. 修改 `RequirementEnums.java`：差异类型/变更结论枚举 + 标签替换。
3. 修改 `RequirementImportService.java`：表头映射、示例值、旧表头 alias。
4. 修改 `RequirementImportServiceTest.java`：新表头断言 + 旧表头用例。
5. 修改 `web/src/views/RequirementsView.vue`：18 处文案替换。
6. 修改 `mock/mock-data.json`：6 处数据替换。
7. 新建 `V145` 迁移：存量数据 UPDATE + 列注释。
8. 局部验证：`mvn -pl :ccb-requirement -am test`、`npm --prefix web run build`、`node scripts/check-all-governance.mjs`、范围检查、`rg` 复扫。

**验收检查：**
- 运行面 `rg '蒙商|金科'` 0 命中；字段标识符 `rg 'monshang|jinke'` 仍存在（未改名）。
- 新旧表头导入测试通过；前端构建通过；治理与范围检查通过。

**回滚：** 回退本需求提交；V145 为追加式迁移。

**停止条件：** 发现标识符需改名、历史脚本需修改、或新表头/旧表头解析冲突。

**升级条件：** 任何超出任务范围的文件需要修改、或本地 MySQL 无法启动导致迁移执行待补验时升级说明。

## 集成检查

- `mvn -pl :ccb-requirement -am test` 退出码 0。
- `npm --prefix web run build` 退出码 0。
- `node scripts/check-all-governance.mjs` 退出码 0。
- `node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260902-057-requirement-terminology/codex-task-scope.yaml --base origin/main --head HEAD --working-tree` 退出码 0。

## 控制模型种子（假设，待建模验证）

- 被控边界：需求模块前端视图、枚举、导入服务、mock 数据、req_* 数据与注释。
- 传感器：`rg` 扫描、模块测试、前端构建、治理检查、范围检查。
- 执行器：文件编辑与新迁移。
- 扰动：本地 MySQL 未启动；分支并行修改。
- 假设：机械替换满足业务口径；旧表头兼容成本低。
