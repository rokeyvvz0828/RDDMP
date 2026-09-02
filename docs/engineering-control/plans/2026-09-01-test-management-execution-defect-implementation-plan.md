# 测试执行与测试缺陷实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

## 状态与来源

- 计划修订：1；设计修订：1；状态：可移交。
- 设计文档：`docs/engineering-control/designs/2026-09-01-test-management-execution-defect-design.md`。
- 用户已确认完整实施，包含新增执行菜单和缺陷新增页面。

## 全局约束

只追加 V127；不改公共 UI 或平台附件；所有服务端写入验证 tenant/domain/project/system/RBAC 并审计；使用现有全局项目选择器、UiDataTable、TestManagementFormDialog、WangEditor 和附件网关。

## 文件职责地图

| 路径 | 状态 | 职责 |
|---|---|---|
| `.../V127__test_management_execution_defect.sql` | candidate-new | 执行、缺陷、关联、附件、轨迹、菜单动作表。 |
| `.../execution/**` | candidate-new | 执行树、导入、记录、批量、附件和轨迹服务。 |
| `.../defect/**` | candidate-new | 缺陷编号、详情、状态机、关联快照、附件和回收。 |
| `.../web/TestExecutionController.java` | candidate-new | `/api/test-management/execution/**` HTTP 适配。 |
| `.../web/TestDefectController.java` | candidate-new | `/api/test-management/defects/**` HTTP 适配。 |
| `.../service/ExecutionDefectWorkbookService.java` | candidate-new | 执行/缺陷 XLSX 导出。 |
| `web/src/modules/test-management/execution/**` | candidate-new | 执行工作台和居中详情/导入弹窗。 |
| `web/src/modules/test-management/defect/**` | candidate-new | 缺陷列表、创建弹窗和独立详情页。 |
| `web/src/modules/test-management/api.ts`、`web/src/router/index.ts` | existing | API 类型、请求和路由接入。 |

## 任务依赖图

`T1 迁移与权限 → T2 执行服务 → T3 缺陷服务 → T4 执行前端 → T5 缺陷前端 → T6 集成验收`。所有任务串行，避免接口和同一迁移冲突。

### T1：执行/缺陷持久化与菜单授权

**需求映射：** R1、R4、R5、R7。
**文件：** 新建 V127 及 SQL 结构测试。
**接口：** 产出执行目录、执行记录、执行附件、执行轨迹、缺陷、缺陷关联、缺陷附件、缺陷轨迹、回收字段和四大类执行菜单权限。
**步骤：** 先检查 V126 版本连续性；追加表、索引、FK 和动作权限；运行 Flyway 连续性与 schema 断言。
**验收：** 所有表按 tenant/domain/project/system 限定，缺陷关联支持 active/snapshot 两种状态。
**回滚：** 未应用时移除 V127；本地已应用库可按用户授权重建。
**停止/升级：** 若必须改历史迁移、平台表或既有菜单语义则停止。

### T2：执行 API、导入、批量与轨迹

**需求映射：** R1、R2、R3、R4、R7。
**文件：** 新建 `execution/**`、`TestExecutionController`、工作簿服务增量和聚焦测试。
**接口：** 树、预览/确认导入、列表/详情、保存结果、批量成功/失败/移动/移除、附件、轨迹、导出。
**步骤：** 先建立服务测试；以参测系统和轮次周期作为树根；实现三种无写入预览和确认导入；实现结果约束、失败关联、回算、快照和审计；运行模块测试。
**验收：** 重复/无效导入、无备注阻塞、无缺陷失败、未解决关联移除、跨系统写入均失败。
**回滚：** 回退 execution 包及控制器，不删除 V127 数据。
**停止/升级：** 若 attachment gateway 不能通过模块策略绑定，停止并升级。

### T3：缺陷 API、状态机、关联快照与回收

**需求映射：** R4、R5、R6、R7。
**文件：** 新建 `defect/**`、`TestDefectController`、工作簿服务增量和聚焦测试。
**接口：** 缺陷列表/新建/详情/更新/流转/关联/解除/附件/轨迹/删除/恢复/导出。
**步骤：** 建立编号与权限测试；实现字典、轮次周期、环境和开发人员候选读取；实现状态机、关联转快照和 30 天回收；运行模块测试。
**验收：** 编号恒定、角色拒绝、历史系统可读、删除受关联保护、快照不可作为执行详情链接。
**回滚：** 回退 defect 包及控制器。
**停止/升级：** 需要更改通知平台或系统角色定义时停止。

### T4：紧凑执行工作台

**需求映射：** R1、R2、R3、R4、R8。
**文件：** 新建 `web/.../execution/**`，修改 API、router。
**步骤：** 接入新执行菜单和全局项目；实现左树、列表、可调列、筛选/批量工具栏；实现居中导入、详情、缺陷关联、富文本和附件交互；检查无项目、只读、移动布局。
**验收：** 无重复项目选择器、无抽屉、未排序时无排序标识、列宽可拖动。
**回滚：** 移除 execution 页面/路由/API 增量。
**停止/升级：** 若公共表格无法支持所需能力，停止而非修改公共组件。

### T5：缺陷列表、新增与详情工作台

**需求映射：** R5、R6、R8。
**文件：** 新建 `web/.../defect/**`，修改 API、router。
**步骤：** 实现左系统树、默认筛选与快捷按钮；实现居中新建弹窗、独立详情页三 TAB、状态动作、关联快照、附件和轨迹；接入导出与回收操作。
**验收：** 所有图标按钮有名称，权限隐藏不替代 API 拒绝，长内容/小屏无横向页面滚动。
**回滚：** 移除 defect 页面/路由/API 增量。
**停止/升级：** 富文本或文件预览若需要新公共依赖则停止。

### T6：集成、权限和浏览器验收

**需求映射：** R1-R8。
**文件：** 当前任务 `.ai-control` 执行/观测记录。
**步骤：** 运行 `mvn -pl :ccb-test-management -am test`、`node scripts/check-flyway-migrations.mjs`、`node scripts/check-all-governance.mjs`、`npm --prefix web run build` 和 diff 检查；本地库进行管理员/无权 API 探针；浏览器检查执行、失败关联、缺陷流转和 375px。
**验收：** 两类以上传感器覆盖每条需求，P0/P1 为零。
**回滚：** 回退本任务应用改动；保留已应用迁移。
**停止/升级：** 迁移失败、跨租户数据泄露或无法启动本地服务时停止。

## 集成检查与风险

执行完成后按 T6 命令和真实浏览器路径记录证据。主要风险为本地附件服务、已有角色配置和环境数据为空；均以明确的空/失败状态处理，不以模拟成功替代服务端验证。
