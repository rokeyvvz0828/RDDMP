# 迁移系统与人员关系（组件清单关联人员）工程设计

## 文档状态

- 修订：1
- 状态：已确认
- 用户确认依据：用户在选择 “人员来源=当前项目成员、增加职责/角色字段” 后确认 “这份设计（含‘一人一角色’这条和职责字典选项）”。

## 目标与成功信号

在“数据迁移 → 基础资料 → 系统/组件清单”中建立“系统-人员”关联能力：一个系统可以关联多个当前项目成员，每条关系带一个职责/角色；列表页新增“关联人员”列，点击/悬浮成员姓名以人员详情浮层展示信息，并在查看抽屉内维护关系。

成功信号：

1. 组件清单列表与移动卡片都能看到每个系统的关联人员（姓名 + 角色）。
2. 点击/悬浮成员姓名出现人员详情浮层（桌面 hover、触屏点击）。
3. 有管理权限的用户可在查看抽屉内为某个系统增删成员、调整角色并保存；只读用户仅能查看。
4. 新增接口全部通过服务端认证、RBAC、项目数据范围和成员归属校验，写操作写入 dm_operation_log 审计。
5. 全新库与既有开发库均可执行 Flyway 迁移（幂等追加），组件删除时关联关系同步清理。

## 使用者与场景

- 数据迁移管理员 / 项目成员：在组件清单中查看某系统涉及哪些人员及其职责。
- 数据迁移管理员：为“迁移系统”配置负责人、对接人、业务人员等关联关系。
- 普通查看用户：只读查看关联人员与人员详情。

## 必须需求与验收条件

- R1 关系表：新增 dm_component_person，主键 (tenant_id, project_id, system_code, user_id)，每条带 person_role；无软删。
  - 验收：全新库与存量库迁移成功；同系统同人员重复保存被拒绝；一个系统可关联多名成员。
- R2 查询：components 列表与 export 返回每个系统的关联人员（user_id、display_name、person_role）与数量。
  - 验收：按项目数据范围返回；无关系时 persons 为空数组、person_count=0。
- R3 成员选项：新增成员选项接口，仅返回当前项目未删除、启用的项目成员（pm_project_member JOIN sys_user）。
  - 验收：非项目成员不出现在选项中；无项目权限返回明确错误。
- R4 保存：新增全量替换保存接口（写权限 data-migration:manage / system:admin）。
  - 验收：服务端校验组件存在、成员属于该项目且启用；保存事务成功且写 dm_operation_log；无权限 403。
- R5 列表展示：桌面表格与移动卡片新增“关联人员”列，成员姓名复用 UiUserIdentity，悬浮/点击显示人员详情浮层。
  - 验收：375/390/430 与桌面视口无横向溢出；人数多时折叠（前 2 个 +N）且浮层可完整查看。
- R6 抽屉管理：查看抽屉新增“关联人员”区块，有写权限可增删、选角色并保存；只读只展示。
  - 验收：保存覆盖加载/空/失败/无权限/提交中状态；重复点击不产生重复请求或重复数据。
- R7 导出：Excel 增加“关联人员（角色）”列，按“姓名（角色）”逗号分隔。
  - 验收：空关系显示“-”；列名与内容与列表一致。
- R8 角色字典：person_role 为受控字典，类别 DM_COMPONENT_PERSON_ROLE（参数管理），迁移幂等种子默认选项：负责人、对接人、业务人员、实施人员。
  - 验收：下拉选项来自字典；类别缺失时页面提示“请先在参数管理配置”，不阻塞列表展示。
- R9 级联清理：组件物理删除时按 (tenant_id, project_id, system_code) 删除关系。
  - 验收：删除组件后该组件关系不再出现；其余组件关系不受影响。
- R10 全状态：加载、空、失败、无权限、提交中、重复提交均有明确表现；深浅主题可读。
  - 验收：按 design-h5.md 视口清单浏览器验收并记录证据。

## 不变量与约束

- 不变量：数据范围恒等于当前项目（顶部项目切换器）；跨租户数据不可见；dm_component 自然键不变。
- 约束：Flyway 只追加；不改历史迁移；迁移文件名符合 check-flyway-migrations 规则（V214__data_migration_component_person.sql）。
- 约束：只写 codex-task-scope.yaml 的 writable_paths；platform/system 的参数管理与人员档案只读；架构物理子系统只读。
- 约束：写接口在服务端校验 RBAC、项目范围、成员归属与实体存在；前端权限只做体验层。
- 约束：复用 web/src/components/ui 与 person-directory 人员档案能力，不新增样式体系。

## 非目标

- 不新增独立菜单或页面（延续基础资料子页面形态）。
- 不修改 dm_component 主表结构、权限模型与启停逻辑。
- 不做关系审批流、人员导入导出（导出列除外）、不做历史存量关系回填（存量关系为空）。
- 不为该功能引入移动端专属交互层级（沿用现有抽屉与卡片）。

## 方案比较与选择

| 决策点 | 推荐方案 | 备选 | 排除原因 |
| --- | --- | --- | --- |
| 人员来源 | 当前项目成员（pm_project_member JOIN sys_user） | 同租户任意 sys_user | 用户选定；数据范围更收敛，且与“项目顶层切换”上下文一致 |
| 关系粒度 | 一人一角色一条，主键含 user_id | 一人多角色多条（主键含 role） | 用户确认一人一角色；模型更简单，UI 直接 |
| 角色字典 | 参数管理类别 DM_COMPONENT_PERSON_ROLE（迁移种子默认项） | 代码常量枚举 / sys_dict 系统字典 | 与模块 DM_PROGRAM_TYPE 等既有惯例一致，加角色无需发版；受控下拉 |
| 保存方式 | 全量替换 PUT（事务删除+插入） | 逐条 POST/DELETE | 抽屉式批量管理天然适配全量替换；幂等、冲突少 |
| 列表展示 | 复用 UiUserIdentity + 人员档案浮层 | 自绘头像/tooltip | 复用已验证能力，桌面 hover 与触屏 click 自动适配 |
| 关系查询 | components 查询内批量带出 persons | 按行实时请求 | 避免 N+1；数据量小，批查可接受 |

## 架构边界与组件职责

- 后端 data-migration 模块：ProjectComponentController/ProjectComponentService 扩展列表、导出与关系读写；DataMigrationCodeValueService.options 复用读取 DM_COMPONENT_PERSON_ROLE。
- 平台只读：platform/system 参数管理（sys_dict_type/sys_config）与人员档案（user-profiles/query）；platform/security 提供 AuthUser 与权限。
- 前端 data-migration 模块：ComponentsPage.vue 扩展列与抽屉区块；api/data-migration.ts 增加 persons/member-options/角色选项封装；person-directory store 与 UiUserIdentity 复用。
- 数据 Owner：dm_component_person 归 business/data-migration；sys_dict_type/sys_config 归 platform/system（只读）；pm_project_member 归项目管理侧（只读 SQL 关联）。

## 接口、数据和状态流

接口（均以 /api 前缀、ApiResponse 包裹）：

- GET /api/data-migration/components —— 现有接口扩展，行内新增 person_count 与 persons。
- GET /api/data-migration/components/export —— 现有接口扩展，新增“关联人员（角色）”列。
- GET /api/data-migration/components/persons?projectId=&systemCode= —— 系统关联人员（查看抽屉）。
- GET /api/data-migration/components/member-options?projectId= —— 项目成员选项。
- PUT /api/data-migration/components/persons —— 全量保存 { projectId, systemCode, persons: [{ userId, personRole }] }，写权限 + 审计。
- 角色选项：复用模块码值选项通路读取 DM_COMPONENT_PERSON_ROLE（DataMigrationCodeValueService.options）。

数据流：

- 列表加载：components 分页查询 → 按 (project_id, system_code) 批查关系与成员姓名 → 行内 persons。
- 保存：前端抽屉收集成员+角色 → PUT 全量保存 → 服务端校验/事务替换/写入 dm_operation_log → 返回最新 persons → 前端刷新列表与抽屉。
- 组件删除：现有删除流程追加按系统键清理关系（同事务）。

## 错误、降级与恢复

- 非项目成员或成员已停用：服务端拒绝并说明具体成员。
- 组件不存在或已删除：保存返回实体不存在错误。
- 角色类别未配置：下拉为空并提示到参数管理配置；列表展示不受影响（角色标签显示原文）。
- 保存冲突/重复提交：全量替换幂等；按钮提交中禁用防重复。
- 接口失败：沿用页面现有错误态与重试模式（el-result + 重新加载）。

## 安全、性能、兼容性与运维

- 安全：所有写接口服务端 RBAC（data-migration:manage/system:admin）+ requireProject 数据范围 + 成员归属校验；不信任前端传入身份；审计写 dm_operation_log（操作码 COMPONENT_PERSON_SAVE 等）。
- 性能：关系数据量小（每系统个位数到十位数），列表用单次批查；成员选项接口按项目返回全量（项目成员规模可控）。
- 兼容性：迁移只追加幂等；组件列表既有字段与接口保持不变，persons 为新增字段；导出列追加在末尾。
- 运维：参数类别与选项由迁移种子，之后由系统管理/参数管理维护；回退为“停用功能保留表”或早期环境重建，不反向改迁移。

## 验证策略

- R1/R9：check-flyway-migrations.mjs + 聚焦 MySQL 迁移测试（Testcontainers）验证全新库执行与级联删除。
- R2-R4：后端聚焦测试覆盖列表 persons、成员选项范围、保存校验/权限/审计。
- R5-R7/R10：npm --prefix web run build；真实浏览器在 375x812、390x844、430x932、1280+ 验收列表、抽屉、浮层、导出与深浅主题。
- 全量：node scripts/check-all-governance.mjs、git diff --check、mvn -pl :ccb-boot -am test。

## 假设、未知项与决策记录

- 假设 A1：pm_project_member 与 sys_user 的 deleted 字段语义与其他 dm 表一致（deleted=0 有效）。
- 假设 A2：人员档案接口（user-profiles/query）能按 sys_user.id 返回姓名/头像/电话/团队，供浮层使用。
- 假设 A3：单租户部署，项目内关系总量小，批查与全量替换方案成立。
- 未知 U1：DM_COMPONENT_PERSON_ROLE 默认选项（负责人/对接人/业务人员/实施人员）是否完全覆盖业务口径 —— 非阻塞，参数管理可随时增改。
- 决策 D1：人员来源=当前项目成员（用户确认）。
- 决策 D2：一人一角色一条（用户确认）。
- 决策 D3：角色字典=参数管理 DM_COMPONENT_PERSON_ROLE（用户确认，默认项）。
- 决策 D4：保存采用全量替换。
- 决策 D5：展示复用 UiUserIdentity/人员档案，无新增样式体系。
- 决策 D6：需求编号 REQ-20260919-076，分支 feat/REQ-20260919-076-dm-component-person。

## 风险与回退原则

- 风险：新表与关系读写属数据库与写接口变更，需 Owner 复核；并发编辑关系采用全量替换，最后提交覆盖先提交（项目内低风险，可接受并在 UI 保存前提示）。
- 回退：回退本需求提交即可停用功能；新表保留不产生副作用；必要时按早期环境重建数据库；不执行反向 Flyway。
