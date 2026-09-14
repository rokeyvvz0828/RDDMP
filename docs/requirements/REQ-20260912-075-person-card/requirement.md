---
id: REQ-20260912-075
status: ready
owner: rokeyvvz0828
module: platform/system
---

# 人员卡片组件与架构／开发管理人员展示统一

> 跨模块说明：本需求同时涉及 `platform/system`（新增只读接口）、`frontend/application`（公共组件与 store）、`business/architecture` 与 `business/development`（页面替换）。`module` 头字段取风险最高的 `platform/system`，各模块的写入边界以 `codex-task-scope.yaml` 的 `writable_paths` 为准。

## 业务目标

架构管理与开发管理两个菜单下的每一处人员展示目前形态各不相同：有的是纯文本姓名（`负责人：张伟`），有的是原始数字 ID（`申请人 #123`），有的只有姓名而无法进一步查看联系方式与角色。业务人员在排期、审批、追责时需要在多个页面之间来回切换才能拼出一个人的完整信息。

本需求提供统一的人员卡片：卡片本体按三档显示少量信息（小＝姓名，中＝姓名+电话，大＝姓名+电话+归属团队），桌面悬停或触屏点击后展示完整信息（头像、姓名、账号、电话、归属团队、角色、启用状态），并用它替换上述两个菜单下所有人员展示。

可观察的成功结果：
- 两个菜单下 24 个文件、38 处人员展示全部渲染为人员卡片，不再出现纯文本人员名与 `#123` 原始 ID 混排。
- 桌面悬停约 120ms 出现完整信息浮层；手机 375px 下点击卡片出现同一浮层，无横向滚动。
- 列表页的人员档案请求数由"页面上不同人员数"决定而非"表格行数"。
- 现有 12 处 `UiUserIdentity` 调用点零代码改动即可运行，外观与行为不变。

## 范围

### 本次实施

- `platform/system` 新增只读接口 `POST /api/platform/user-profiles/query`：登录即可访问、强制租户隔离、字段白名单、单次上限 50 个 id、返回 `missingIds`、包含停用用户状态。复用既有 `SystemUserDirectory` 的 `sys_user LEFT JOIN sys_org` 思路，补 `sys_user_role` + `sys_role` 角色查询。
- `frontend/application` 新增 `web/src/api/user-profile.ts`、`web/src/stores/person-directory.ts`、`web/src/components/ui/UiPersonProfileCard.vue`；改造 `web/src/components/ui/UiUserIdentity.vue`（触发器）与 `web/src/styles.css` 的三档与浮层样式。
- `ComponentShowcaseView.vue` 新增三档与三个显示开关的示例区，作为档位与开关行为的可复现验收载体。
- `business/architecture` 与 `business/development` 共 24 个 `.vue` 文件、38 处人员展示替换；`business/architecture` 的 3 个响应 DTO 追加人员 id 字段（`DecisionMatterSummary.proposerId`、`DecisionMaterial.createdBy`、`StandardDocumentDetail.createdBy`）。
- 7 处原始数字 ID 展示改为人员卡片。

### 本次不实施

- 人员选择器（`el-select` 及其下拉项）与 `SubsystemChangePhysicalCard.vue`。
- 系统管理、角色维护、项目管理、测试管理、发布管理、数据迁移等其它菜单下的人员展示。
- 人员编辑、头像上传、角色分配、导入导出。
- 电话脱敏或按权限分级。
- 数据库迁移、虚拟滚动或分页策略优化。
- 引入前端单元测试框架。

## 现状与规则

- 当前入口与形态：架构管理 18 个文件 30 处、开发管理 6 个文件 8 处，形态含表格列、详情描述项、移动卡片、时间线、变更日志、tag 列表。逐处清单见设计文档附录 A。
- 业务规则和边界：人员展示为只读；人员与角色数据属平台数据，业务模块不得直接写或查 `sys_user`；选择器是"选人"控件不属于"展示"，本次不动。
- 角色、权限、数据范围与审计：新接口仅要求登录，但强制 `tenant_id = actor.tenantId()`；跨租户 id 统一落入 `missingIds` 且不暴露存在性；只读接口不写审计表（审计针对写操作）。
- 现有可复用能力：`SystemUserDirectory`（已 join `sys_org`）、`MinioStorageService.presignedUrl`、`com.ccb.system.capability` 公开契约、`web/src/components/ui` 既有组件与 `styles.css` 语义变量、`modules/delivery-showcase` 的页面结构参考。
- 外部系统、附件或敏感信息：无外部系统接入；不涉及生产数据、真实个人信息、口令或密钥；验证数据使用本地 Mock 数据。

## 接口与数据

- API 契约：
  - `POST /api/platform/user-profiles/query`，`@PreAuthorize("isAuthenticated()")`
  - 请求 `{ "userIds": [12, 18, 25] }`：去重、忽略 null、单次上限 50
  - 响应 `data = { profiles: [{ id, username, displayName, mobilePhone, orgName, avatarUrl, roles: [{ id, code, name }], status }], missingIds: [25] }`
  - 超限返回 400；空数组返回空结果；停用用户返回并由 `status` 标记
  - 响应不含 `password_hash`、`tenant_id`、`deleted`、`last_login_at`、`avatar_object_key`
  - 统一 `{ code, data, message }` 结构与 `TraceId.getOrCreate()`
- 架构模块响应追加（均为向后兼容的字段追加，不删不改既有字段）：
  - `ArchitectureDecisionController.MatterSummaryResponse` 追加 `long proposerId`
  - `ArchitectureDecisionController.MaterialResponse` 追加 `long createdBy`
  - `ArchitectureStandardController.DocumentDetailResponse` 追加 `long createdBy`
  - 三者对应的模型（`DecisionMatter.proposerId()`、`MaterialRecord.createdBy()`、`StandardDocument.createdBy()`）与 SQL 已存在，**无需改迁移或既有查询**
- 数据 Owner：`platform/system`（用户、组织、角色）。
- 数据库迁移与存量兼容：**无迁移**；不修改任何已发布 `V*__*.sql`。
- 脱敏输入输出示例：验证使用 `zhangwei / 13800138000 / 基础技术中心 · 平台研发一组 / 架构管理员` 等虚构数据。

## 验收标准

1. R1 三档卡片与 `show-avatar`/`show-phone`/`show-team` 独立开关生效，三档与开关组合可在组件示范页复现。
2. R2 桌面 hover（约 120ms 延迟）与触屏 click 均能打开完整信息浮层。
3. R3 浮层为「头部身份区 + 联系方式／组织归属／角色三个分区 + 角色 chips」，桌面 300px、窄屏 `min(300px, calc(100vw - 24px))`，超长文本换行不省略。
4. R4 接口满足租户隔离、50 上限、字段白名单、`missingIds`、停用用户状态。
5. R5 store 去重合并、50 一批、TTL 10 分钟、失败降级与重试、会话重置；120 个 id 实测 3 批请求，重复调用新增 0 请求。
6. R6 现有 12 处调用点零改动可用，且无可解析 id 时不发请求。
7. R7 24 个文件 38 处全部替换，人员选择器未被改动，人员列固定宽小于 130px 时改为 `min-width`。
8. R8 三处补齐后可悬浮出完整信息。
9. R9 7 处原始 ID 改为人员卡片。
10. R10 六种状态（加载中／加载失败+重试／未找到／无角色／停用／超长文本）可复现，明暗主题可读，1440 与 375 视口无遮挡溢出。
11. R11 组件示范页可见三档与开关组合结果。

## 测试与发布

- 必须执行的测试：
  - `mvn -pl :ccb-system -am test`（新接口单测与 Testcontainers MySQL 租户隔离）
  - `mvn -pl :ccb-architecture,:ccb-development -am test`（响应字段补齐与模块回归）
  - `npm --prefix web run build`（`vue-tsc --noEmit` 类型检查 + 生产构建）
  - `node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260912-075-person-card/codex-task-scope.yaml --working-tree`
  - `git diff --check`
- 上线验证：真实浏览器按设计附录 A 的 38 处逐处走查，覆盖桌面 1440 与手机 375、明暗主题、六种浮层状态、断网重试；并确认 `ModuleView`、`AppLayout`、`ProjectView` 三个既有调用文件未出现在本次 diff 中。
- 回退或补偿：删除新增的 2 个前端文件与 2 个后端主类及对应测试，恢复 `UiUserIdentity.vue` 与 `styles.css`，回滚 3 个响应 DTO 的追加字段与 `web/src/modules/architecture/types.ts` 的追加类型。无数据库迁移、无数据补偿。
- 风险与人工复核人：
  - 公共能力变更（`platform/system` 新增接口、`frontend/application` 公共组件与 store）需 Owner `rokeyvvz0828` 复核兼容策略与回归测试。
  - 电话对已登录用户可见的暴露面需安全评审确认（与既有「系统管理用户列表」「测试管理提出人」一致）。
  - 已知基线故障：`docs/requirements/REQ-20260904-061-release-plan-timeline-instructions/codex-task-scope.yaml` 不是 JSON 兼容 YAML，导致 `node scripts/check-ai-control-layout.mjs` 失败；本次不顺手修复，处理方向待定。

## 设计与计划

- 设计：`docs/engineering-control/designs/2026-09-12-person-card-design.md`（修订 2）
- 实施计划：`docs/engineering-control/plans/2026-09-12-person-card-implementation-plan.md`

需求评审通过且不存在阻塞问题后，将头部 `status` 改为 `ready`。
