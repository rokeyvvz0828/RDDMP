---
id: REQ-20260919-076
status: ready
owner: rokeyvvz0828
module: business/data-migration
---

# 迁移系统与人员关系（组件清单关联人员）

## 业务目标

数据迁移管理员需要在“系统/组件清单”中表达“迁移系统与人员”的关联：一个系统可关联多个当前项目成员，并标注每位成员的职责/角色（负责人、对接人等）。列表页直接可见每个系统的关联人员，点击或悬浮成员姓名时以人员详情浮层查看完整信息；有管理权限的用户在查看抽屉内维护关系。

可观察的成功结果：组件清单列表与移动卡片可查看每个系统的关联人员与角色；查看抽屉可增删成员、调整角色并保存；所有写操作经服务端权限与数据范围校验并留痕审计。

## 范围

### 本次实施

- 新增关系表 `dm_component_person`（含职责字段与审计留痕），Flyway 幂等迁移。
- 组件列表与 Excel 导出返回关联人员；列表新增“关联人员”列，成员姓名复用 `UiUserIdentity` 及人员档案浮层。
- 查看抽屉新增“关联人员”区块：有写权限可管理，只读仅展示。
- 新增项目成员选项接口与关系保存接口（全量替换），服务端校验与 `dm_operation_log` 审计。
- 职责/角色采用参数管理类别 `DM_COMPONENT_PERSON_ROLE`，迁移幂等种子默认选项。
- 组件物理删除时级联清理关系。

### 本次不实施

- 不新增菜单或独立页面，不修改组件主表结构与启停逻辑。
- 不做审批流、历史存量回填；不做人员导入导出（导出列除外）。
- 不改权限模型与平台人员档案能力。

## 现状与规则

- 当前入口：数据迁移 → 基础资料 → 系统/组件清单（`web/src/modules/data-migration/views/base/ComponentsPage.vue`），项目级数据，桌面表格 + 移动卡片，已有查看/修改/启停/删除/导出。
- 业务规则：组件自然键 `(tenant_id, project_id, system_code)`；关系一人一角色一条；人员仅限当前项目启用成员。
- 角色权限：读 `data-migration:components`，写 `data-migration:manage`/`system:admin`；前端不做权限校验替代。
- 审计：沿用模块惯例写入 `dm_operation_log`（actor、operation_code、entity_type、entity_id）。
- 数据与复用：人员档案走 `person-directory` + `user-profiles/query`；角色选项复用 `DataMigrationCodeValueService.options` 读取参数管理码值；物理子系统仅只读联动。
- 敏感信息：不使用生产数据；人员姓名来自本地开发库的测试/虚构数据。

## 接口与数据

- API 契约（`web/src/api/data-migration.ts`，均 `/api` 前缀、`ApiResponse` 包裹）：
  - `GET /api/data-migration/components`：行内新增 `person_count` 与 `persons: [{ user_id, display_name, person_role }]`。
  - `GET /api/data-migration/components/export`：新增“关联人员（角色）”列。
  - `GET /api/data-migration/components/persons?projectId=&systemCode=`：系统关联人员。
  - `GET /api/data-migration/components/member-options?projectId=`：当前项目启用成员选项。
  - `PUT /api/data-migration/components/persons`：全量保存 `{ projectId, systemCode, persons: [{ userId, personRole }] }`，写权限 + 审计。
- 数据 Owner：`dm_component_person` 归 `business/data-migration`；参数类别归 `platform/system`（只读）；`pm_project_member` 归项目管理侧（只读 SQL 关联）。
- 数据库迁移：新增 `V214__data_migration_component_person.sql`（幂等、information_schema 判断、追加不修改历史），并种子 `DM_COMPONENT_PERSON_ROLE` 类别与默认选项。
- 脱敏示例：`{ projectId: 123, systemCode: 'CRM', persons: [{ userId: 5, personRole: '负责人' }] }`。

## 验收标准

1. 全新库与存量开发库执行迁移成功；同一系统同一人员仅一条关系；一个系统可关联多个成员。
2. 组件列表与导出按项目范围返回关联人员与数量；无关系时为空数组/“-”。
3. 成员选项仅含当前项目启用成员；非成员、停用成员不可选。
4. 关系保存校验组件存在、成员归属与启用状态；无 `data-migration:manage`/`system:admin` 权限返回 403；保存成功写入 `dm_operation_log`。
5. 桌面表格与移动卡片展示“关联人员”列；成员姓名复用 `UiUserIdentity`，悬浮/点击显示人员详情；375/390/430 与桌面视口无横向溢出。
6. 查看抽屉内可管理关系（权限用户）或只读展示；覆盖加载/空/失败/无权限/提交中状态。
7. 角色选项来自 `DM_COMPONENT_PERSON_ROLE` 字典；类别缺失时提示配置且不阻塞列表。
8. 删除组件后其关系被清理，其他组件关系不受影响。

## 测试与发布

- 必须执行：`node scripts/check-flyway-migrations.mjs`、`node scripts/check-all-governance.mjs`、后端聚焦测试（列表/成员选项/保存校验/权限/审计/迁移）、`mvn -pl :ccb-boot -am test`、`npm --prefix web run build`、真实浏览器按 375/390/430 与桌面视口验收。
- 上线验证：本地 dev 环境启动后按管理员与只读角色走通“查看→管理关系→保存→浮层详情→导出”。
- 回退或补偿：回退本需求提交即停用功能；新表保留无副作用；必要时按早期环境重建；不执行反向 Flyway。
- 风险与人工复核人：数据库迁移与写接口由模块 Owner（rokeyvvz0828）复核。
