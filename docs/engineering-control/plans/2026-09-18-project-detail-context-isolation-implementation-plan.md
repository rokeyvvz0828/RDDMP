# 项目详情上下文隔离修复实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 使所有项目详情请求以详情路由项目为上下文，且不改变顶部项目选择器。

**架构：** 保持后端 `JwtAuthenticationFilter` 的路径与 `X-Project-Id` 一致性校验。仅在项目 API 客户端把已有的 `withProjectContext(projectId)` 显式传给每个 `/api/project/{projectId}` 请求，覆盖 HTTP 拦截器对全局选择器的默认回退。

**技术栈：** Vue 3、TypeScript、Axios、现有 `withProjectContext` 请求配置、Element Plus。

## 状态与来源

- 计划修订：1
- 设计修订：5
- 设计文档：[项目详情上下文隔离修复设计](../designs/2026-09-18-project-detail-context-isolation-design.md)
- 状态：已确认

## 全局约束

- 只修改 REQ-20260917-083 任务范围授权的 `web/src/api/project.ts`、`web/src/api/attachments.ts` 及当前任务文档/账本。
- 不修改 `web/src/api/http.ts`、`web/src/stores/project-context.ts`、后端安全过滤器、数据库、后端权限或审计实现。
- `/api/project/{projectId}` 的 `X-Project-Id` 必须等于路径项目 ID；缺失或不一致仍由服务端拒绝。
- 不调用 `projectContext.select`，不写入 `ccb.current_project_id` 或 `ccb.current_project_ref`。
- 保留所有 API 路径、方法、参数和响应类型；不读取 `.env`，不访问生产环境。

## 文件职责地图

| 文件 | 状态 | 职责与依据 |
| --- | --- | --- |
| `web/src/api/project.ts` | existing | 项目基础信息、设置、阶段、计划、风险、成员、组织、角色、公告、投产日历的 API 封装；现有日历和公告已使用 `withProjectContext(id)`，其余项目详情 API 尚未显式绑定。 |
| `web/src/api/attachments.ts` | existing | 项目附件分类及文件操作 API 封装；其项目路径请求尚未显式绑定详情项目上下文。 |
| `web/src/api/http.ts` | existing, read-only | 请求拦截器支持 `withProjectContext`，在未传配置时回退到全局选择器的持久化项目。 |
| `server/src/platform/security/src/main/java/com/ccb/security/web/JwtAuthenticationFilter.java` | existing, read-only | 继续验证项目路径和 `X-Project-Id` 一致；本计划不修改。 |

## 任务依赖图与并行策略

`T1` 和 `T2` 修改不同文件，本可并行；本次串行执行以先固定项目 API 的统一模式，再按同一模式完成附件客户端。`T3` 在前两项完成后执行。

```text
T1 project API context binding ─┐
                                ├─> T3 integration verification
T2 attachment API context binding ─┘
```

## 需求覆盖表

| 需求 | 任务 | 传感器 |
| --- | --- | --- |
| R7：详情路由项目独立于全局选择器 | T1、T2、T3 | TypeScript 构建、API 客户端静态检查、浏览器网络请求与本地存储检查 |

### T1：项目资源 API 显式绑定详情项目上下文

**需求映射：** R7

**前置任务：** 无

#### 文件边界与接口

- 修改：`web/src/api/project.ts`
- 测试：无现有前端 API 单测目录；使用 TypeScript 生产构建和静态请求配置检查。
- 消费：`withProjectContext(projectContext: number | null): ProjectContextRequestConfig`。
- 产出：每个以 `id: number` 定位 `/project/${id}` 的详情资源 API 都传入 `withProjectContext(id)`；带查询参数的请求在同一配置对象中合并 `params`。

#### 操作步骤、命令和预期信号

- [ ] **步骤 1：记录基线**

  运行：`rg -n "http\.(get|post|put|delete).*`/project/\$\{id\}" web/src/api/project.ts`

  预期：可观察到基础信息、计划、风险、成员等接口没有第三个上下文配置参数，而日历和公告接口已经有。

  证据：保存匹配行与当前工作树状态。

- [ ] **步骤 2：实施最小客户端绑定**

  对所有详情资源 API 统一传入 `withProjectContext(id)`；`getProjectReleaseCalendar`、公告等已有绑定保持原语义，查询参数请求采用 `{ ...withProjectContext(id), params: ... }`。

  预期：API 路径、请求体和返回泛型不变，仅新增请求级 `projectContext` 配置。

- [ ] **步骤 3：静态验收**

  运行：`rg -n "^export function .*\(id: number" web/src/api/project.ts`

  预期：每个项目详情 API 调用的 Axios 配置显式引用 `withProjectContext(id)`；不出现 `projectContext.select` 或本地存储写入。

- [ ] **步骤 4：构建检查点**

  运行：`npm --prefix web run build`

  预期：退出码为 0，TypeScript 与 Vite 均通过。

#### 验收、证据与回退

- 验收：基础信息、设置、阶段、计划、风险、成员、组织、角色、公告和投产日历请求均绑定路径 ID。
- 证据：API 源码差异、静态搜索结果、前端构建输出。
- 回退：只回退 `web/src/api/project.ts` 的请求配置变更，不触及 HTTP 拦截器和后端。

#### 停止和升级条件

- 停止：任一项目详情 API 无法在不更改请求体或路径的情况下传入 `withProjectContext(id)`。
- 升级：修复需要修改 `http.ts`、项目上下文 Store、路由或后端安全过滤器。

### T2：项目附件 API 显式绑定详情项目上下文

**需求映射：** R7

**前置任务：** 无

#### 文件边界与接口

- 修改：`web/src/api/attachments.ts`
- 测试：无现有前端 API 单测目录；使用 TypeScript 生产构建和浏览器项目附件路径。
- 消费：`withProjectContext(projectContext: number | null): ProjectContextRequestConfig`。
- 产出：所有 `/project/${projectId}/attachments` 和 `/project/${projectId}/attachment-categories` 调用使用 `withProjectContext(projectId)`；有 `params`、multipart `headers` 或 `timeout` 的调用保留并合并现有配置。

#### 操作步骤、命令和预期信号

- [ ] **步骤 1：记录基线**

  运行：`rg -n "`/project/\$\{projectId\}/(attachments|attachment-categories)" web/src/api/attachments.ts`

  预期：所有项目附件路径可定位，且当前配置不包含详情项目上下文。

  证据：保存匹配行。

- [ ] **步骤 2：实施最小客户端绑定**

  从 `./http` 同时导入 `withProjectContext`，并在每个项目附件路径请求中合并 `withProjectContext(projectId)`；上传请求保留 multipart 内容类型和 60 秒超时。

  预期：分类创建、文件上传、分类调整、预览、下载和删除均带正确上下文，非项目附件接口保持不变。

- [ ] **步骤 3：构建检查点**

  运行：`npm --prefix web run build`

  预期：退出码为 0，附件 API 调用类型兼容。

#### 验收、证据与回退

- 验收：项目附件和分类操作在顶部项目与详情项目不同的情况下仍发送详情项目 ID。
- 证据：API 源码差异、前端构建输出、T3 浏览器网络证据。
- 回退：只回退 `web/src/api/attachments.ts` 的项目请求配置；不改变文件服务、权限或数据。

#### 停止和升级条件

- 停止：合并请求配置会覆盖 multipart 头、超时或查询参数。
- 升级：附件服务路径不遵守 `/api/project/{projectId}` 校验或需要后端变更。

### T3：分离上下文的端到端验收与范围审计

**需求映射：** R7

**前置任务：** T1、T2

#### 文件边界与接口

- 修改：`.ai-control/requirements/req-20260917-083-project-stage-announcements/*.json`，仅记录真实执行与观测证据。
- 测试：本地前端、已启动本地后端、真实浏览器。
- 消费：T1/T2 的请求级上下文绑定，顶部选择器本地存储键，后端项目路径校验。
- 产出：顶部项目 A、详情项目 B 的可复现请求和 UI 观察记录。

#### 操作步骤、命令和预期信号

- [ ] **步骤 1：选择两条可访问项目记录**

  在本地工作台确认两个项目 ID 不同，并将顶部选择器固定为 A、打开 B 的详情路由。

  预期：页面没有主动改变 `ccb.current_project_id` 或 `ccb.current_project_ref`。

  证据：浏览器本地存储、地址栏和选择器文本。

- [ ] **步骤 2：验证基础信息和计划写入**

  在 B 的详情编辑项目基础信息与一条计划，检查网络请求。

  预期：请求 URL 和 `X-Project-Id` 都是 B，保存不显示“请求项目与当前项目不一致”，顶部仍是 A。

  证据：浏览器请求头、成功反馈和顶部选择器状态。

- [ ] **步骤 3：验证附件读取或操作**

  打开 B 的附件页，读取分类或列表；可用本地虚构文件时上传、调整分类或删除。

  预期：所有项目附件请求 URL 和 `X-Project-Id` 都是 B；不会因 A 而失败。

  证据：浏览器请求头和页面结果；没有可安全操作的附件时记录只读查询证据及限制。

- [ ] **步骤 4：执行范围和代码质量检查**

  运行：`git diff --check`；`node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260917-083-project-stage-announcements/codex-task-scope.yaml --working-tree`

  预期：无本次改动造成的空白错误，范围检查仅接受当前任务授权文件；已有无关工作树扰动单独记录，不回退。

#### 验收、证据与回退

- 验收：A/B 分离场景通过，顶部选择器保持 A，详情请求都使用 B。
- 证据：构建、网络请求、浏览器状态、范围检查与 diff 检查。
- 回退：回退 T1/T2 两个客户端文件；无需数据或服务端回退。

#### 停止和升级条件

- 停止：仅有一个可访问项目，无法建立 A/B 场景，或本地服务不可用。
- 升级：任一正确路径项目上下文仍被安全过滤器拒绝，或请求需要改变公共 HTTP 拦截器。

## 集成检查

执行 `npm --prefix web run build`、`git diff --check` 和当前任务范围检查。浏览器以两个不同的可访问项目执行“顶部 A、详情 B”的基础信息、计划和附件路径，检查每个请求的 `X-Project-Id` 等于 B。

## 控制模型种子

- 状态：`hypotheses-only`。
- 被控边界候选：`project.ts` 与 `attachments.ts` 的 Axios 请求配置。
- 状态变量候选：路由 `projectId`、本地存储中的全局项目 ID、请求 `X-Project-Id`。
- 传感器候选：源码请求配置搜索、Vite 生产构建、浏览器网络请求、本地存储和后端返回。
- 执行器候选：两个 API 客户端中的 `withProjectContext` 配置。
- 扰动候选：已有脏工作树、仅有一个可访问项目、会话过期、附件数据不可写。
- 时延候选：Vite 构建与浏览器请求完成。
- 可证伪假设：`withProjectContext(id)` 在 HTTP 拦截器中优先于持久化全局项目；若浏览器请求头仍为 A，则该假设不成立。

## 风险与用户批准

主要风险是遗漏一个项目路径请求，或合并附件上传配置时覆盖已有 multipart 头和超时。T1/T2 分别审计请求清单，T3 以不同项目的真实请求验证。此计划不包含后端放宽、全局 Store 同步、数据迁移或公共 HTTP 层重构。

用户于 2026-09-18 确认按本计划实施。
