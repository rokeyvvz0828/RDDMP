# 项目风险抽屉与进展评论 实施计划

## 状态与来源
- 计划修订：4
- 设计修订：4
- 设计文档：`docs/engineering-control/designs/2026-08-17-project-risk-drawer-comments-design.md`
- 状态：可移交

## 目标与全局约束

实现风险右侧抽屉三模式和持久化评论。保持 `platform/system` 数据所有权、项目成员范围、风险字段原有权限和 V43 不变；只追加 V44；评论不新增权限节点。当前修订补充企业后台抽屉的固定头部、页签导航、局部滚动正文和固定底部操作区。

## 文件职责地图

- 修改 `ProjectController.java`：评论 GET/POST 路由。
- 修改 `ProjectService.java`：评论查询、写入、范围校验和审计。
- 新建 `V44__project_risk_comments.sql`：评论表、索引和字段注释。
- 修改 `ProjectServiceTest.java`：评论输入和范围回归测试。
- 修改 `project.ts`、`project.ts` 类型：评论契约。
- 修改 `ProjectView.vue`：抽屉状态、三模式、评论加载/提交和字段拆分。
- 修改 `styles.css`：抽屉、只读摘要、评论列表和移动端布局。

## 任务依赖图与并行策略

T1 数据与后端契约 -> T2 前端抽屉与评论交互 -> T3 集成验证。任务串行，避免前后端契约和同一页面文件冲突。

## 需求覆盖表

R1 -> T2；R2 -> T1,T2；R3 -> T2；R4 -> T1,T3；R5 -> T3。

### T1：评论数据与服务端接口

#### 需求映射与前置事实

映射 R2、R4。现有风险服务已经有 `requireProjectAccess`、`ensureRisk`、`audit` 和项目租户查询；评论沿用这些能力，不扩展风险字段权限。

#### 文件边界与接口

- 新建：`server/src/platform/infrastructure/src/main/resources/db/migration/V44__project_risk_comments.sql`
- 修改：`ProjectController.java`、`ProjectService.java`、`ProjectServiceTest.java`
- 产出：`GET /api/project/{projectId}/risks/{riskId}/comments` 和 `POST .../comments`；POST仅消费 `{comment_text}`并返回作者信息和时间。

#### 操作步骤、命令和预期信号

1. 先运行 `mvn -pl :ccb-system -am test` 记录当前基线，预期当前风险测试通过。
2. 追加评论表，要求 `tenant_id/project_id/risk_id/user_id/comment_text/created_at/deleted` 有注释，索引覆盖风险和时间排序；执行 `node scripts/check-flyway-migrations.mjs`，预期无历史迁移修改警告。
3. 增加 Controller 路由和 Service 方法：读取/发表只要求项目查看与成员范围；发表要求非空且不超过2000字符，作者从 `AuthUser` 读取，查询按时间倒序。
4. 增加测试，断言空评论拒绝、客户端作者字段不生效、评论项目范围校验存在；运行 `mvn -pl :ccb-system -am test`，预期通过。

#### 验收、证据与回滚

验收为接口契约、服务端范围和审计路径可追踪；记录 `.ai-control/.../execution-T1.json`。回滚仅恢复本任务后端文件并按部署流程回滚 V44，不修改 V43。

#### 停止和升级条件

发现无法在现有项目范围校验中区分项目成员，或需要新增权限节点时停止并升级用户。

### T2：风险右侧抽屉三模式

#### 需求映射与前置事实

映射 R1、R2、R3。现有 `ProjectView.vue` 使用 `el-dialog` 全量三段表单；现有 `UiUserIdentity` 可通过 `show-profile=false` 复用。

#### 文件边界与接口

- 修改：`web/src/api/project.ts`、`web/src/types/project.ts`、`web/src/views/ProjectView.vue`、`web/src/styles.css`
- 消费：T1 评论 GET/POST 契约。
- 产出：`riskMode` 为 `report|progress|tracking`；新增/编辑进入 report，操作列进度描述进入 progress，问题跟踪进入 tracking。

#### 操作步骤、命令和预期信号

1. 先运行 `npm --prefix web run build` 记录前端基线，预期通过。
2. 增加 `ProjectRiskComment` 类型和评论 API；增加评论加载、提交状态和错误保留逻辑。
3. 将风险 `el-dialog` 改为右侧 `el-drawer`：report只渲染第一部分；progress渲染只读第一部分、评论列表和评论输入；tracking只渲染第三部分并提交第三部分字段；风险编辑和跟踪请求只发送对应字段。
4. 操作列按编辑、进度描述、问题跟踪、删除排列；评论按钮对所有可查看风险的项目成员可见，跟踪按钮继续受原有更新权限和负责人约束。
5. 增加抽屉滚动、评论时间线、空/加载/失败和375px移动布局样式；运行 `npm --prefix web run build`，预期无TypeScript/Vite错误。

#### 验收、证据与回滚

验收为三模式不混淆、只读状态、评论最新置顶和风险表单字段不丢失；记录 `.ai-control/.../execution-T2.json`。回滚恢复四个前端文件即可，保留数据库迁移按部署回滚策略处理。

#### 停止和升级条件

若 Element Plus 抽屉在移动视口无法滚动或现有风险字段无法拆分而需改后端契约，停止并回到计划复核。

### T4：企业后台风险抽屉布局

#### 需求映射与前置事实

映射 R1、R2、R3。现有抽屉已经承载三种模式和评论状态，但宽度、层级和滚动边界不符合固定头部/正文/底部操作的页面规则；旧版风险弹窗不再承担业务职责，应从渲染树移除。

#### 文件边界与接口

- 修改：`web/src/views/ProjectView.vue`、`web/src/styles.css`
- 修改：`docs/engineering-control/designs/2026-08-17-project-risk-drawer-comments-design.md`、本计划及当前任务账本
- 不修改：后端接口、评论 DTO、风险权限、数据库迁移和公共组件

#### 操作步骤、命令和预期信号

1. 将抽屉调整为固定头部摘要、可访问模式页签、模式正文局部滚动和固定底部操作区；问题上报字段保持可编辑，进度描述保持只读评论，问题跟踪保持第三部分编辑。
2. 将问题上报和问题跟踪按业务语义分组，使用现有语义主题变量；评论时间线和输入区保留独立层级，不改变评论提交行为。
3. 删除旧版隐藏风险 `el-dialog`，将桌面宽度收敛到约 800px，移动端使用视口宽度约束并禁止抽屉页面级横向溢出。
4. 运行 `git diff --check` 和 `npm --prefix web run build`；预期无空白错误和前端构建错误。
5. 在桌面端和 375px 视口检查三个模式、滚动区域、固定底部操作、明暗主题和关闭/保存入口；预期正文可滚动、操作区始终可达且页面无横向溢出。

#### 验收、证据与回滚

验收为三个模式的字段边界和原交互保持不变，抽屉在桌面和移动端层级清晰、滚动稳定、底部操作可达。回滚仅恢复 T4 修改的前端文件和当前任务账本，不回退后端或 V44。

### T5：风险入口模式裁剪与用户资料卡

#### 需求映射与前置事实

映射 R1、R2、R3。当前三个入口共用全部模式页签，评论使用 `UiUserIdentity` 的无资料卡模式；现有组件示例已验证默认模式为头像、姓名和悬浮资料卡。

#### 文件边界与接口

- 修改：`web/src/views/ProjectView.vue`、`web/src/styles.css`
- 修改：当前任务设计、计划和账本 JSON
- 不修改：风险评论接口、权限校验、数据结构和公共组件实现

#### 操作步骤、命令和预期信号

1. 增加风险抽屉入口上下文：编辑仅允许问题上报，进度描述仅允许进度内容，问题跟踪允许进度描述和问题跟踪两个页签。
2. 将风险操作列包装为不换行的紧凑 flex 操作组，固定操作列宽度并保留现有权限和危险操作。
3. 将评论作者改为 `UiUserIdentity` 默认展示模式，移除重复姓名文本，保留头像/姓名悬浮资料卡和时间信息。
4. 运行 `npm --prefix web run build` 和 `git diff --check`；预期构建、类型检查和空白检查通过。
5. 浏览器验证三个入口的可见模式、问题跟踪页签切换、操作列单行展示、评论用户资料卡和 375px 无横向溢出。

#### 验收、证据与回滚

验收为入口与内容一一对应，操作列不换行，评论悬浮显示用户资料。回滚仅恢复 T5 的前端文件和当前任务账本，不回退后端或 V44。

### T6：进度描述只读问题上报摘要

#### 需求映射与前置事实

映射 R2。用户最新确认进度描述需要查看问题上报信息，但仍不可编辑；问题跟踪和编辑入口的可见范围不变。

#### 文件边界与接口

- 修改：`web/src/views/ProjectView.vue`
- 修改：当前任务设计、计划和账本 JSON
- 不修改：风险评论接口、风险更新接口、权限和公共组件

#### 操作步骤、命令和预期信号

1. 在进度描述正文顶部恢复问题上报只读摘要，复用现有 `el-descriptions` 展示，不绑定 `v-model`。
2. 保持进度描述入口无模式页签、无问题跟踪内容；评论区继续可见并可发表。
3. 运行 `npm --prefix web run build`、`git diff --check`，并在桌面和 375px 视口确认只读字段、评论区和滚动边界。

#### 验收、证据与回滚

验收为进度描述入口同时显示问题上报只读摘要和进度评论，任一问题上报控件均不可编辑；回滚仅恢复 T6 前端文件和当前任务账本。

### T3：集成验证与独立观测

#### 需求映射与前置事实

映射 R4、R5，依赖 T1、T2。

#### 文件边界与接口

- 修改：`.ai-control/requirements/req-20260817-031-project-risk-drawer-comments/*.json`
- 消费：T1/T2真实命令和浏览器结果。

#### 操作步骤、命令和预期信号

1. 运行 `git diff --check`，预期无空白错误。
2. 运行 `node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260817-031-project-risk-drawer-comments/codex-task-scope.yaml --working-tree`，预期范围检查通过。
3. 运行 `node scripts/check-flyway-migrations.mjs`、`mvn -pl :ccb-system -am test`、`npm --prefix web run build`，预期全部通过。
4. 启动已执行 V44 的后端，在浏览器以项目成员验证风险抽屉新增/编辑/评论/跟踪/删除和刷新；用375px视口验证抽屉滚动、按钮和评论内容无溢出；记录接口、DOM和控制台结果。
5. 写入 execution、observation、convergence；如有失败只记录真实偏差并回到 T1/T2，不标记完成。

#### 验收、证据与回滚

全部 R1-R5 有对应证据，且无跨项目/跨租户访问；浏览器白屏、接口500、字段丢失或权限越权均判定失败。

#### 停止和升级条件

环境无法执行 V44 或无法启动服务时，保留构建和静态证据，明确报告运行态缺口，不宣称浏览器验收通过。

## 集成检查

`node scripts/check-flyway-migrations.mjs`、`mvn -pl :ccb-system -am test`、`npm --prefix web run build`、`git diff --check`、当前任务范围检查和真实浏览器路径。

## 控制模型种子

候选被控边界：项目风险列表和右侧抽屉；候选状态变量：riskMode、riskForm、comments、commentSubmitting、loading/error；候选传感器：Maven测试、Vite构建、Flyway检查、API响应、浏览器DOM/控制台；候选执行器：评论接口、风险更新接口和前端抽屉状态。以上均为假设，需在建模和观测阶段验证。

## 风险与用户批准

风险为 V43/V44 顺序、评论范围隔离和只读模式误提交；用户已确认评论不单独鉴权且所有可进入风险页面的用户可发表评论。未批准范围不得扩展。
