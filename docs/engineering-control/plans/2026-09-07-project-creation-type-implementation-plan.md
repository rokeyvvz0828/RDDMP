# 项目创建类型实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 项目创建类型持久化，并在顶部三个切换入口一致展示。

**架构：** 保留ProjectService与pm_project，新增一个枚举列；复用原接口及Pinia，把保存结果同步到顶部元数据。只修改字段相关代码及局部布局。

**技术栈：** Java17/Spring Boot/JdbcTemplate/MySQL8.4/Flyway；Vue3/Element Plus/Pinia；JUnit/Mockito/Node。

## 状态

设计与计划修订1均已获用户确认。代码及隔离迁移验证已实施，当前phase=observing；详见需求目录acceptance.md。未访问当前库。

## 全局约束

- 只追加Flyway迁移；不直接修改现有库
- 涉及platform/system、迁移及应用壳的写入须经范围和Owner批准
- 不读取.env，不重启服务，不提交推送
- 设计批准后先编写实施计划，实施须通过计划批准及控制门禁
- 同一licon工作区串行增量，不新建worktree、切分支、提交或推送
- 不启动/停止现有服务；不连接当前库；隔离测试条件不足时报告缺口
- 不改api/project.ts、公共UiStatusTag或其他业务模块

## 文件地图

- server/src/platform/system/src/main/java/com/ccb/system/project/ProjectService.java：existing；项目类型读写校验。
- server/src/platform/system/src/test/java/com/ccb/system/project/ProjectServiceTest.java：existing；后端回归测试。
- web/src/types/project.ts：existing；类型、表单及顶部同步。
- web/src/types/project-context.ts：existing；类型、表单及顶部同步。
- web/src/stores/project-context.ts：existing；类型、表单及顶部同步。
- web/src/views/ProjectView.vue：existing；类型、表单及顶部同步。
- web/src/views/AppLayout.vue：existing；类型、表单及顶部同步。
- server/src/platform/infrastructure/src/main/resources/db/migration/V199__project_creation_type.sql：candidate-new；追加项目创建类型列与默认约束。
- docs/requirements/REQ-20260907-070-project-creation-type/creation-type.test.mjs：candidate-new；Node/Vue/Pinia状态与SFC接入检查。

## 依赖与覆盖

T1后T2，串行。R1/R2/R4由两任务覆盖，R3由T2覆盖。候选迁移V199写入前必须再次确认唯一性。

## T1：项目类型持久化、兼容读写并保持授权审计

需求：R1、R2、R4；前置：无。

已证实事实：

- ProjectService.create/update采用Map输入和JdbcTemplate，保留requireAction、requireProjectAccess及audit
- workbench第81行与project第896行显式SELECT，均需增加creation_type
- ProjectServiceTest已有Mockito JdbcTemplate与权限测试
- 迁移文件当前最高V198，V199为候选，执行前重查

新建：server/src/platform/infrastructure/src/main/resources/db/migration/V199__project_creation_type.sql。
修改：server/src/platform/system/src/main/java/com/ccb/system/project/ProjectService.java；server/src/platform/system/src/test/java/com/ccb/system/project/ProjectServiceTest.java。
测试：server/src/platform/system/src/test/java/com/ccb/system/project/ProjectServiceTest.java。

消费：pm_project、现有project接口Map输入；AuthUser与现有项目权限。
产出：creation_type=NEW|CONTINUATION；创建缺失默认NEW，更新缺失不改，显式空或非法400；workbench/detail/create/update响应包含creation_type。

- [x] T1-S1：记录工作区基线并确认迁移版本无冲突

运行：`git status --short`。
预期：保留既有改动；若V199已存在暂停重新确定迁移文件。
证据：文件基线及迁移文件列表。

- [x] T1-S2：添加后端失败测试

运行：`mvn -pl :ccb-system -am -Dtest=ProjectServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`。
预期：新增创建类型字段SQL参数与非法值断言在实现前失败；原测试结果单独记录。
证据：测试名、失败断言及退出码。

- [x] T1-S3：追加列和CHECK约束；增量修改create/update与两个读取SELECT

运行：受控编辑或授权检查，无业务库操作。
预期：creation_type VARCHAR(16) NOT NULL DEFAULT 'NEW'；CHECK仅允许NEW/CONTINUATION；禁止编辑历史迁移。
证据：实际diff；新增字段检查。

- [x] T1-S4：验证合法创建/更新、缺省保留、显式空及非法类型不写库、读取返回与权限审计

运行：`mvn -pl :ccb-system -am test`。
预期：目标模块与依赖测试退出0；新测试捕获SQL及绑定参数而非只比较字符串。
证据：Surefire报告与权限/审计断言。

- [x] T1-S5：在授权隔离MySQL验证迁移的存量及新行默认值

运行：受控编辑或授权检查，无业务库操作。
预期：仅独立测试库执行候选迁移：原项目得NEW，续建可写，非法值拒绝；无隔离环境则留缺口，不访问当前库或.env。
证据：隔离目标证明、SQL结果与迁移校验；不可用记录原因。

- [x] T1-S6：记录执行与第一次采样

运行：受控编辑或授权检查，无业务库操作。
预期：不创建Git提交；只保存T1执行与观测证据。
证据：execution-T1、observation-T1。

验收：NEW/CONTINUATION创建和互相编辑；缺失创建NEW；更新缺失保留CONTINUATION；非法/空值不持久化；读写权限和审计保持；隔离MySQL存量行默认NEW。
风险：新增列部署顺序；Mockito不能证明真实SQL执行。
回退：反向撤销本次代码增量；已执行迁移保留列和值，不删除数据；数据库纠偏仅追加迁移。
停止：V199冲突；需读取.env或当前业务库；需改历史迁移或权限模型。
升级：隔离MySQL不可用需明确验收缺口；授权文件以外平台改动。

## T2：表单维护类型并在顶部三个入口即时一致展示

需求：R1、R2、R3、R4；前置：T1。

已证实事实：

- project-context将workbench映射为ref/name/shortName/status；ref当前为project_code
- AppLayout第244/262/271行有三个选择器且共享confirmProjectSwitch
- ProjectView.resetProjectForm/openEditProject/saveProject负责表单流，目前保存后不更新顶部store
- api/project.ts现有Record请求和Project响应可复用无需更改

新建：docs/requirements/REQ-20260907-070-project-creation-type/creation-type.test.mjs。
修改：web/src/types/project.ts；web/src/types/project-context.ts；web/src/stores/project-context.ts；web/src/views/ProjectView.vue；web/src/views/AppLayout.vue。
测试：docs/requirements/REQ-20260907-070-project-creation-type/creation-type.test.mjs。

消费：T1新增creation_type；现有Project和ProjectContextItem、Vue/Pinia、UiStatusTag。
产出：ProjectCreationType联合类型及统一中文标签；ProjectContextItem携带creationType；store.syncProject(project: Project): void，更新服务端确认的项目元数据，不触发select/路由/业务请求。

- [x] T2-S1：建立前端测试并记录预期失败

运行：`node docs/requirements/REQ-20260907-070-project-creation-type/creation-type.test.mjs`。
预期：实现前因缺少类型投影/三个标签入口/保存同步出现可定位失败。
证据：退出码及断言。

- [x] T2-S2：补类型与store元数据同步

运行：受控编辑或授权检查，无业务库操作。
预期：project.ts新增NEW|CONTINUATION和标签映射，响应缺字段仅兼容NEW；同步按已知项目身份更新或添加选项，类型变更不修改currentRef/localStorage；在途列表不能覆盖已确认新值。
证据：状态测试；原select/确认逻辑保留。

- [x] T2-S3：补创建/编辑字段及保存成功同步

运行：受控编辑或授权检查，无业务库操作。
预期：reset默认NEW，编辑反显原值，必填选择器；新增saving早退防重入；失败保留输入与原顶部值；成功调用syncProject。
证据：表单模型与保存调用断言。

- [x] T2-S4：三个el-select增加当前选中label插槽和选项标签

运行：受控编辑或授权检查，无业务库操作。
预期：保留ref值、confirmProjectSwitch、loading/empty/retry及原label；名称可省略并有完整提示，类型标签不压缩。
证据：SFC结构及三个入口检查。

- [x] T2-S5：仅调整本表单和选择器的响应式边界

运行：受控编辑或授权检查，无业务库操作。
预期：项目弹窗宽min(600px,calc(100vw - 24px))、内容局部滚动、标题页脚可达；760px以下单列；复用UiStatusTag和主题，不改全局UI。
证据：局部diff和四视口检查。

- [x] T2-S6：运行前端状态和接入测试

运行：`node docs/requirements/REQ-20260907-070-project-creation-type/creation-type.test.mjs`。
预期：新建/续建映射、保存同步、在途旧响应、失败、已选项保持、三个入口全部通过。
证据：Node输出及SFC断言。

- [x] T2-S7：完整构建与后端回归

运行：`npm --prefix web run build`。
预期：vue-tsc/Vite退出0；另执行mvn test，原失败独立报告不绕过。
证据：命令退出码和构建/测试报告。

- [x] T2-S8：治理与范围检查

运行：`git diff --check`。
预期：空白检查通过；执行check-all-governance和当前scope检查，已知REQ061 YAML和licon命名故障如实记录。
证据：diff、归属、治理命令输出。

- [ ] T2-S9：浏览器验收及最终记录

运行：受控编辑或授权检查，无业务库操作。
预期：四视口与明暗主题：新增/编辑、三个顶部入口、当前标签、取消切换、刷新和错误；浏览器无授权不绕过，不宣称converged。
证据：截图、DOM尺寸及实际操作；execution-T2/observation-T2与独立验收。

验收：表单选择保存并回显；三个入口展开/收起均展示标签；保存后即时同步，不自动切项目；旧列表响应不覆盖新类型；现有切换确认、失败/空/加载、权限不变；四视口无溢出及遮挡。
风险：共享store影响所有业务项目选择消费者；项目编码可编辑是既有身份风险，本任务不改变身份模型；浏览器授权缺口。
回退：仅撤销T2新增字段、标签、store同步和本次专项样式；不回退其他页面改动。
停止：需要改变项目引用身份或其他业务选择逻辑；共享UI实现需要修改。
升级：无法在既有project_code引用下保持正确选择；真实浏览器验收受阻，保留待验收状态。

## 关键实现契约

后端先区分containsKey与缺省：创建缺失NEW；传入时required校验后验证枚举；更新仅containsKey时增加creation_type赋值。保留原事务、权限与audit位置。迁移候选SQL：

```sql
ALTER TABLE pm_project
  ADD COLUMN creation_type VARCHAR(16) NOT NULL DEFAULT 'NEW',
  ADD CONSTRAINT chk_pm_project_creation_type
    CHECK (creation_type IN ('NEW', 'CONTINUATION'));
```

前端统一类型：

```ts
export type ProjectCreationType = 'NEW' | 'CONTINUATION'
export const projectCreationTypeLabels = { NEW: '新建', CONTINUATION: '续建' }
// Store contract: no select(), routing, or business request on metadata synchronization.
function syncProject(project: Project): void
```

选择器仍以project.ref为value、project.name为label，仅添加当前label与option插槽内的UiStatusTag。原confirmProjectSwitch函数不修改。ProjectView保存成功调用syncProject(response.data.data)，失败不调用；表单默认和回显分别取NEW和实际类型。

syncProject需处理保存期间的在途列表：已确认新值不能被更早启动的列表覆盖；实现阶段通过请求版本或局部修订合并验证。不得用每次强制initialize导致currentRef重新从storage恢复。项目编码修改是既有边界，只更新已识别对象；若需改变身份模型则停下重规划。

## 集成验证

- `mvn test`：退出0或如实记录独立基线失败。
- `npm --prefix web run build`：退出0。
- `node docs/requirements/REQ-20260907-070-project-creation-type/creation-type.test.mjs`：状态及三个入口断言通过。
- `node scripts/check-all-governance.mjs`：实际结果，既有REQ061失败不得伪称通过。
- `node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260907-070-project-creation-type/codex-task-scope.yaml --base HEAD --head HEAD --working-tree`：实际结果，licon命名故障不通过改分支绕过。

隔离MySQL不可用时不得连接当前库代替，也不得把Mockito当作迁移执行证据。浏览器访问仍须检查授权，不绕过先前限制；缺真实浏览器或独立验收则保留observing。视口1280x800、375x812、390x844、430x932；明暗主题与三类入口。

## 自检与批准边界

路径和接口均按现有源码核对，候选文件显式标明。控制种子为hypotheses-only，计划批准后由control-engineering重新建模，不预建执行账本。无Git提交检查点，以任务执行记录代替提交。

设计及实施计划已获用户确认；产品代码已实施，浏览器验收仍未完成。批准后再将scope精确产品路径转为writable，并将其他公共代码保留只读，导入交接包，从baseline开始。数据库只允许隔离验证，不授权当前库迁移、服务重启或生产操作。
