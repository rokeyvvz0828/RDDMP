# 人员卡片组件实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 把架构管理与开发管理两个菜单下的 24 个文件、38 处人员展示统一替换为同一个可配置三档、桌面悬停/触屏点击展示完整信息的人员卡片。

**架构：** 新增一个登录即可访问的平台级只读接口 `POST /api/platform/user-profiles/query`，批量返回人员档案白名单字段；前端用 `stores/person-directory.ts` 承担去重合并、50 个一批、TTL 缓存与失败降级；`UiUserIdentity.vue` 只做触发器并把 id 交给 store，`UiPersonProfileCard.vue` 负责浮层内容与六种状态；业务页面只做模板替换。

**技术栈：** Java 17 / Spring Boot 3.4.4 / JdbcTemplate / JUnit 5 + Mockito + Testcontainers MySQL 8.4；Vue 3 / TypeScript / Vite / Element Plus / Pinia。

## 状态与来源

- 计划修订：1
- 设计修订：2
- 设计文档：`docs/engineering-control/designs/2026-09-12-person-card-design.md`
- 机器可读设计与交接包：`.ai-control/requirements/req-20260912-075-person-card/design.json`、`handoff.json`
- 关联需求：`docs/requirements/REQ-20260912-075-person-card/requirement.md`
- 控制前缀：`req-20260912-075-person-card`
- 状态：可移交（用户已批准设计与计划；基线故障按「已知例外」处理，本次不修复）

## 全局约束

- 只修改 `docs/requirements/REQ-20260912-075-person-card/codex-task-scope.yaml` 中 `writable_paths` 覆盖的文件。
- 不修改已发布的 `V*__*.sql`；本次**无数据库迁移**。
- 保留现有 Java 包名 `com.ccb.*`；不调整目录、包名与既有业务逻辑。
- `platform/system` 与 `frontend/application` 属公共能力，`public_capability_change.required=true`，兼容策略见 T3 的 R6 验收，回归测试见 T10。
- 人员展示统一走 `UiUserIdentity`，**不建立平行组件**。
- 人员列固定宽小于 130px 时改为 `min-width`；**不修改非人员列宽度**。
- 不替换人员选择器（`el-select`）与 `SubsystemChangePhysicalCard.vue`。
- 前端不加死颜色，一律使用 `web/src/styles.css` 的语义变量（`--brand`、`--line`、`--text`、`--muted`、`--panel-bg`、`--panel-muted`、`--success`、`--shadow`）。
- 接口响应必须走统一 `{ code, data, message }` 结构与 `TraceId.getOrCreate()`。
- 未执行的验证不得写入完成报告。

---

## 文件职责地图

| 路径 | 状态 | 职责 | 证据 |
| --- | --- | --- | --- |
| `server/src/platform/system/src/main/java/com/ccb/system/service/UserProfileService.java` | candidate-new | 租户范围内批量查询人员档案与角色的 JDBC 只读服务 | 目录 `server/src/platform/system/src/main/java/com/ccb/system/service/` 已存在 |
| `server/src/platform/system/src/main/java/com/ccb/system/web/UserProfileController.java` | candidate-new | `POST /api/platform/user-profiles/query` 的入参校验、上限、响应组装 | `web/src/platform/system/.../web/` 已存在且已有 4 个 Controller |
| `server/src/platform/system/src/test/java/com/ccb/system/service/UserProfileServiceMySqlTest.java` | candidate-new | 租户隔离、角色 JOIN、停用用户、软删除过滤 | `capability/ProjectMemberReferenceLockMySqlTest.java` 已有 Testcontainers 先例 |
| `server/src/platform/system/src/test/java/com/ccb/system/web/UserProfileControllerTest.java` | candidate-new | 权限注解、上限 400、空数组、`missingIds`、白名单字段 | `change/web/SubsystemChangeApplicationControllerTest.java` 已有 standalone MockMvc 先例 |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/decision/web/ArchitectureDecisionController.java` | existing（修改 :535、:544、:598 附近） | 为事项列表与材料响应补 `proposerId` / `createdBy` | 已读取；模型 `DecisionMatter.proposerId()`、`MaterialRecord.createdBy()` 已存在 |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/standard/web/ArchitectureStandardController.java` | existing（修改 :266 附近） | 为文档详情响应补 `createdBy` | 已读取；`StandardDocument.createdBy()` 已存在 |
| `server/src/modules/architecture/src/test/java/com/ccb/architecture/web/ArchitecturePersonReferenceFieldTest.java` | candidate-new | 断言三处响应确实暴露人员 id | `src/test/java/com/ccb/architecture/web/` 目录尚不存在，属 candidate-new |
| `web/src/types/system.ts` | existing（追加类型） | `PersonProfile` / `PersonRole` 类型 | 已读取，`UserProfile` 位于 :15 |
| `web/src/api/user-profile.ts` | candidate-new | 接口封装 | `web/src/api/` 已有 14 个模块化 api 文件 |
| `web/src/stores/person-directory.ts` | candidate-new | 去重合并、批大小、TTL、失败降级、会话重置 | `web/src/stores/` 已有 4 个 store，用 setup 语法 |
| `web/src/components/ui/UiUserIdentity.vue` | existing（改造） | 触发器：头像 + 姓名 + 按档位的电话/团队；入队；兼容既有 prop | 已完整读取 |
| `web/src/components/ui/UiPersonProfileCard.vue` | candidate-new | 浮层内容与六种状态 | 同目录已有 20 个 `Ui*` 组件 |
| `web/src/styles.css` | existing（修改 :1187-1197） | 三档尺寸语义与浮层分区样式 | 已读取该区间 |
| `web/src/views/ComponentShowcaseView.vue` | existing（修改 :120） | 三档与三个开关的示例区 | 已读取 |
| `web/src/modules/architecture/` 下 18 个 `.vue` | existing（修改） | 38 处替换中的 30 处 | 见附录 A |
| `web/src/modules/development/` 下 6 个 `.vue` | existing（修改） | 38 处替换中的 8 处 | 见附录 A |

## 任务依赖图与并行策略

```
并行组 1: T1（后端接口）        T5（架构模块响应字段）
并行组 2: T2（前端 api + store）      ← 依赖 T1
并行组 3: T3（卡片组件）              ← 依赖 T2
并行组 4: T4（示范页）  T6  T7  T8  T9   ← T6/T7 依赖 T3+T5；T8/T9 依赖 T3
并行组 5: T10（集成验收）
```

并行安全性：T4/T6/T7/T8/T9 的写入路径两两不相交，且不共享新增接口，可并行。单人实施时建议按 T4→T6→T7→T8→T9 串行，保持每个任务一个可回滚提交。

## 需求覆盖表

| 需求 | 覆盖任务 |
| --- | --- |
| R1 | T3, T4 |
| R2 | T3 |
| R3 | T3 |
| R4 | T1 |
| R5 | T2 |
| R6 | T3, T10 |
| R7 | T6, T7, T8, T9, T10 |
| R8 | T5, T6, T7 |
| R9 | T6, T7, T8 |
| R10 | T3, T10 |
| R11 | T4 |

---

### T1：平台人员档案批量查询接口

**需求映射：** R4

**前置任务：** 无

**文件：**
- 新建：`server/src/platform/system/src/main/java/com/ccb/system/service/UserProfileService.java`
- 新建：`server/src/platform/system/src/main/java/com/ccb/system/web/UserProfileController.java`
- 测试：`server/src/platform/system/src/test/java/com/ccb/system/service/UserProfileServiceMySqlTest.java`
- 测试：`server/src/platform/system/src/test/java/com/ccb/system/web/UserProfileControllerTest.java`

**接口：**
- 消费：`com.ccb.security.model.AuthUser(long id, long tenantId, ...)`；`com.ccb.infrastructure.storage.MinioStorageService.presignedUrl(String objectKey)`；`com.ccb.common.api.ApiResponse.success(T, String)`；`com.ccb.common.trace.TraceId.getOrCreate()`
- 产出：`POST /api/platform/user-profiles/query`，请求 `{ "userIds": [1,2] }`，响应 `data = { profiles: [{ id, username, displayName, mobilePhone, orgName, avatarUrl, roles: [{ id, code, name }], status }], missingIds: [3] }`

- [ ] **步骤 1：建立失败检查（服务层租户隔离）**

新建 `UserProfileServiceMySqlTest`，`@Testcontainers` + `MySQLContainer<>("mysql:8.4")`，建表 `sys_user`、`sys_org`、`sys_role`、`sys_user_role`，插入两个租户的用户后断言第二个租户的 id 不在结果里：

```java
@Test void 只返回当前租户的人员() {
    var rows = service.query(actorTenant1, List.of(1L, 2L));   // 2L 属租户 2
    assertThat(rows.stream().map(UserProfileService.Row::id)).containsExactly(1L);
}
```

运行：`mvn -pl :ccb-system -am test -Dtest=UserProfileServiceMySqlTest`
预期：编译失败 `cannot find symbol: class UserProfileService`
证据：保存命令与报错首行

- [ ] **步骤 2：实现 `UserProfileService`**

```java
@Service
public class UserProfileService {
    public record RoleItem(long id, String code, String name) {}
    public record Row(long id, String username, String displayName, String mobilePhone,
                      Long orgId, String orgName, String avatarObjectKey, int status, List<RoleItem> roles) {}

    private static final String USER_SQL = """
            SELECT u.id, u.username, u.display_name, COALESCE(u.mobile_phone, '') AS mobile_phone,
                   u.org_id, COALESCE(o.org_name, '') AS org_name, u.avatar_object_key, u.status
            FROM sys_user u
            LEFT JOIN sys_org o ON o.id = u.org_id AND o.tenant_id = u.tenant_id AND o.deleted = 0
            WHERE u.tenant_id = ? AND u.deleted = 0 AND u.id IN (%s)
            ORDER BY u.id
            """;
    private static final String ROLE_SQL = """
            SELECT ur.user_id, r.id, r.role_code, r.role_name
            FROM sys_user_role ur
            JOIN sys_role r ON r.id = ur.role_id AND r.tenant_id = ur.tenant_id
            WHERE ur.tenant_id = ? AND ur.user_id IN (%s) AND r.status = 1 AND r.deleted = 0
            ORDER BY r.id
            """;
    // 关键逻辑：先按 id 查用户，再一次性按 id 查角色并在内存里聚合，避免 N+1。
    // 强制 tenant_id = actor.tenantId()；不返回 password_hash、tenant_id、deleted、last_login_at。
    public List<Row> query(AuthUser actor, List<Long> userIds) { /* ... */ }
}
```

`placeholders(int n)` 用 `String.join(",", Collections.nCopies(n, "?"))`；`userIds` 为空时直接返回 `List.of()` 且不发 SQL。

- [ ] **步骤 3：运行服务层测试**

运行：`mvn -pl :ccb-system -am test -Dtest=UserProfileServiceMySqlTest`
预期：通过；额外断言覆盖：跨租户不返回、软删除用户不返回、停用用户**返回**且 `status=0`、组织为空时 `orgName` 为 `''`、角色为空时 `roles` 为空列表
证据：保存测试报告路径与通过数

- [ ] **步骤 4：建立失败检查（控制器权限与上限）**

新建 `UserProfileControllerTest`，用 `MockMvcBuilders.standaloneSetup(new UserProfileController(mockService))`：

```java
@Test void 声明登录即可访问且不要求 system_access() throws Exception {
    PreAuthorize anno = UserProfileController.class.getAnnotation(PreAuthorize.class);
    assertThat(anno.value()).isEqualTo("isAuthenticated()");
}

@Test void 超过五十个标识返回四百() throws Exception {
    // userIds 长度 51
    mockMvc.perform(post("/api/platform/user-profiles/query")
            .contentType(MediaType.APPLICATION_JSON).content(json))
        .andExpect(status().isBadRequest());
}
```

运行：`mvn -pl :ccb-system -am test -Dtest=UserProfileControllerTest`
预期：编译失败 `cannot find symbol: class UserProfileController`
证据：保存报错首行

- [ ] **步骤 5：实现 `UserProfileController`**

```java
@RestController
@RequestMapping("/api/platform/user-profiles")
@PreAuthorize("isAuthenticated()")
public class UserProfileController {
    public record QueryRequest(List<Long> userIds) {}
    public record RoleResponse(long id, String code, String name) {}
    public record ProfileResponse(long id, String username, String displayName, String mobilePhone,
                                  String orgName, String avatarUrl, List<RoleResponse> roles, int status) {}
    public record QueryResponse(List<ProfileResponse> profiles, List<Long> missingIds) {}

    @PostMapping("/query")
    public ApiResponse<QueryResponse> query(@AuthenticationPrincipal AuthUser actor,
                                            @RequestBody QueryRequest request) { /* ... */ }
}
```

约束：`request.userIds()` 去重并忽略 null；去重后大小 > 50 抛 `IllegalArgumentException`（项目既有异常映射为 400，需在实现时确认 `ArchitectureExceptionAdvice` 的同类做法在 `ccb-system` 中由哪个 Advice 承担，若无则显式抛 `com.ccb.common.exception` 中的参数异常类型）；空列表返回 `QueryResponse(List.of(), List.of())`；`missingIds` = 请求去重后不在结果里的 id；`avatarUrl` 由 `MinioStorageService.presignedUrl` 生成，`avatar_object_key` 为空时不调用。

- [ ] **步骤 6：运行局部与相关回归**

运行：
```bash
mvn -pl :ccb-system -am test -Dtest=UserProfileServiceMySqlTest,UserProfileControllerTest
mvn -pl :ccb-system -am test
```
预期：两次均通过，0 个失败
证据：保存两次命令与 `Tests run:` 汇总行

- [ ] **步骤 7：建立提交检查点**

```bash
git add server/src/platform/system/src/main/java/com/ccb/system/service/UserProfileService.java \
        server/src/platform/system/src/main/java/com/ccb/system/web/UserProfileController.java \
        server/src/platform/system/src/test/java/com/ccb/system/service/UserProfileServiceMySqlTest.java \
        server/src/platform/system/src/test/java/com/ccb/system/web/UserProfileControllerTest.java
git commit -m "feat(postcard): 新增平台人员档案批量查询接口"
```

**验收检查：** 跨租户隔离；上限 400；空数组成功；`missingIds` 正确；响应不含 `password_hash`/`tenant_id`/`deleted`/`last_login_at`/`avatar_object_key`；停用用户返回并带 `status`；类级注解恰为 `isAuthenticated()`

**回滚：** 删除两个新建主类与两个测试文件，`git revert` 该提交

**停止条件：** 若 `ccb-system` 中不存在把参数校验异常映射为 400 的既有机制，且新增 Advice 会触碰公共行为时停止并升级

**升级条件：** 若 Owner 在评审中要求改路径、要求电话脱敏或要求按数据范围过滤非项目成员，停止实施并回到设计

---

### T2：前端接口封装与人员目录 store

**需求映射：** R5

**前置任务：** T1

**文件：**
- 新建：`web/src/api/user-profile.ts`
- 新建：`web/src/stores/person-directory.ts`
- 修改：`web/src/types/system.ts`（追加类型）

**接口：**
- 消费：T1 的 `POST /api/platform/user-profiles/query` 契约；`web/src/api/http.ts` 的 axios 实例（`baseURL: '/api'`）
- 产出：`PersonProfile`、`PersonRole` 类型；`usePersonDirectoryStore()` 暴露 `ensure(ids)`、`get(id)`、`stateOf(id)`、`retry(ids)`、`reset()`

- [ ] **步骤 1：追加类型**

在 `web/src/types/system.ts` 末尾追加：

```ts
export interface PersonRole { id: number; code: string; name: string }
export interface PersonProfile {
  id: number
  username: string
  displayName: string
  mobilePhone: string | null
  orgName: string | null
  avatarUrl: string | null
  roles: PersonRole[]
  status: number
}
export interface PersonProfileQueryResult { profiles: PersonProfile[]; missingIds: number[] }
```

- [ ] **步骤 2：写接口封装**

`web/src/api/user-profile.ts`：

```ts
import http from './http'
import type { ApiResponse } from '../types/auth'
import type { PersonProfileQueryResult } from '../types/system'

export function queryUserProfiles(userIds: Array<number | string>) {
  return http.post<ApiResponse<PersonProfileQueryResult>>('/platform/user-profiles/query', {
    userIds: userIds.map(Number).filter(id => Number.isFinite(id))
  }).then(r => r.data.data)
}
```

- [ ] **步骤 3：写 store 并建立可判别的批大小检查**

`web/src/stores/person-directory.ts` 用 setup 语法，导出常量便于验收：

```ts
export const PROFILE_BATCH_SIZE = 50
export const PROFILE_CACHE_TTL_MS = 10 * 60 * 1000
export const PROFILE_MAX_AUTO_RETRY = 1

export const usePersonDirectoryStore = defineStore('person-directory', () => {
  const entries = ref(new Map<string, { status: 'loading'|'ready'|'missing'|'error'; profile: PersonProfile | null; fetchedAt: number; retries: number }>())
  const pending = new Set<string>()
  let flushScheduled = false
  function ensure(ids: Array<number | string | null | undefined>) { /* 规范化 + 过滤已 fresh + 入 pending + queueMicrotask(flush) */ }
  function flush() { /* pending 按 PROFILE_BATCH_SIZE 分片，并行发 queryUserProfiles */ }
  function get(id) { /* 读 entries，命中 TTL 内 ready 返回 profile */ }
  function stateOf(id) { /* idle | loading | ready | missing | error */ }
  function retry(ids) { /* 清 error 标记后重新 ensure */ }
  function reset() { entries.value = new Map(); pending.clear() }
  return { ensure, get, stateOf, retry, reset }
})
```

约束：`ensure` 对已在 `pending` 或已有 TTL 内 `ready` 的 id 直接跳过；单批失败只把该批 id 标记为 `error`，`retries < PROFILE_MAX_AUTO_RETRY` 时自动重发一次；`missingIds` 对应条目标记为 `missing`（不算错误）。

- [ ] **步骤 4：类型检查**

运行：`npm --prefix web run build`
预期：通过。此时 store 尚无调用方，若 `vue-tsc` 因未使用导出报警，按项目 `tsconfig` 既有策略处理（不得关闭严格检查）
证据：保存命令与退出码

- [ ] **步骤 5：在真实浏览器的控制台验证批大小与去重**

前置：本地开发环境已启动（`./scripts/dev.sh`），浏览器已登录。
在控制台执行：

```js
const s = usePersonDirectoryStore()          // 通过 Vue devtools 暴露的 app 实例获取
s.ensure(Array.from({ length: 120 }, (_, i) => i + 1))
```

运行：观察 Network 面板中 `/api/platform/user-profiles/query` 的请求条数
预期：**3** 条（ceil(120 / 50)）；随后重复调用同一批 id，预期**新增 0 条**（去重与 TTL 生效）
证据：保存请求条数截图或 HAR 片段

- [ ] **步骤 6：建立提交检查点**

```bash
git add web/src/types/system.ts web/src/api/user-profile.ts web/src/stores/person-directory.ts
git commit -m "feat(person-card): 新增人员档案接口封装与目录 store"
```

**验收检查：** 批大小与去重符合预期；失败只影响该批；`reset()` 清空；`npm --prefix web run build` 通过

**回滚：** 删除两个新建文件并还原 `web/src/types/system.ts` 的追加段

**停止条件：** 若 `vue-tsc` 报错来自既有代码而非本次改动，停止并记录基线故障，不得顺手修复

**升级条件：** 若 120 个 id 的实测批次数不是 3，说明合并逻辑失效，升级并重新建模

---

### T3：人员卡片触发器与浮层组件

**需求映射：** R1, R2, R3, R6, R10

**前置任务：** T2

**文件：**
- 修改：`web/src/components/ui/UiUserIdentity.vue`
- 新建：`web/src/components/ui/UiPersonProfileCard.vue`
- 修改：`web/src/styles.css:1187-1197`

**接口：**
- 消费：`usePersonDirectoryStore` 的 `ensure` / `get` / `stateOf` / `retry`
- 产出：`UiUserIdentity` 的 prop 契约（`user`、`userId`、`variant`、`showAvatar`、`showPhone`、`showTeam`、`size`、`showName`、`showProfile`、`fallbackName`），供 T4/T6/T7/T8/T9 使用

- [ ] **步骤 1：建立基准（改造前行为）**

打开组件示范页并记录当前 `UiUserIdentity` 的外观截图（头像 32px + 姓名，hover 出「所属组织 / 角色」）。
证据：保存改造前截图，作为 R6 的对照基线

- [ ] **步骤 2：改造 `UiUserIdentity.vue`**

关键实现要点（完整片段在实施时按现有文件结构写入）：
- 计算 `resolvedId = toId(props.userId ?? props.user?.id)`；`toId` 接受 `number | string`，非有限数字返回 `null`。
- `needsDirectory = resolvedId !== null`；`profile = computed(() => store.get(resolvedId))`。
- 只在 `needsDirectory && (!hasLocal(phone) || !hasLocal(team))` 时调用 `store.ensure([resolvedId])`（放在 `onMounted` 与 `watch(resolvedId)` 中，不在渲染函数里调用）。
- 触发器结构按已确认的「两行堆叠」：`<span class="ui-user-identity ui-user-identity--{variant}">` + 头像 + `<span class="lines">`（第一行姓名，第二行电话，第三行团队）。
- 当 `resolvedId === null` 时 `showProfile` 强制为 `false`，不发请求、不渲染 `el-popover`（R6 硬边界）。
- 浮层触发：`<el-popover :trigger="touchTrigger" ...>`，`touchTrigger` 由 `window.matchMedia('(hover: none)')` 决定为 `'click'` 或 `'hover'`，`show-after` 桌面为 `120`。
- 显示开关：`showAvatar` 默认 `true`；`showPhone` 默认 `variant !== 'compact'`；`showTeam` 默认 `variant === 'full'`；显式传入时优先。

- [ ] **步骤 3：新增 `UiPersonProfileCard.vue`**

按已确认的「分区卡片 + 角色 chips」：头部身份区（44px 头像 + 姓名 + 账号 + 启用/停用 badge），随后「联系方式」「组织归属」「角色」三个分区，分区之间 `border-top: 1px dashed var(--line)`。属性 `wrap-anywhere` 用 `overflow-wrap: anywhere`。角色为空时渲染虚线 chip「暂无角色」。状态渲染：
- `stateOf === 'loading'`：头部用传入姓名渲染，下方 2 行骨架（`el-skeleton`），不清空头部。
- `stateOf === 'missing'`：显示「未找到该人员信息」。
- `stateOf === 'error'`：显示「人员信息加载失败」+「重试」链接，点击调用 `store.retry([id])`。

- [ ] **步骤 4：更新 `styles.css`**

替换 `:1187-1197` 的 `.ui-user-identity` / `.ui-user-profile` 区块为：
- 保留 `.ui-user-identity` 的 `display:inline-flex` 与既有 `strong` 省略号规则，追加 `--compact/--standard/--full` 三档的 `gap` 与字号差异；
- 新增 `.ui-user-identity__lines { display:grid; gap:2px; min-width:0 }`、`.ui-user-identity__sub { color:var(--muted); font-size:12px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis }`；
- 新增 `.ui-person-card` 系列类（头部、分区、chips），颜色全部走语义变量；
- 移除不再使用的 `.ui-user-profile` 规则前，确认 `web/src` 内无其他引用（`grep -rn "ui-user-profile" web/src`）。
- 追加 `@media (max-width: 760px)` 覆盖：浮层 `width: min(300px, calc(100vw - 24px))`。

- [ ] **步骤 5：局部验证**

运行：
```bash
grep -rn "ui-user-profile" web/src
npm --prefix web run build
```
预期：`grep` 无输出或仅剩新组件；构建通过
证据：保存两条命令输出

- [ ] **步骤 6：浏览器验证三档、开关与 R6 兼容边界**

在组件示范页之外，先用现有调用点验证 R6：打开开发管理任务列表（不修改任何调用文件）确认姓名与头像渲染正常、hover 出浮层。
证据：保存截图与浏览器控制台无报错

- [ ] **步骤 7：建立提交检查点**

```bash
git add web/src/components/ui/UiUserIdentity.vue web/src/components/ui/UiPersonProfileCard.vue web/src/styles.css
git commit -m "feat(person-card): 改造 UiUserIdentity 并新增人员档案浮层"
```

**验收检查：** R1 三档与开关；R2 桌面 hover 与触屏 click；R3 浮层分区与宽度；R6 无可解析 id 时不发请求；R10 六种状态与明暗主题

**回滚：** `git revert` 该提交；浮层与样式全部内聚在本次新增文件与 `styles.css` 的该区间

**停止条件：** 若 Element Plus `el-popover` 在表格固定列内被裁剪且调整 `placement` 无效时停止

**升级条件：** 若 R6 回归失败（现有调用点行为变化），立即停止并回到设计

---

### T4：组件示范页三档与开关示例

**需求映射：** R11

**前置任务：** T3

**文件：**
- 修改：`web/src/views/ComponentShowcaseView.vue:120`（既有「用户身份」卡片）

**接口：**
- 消费：T3 的 `UiUserIdentity` prop 契约
- 产出：无（终端展示任务）

- [ ] **步骤 1：在既有「用户身份」卡片内追加示例**

在同一 `el-card` 内 `UiAvatarUpload` 之前追加：
- 三档各一个实例：`<UiUserIdentity :user-id="demoUser.id" variant="compact" />`、`variant="standard"`、`variant="full"`；
- 三个开关：用局部 `ref`（`showAvatar` / `showPhone` / `showTeam`）绑定 `el-switch`，作用于同一个 `variant="full"` 实例；
- 一段 `showcase-note` 说明档位默认值与开关覆盖关系。

- [ ] **步骤 2：构建**

运行：`npm --prefix web run build`
预期：通过
证据：保存退出码

- [ ] **步骤 3：浏览器走查三档与开关组合**

打开组件示范页，依次切换三个开关并截图，确认：关闭 `show-phone` 后 `full` 档不渲染电话行且不留空隙；关闭 `show-avatar` 后头像消失且姓名不塌陷。
证据：保存至少 3 张截图

- [ ] **步骤 4：建立提交检查点**

```bash
git add web/src/views/ComponentShowcaseView.vue
git commit -m "docs(person-card): 组件示范页补充三档与开关示例"
```

**验收检查：** 三档可见；三个开关即时生效；明暗主题均可读

**回滚：** `git revert`；改动局限在单个卡片内

**停止条件：** 若示范页需要引入新的公共样式类才能成立，停止并回到设计（示范页应复用 T3 已有类）

**升级条件：** 无

---

### T5：架构模块三处响应字段补齐

**需求映射：** R8

**前置任务：** 无

**文件：**
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/decision/web/ArchitectureDecisionController.java`（`MatterSummaryResponse` 约 :535、`MaterialResponse` 约 :598）
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/standard/web/ArchitectureStandardController.java`（`DocumentDetailResponse` 约 :266）
- 测试：`server/src/modules/architecture/src/test/java/com/ccb/architecture/web/ArchitecturePersonReferenceFieldTest.java`
- 修改：`web/src/modules/architecture/types.ts`（`DecisionMatterSummary.proposerId`、`DecisionMaterial.createdBy`、`StandardDocumentDetail.createdBy`）

**接口：**
- 消费：`DecisionMatter.proposerId()`、`MaterialRecord.createdBy()`、`StandardDocument.createdBy()`（三者均已存在，**无需改 SQL 与模型**）
- 产出：三个响应 DTO 新增 `long proposerId` / `long createdBy`，供 T6/T7 的页面取 `userId`

- [ ] **步骤 1：建立失败检查**

```java
@Test void 事项列表响应暴露提出人标识() {
    var matter = /* 构造 DecisionMatter，proposerId = 21 */;
    assertThat(ArchitectureDecisionController.MatterSummaryResponse.from(matter).proposerId()).isEqualTo(21L);
}

@Test void 材料响应暴露创建人标识() { /* MaterialRecord.createdBy() = 31 */ }
@Test void 标准文档详情响应暴露创建人标识() { /* StandardDocument.createdBy() = 41 */ }
```

运行：`mvn -pl :ccb-architecture -am test -Dtest=ArchitecturePersonReferenceFieldTest`
预期：编译失败，提示 `cannot find symbol: method proposerId()`
证据：保存报错首行

- [ ] **步骤 2：实施最小变更**

三处均为「在 record 组件列表追加一个字段 + 在 `from()` 的构造调用对应位置补一个实参」。不得改动字段顺序中的既有项语义，不得改 SQL。

- [ ] **步骤 3：同步前端类型**

在 `web/src/modules/architecture/types.ts` 的 `DecisionMatterSummary` 追加 `proposerId: number`，`DecisionMaterial` 追加 `createdBy: number`，`StandardDocumentDetail` 追加 `createdBy: number`。

- [ ] **步骤 4：运行局部与相关回归**

运行：
```bash
mvn -pl :ccb-architecture -am test -Dtest=ArchitecturePersonReferenceFieldTest
mvn -pl :ccb-architecture -am test
npm --prefix web run build
```
预期：三次均通过
证据：保存三次命令与结果汇总

- [ ] **步骤 5：建立提交检查点**

```bash
git add server/src/modules/architecture/src/main/java/com/ccb/architecture/decision/web/ArchitectureDecisionController.java \
        server/src/modules/architecture/src/main/java/com/ccb/architecture/standard/web/ArchitectureStandardController.java \
        server/src/modules/architecture/src/test/java/com/ccb/architecture/web/ArchitecturePersonReferenceFieldTest.java \
        web/src/modules/architecture/types.ts
git commit -m "feat(architecture): 响应补齐提出人与创建人标识"
```

**验收检查：** 三个响应确实返回 id；既有字段值不变；架构模块全量测试通过

**回滚：** `git revert` 该提交

**停止条件：** 若发现某处模型实际没有 id（与勘察结论不符），停止并升级——那意味着需要改 SQL 与模型，超出本任务边界

**升级条件：** 若架构模块全量测试出现既有失败，作为基线故障如实记录并升级，不得顺手修复

---

### T6：架构管理替换（物理子系统、参与人员、交付与部署单元、个人任务板）

**需求映射：** R7, R8, R9

**前置任务：** T3, T5

**文件（5 个，8 处）：**
- 修改：`web/src/modules/architecture/PhysicalSubsystemPage.vue`（:96 中、:240 小、:247 大）
- 修改：`web/src/modules/architecture/components/SubsystemParticipants.vue`（:134 大、:160 小）
- 修改：`web/src/modules/architecture/components/DeliveryUnitDetailDrawer.vue`（:56 小）
- 修改：`web/src/modules/architecture/components/DeploymentUnitDetailDrawer.vue`（:50 小）
- 修改：`web/src/modules/architecture/components/PersonalTaskBoard.vue`（:30 中）

**接口：**
- 消费：`UiUserIdentity`（T3）；`PhysicalSubsystem.ownerUserId`、`SubsystemParticipation.ownerUserId` 与 `participants[].userId`、`DeliveryUnit.createdBy`、`DeploymentUnit.createdBy`、`PlanTask.ownerUserId`
- 产出：无（终端展示任务）

- [ ] **步骤 1：页内引入组件**

页面文件加 `import UiUserIdentity from '../../components/ui/UiUserIdentity.vue'`；`components/` 下文件加 `'../../../components/ui/UiUserIdentity.vue'`。路径已按既有 import 核实。

- [ ] **步骤 2：按档位替换 8 处**

统一替换范式：

```vue
<!-- 表格列（小档；固定宽 < 130px 时改 min-width） -->
<el-table-column label="负责人" min-width="130">
  <template #default="scope">
    <UiUserIdentity :user-id="scope.row.ownerUserId" :fallback-name="scope.row.ownerDisplayName" variant="compact" />
  </template>
</el-table-column>

<!-- 详情描述项（中档） -->
<div><dt>负责人</dt><dd><UiUserIdentity :user-id="detail.ownerUserId" :fallback-name="detail.ownerDisplayName" variant="standard" /></dd></div>

<!-- 移动卡片（大档） -->
<div><dt>负责人</dt><dd><UiUserIdentity :user-id="row.ownerUserId" :fallback-name="row.ownerDisplayName" variant="full" /></dd></div>

<!-- 参与人员 tags（小档） -->
<UiUserIdentity v-for="person in otherSelected" :key="person" :user-id="person" variant="compact" />
```

`SubsystemParticipants.vue:134-135` 的自定义 `el-avatar` + `strong` 身份块整体替换为 `variant="full"` 的卡片，并保留其右侧「默认参与」`el-tag` 与下方的说明文案（`负责人自动参与，不在下方重复添加…` 必须原样保留，那是业务提示而非装饰）。

- [ ] **步骤 3：构建与自检**

运行：
```bash
npm --prefix web run build
grep -n "ownerDisplayName\|el-avatar" web/src/modules/architecture/PhysicalSubsystemPage.vue web/src/modules/architecture/components/SubsystemParticipants.vue
```
预期：构建通过；`grep` 结果中人员相关的纯文本渲染已消失，`el-avatar` 仅剩非人员用途（若有）
证据：保存两条输出

- [ ] **步骤 4：浏览器走查**

覆盖：物理子系统列表「负责人」列与详情抽屉、系统负责人身份块、参与人员只读态、交付单元详情抽屉、部署单元详情抽屉、个人任务板。桌面与手机视口各一遍。
证据：每个页面至少一张截图

- [ ] **步骤 5：建立提交检查点**

```bash
git add web/src/modules/architecture/PhysicalSubsystemPage.vue \
        web/src/modules/architecture/components/SubsystemParticipants.vue \
        web/src/modules/architecture/components/DeliveryUnitDetailDrawer.vue \
        web/src/modules/architecture/components/DeploymentUnitDetailDrawer.vue \
        web/src/modules/architecture/components/PersonalTaskBoard.vue
git commit -m "refactor(architecture): 物理子系统与交付单元人员展示改用人员卡片"
```

**验收检查：** 8 处替换完成；表格列未被挤压；「默认参与」等业务提示保留

**回滚：** `git revert` 该提交

**停止条件：** 若某处当前只有姓名没有 id 且不在 R8 的三处清单内，停止并升级（说明设计清单有遗漏）

**升级条件：** 若 `SubsystemParticipants` 的可编辑语义（谁能维护名单）因替换被改变，立即停止

---

### T7：架构管理替换（决策事项、标准文档、搭建计划）

**需求映射：** R7, R8, R9

**前置任务：** T3, T5

**文件（4 个，11 处）：**
- 修改：`web/src/modules/architecture/DecisionMatterListPage.vue`（:152 小，用 T5 新增的 `proposerId`）
- 修改：`web/src/modules/architecture/DecisionMatterDetailPage.vue`（:554 中、:621 小、:641 小、:653 小）
- 修改：`web/src/modules/architecture/StandardDocumentListPage.vue`（:321 小、:353 小，用 T5 新增的 `createdBy`）
- 修改：`web/src/modules/architecture/PlanDetailPage.vue`（:1317 小、:1526 小、:1437 中、:1468 小）

**接口：**
- 消费：`UiUserIdentity`（T3）；T5 新增的 `proposerId` / `createdBy`；`DecisionMatterDetail.firstHandlerId`、`submitterId`；`DecisionReview` 参与人 `userId`；`StandardDocumentSummary.publishedBy`；`PlanTask.ownerUserId`、`operatorUserId`、`completedBy`
- 产出：无（终端展示任务）

- [ ] **步骤 1：替换表格列与描述项**

沿用 T6 的三种范式。`DecisionMatterDetailPage:621` 的时间线特别注意：**保留时间戳文本**，把创建人拆成独立卡片元素，不在时间线内堆两行卡片：

```vue
<el-timeline-item v-for="item in materials" :key="item.id" placement="top">
  <template #timestamp>
    <span>{{ formatDateTime(item.createdAt) }}</span>
    <UiUserIdentity :user-id="item.createdBy" variant="compact" />
  </template>
</el-timeline-item>
```

- [ ] **步骤 2：替换 `PlanDetailPage` 的 `userName()` 文本渲染**

把 `{{ userName(row.operatorUserId) }}`、`{{ userName(currentTask.ownerUserId) }}`、`{{ userName(item.completedBy) }}` 改为卡片。替换后若 `userName()` 不再被引用，删除该函数与只服务于它的 `users` 映射（`PlanDetailPage.vue:833`、`:843`），并确认删除后 `npm --prefix web run build` 通过；**不得**删除仍被 `el-select` 的 `:label` 使用的数据源。

- [ ] **步骤 3：替换参与人 tags 与行动项责任人**

`DecisionMatterDetailPage:641` 的评审参与人 tags、`:653` 的行动项责任人文本改为小档卡片。`:794` 的 `el-input` 与 `:776` 的 `el-select` **不动**（输入控件不是展示）。

- [ ] **步骤 4：构建与自检**

运行：
```bash
npm --prefix web run build
grep -n "userName(" web/src/modules/architecture/PlanDetailPage.vue
```
预期：构建通过；`grep` 无输出（函数已删除）或有输出但全部为 `el-select` `:label` 用途
证据：保存输出

- [ ] **步骤 5：浏览器走查**

覆盖：决策事项列表「提出人」列与详情「提出人」、材料时间线创建人、评审参与人、行动项责任人；标准文档列表「发布人」与详情「创建人」；搭建计划详情的责任人、完成人、两处操作人列，以及新建计划向导（确认选人控件仍正常）。
证据：每个页面至少一张截图，含向导选人控件对照图

- [ ] **步骤 6：建立提交检查点**

```bash
git add web/src/modules/architecture/DecisionMatterListPage.vue \
        web/src/modules/architecture/DecisionMatterDetailPage.vue \
        web/src/modules/architecture/StandardDocumentListPage.vue \
        web/src/modules/architecture/PlanDetailPage.vue
git commit -m "refactor(architecture): 决策、标准与计划页面人员展示改用人员卡片"
```

**验收检查：** 11 处替换完成；3 处 R8 场景可悬浮出完整信息；选人控件未被改动

**回滚：** `git revert` 该提交

**停止条件：** 若删除 `userName()` 导致选人控件下拉项失去文案，停止并回退该删除动作

**升级条件：** 若 `DecisionMatterDetail` 的时间线结构不支持在 timestamp 槽内放两个元素，停止并回到设计确认降级形态

---

### T8：架构管理替换（网络、变更、实例、资源申请、导入）

**需求映射：** R7, R9

**前置任务：** T3

**文件（9 个，11 处）：**
- 修改：`web/src/modules/architecture/DeploymentUnitImportPage.vue`（:269 小）
- 修改：`web/src/modules/architecture/NetworkWorkOrderDetailPage.vue`（:325 中、:405 小）
- 修改：`web/src/modules/architecture/NetworkWorkOrderListPage.vue`（:229 大）
- 修改：`web/src/modules/architecture/SubsystemChangeApplicationDetailPage.vue`（:279 中）
- 修改：`web/src/modules/architecture/SubsystemChangeApplicationListPage.vue`（:195 大）
- 修改：`web/src/modules/architecture/components/SubsystemChangeTimeline.vue`（:32 小）
- 修改：`web/src/modules/architecture/InstanceListPage.vue`（:631 中）
- 修改：`web/src/modules/architecture/ResourceRequestPage.vue`（:1313 中）
- 修改：`web/src/modules/architecture/NetworkAccessPage.vue`（:1292 大、:1584 中）

**接口：**
- 消费：`UiUserIdentity`（T3）；`NetworkWorkOrder.applicantId`、`SubsystemChangeApplication.applicantId`、`NetworkAccessApplication.applicantId`、`ResourceRequest.applicantId`、时间线 `operatorId`、`InstanceDetail.offlinedBy`、`DeploymentUnitImportBatch.createdBy`
- 产出：无（终端展示任务）

- [ ] **步骤 1：替换 7 处原始 ID 展示（R9）**

`申请人 #{{ row.applicantId }}`、`操作人 #{{ item.operatorId }}`、`下线操作人 ID` 等改为：

```vue
<div><dt>申请人</dt><dd><UiUserIdentity :user-id="workOrder.applicantId" variant="standard" /></dd></div>
<span>第 {{ item.businessRound }} 轮 · <UiUserIdentity :user-id="item.operatorId" variant="compact" /></span>
```

- [ ] **步骤 2：替换其余 4 处文本展示**

`SubsystemChangeApplicationDetailPage` 与 `ResourceRequestPage` 中的 `userLabel(id)` 文本改为卡片。替换后若 `userLabel()` 变为未引用则删除；其中包含 `auth.user?.id` 回退分支，删除前确认该回退语义已由 store 的 `missing` 态覆盖（当前登录用户必然能查到自己的档案），若不成立则保留回退。

- [ ] **步骤 3：把「下线操作人 ID」标签改为「下线操作人」**

`InstanceListPage.vue:631` 的 `label` 文案随替换一并去掉「ID」二字，避免标签与内容语义不一致。

- [ ] **步骤 4：构建与自检**

运行：
```bash
npm --prefix web run build
grep -rn "applicantId }}\|operatorId }}\|offlinedBy }}" web/src/modules/architecture
```
预期：构建通过；`grep` 无输出（不再有裸 ID 插值）
证据：保存输出

- [ ] **步骤 5：浏览器走查**

覆盖：部署单元导入历史、网络工单详情与移动卡片、子系统变更详情与移动卡片与时间线、实例详情、资源申请详情、网络访问详情与移动卡片。
证据：每个页面至少一张截图；R9 的 7 处逐一截图

- [ ] **步骤 6：建立提交检查点**

```bash
git add web/src/modules/architecture/DeploymentUnitImportPage.vue \
        web/src/modules/architecture/NetworkWorkOrderDetailPage.vue \
        web/src/modules/architecture/NetworkWorkOrderListPage.vue \
        web/src/modules/architecture/SubsystemChangeApplicationDetailPage.vue \
        web/src/modules/architecture/SubsystemChangeApplicationListPage.vue \
        web/src/modules/architecture/components/SubsystemChangeTimeline.vue \
        web/src/modules/architecture/InstanceListPage.vue \
        web/src/modules/architecture/ResourceRequestPage.vue \
        web/src/modules/architecture/NetworkAccessPage.vue
git commit -m "refactor(architecture): 网络与变更页面人员展示改用人员卡片并替换原始ID"
```

**验收检查：** 11 处替换完成；7 处原始 ID 消失；其他列的固定宽未被改动

**回滚：** `git revert` 该提交

**停止条件：** 若某处的 `applicantId` 在类型上可能为 `null`/`undefined`，停止并确认组件对空 id 的降级渲染符合 R6

**升级条件：** 无

---

### T9：开发管理替换

**需求映射：** R7, R9

**前置任务：** T3

**文件（6 个，8 处）：**
- 修改：`web/src/modules/development/DevelopmentTaskListPage.vue`（:65 小、:74 大）
- 修改：`web/src/modules/development/DevelopmentTaskDetailPage.vue`（:81 中）
- 修改：`web/src/modules/development/components/TaskStagePanel.vue`（:114 小）
- 修改：`web/src/modules/development/components/WorkItemsView.vue`（:105 小、:118 大）
- 修改：`web/src/modules/development/components/WorkItemCard.vue`（:27 大）
- 修改：`web/src/modules/development/components/TaskChangeLog.vue`（:59 小）

**接口：**
- 消费：`UiUserIdentity`（T3）；`DevelopmentTask.owner.id`（`string` 类型）、`WorkItem.assignee.id`（`string`）、`DevelopmentChange.actor.id`（`string`）
- 产出：无（终端展示任务）

- [ ] **步骤 1：替换时传入字符串 id**

开发模块的用户 id 是**字符串**（后端 `UserView(String id, String name)`）。`UiUserIdentity` 的 `userId` 接受 `number | string`，替换时保持字符串不转数字：

```vue
<el-table-column label="负责人" min-width="130">
  <template #default="{ row }"><UiUserIdentity :user-id="row.owner.id" :fallback-name="row.owner.name" variant="compact" /></template>
</el-table-column>
```

- [ ] **步骤 2：替换变更日志的操作人**

`TaskChangeLog.vue:59` 的 `{{ event.actor.name }} · 动作` 改为小档卡片后接原动作文本，`objectLabels` 拼接逻辑不变。

- [ ] **步骤 3：确认选人控件未被改动**

运行：
```bash
git diff --stat web/src/modules/development/components/TaskFormDrawer.vue web/src/modules/development/components/WorkItemFormDrawer.vue
npm --prefix web run build
```
预期：`git diff --stat` 无输出；构建通过
证据：保存两条输出

- [ ] **步骤 4：浏览器走查**

覆盖：开发任务列表「负责人」列与移动卡片、任务详情「任务负责人」、阶段面板需求承接、工作项看板卡片与列表、变更日志。桌面与手机视口各一遍。
证据：每个页面至少一张截图

- [ ] **步骤 5：建立提交检查点**

```bash
git add web/src/modules/development/DevelopmentTaskListPage.vue \
        web/src/modules/development/DevelopmentTaskDetailPage.vue \
        web/src/modules/development/components/TaskStagePanel.vue \
        web/src/modules/development/components/WorkItemsView.vue \
        web/src/modules/development/components/WorkItemCard.vue \
        web/src/modules/development/components/TaskChangeLog.vue
git commit -m "refactor(development): 开发管理人员展示改用人员卡片"
```

**验收检查：** 8 处替换完成；字符串 id 未被错误转成数字；两个表单抽屉零改动

**回滚：** `git revert` 该提交

**停止条件：** 若无任何开发任务数据可走查（空列表），停止并先解决数据前置，不得以"无法验证"结项

**升级条件：** 若字符串 id 走查出现 id 解析失败（浮层恒为「未找到」），升级并检查 `toId` 实现

---

### T10：集成验收与回归

**需求映射：** R6, R7, R10

**前置任务：** T4, T5, T6, T7, T8, T9

**文件：**
- 修改：`.ai-control/requirements/req-20260912-075-person-card/*.json`（观测与收敛证据）

**接口：**
- 消费：全部前序任务的产物
- 产出：`observation-*.json`、`convergence.json` 等账本证据

- [ ] **步骤 1：类型检查与生产构建**

运行：`npm --prefix web run build`
预期：通过，0 个类型错误
证据：保存命令与退出码

- [ ] **步骤 2：后端全量测试**

运行：
```bash
mvn -pl :ccb-system -am test
mvn -pl :ccb-architecture,:ccb-development -am test
```
预期：通过
证据：保存两次 `Tests run:` 汇总

- [ ] **步骤 3：现有 12 处调用点回归（R6）**

`grep -rn "UiUserIdentity" web/src --include=*.vue` 确认这四个文件（`ModuleView.vue`、`AppLayout.vue`、`ProjectView.vue`、`ComponentShowcaseView.vue`）**未出现在本次 git diff 的改动列表中**（`ComponentShowcaseView.vue` 因 T4 属预期例外，只允许新增示例、不得改动既有调用行）。

运行：`git diff --name-only <本次首个提交>..HEAD -- web/src/views/ModuleView.vue web/src/views/AppLayout.vue web/src/views/ProjectView.vue`
预期：无输出
证据：保存输出；再在浏览器中走查这四个页面的人员展示

- [ ] **步骤 4：替换清单逐处走查（R7）**

按设计附录 A 的 38 行逐处核对渲染结果，确认：无纯文本与卡片混排、无原始 ID、表格列未被挤压。

- [ ] **步骤 5：六种状态与主题验收（R10）**

构造并记录：
1. 加载中——节流网络后在列表页首次 hover；
2. 加载失败——断网后 hover，确认「人员信息加载失败」+「重试」，恢复网络后点重试成功；
3. 未找到——调用 `store.ensure([999999])` 后查看该 id 渲染；
4. 无角色——构造无角色用户；
5. 停用——用停用账号；
6. 超长文本——构造超长团队名与角色名。
每种状态在明暗主题与桌面 1440 / 手机 375 下各截一张图。

- [ ] **步骤 6：治理与任务范围检查**

运行：
```bash
node scripts/check-all-governance.mjs
node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260912-075-person-card/codex-task-scope.yaml --working-tree
git diff --check
```
预期：`check-codex-scope.mjs` 通过；`git diff --check` 无空白错误；`check-all-governance.mjs` 若仍因 `REQ-20260904-061` 的既有基线故障失败，如实记录为该基线故障，**不得声称门禁全绿**
证据：保存三条命令输出

- [ ] **步骤 7：写入观测与收敛证据**

在 `.ai-control/requirements/req-20260912-075-person-card/` 写入 `observation-T*.json` 与 `convergence.json`，逐条记录 R1–R11 的通过/失败、失败时的原子负反馈、未执行项与残余风险。

- [ ] **步骤 8：建立提交检查点**

```bash
git add .ai-control/requirements/req-20260912-075-person-card/
git commit -m "chore(person-card): 记录集成验收与收敛证据"
```

**验收检查：** R1–R11 均有通过或明确失败的记录；未执行项被显式标注

**回滚：** 账本与代码分别 `git revert`；无数据库变更

**停止条件：** 若任一 `must` 需求未通过，停止并回到偏差纠正，不得收敛

**升级条件：** 若出现需要改公共契约、迁移或超出 `writable_paths` 的修复，升级给 Owner 并回到设计

---

## 集成检查

| 时点 | 命令 | 预期 |
| --- | --- | --- |
| T1 后 | `mvn -pl :ccb-system -am test` | 通过 |
| T2 后 | `npm --prefix web run build` | 通过 |
| T3 后 | `npm --prefix web run build` + 现有调用点浏览器回归 | 通过且无行为变化 |
| T5 后 | `mvn -pl :ccb-architecture -am test` | 通过 |
| T9 后 | `npm --prefix web run build` | 通过 |
| T10 | 上述全部 + `node scripts/check-all-governance.mjs` + `node scripts/check-codex-scope.mjs --working-tree` | 见 T10 步骤 6 |

## 控制模型种子

`seed_status: hypotheses-only`。以下全部为候选假设，必须由 `$model-engineering-system` 验证后才能作为控制依据。

- **被控边界候选**：`web/src/components/ui/UiUserIdentity.vue` 的外部可观察行为；`POST /api/platform/user-profiles/query` 的响应契约；38 处页面的渲染结果。
- **状态变量候选**：store 中 `Map<id, {status, profile, fetchedAt, retries}>`；`UiUserIdentity` 的 `resolvedId`；`styles.css` 中的档位类。
- **接口候选**：查询接口的请求/响应；`UiUserIdentity` 的 prop 契约；`UiPersonProfileCard` 的 props。
- **传感器候选**：`npm --prefix web run build` 退出码；`mvn ... test` 结果；浏览器 Network 面板的请求条数；页面截图；`grep` 对裸 ID 与纯文本人员渲染的检索结果。
- **执行器候选**：T1–T9 的文件变更。
- **扰动候选**：并发请求竞态（同一 id 在飞行中重复入队）；Element Plus popover 定位；后端无测试数据导致页面为空；既有基线故障（`check-ai-control-layout.mjs`）。
- **时延候选**：hover 到浮层出现的 120ms 延迟；批量请求的往返时间；`vue-tsc` 与 Maven 全量测试的执行时长。
- **假设**：设计文档中的 3 条假设全部沿用，未在本计划中新增假设。

## 风险与用户批准

高风险动作（需用户明确批准后才执行）：

1. 修改 `server/src/platform/system`（平台只读模块）新增接口——公共能力变更，已声明 Owner 获批。
2. 修改 `web/src/components/ui/UiUserIdentity.vue` 与 `styles.css`（`frontend/application` 公共契约）——公共能力变更。
3. 修改 `web/src/views/ComponentShowcaseView.vue`——设计修订 2 的新增范围（D8）。
4. 修改 `ArchitectureDecisionController` 与 `ArchitectureStandardController` 的响应 DTO——业务模块公开响应结构追加字段（向后兼容的追加，不删不改既有字段）。

已知基线故障（不得由本次任务顺手修复）：

- `docs/requirements/REQ-20260904-061-release-plan-timeline-instructions/codex-task-scope.yaml` 不是 JSON 兼容 YAML，导致 `node scripts/check-ai-control-layout.mjs` 失败（证据：`Unexpected token 's', "schema_ver"... is not valid JSON`；该文件最后改动为 `0f8d76f`，本次未修改）。处理方向需用户决定：单独授权修复，或在任务范围中声明为已知例外。
