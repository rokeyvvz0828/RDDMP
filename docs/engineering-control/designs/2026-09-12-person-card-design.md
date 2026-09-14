# 人员卡片组件（UiUserIdentity 改造）工程设计

## 文档状态
- 修订：2
- 状态：待确认
- 修订 2 变更（2026-09-12，计划阶段自检发现，需用户重新确认）：
  1. 文件计数更正：替换清单由「22 个文件」更正为 **24 个文件**；开发管理由「4 文件 / 8 处」更正为 **6 文件 / 8 处**（首次统计漏计 `DevelopmentTaskDetailPage.vue` 与 `components/TaskStagePanel.vue`）。替换处数 38 不变。
  2. 新增 R11：在组件示范页新增三档与三个开关的示例，使 R1 的开关验收有可复现位置（原设计把 `ComponentShowcaseView.vue` 仅列为回归对象，导致 R1 的开关行为没有可观测载体）。
  3. 新增约束：人员列的固定宽小于 130px 时改为 `min-width`，避免头像+姓名被挤压。
- 用户确认依据：2026-09-12 当前会话。用户提出"开发人员卡片组件，少量信息可配置、悬停展示完整信息，并替代架构管理与开发管理菜单下所有人员展示"；随后逐项确认：范围只含只读展示（表格列 + 详情描述 + 时间线/评论操作人）、在 `UiUserIdentity.vue` 基础上改造、数据走新增平台级人员档案批量接口（已申报 Owner 获批）、触屏点击触发、三档字段显示在卡片本体上且头像/电话/团队可独立开关、三档形态选"两行堆叠"、浮层选"分区卡片 + 角色 chips"并接受 5 条共同约定、⚠️ 3 处补齐后端字段、段 3/段 4 复核通过。
- 关联需求：`REQ-20260912-075`（`docs/requirements/REQ-20260912-075-person-card/requirement.md`，待随实施计划生成）
- 控制前缀：`req-20260912-075-person-card`

## 目标与成功信号

让架构管理与开发管理两个菜单下的每一处人员展示都变成同一个可复用的人员卡片，使"这个人是谁、怎么联系、属于哪个团队、担任什么角色"在任意列表、详情、时间线处都能就地读到，不再出现纯文本姓名或 `申请人 #123` 这类原始 ID。

成功信号：
- 两个菜单下 38 处人员展示全部渲染为人员卡片，未出现"一部分是卡片、一部分是纯文本"的混排。
- 表格列（小档）与详情/抽屉（中档）在不改变现有页面布局节奏的前提下完成替换，表格列宽与行高不被撑破。
- 桌面悬停 120ms 内出现浮层，浮层含头像、姓名、账号、电话、归属团队、角色与启用状态；手机 375px 下点击卡片出现同一浮层，无横向滚动。
- 列表页批量预取的请求数由"页面上不同人员数"决定，而非"表格行数"。
- 现有 12 处 `UiUserIdentity` 调用点无代码改动即可运行，外观与行为不变。

## 使用者与场景

- 技术架构师 / 架构相关角色（`architecture:view`、`architecture:apply`、`architecture:manage` 等）：在物理子系统、决策事项、标准文档、搭建计划、交付/部署单元、网络工单、子系统变更、实例、资源申请、网络访问等页面查看人员。
- 开发管理角色（`development:task:read`、`development:admin`）：在开发任务列表/详情、阶段面板、工作项看板/列表、变更日志中查看负责人与指定人员。
- 平台管理员（`system:access`）：现有系统管理、角色维护页面的人员展示，作为回归对象而非本次替换对象。
- 平台侧调用方：既有的 `ModuleView`、`AppLayout`、`ProjectView`、`ComponentShowcaseView` 中已有的 `UiUserIdentity` 调用。

## 必须需求与验收条件

| ID | 必须行为 | 验收 | 反例 |
| --- | --- | --- | --- |
| R1 | 三档卡片：`variant` 取 `compact`/`standard`/`full`，分别默认显示「头像+姓名」「头像+姓名+电话」「头像+姓名+电话+团队」；`show-avatar`/`show-phone`/`show-team` 可独立覆盖档位默认值 | 三档在表格单元格（约 130～180px 列宽）内渲染正确；关闭 `show-phone` 后中档不再渲染电话行；关闭 `show-avatar` 后不再渲染头像；三档与三个开关的组合可在组件示范页就地复现 | 三档差异只体现在字号；开关被档位默认值覆盖；关掉开关后仍留占位空隙 |
| R2 | 完整信息浮层：桌面 hover 触发（延迟约 120ms 防误触），`@media (hover: none)` 下改为点击触发；内容含头像、姓名、账号、电话、归属团队、角色、启用状态 | 桌面悬停出现浮层；触屏点击出现同一浮层；浮层内容随数据补全后更新 | 触屏无法打开浮层；浮层只显示组织与角色、缺电话或账号 |
| R3 | 浮层布局：头部身份区 + 「联系方式」「组织归属」「角色」三个分区，分区之间细虚线分隔，角色用可换行 chip；桌面宽 300px，窄屏 `min(300px, calc(100vw - 24px))` | 桌面与 375px 视口下浮层不超出视口、不产生横向滚动；long 团队名与角色名换行而非省略 | 浮层固定 300px 在窄屏溢出；角色用逗号拼接的一串文字；超长文本被省略号截断 |
| R4 | 平台人员档案批量查询接口：`POST`、登录即可访问、强制租户隔离、字段白名单、单次 ≤50 个 id、返回 `missingIds`、包含停用用户状态 | 跨租户 id 落入 `missingIds` 且不报错；超过 50 个 id 返回 400；响应体不含 `password_hash`/`tenant_id`/`deleted`/`last_login_at`/`avatar_object_key`；停用用户返回并带 `status` | 跨租户用户被返回；跨租户查询抛错并暴露存在性；响应体带任何非白名单字段 |
| R5 | 前端目录 store：`ensure(ids)` 入队并在同一微任务内去重合并、按 50 分批；同一 id 飞行中不重复入队；TTL 10 分钟缓存；单批失败只标记该批并支持重试（自动重试最多 1 次）；项目切换/登出/租户变化时清空 | 一个含 100 行、每行 1 个唯一负责人的列表页总请求数为 2 批；重复渲染同一用户不产生新请求；断网后浮层显示失败态与重试入口 | 每行各发一次请求；同一用户重复请求；失败后缓存被永久标记为错误且无法恢复 |
| R6 | 向后兼容：现有 12 处 `UiUserIdentity` 调用点零代码改动即可运行；组件在无可解析 `userId` 时不发起任何请求，行为与现状一致 | `ModuleView`、`AppLayout`、`ProjectView`、`ComponentShowcaseView` 四个文件不改动即可编译并通过浏览器验收；无 `userId` 且无 `user` 时只渲染兜底文本、不弹浮层 | 现有调用点被迫传入新 prop 才能编译；无 id 时仍发请求或弹出空浮层 |
| R7 | 替换清单落地：两个菜单下 24 个文件、38 处人员展示全部替换；人员选择器（`el-select`）保持不动 | 逐处走查通过（见附录 A）；`TaskFormDrawer`、`WorkItemFormDrawer`、`SubsystemChangePhysicalCard` 以及各页 `el-select` 选人控件未被改动 | 漏替换其中任意一处；把选人控件也改成卡片导致多选 tag 与远程搜索行为变化 |
| R8 | 补齐 3 处缺失 `userId` 的后端字段：`DecisionMatterListItem.proposerId`、`DecisionMaterial.createdBy`、`StandardDocumentDetail.createdBy` | 这 3 处也能悬浮出完整信息；架构模块对应查询与测试通过 | 这 3 处只能显示纯文本、永远无法悬浮 |
| R9 | 原始 ID 展示改为人员卡片：`申请人 #123`、`操作人 #123`、`下线操作人 ID` 等共 7 处 | 这 7 处渲染为「头像 + 姓名」，不再出现 `#123`；用户不存在时显示「未找到该人员信息」而非空白 | 仍渲染原始数字 ID；用户不存在时整块空白或抛错 |
| R10 | 全状态与主题：浮层覆盖加载中、加载失败+重试、未找到、无角色、停用、超长文本六种状态；明暗主题可读；桌面 1440 与手机 375 视口无遮挡与溢出 | 六种状态逐一可复现；`data-theme` 切换后浮层与卡片文字对比度可读；表格固定列内浮层不被 `overflow:hidden` 裁剪 | 加载中清空已显示的姓名；失败态无重试入口；暗色主题下浮层文字不可读；固定列内浮层被裁剪 |
| R11 | 组件示范页新增人员卡片示例区：三档各一个实例 + `show-avatar`/`show-phone`/`show-team` 三个开关的即时切换演示 | `ComponentShowcaseView` 可见三档与开关组合结果，作为 R1 的可复现验收载体与后续参考 | 只替换业务页面而不留下任何可复现的档位/开关示例 |

## 不变量与约束

- 现有 `UiUserIdentity` 12 处调用点的外部行为不变，这是本次改动唯一的强制兼容边界。
- 组件在无可解析 `userId` 时不发起任何网络请求。
- 查询接口只返回白名单字段，且不返回 `password_hash`、`tenant_id`、`deleted`、`last_login_at`、`avatar_object_key`。
- 跨租户用户 id 不得泄漏存在性，统一进入 `missingIds`。
- 业务模块（`business/architecture`、`business/development`）不得新增对 `sys_user` 的直接 SQL；平台数据只通过 `com.ccb.system.capability` 公开契约访问。
- 人员展示统一走 `UiUserIdentity`，不建立平行组件。
- 人员列的固定宽小于 130px 时改为 `min-width`（下限 130px），避免头像与姓名被挤压；不调整非人员列的宽度。
- 本次无数据库迁移；Flyway 脚本零改动。
- `platform/system` 与 `frontend/application` 的 `codex_default_access` 均为 `read_only`，本次改动属公共能力变更，已获 Owner 审批，兼容策略与回归测试在段 4 与本文"验证策略"中声明。

## 非目标

- 不替换人员选择器（`el-select` 及其 `el-option` 下拉项）。
- 不替换系统管理、角色维护、项目管理、测试管理、发布管理、数据迁移等其它菜单下的人员展示。
- 不新增人员编辑、头像上传、角色分配、导入导出能力。
- 不做虚拟滚动或表格分页策略优化。
- 不引入前端单元测试框架（`web/package.json` 目前没有测试脚本）。
- 不给电话做脱敏或按权限分级——电话可见性沿用现有「系统管理用户列表」与「测试管理提出人」的暴露面。
- 不改动 `docs/integration/frontend-ui-contract.md` 之外的既有公共契约文档。

## 方案比较与选择

`selected_approach`：**方案乙 —— 触发器 + 浮层卡片 + 共享目录 store 三层分离**。

| | 甲：组件自治 | 乙（选中） | 丙：组件纯展示，页面预取传入 |
| --- | --- | --- | --- |
| 取数位置 | 组件内 hover 时单点请求 | `stores/person-directory.ts` 合并批量请求 | 各调用页面自己预取后传 prop |
| 列表页请求量 | 每行 1 次，100 行最坏 100 次 | 每页 1～2 次（去重 + 50/批） | 每个页面各写一套，22 处重复 |
| 改动文件数 | 最少 | 中 | 最多 |
| 缓存/去重/重试 | 需手写 | store 内集中实现 | 各页面各自实现 |

排除理由：甲在大列表必然出现请求放大，是最容易被观测打回的误差；丙把成本平摊到 22 个调用点并重复实现缓存与重试，与"优先复用公共能力"的规约相反。

同一层级的其余决策见"决策记录"。

## 架构边界与组件职责

边界内：
- `server/src/platform/system`：新增人员档案批量查询接口与角色查询能力。
- `web/src/api/user-profile.ts`：接口封装。
- `web/src/stores/person-directory.ts`：批量合并、去重、缓存、失败降级。
- `web/src/components/ui/UiUserIdentity.vue`：触发器（改造）。
- `web/src/components/ui/UiPersonProfileCard.vue`：浮层内容（新增）。
- `web/src/styles.css`：三档尺寸语义与浮层样式。
- `web/src/modules/architecture`、`web/src/modules/development`：38 处模板替换 + 3 处 DTO 字段补齐。
- `web/src/views/ComponentShowcaseView.vue`：新增三档与开关示例区。

边界外：人员选择器、其它菜单的人员展示、人员写操作、数据库迁移。

| 组件 | 单一职责 | 依赖 |
| --- | --- | --- |
| `UiUserIdentity.vue` | 渲染触发器（头像 + 姓名 + 按档位的电话/团队），在需要补全时把 id 交给 store；不直接调用接口 | `person-directory` store、`UiPersonProfileCard` |
| `UiPersonProfileCard.vue` | 渲染浮层内容与六种状态，从 store 读数据 | `person-directory` store |
| `person-directory` store | 入队、同微任务去重合并、50/批、TTL 缓存、失败标记与重试、会话重置 | `api/user-profile` |
| `api/user-profile.ts` | 批量查询接口的类型化封装 | `api/http` |
| 人员档案接口 | 租户隔离的只读批量查询，白名单字段 | `SystemUserDirectory`、`sys_org`、`sys_user_role`、`sys_role` |

## 接口、数据和状态流

### 后端接口契约

```
POST /api/platform/user-profiles/query
@PreAuthorize("isAuthenticated()")

请求：{ "userIds": [12, 18, 25] }        // 去重，忽略 null，单次上限 50
响应：{ code, message, data: {
          profiles: [{ id, username, displayName, mobilePhone, orgName, avatarUrl,
                       roles: [{ id, code, name }], status }],
          missingIds: [25] } }
```

- 租户隔离：`tenant_id = actor.tenantId()`；跨租户 id 落入 `missingIds`。
- 数据来源：复用 `SystemUserDirectory` 的 `sys_user LEFT JOIN sys_org`，补 `sys_user_role JOIN sys_role`（`status = 1 AND deleted = 0`）。
- 停用用户也返回（历史责任人可能是停用账号），由 `status` 标记。
- `userIds.size() > 50` 返回 400；空数组返回空结果（非错误）。
- 只读接口不写审计表（项目规约要求审计针对写操作）。
- 头像沿用既有 `storage.presignedUrl(avatar_object_key)` 生成 `avatarUrl`，不下发 `avatar_object_key`。

### 前端数据流

```
页面模板 → UiUserIdentity(userId, variant, 可选 user/profile)
              │ 需要电话/团队/角色且本地无
              ▼
     stores/person-directory.ts  ensure(ids)
              │ 微任务内去重 + 合并，50/批
              ▼
     api/user-profile.ts → POST /api/platform/user-profiles/query
              │
              ▼
     Map<id, { status: loading|ready|missing|error, profile }>（响应式，TTL 10 分钟）
              │
              ▼
     UiPersonProfileCard 读缓存渲染
```

### 组件契约

`UiUserIdentity.vue`（现有 12 处调用点零改动即可运行）：

| prop | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `user` | `UserProfile \| null` | `null` | 现有 prop，保持；`userId` 缺省时回落到 `user.id` |
| `userId` | `number \| string \| null` | `null` | 新增；有可解析 id 才允许发起补全请求 |
| `variant` | `'compact' \| 'standard' \| 'full'` | `standard` | 三档 |
| `showAvatar` | `boolean` | `true` | 独立开关 |
| `showPhone` / `showTeam` | `boolean` | 按 `variant` 推导 | 独立开关，可覆盖档位默认 |
| `size` / `showName` / `showProfile` | 既有类型 | 保持 | 兼容 |
| `fallbackName` | `string` | 保持现有语义 | 无姓名时兜底 |

## 错误、降级与恢复

| 条件 | 外部行为 | 恢复 |
| --- | --- | --- |
| 电话或团队字段缺失 | 该行不渲染，不留空行、不显示占位；浮层内显示「—」 | 数据补齐后自动出现 |
| 角色为空 | 渲染虚线 chip「暂无角色」 | 角色变更后随缓存 TTL 刷新 |
| 用户不存在或跨租户 | 浮层显示「未找到该人员信息」，卡片本体仍用已知姓名渲染 | 无需恢复 |
| 接口 4xx/5xx | 浮层显示「人员信息加载失败」+「重试」链接；卡片本体不受影响 | 点击重试或下次 `ensure` 重发，自动重试最多 1 次 |
| 加载中 | 头部先用已知姓名渲染，下方 2 行骨架，**不清空已显示内容** | 数据到达后替换骨架 |
| 无 `userId` 且无 `user` | 纯文本兜底，不发请求，`showProfile` 自动降级为 `false` | 无需恢复 |
| 项目切换 / 登出 / 租户变化 | store 清空缓存，避免跨项目脏数据 | 重新 `ensure` |
| 触屏 | `@media (hover: none)` 下 `trigger="click"` | 点击浮层外部关闭 |

## 安全、性能、兼容性与运维

- **安全**：接口要求登录并强制租户隔离；响应只含白名单字段；不返回口令、租户、删除标记、登录信息与对象存储 key；跨租户 id 不暴露存在性。暴露面与现有「系统管理用户列表」「测试管理提出人」一致，已含在 Owner 审批范围。
- **性能**：同微任务去重合并 + 50/批 + 10 分钟 TTL，请求数由"页面上不同人员数"决定；同一 id 飞行中不重复入队。
- **兼容性**：`UiUserIdentity` 现有 prop 全部保留，默认值保持现有观感；无可解析 id 时不发请求，行为与改造前一致；不新增平行组件。
- **运维**：无数据库迁移、无新配置项、无外部系统接入、无新增定时任务。接口为无状态只读查询，可水平扩展；失败只影响浮层内容，不影响页面主流程。

## 验证策略

| 需求 | 验证信号 | 验证方式 |
| --- | --- | --- |
| R1 | 三档渲染结果与开关覆盖正确 | 真实浏览器走查三档 + 三个开关组合；`vue-tsc` 类型检查 |
| R2 | 桌面 hover 与触屏点击都能打开浮层 | 桌面鼠标悬停 + 移动视口点击；`@media (hover: none)` 行为对照 |
| R3 | 浮层在 1440 与 375 视口不溢出 | 浏览器视口切换 + 长团队名/长角色名数据 |
| R4 | 租户隔离、上限、白名单、`missingIds` | `mvn -pl :ccb-system -am test` 接口单测 |
| R5 | 请求批次与缓存行为 | store 行为验收：100 行列表实测请求数；断网模拟失败与重试 |
| R6 | 现有调用点零改动可用 | 四个现有调用文件不改动即编译通过 + 浏览器回归 |
| R7 | 38 处全部替换、选择器未动 | 按附录 A 逐处走查；`git diff` 核对未触及选人控件 |
| R8 | 3 处补齐后可悬浮 | 架构模块 `mvn -pl :ccb-architecture -am test` + 页面走查 |
| R9 | 7 处原始 ID 变为卡片 | 页面走查 + 构造用户不存在场景 |
| R10 | 六种状态 + 主题 + 视口 | 断网、构造空角色/停用用户/超长文本，切换明暗主题与视口 |
| R11 | 示范页可见三档与三个开关的组合结果 | 浏览器打开组件示范页走查 |

必执行命令：
1. `mvn -pl :ccb-system -am test`
2. `mvn -pl :ccb-architecture,:ccb-development -am test`
3. `npm --prefix web run build`（含 `vue-tsc --noEmit`）
4. `node scripts/check-all-governance.mjs`
5. `node scripts/check-codex-scope.mjs --scope <本次 codex-task-scope.yaml>`

说明：前端无单元测试框架，前端门禁由类型检查、生产构建与真实浏览器验收承担；未执行的验证不得描述为通过。

## 假设、未知项与决策记录

假设：
- 用户已申报并取得 Owner 对 `platform/system` 与 `frontend/application` 两处公共能力变更的批准（用户口述确认）。
- `sys_user.mobile_phone` 在当前租户内对已登录用户可见，与既有页面一致。
- Element Plus `el-popover` 的 teleport 定位在表格固定列与横向滚动容器内正确。

未知项（非阻塞）：
- 大列表（100 行、多人员列）预取的实际批次数。影响：若超过 1 批，需要补充页面级批大小策略。决策者：研发。

决策记录：

| ID | 决策 | 原因 | 来源 |
| --- | --- | --- | --- |
| D1 | 范围只含只读展示（表格列 + 详情描述 + 时间线/评论操作人），不含人员选择器 | 选择器是"选人"控件，`el-select` 内嵌卡片会牵动多选 tag、远程搜索与失效人员提示，收益低回归面大 | 用户确认"A 没问题" |
| D2 | 采用方案乙（触发器 + 浮层卡片 + 共享目录 store） | 避免大列表请求放大，取数逻辑集中可测，符合优先复用公共能力 | 用户确认"方案 1 可以"后确认方案乙 |
| D3 | 三档字段显示在卡片本体上，因此必须预取 | 用户明确"最小的有人员姓名，中等还有人员电话，大型还有人员归属团队" | 用户确认"A，顺便留出可配置的空间" |
| D4 | 三档形态采用"两行堆叠" | 窄表格列下电话与团队可换行，信息层级清晰 | 用户确认"B 挺好" |
| D5 | 浮层采用"分区卡片 + 角色 chips"，并接受 5 条共同约定 | 扫读顺序自然、多角色可控、新增字段有位置可放 | 用户确认"可以" |
| D6 | 触屏点击触发（`@media (hover: none)`） | hover 在触屏不存在，`design-h5.md` 要求移动端功能对等 | 用户确认"可以" |
| D7 | 3 处缺 `userId` 的展示点补齐后端字段 | 避免"有的卡片能悬停、有的不能"的认知不一致 | 用户确认"补齐后端字段" |
| D8 | 在组件示范页新增三档与开关示例 | 原设计把该页仅列为回归对象，导致 R1 的开关行为缺少可复现载体；该页本就是组件示例页，新增示例不影响业务页面 | 计划阶段自检发现，修订 2 一并请用户确认 |
| D9 | 人员列固定宽小于 130px 时改为 `min-width` | 小档含 24px 头像，100px 列宽会挤压姓名 | 计划阶段自检发现，修订 2 一并请用户确认 |

## 风险与回退原则

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| 新接口把电话暴露给所有已登录用户 | 数据暴露面扩大 | 仅同租户 + 白名单字段 + 不含口令与登录信息；暴露面与既有页面一致，已含在 Owner 审批范围 |
| 列表预取增加请求量 | 页面加载变慢 | 去重 + 50/批 + 10 分钟缓存；验收时实测请求数，超过 1 批再补批大小策略 |
| 固定列/横向滚动表格内浮层定位异常 | 浮层被裁剪或错位 | 依赖 Element Plus teleport；如异常只调整 `placement`，不改公共组件契约 |
| 38 处替换出现遗漏或误改选人控件 | 视觉混排或交互回归 | 附录 A 逐处走查 + `git diff` 核对 + 现有 12 处调用点回归 |
| 补齐 3 处 DTO 字段影响架构模块既有查询 | 架构模块回归失败 | 只加字段不改语义；`mvn -pl :ccb-architecture -am test` 全量回归 |

回退原则：删除 2 个新增前端文件、恢复 `UiUserIdentity.vue` 与 `styles.css`、回滚后端接口与 3 个 DTO 字段。无数据库迁移、无数据补偿。

## 附录 A：替换清单（24 文件 / 38 处）

分档规则：**小**＝表格列、时间线、变更日志、tag 列表（列宽 90～120px）；**中**＝桌面详情描述项、抽屉身份块、独立卡片；**大**＝移动端卡片的人员项、`SubsystemParticipants` 系统负责人块。

### 架构管理（18 文件 / 30 处）

| 文件 | 位置 | 现状 | 档位 |
| --- | --- | --- | --- |
| `PhysicalSubsystemPage.vue` | :240 表格「负责人」 | 纯姓名 | 小 |
| | :96 详情「负责人」 | 纯姓名 | 中 |
| | :247 移动卡片「负责人」 | 纯姓名 | 大 |
| `DecisionMatterListPage.vue` | :152 表格「提出人」 | 纯姓名 | 小（R8 补字段） |
| `DecisionMatterDetailPage.vue` | :554 详情「提出人」 | 纯姓名 | 中 |
| | :621 材料时间线「创建人」 | 文本拼接 | 小（R8 补字段） |
| | :641 评审「参与人」 | `el-tag` 姓名 | 小 |
| | :653 行动项「责任人」 | 纯文本 | 小 |
| `StandardDocumentListPage.vue` | :321 表格「发布人」 | 纯姓名 | 小 |
| | :353 详情「创建人」 | 纯姓名 | 小（R8 补字段） |
| `PlanDetailPage.vue` | :1317 表格「操作人」 | `userName()` 文本 | 小 |
| | :1526 表格「操作人」 | 原始 prop 值 | 小 |
| | :1437 描述「责任人」 | `userName()` 文本 | 中 |
| | :1468 检查项「完成人」 | `userName()` 文本 | 小 |
| `components/SubsystemParticipants.vue` | :134 系统负责人身份块 | 自定义 `el-avatar` + `strong` | 大 |
| | :160 只读「其他参与人员」 | `el-tag` 姓名 | 小 |
| `components/DeliveryUnitDetailDrawer.vue` | :56 详情「创建人」 | 文本 | 小 |
| `components/DeploymentUnitDetailDrawer.vue` | :50 详情「创建人」 | 文本 | 小 |
| `components/PersonalTaskBoard.vue` | :30「负责人：X」 | 文本 | 中 |
| `DeploymentUnitImportPage.vue` | :269 历史「操作人 X」 | 文本拼接 | 小 |
| `NetworkWorkOrderDetailPage.vue` | :325 详情「申请人」 | `#123` 原始 ID | 中 |
| | :405 时间线「操作人 #123」 | 原始 ID | 小 |
| `NetworkWorkOrderListPage.vue` | :229 移动卡片「申请人 #123」 | 原始 ID | 大 |
| `SubsystemChangeApplicationDetailPage.vue` | :279 详情「申请人」 | `userLabel()` 文本 | 中 |
| `SubsystemChangeApplicationListPage.vue` | :195 移动卡片「申请人 #123」 | 原始 ID | 大 |
| `components/SubsystemChangeTimeline.vue` | :32 时间线「操作人 #123」 | 原始 ID | 小 |
| `InstanceListPage.vue` | :631 详情「下线操作人 ID」 | 原始 ID | 中 |
| `ResourceRequestPage.vue` | :1313 详情「申请人」 | `userLabel()` 文本 | 中 |
| `NetworkAccessPage.vue` | :1292 移动卡片「申请人 #123」 | 原始 ID | 大 |
| | :1584 详情「申请人 #123」 | 原始 ID | 中 |

### 开发管理（6 文件 / 8 处）

| 文件 | 位置 | 现状 | 档位 |
| --- | --- | --- | --- |
| `DevelopmentTaskListPage.vue` | :65 表格「负责人」 | `owner.name` | 小 |
| | :74 移动卡片「负责人」 | `owner.name` | 大 |
| `DevelopmentTaskDetailPage.vue` | :81 详情「任务负责人」 | `owner.name` | 中 |
| `components/TaskStagePanel.vue` | :114 需求承接「负责人」 | `owner.name` | 小 |
| `components/WorkItemsView.vue` | :105 表格「指定人员」 | `assignee.name` | 小 |
| | :118 移动卡片「指定人员」 | `assignee.name` | 大 |
| `components/WorkItemCard.vue` | :27「指定人员」 | `assignee.name` | 大 |
| `components/TaskChangeLog.vue` | :59「操作人 · 动作」 | 文本 | 小 |

### 新增示例（不属替换）

- `web/src/views/ComponentShowcaseView.vue`：在既有的「用户身份」卡片内扩充三档实例与三个开关的即时切换演示（对应 R11）。

### 不替换

- 人员选择器：`components/TaskFormDrawer.vue`、`components/WorkItemFormDrawer.vue`、`components/SubsystemChangePhysicalCard.vue`，以及 `PlanDetailPage`、`PlanListPage`、`DecisionMatterDetailPage` 中的 `el-select` 选人控件。
- `PhysicalSubsystemPage.vue:239`「负责团队」是团队名而非人员。

### 回归对象（不改动，仅验证）

`web/src/views/ModuleView.vue`、`web/src/views/AppLayout.vue`、`web/src/views/ProjectView.vue`、`web/src/views/ComponentShowcaseView.vue` 中共 12 处现有 `UiUserIdentity` 调用。
