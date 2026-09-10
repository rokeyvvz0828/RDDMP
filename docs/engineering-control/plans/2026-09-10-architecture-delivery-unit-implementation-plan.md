# 交付单元（架构管理）实施计划

## 状态与来源
- 计划修订：1
- 设计修订：1
- 设计文档：`docs/engineering-control/designs/2026-09-10-architecture-delivery-unit-design.md`
- 机器可读设计：`.ai-control/requirements/req-20260910-073-architecture-delivery-unit/design.json`
- 需求文档：`docs/requirements/REQ-20260910-073-architecture-delivery-unit/requirement.md`
- 状态：待确认

> 执行要求：使用 `$control-engineering` 逐任务实施。本计划是候选控制输入，必须先完成需求基准复核与系统建模，不得直接跳到执行阶段。

**目标：** 在架构管理菜单下提供交付单元（名称 + 归属物理子系统）的增删改查，并与同一物理子系统下的部署单元建立无方向、双向可查的关联。

**架构：** 在 `business/architecture` 模块内新增独立交付单元聚合（模型 / 存储 / 服务 / 控制器）与三张 `arch_` 表；同物理子系统不变量由关联表复合外键在数据库层强制；反向查询通过部署单元路径下的只读子资源接口暴露；前端复用 `UiFormDrawer`、`UiDataTable`、`UiToolbar` 与既有架构样式。

**技术栈：** JDK 17、Spring Boot 3.4.4、Spring Security `@PreAuthorize`、MyBatis 之外的既有 `JdbcTemplate` 存储模式、Flyway、JUnit 5 + AssertJ + Mockito + Testcontainers(MySQL 8.4)、Vue 3 + TypeScript + Element Plus。

## 全局约束
- 只允许修改 `docs/requirements/REQ-20260910-073-architecture-delivery-unit/codex-task-scope.yaml` 中 `writable_paths` 覆盖的文件。
- 模块 `business/architecture`，Owner `rokeyvvz0828`；只读写本模块 `arch_` 表；平台数据只经 `com.ccb.system.capability` 公开契约访问。
- 不修改已发布 Flyway 迁移；只追加 `V202__create_architecture_delivery_units.sql`。
- HTTP DTO 不接收或返回 `tenantId`；租户与项目只能来自 `AuthUser` 与 `ProjectAccess`。
- Java 包名保持 `com.ccb.architecture`；不重构既有包、目录与类名。
- 交付单元归属物理子系统创建后不可变更；关联双方必须属于同一物理子系统；编号创建时分配且不可修改。
- 复用 `web/src/components/ui` 与 `web/src/modules/architecture/architecture.css` 既有类，不新增平台公共组件。
- 前端覆盖加载、空、失败、无权限、提交中状态；桌面与手机视口均验收。
- 迁移、菜单与权限种子仅面向 `tenant_id = 1`，使用稳定 ID 与 `NOT EXISTS` / `INSERT IGNORE` 保证可重复部署。
- 不连接生产系统，不使用真实数据、口令或密钥。

## 文件职责地图

| 路径 | 状态 | 职责 |
| --- | --- | --- |
| `server/src/platform/infrastructure/src/main/resources/db/migration/V202__create_architecture_delivery_units.sql` | candidate-new | 交付单元三张表、部署单元复合外键所需唯一键、菜单 816 与权限 8161/8162 种子及守卫 |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/model/DeliveryUnitModels.java` | candidate-new | 交付单元领域值、命令/查询 DTO、状态枚举、部署单元投影 |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/persistence/DeliveryUnitStore.java` | candidate-new | 主记录 CRUD、编号分配、关联读写、正反向分页查询 |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/persistence/DeliveryUnitNumberCapacityExceededException.java` | candidate-new | 编号容量耗尽异常（服务层转换为 409） |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/service/DeliveryUnitService.java` | candidate-new | 校验、事务编排、视图组装、审计、权限前置断言 |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/service/DeliveryUnitDeploymentUnitReferenceChecker.java` | candidate-new | 把交付单元关联纳入部署单元作废引用守卫（SPI 实现） |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/web/DeliveryUnitController.java` | candidate-new | 交付单元全部读写接口与部署单元候选接口 |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/web/DeploymentUnitDeliveryUnitController.java` | candidate-new | 部署单元路径下的只读反向查询子资源 |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/persistence/DeploymentUnitStore.java` | existing（修改） | `searchActiveOptions` 增加可选 `physicalSubsystemId` 过滤 |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/service/DeploymentUnitService.java` | existing（修改） | `options` 增加带 `physicalSubsystemId` 的重载，保留原签名行为 |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/web/ArchitectureOptionsController.java` | existing（修改） | 新增 `GET /architecture/options/delivery-unit/physical-subsystems` |
| `server/src/modules/architecture/src/test/java/com/ccb/architecture/service/DeliveryUnitServiceTest.java` | candidate-new | 服务层单元测试（Mockito） |
| `server/src/modules/architecture/src/test/java/com/ccb/architecture/service/DeliveryUnitMySqlTest.java` | candidate-new | 真实 MySQL + Flyway 集成测试（编号、唯一键、软删、复合外键、跨项目隔离） |
| `server/src/modules/architecture/src/test/java/com/ccb/architecture/web/DeliveryUnitControllerTest.java` | candidate-new | 权限与接口契约测试（MockMvc） |
| `web/src/modules/architecture/types.ts` | existing（修改） | 交付单元类型与载荷 |
| `web/src/modules/architecture/api.ts` | existing（修改） | 交付单元 API 封装 |
| `web/src/modules/architecture/utils.ts` | existing（修改） | 交付单元状态标签与色调 |
| `web/src/modules/architecture/architecture.css` | existing（修改，仅在有需要时追加少量类） | 复用不足时补充局部样式 |
| `web/src/modules/architecture/DeliveryUnitPage.vue` | candidate-new | 列表、筛选、抽屉表单与全状态 |
| `web/src/modules/architecture/components/DeliveryUnitDetailDrawer.vue` | candidate-new | 抽屉式信息展示与关联维护 |
| `web/src/modules/architecture/components/DeploymentUnitDetailDrawer.vue` | existing（修改） | 新增只读“关联交付单元”区块 |
| `web/src/router/index.ts` | existing（修改） | 新增 `/architecture/delivery-units` 路由 |

## 任务依赖图与并行策略

```text
T1 (数据层) --> T2 (应用与 Web 层) --> T3 (前端) --> T4 (端到端验收与收敛)
```

- 全部串行。四类任务共享同一迁移版本号、同一接口契约和同一数据库状态，且 T2 依赖 T1 的存储方法签名、T3 依赖 T2 的响应结构，无法证明并行安全。
- 每个任务内部按“先失败检查、再实施、再回归”的顺序执行。

## 需求覆盖表

| 需求 | 覆盖任务 |
| --- | --- |
| R1 新增与编号 | T1, T2, T3, T4 |
| R2 查询与详情 | T1, T2, T3, T4 |
| R3 修改与乐观锁 | T1, T2, T3, T4 |
| R4 软删除与关联清理 | T1, T2, T3, T4 |
| R5 同物理子系统无方向关联 | T1, T2, T3, T4 |
| R6 部署单元反查 | T2, T3, T4 |
| R7 权限、菜单与审计 | T1, T2, T4 |
| R8 迁移只追加与前端全状态 | T1, T3, T4 |

---

### T1 数据层：迁移、模型与存储

**需求映射：** R1, R2, R3, R4, R5, R7, R8

**前置任务：** 无

**已证实输入事实：**
- `arch_deployment_unit` 现有列与索引见 `V96__create_architecture_deployment_units.sql`；`V148` 已把名称改为 `(tenant_id, name)` 唯一、类型为 `APPLICATION/DATABASE/WEB`。
- `arch_physical_subsystem` 具备 `UNIQUE KEY (tenant_id, id)`（V96 部署单元外键已依赖）。
- 部署单元编号策略：`GET_LOCK` + 序列表 `FOR UPDATE`，格式 `D<物理编号><三位序号>`，上限 999（`DeploymentUnitStore.allocateNumber`）。
- `sys_menu` 800 下已占用 801—815；`sys_role` 已存在 1（超级管理员）与 111（技术架构师）。

**文件：**
- 新建：`server/src/platform/infrastructure/src/main/resources/db/migration/V202__create_architecture_delivery_units.sql`
- 新建：`server/src/modules/architecture/src/main/java/com/ccb/architecture/model/DeliveryUnitModels.java`
- 新建：`server/src/modules/architecture/src/main/java/com/ccb/architecture/persistence/DeliveryUnitNumberCapacityExceededException.java`
- 新建：`server/src/modules/architecture/src/main/java/com/ccb/architecture/persistence/DeliveryUnitStore.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/persistence/DeploymentUnitStore.java`（`searchActiveOptions` 增加 `Long physicalSubsystemId` 参数）
- 测试：`server/src/modules/architecture/src/test/java/com/ccb/architecture/service/DeliveryUnitMySqlTest.java`

**接口：**
- 消费：既有 `PageQuery(long page, long size)`、`PageResult<T>(List<T> records, long total, long page, long size)`、`JdbcTemplate`。
- 产出（后续任务必须使用完全一致的签名）：
  - `DeliveryUnitModels.DeliveryUnitStatus { ACTIVE, INACTIVE }`
  - `record DeliveryUnit(long id, String code, long physicalSubsystemId, String name, String status, String description, String remark, long createdBy, long updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt, long rowVersion)`
  - `record DeliveryUnitCommand(Long physicalSubsystemId, String name, String description, String remark, List<Long> relatedDeploymentUnitIds, Long rowVersion)`
  - `record DeliveryUnitQuery(String name, Long physicalSubsystemId, String status)`，含 `static DeliveryUnitQuery empty()`
  - `record DeploymentUnitRef(long id, String code, String name, String status, boolean deleted, long physicalSubsystemId)`
  - `record PhysicalSubsystemProjection(long id, String code, String name, String status, boolean deleted)`
  - `DeliveryUnitStore.pageUnits(long tenantId, long projectId, PageQuery page, DeliveryUnitQuery query)`
  - `DeliveryUnitStore.findUnit(long tenantId, long projectId, long id)` / `lockUnit(...)`
  - `DeliveryUnitStore.unitNameExists(long tenantId, long projectId, long physicalSubsystemId, String name, Long excludeId)`
  - `DeliveryUnitStore.findPhysical(long tenantId, long projectId, long physicalSubsystemId)`
  - `DeliveryUnitStore.allocateNumber(long tenantId, long projectId, long physicalSubsystemId, String physicalCode)`
  - `DeliveryUnitStore.insertUnit(long id, long tenantId, long projectId, String code, long physicalSubsystemId, String name, String description, String remark, long actorId)`
  - `DeliveryUnitStore.updateUnitContent(long tenantId, long projectId, long id, long expectedRowVersion, String name, String description, String remark, long actorId)`
  - `DeliveryUnitStore.updateUnitStatus(long tenantId, long projectId, long id, String fromStatus, String toStatus, long actorId)`
  - `DeliveryUnitStore.softDelete(long tenantId, long projectId, long id, long actorId)`
  - `DeliveryUnitStore.replaceDeploymentUnits(long tenantId, long projectId, long physicalSubsystemId, long deliveryUnitId, Set<Long> deploymentUnitIds, long actorId)`
  - `DeliveryUnitStore.findDeploymentUnitsByIds(long tenantId, long projectId, Collection<Long> ids)`
  - `DeliveryUnitStore.findRelatedDeploymentUnits(long tenantId, long projectId, long deliveryUnitId)`
  - `DeliveryUnitStore.findRelatedDeliveryUnits(long tenantId, long projectId, long deploymentUnitId)`
  - `DeliveryUnitStore.hasDeliveryUnitRelation(long tenantId, long projectId, long deploymentUnitId)`
  - `DeliveryUnitStore.MAX_ORDINAL_PER_PHYSICAL = 999`

- [ ] **步骤 1：编写 MySQL 集成测试（先失败）**

在 `DeliveryUnitMySqlTest` 中按 `DeploymentUnitLifecycleMySqlTest` 的 `@Testcontainers` + `Flyway` 全量迁移方式搭建，至少断言：

```java
// 编号格式与序号不回收
String code = store.allocateNumber(TENANT_ID, PROJECT.id(), PHYSICAL_ID, "ECIP");
assertThat(code).isEqualTo("DUECIP001");
assertThat(store.allocateNumber(TENANT_ID, PROJECT.id(), PHYSICAL_ID, "ECIP")).isEqualTo("DUECIP002");

// 同物理子系统内重名唯一，跨物理子系统可同名
store.insertUnit(1L, TENANT_ID, PROJECT.id(), "DUECIP001", PHYSICAL_ID, "统一认证交付包", null, null, 88L);
assertThatThrownBy(() -> store.insertUnit(2L, TENANT_ID, PROJECT.id(), "DUECIP002", PHYSICAL_ID,
        "统一认证交付包", null, null, 88L)).isInstanceOf(DuplicateKeyException.class);

// 复合外键拒绝跨物理子系统关联（绕过服务层的直接插入也必须失败）
assertThatThrownBy(() -> jdbc.update(
        "INSERT INTO arch_delivery_unit_deployment_unit "
        + "(tenant_id, project_id, physical_subsystem_id, delivery_unit_id, deployment_unit_id, created_by) "
        + "VALUES (?, ?, ?, ?, ?, ?)",
        TENANT_ID, PROJECT.id(), PHYSICAL_ID, deliveryUnitId, otherPhysicalDeploymentUnitId, 88L))
        .isInstanceOf(DataIntegrityViolationException.class);

// 软删除后查询不可见，关联被清理，部署单元主记录保留
store.softDelete(TENANT_ID, PROJECT.id(), deliveryUnitId, 88L);
assertThat(store.findUnit(TENANT_ID, PROJECT.id(), deliveryUnitId)).isEmpty();
assertThat(store.hasDeliveryUnitRelation(TENANT_ID, PROJECT.id(), deploymentUnitId)).isFalse();

// 反向查询与跨项目隔离
assertThat(store.findRelatedDeliveryUnits(TENANT_ID, PROJECT_B.id(), deploymentUnitId)).isEmpty();
```

- [ ] **步骤 2：运行检查并确认当前信号**

运行：`mvn -pl :ccb-architecture -am test -Dtest=DeliveryUnitMySqlTest -Dsurefire.failIfNoSpecifiedTests=false`
预期：编译失败或迁移失败（不存在 `arch_delivery_unit` 表 / 不存在 `DeliveryUnitStore`）。
证据：保存退出码与首个错误行。

- [ ] **步骤 3：编写迁移 `V202__create_architecture_delivery_units.sql`**

文件头部注释写明 `-- REQ-20260910-073：架构管理交付单元、编号序列与部署单元无方向关联。` 并说明只追加。DDL：

```sql
-- 关联表复合外键需要父表在 (tenant_id, physical_subsystem_id, id) 上唯一；该组合由主键蕴含，不改变既有语义。
ALTER TABLE arch_deployment_unit
    ADD UNIQUE KEY uk_arch_deployment_unit_tenant_physical_id (tenant_id, physical_subsystem_id, id);

CREATE TABLE arch_delivery_unit (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '交付单元编号 DU<物理编号><三位序号>；创建时分配，创建后不可修改',
    physical_subsystem_id BIGINT NOT NULL COMMENT '归属物理子系统；创建后不可变更',
    name VARCHAR(200) NOT NULL COMMENT '交付单元名称，同租户同项目同物理子系统内唯一',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
    description VARCHAR(2000) NULL,
    remark VARCHAR(1000) NULL,
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '软删除标记',
    row_version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_delivery_unit_tenant_code (tenant_id, code),
    UNIQUE KEY uk_arch_delivery_unit_name (tenant_id, project_id, physical_subsystem_id, name),
    UNIQUE KEY uk_arch_delivery_unit_tenant_physical_id (tenant_id, physical_subsystem_id, id),
    KEY idx_arch_delivery_unit_project (tenant_id, project_id, deleted, status, id),
    KEY idx_arch_delivery_unit_physical (tenant_id, project_id, physical_subsystem_id, deleted, id),
    CONSTRAINT fk_arch_delivery_unit_physical FOREIGN KEY (tenant_id, physical_subsystem_id)
        REFERENCES arch_physical_subsystem (tenant_id, id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_delivery_unit_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_arch_delivery_unit_deleted CHECK (deleted IN (0, 1)),
    CONSTRAINT chk_arch_delivery_unit_code CHECK (code LIKE 'DU%' AND LENGTH(code) BETWEEN 8 AND 32),
    CONSTRAINT chk_arch_delivery_unit_row_version CHECK (row_version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交付单元主记录';

CREATE TABLE arch_delivery_unit_number_seq (
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    physical_subsystem_id BIGINT NOT NULL,
    next_ordinal INT NOT NULL COMMENT '该物理子系统下待分配序号 1..1000',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, project_id, physical_subsystem_id),
    CONSTRAINT fk_arch_delivery_unit_number_seq_physical FOREIGN KEY (tenant_id, physical_subsystem_id)
        REFERENCES arch_physical_subsystem (tenant_id, id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_delivery_unit_number_seq_next CHECK (next_ordinal BETWEEN 1 AND 1000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交付单元编号分配序号（行锁分配，永久占用）';

CREATE TABLE arch_delivery_unit_deployment_unit (
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    physical_subsystem_id BIGINT NOT NULL COMMENT '关联双方共同归属的物理子系统',
    delivery_unit_id BIGINT NOT NULL,
    deployment_unit_id BIGINT NOT NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, delivery_unit_id, deployment_unit_id),
    KEY idx_arch_delivery_unit_deployment_reverse (tenant_id, deployment_unit_id, delivery_unit_id),
    CONSTRAINT fk_arch_du_relation_delivery FOREIGN KEY (tenant_id, physical_subsystem_id, delivery_unit_id)
        REFERENCES arch_delivery_unit (tenant_id, physical_subsystem_id, id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_arch_du_relation_deployment FOREIGN KEY (tenant_id, physical_subsystem_id, deployment_unit_id)
        REFERENCES arch_deployment_unit (tenant_id, physical_subsystem_id, id) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交付单元与部署单元的无方向关联';
```

随后追加菜单与权限种子（与 V97 同法，稳定 ID，`NOT EXISTS` / `INSERT IGNORE`）：

```sql
INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no)
SELECT 816, 1, 800, 'menu', '交付单元', 'ArchitectureDeliveryUnits', '/architecture/delivery-units',
       'architecture/delivery-units/index', 'architecture:delivery-unit:view', 'tickets', 45
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 800 AND tenant_id = 1 AND deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 816);

INSERT INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 8161, 1, 816, 'view', 'architecture:delivery-unit:view', '查看交付单元'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 816 AND tenant_id = 1 AND deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM sys_menu_permission WHERE id = 8161);

INSERT INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name)
SELECT 8162, 1, 816, 'manage', 'architecture:delivery-unit:manage', '维护交付单元与部署单元关联'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 816 AND tenant_id = 1 AND deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM sys_menu_permission WHERE id = 8162);
```

再按下述主体授权，全部使用 `INSERT IGNORE`：角色 1 与角色 111 获得菜单 816 与权限 8161、8162；持有 `architecture:view/apply/manage`（8031/8032/8033）或 `architecture:deployment-unit:view`（8041）的存量角色获得权限 8161 与菜单 816；本地用户 1 加入角色 111（若尚未加入）。最后用与 V97 相同的临时表守卫断言：菜单 816、权限 8161/8162、角色 111 的两条 `sys_role_permission`、角色 1 的两条 `sys_role_permission`、角色 1 与 111 的 `sys_role_menu(816)` 均存在，否则 `CHECK (marker = 0)` 失败关闭。

- [ ] **步骤 4：实施模型与存储**

`DeliveryUnitModels` 按“产出接口”定义记录与枚举。`DeliveryUnitStore` 关键实现要求：

```java
public PageResult<DeliveryUnit> pageUnits(long tenantId, long projectId, PageQuery page, DeliveryUnitQuery query) {
    // WHERE tenant_id = ? AND project_id = ? AND deleted = 0
    // AND name LIKE ?（转义 %/_）、AND physical_subsystem_id = ?、AND status = ?
    // ORDER BY name, id LIMIT ? OFFSET ?
}

public String allocateNumber(long tenantId, long projectId, long physicalSubsystemId, String physicalCode) {
    // GET_LOCK("delivery-unit-alloc-<tenant>-<project>-<physical>", 10)
    // SELECT next_ordinal ... FOR UPDATE；不存在则 INSERT next_ordinal = 2 并返回 DU<code>001
    // next_ordinal > 999 抛 DeliveryUnitNumberCapacityExceededException
    // RETURN String.format(Locale.ROOT, "DU%s%03d", physicalCode, next - 1)
}

public void softDelete(long tenantId, long projectId, long id, long actorId) {
    // 先 DELETE FROM arch_delivery_unit_deployment_unit WHERE tenant_id=? AND project_id=? AND delivery_unit_id=?
    // 再 UPDATE arch_delivery_unit SET deleted = 1, updated_by=?, row_version = row_version + 1
    //   WHERE tenant_id=? AND project_id=? AND id=? AND deleted = 0
}
```

`replaceDeploymentUnits` 先查询当前关联集合，按差集 `INSERT` / `DELETE`，不做全量重建，避免无关行被重写。`DeploymentUnitStore.searchActiveOptions` 增加参数 `Long physicalSubsystemId`，非空时追加 `AND unit.physical_subsystem_id = ?`。

- [ ] **步骤 5：运行局部与相关回归**

运行：`mvn -pl :ccb-architecture -am test -Dtest=DeliveryUnitMySqlTest,DeploymentUnitLifecycleMySqlTest,DeploymentUnitImportMySqlTest -Dsurefire.failIfNoSpecifiedTests=false`
预期：全部通过，0 失败；迁移在真实 MySQL 8.4 上完整执行成功。
证据：退出码 0 与测试统计行。

- [ ] **步骤 6：建立提交检查点**

```bash
git add server/src/platform/infrastructure/src/main/resources/db/migration/V202__create_architecture_delivery_units.sql \
        server/src/modules/architecture/src/main/java/com/ccb/architecture/model/DeliveryUnitModels.java \
        server/src/modules/architecture/src/main/java/com/ccb/architecture/persistence/ \
        server/src/modules/architecture/src/test/java/com/ccb/architecture/service/DeliveryUnitMySqlTest.java
git commit -m "feat(architecture): 新增交付单元数据模型与迁移"
```

**验收、证据与回滚**
- 验收：迁移在空库与已有库上均可执行；编号确定性且并发不重号；唯一键与复合外键按预期拒绝；软删除清理关联；跨项目查询隔离。
- 证据：`DeliveryUnitMySqlTest` 输出、`node scripts/check-flyway-migrations.mjs` 结果。
- 回滚：`git revert` 本任务提交；数据库中 `arch_delivery_unit*` 为空时可直接 `DROP TABLE`，有数据时先导出。

**停止条件：** 迁移无法在真实 MySQL 8.4 上执行；`ALTER TABLE arch_deployment_unit` 因存量数据不满足唯一键而失败；复合外键无法建立。
**升级条件：** 需要修改既有迁移、需要改写存量 `arch_deployment_unit` 行、或需要给既有表加长时锁影响其他模块。

---

### T2 应用与 Web 层：服务、控制器与引用守卫

**需求映射：** R1, R2, R3, R4, R5, R6, R7

**前置任务：** T1

**已证实输入事实：**
- 既有写操作审计入口为 `com.ccb.system.capability.SystemOperationAudit.recordSuccess/recordFailure(SystemOperationAuditCommand)`，失败只记日志不阻断（`DeploymentUnitService`）。
- 既有引用守卫扩展点：`com.ccb.architecture.integration.DeploymentUnitReferenceChecker`，由 `DeploymentUnitReferenceGuard` 聚合；`REFERENCED` → 409，`INDETERMINATE` → 503。
- 项目范围校验入口：`ProjectAccessService.requireAccessible(projectRef, actor)`，控制器以此获得 `ProjectAccess`。
- 异常映射由 `ArchitectureExceptionAdvice` 按包覆盖，`BusinessException` 已映射 400/401/403/409。

**文件：**
- 新建：`server/src/modules/architecture/src/main/java/com/ccb/architecture/service/DeliveryUnitService.java`
- 新建：`server/src/modules/architecture/src/main/java/com/ccb/architecture/service/DeliveryUnitDeploymentUnitReferenceChecker.java`
- 新建：`server/src/modules/architecture/src/main/java/com/ccb/architecture/web/DeliveryUnitController.java`
- 新建：`server/src/modules/architecture/src/main/java/com/ccb/architecture/web/DeploymentUnitDeliveryUnitController.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/service/DeploymentUnitService.java`（新增 `options` 重载）
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/web/ArchitectureOptionsController.java`（新增交付单元物理子系统选项）
- 测试：`server/src/modules/architecture/src/test/java/com/ccb/architecture/service/DeliveryUnitServiceTest.java`
- 测试：`server/src/modules/architecture/src/test/java/com/ccb/architecture/web/DeliveryUnitControllerTest.java`

**接口：**
- 消费：T1 的 `DeliveryUnitStore` 全部方法与 `DeliveryUnitModels` 记录；既有 `DeploymentUnitService.RelatedDeploymentUnitView(long id, String code, String name, String kind, long physicalSubsystemId, String physicalSubsystemName, String status)`。
- 产出：
  - `record DeliveryUnitView(long id, String code, long physicalSubsystemId, String physicalSubsystemCode, String physicalSubsystemName, String physicalSubsystemStatus, String name, String status, List<RelatedDeploymentUnitView> relatedDeploymentUnits, String description, String remark, long createdBy, String createdByDisplayName, long updatedBy, String updatedByDisplayName, LocalDateTime createdAt, LocalDateTime updatedAt, long rowVersion)`
  - `record RelatedDeliveryUnitView(long id, String code, String name, String status)`
  - `DeliveryUnitService.list(AuthUser, ProjectAccess, PageQuery, DeliveryUnitQuery)` → `PageResult<DeliveryUnitView>`
  - `DeliveryUnitService.detail(AuthUser, ProjectAccess, long id)` → `DeliveryUnitView`
  - `DeliveryUnitService.create(AuthUser, ProjectAccess, DeliveryUnitCommand, String traceId)` → `DeliveryUnitView`
  - `DeliveryUnitService.update(AuthUser, ProjectAccess, long id, DeliveryUnitCommand, String traceId)` → `DeliveryUnitView`
  - `DeliveryUnitService.replaceDeploymentUnits(AuthUser, ProjectAccess, long id, List<Long> deploymentUnitIds, String traceId)` → `DeliveryUnitView`
  - `DeliveryUnitService.deactivate(AuthUser, ProjectAccess, long id, String traceId)` / `reactivate(...)` → `DeliveryUnitView`
  - `DeliveryUnitService.delete(AuthUser, ProjectAccess, long id, String traceId)` → `void`
  - `DeliveryUnitService.relatedDeliveryUnits(AuthUser, ProjectAccess, long deploymentUnitId)` → `List<RelatedDeliveryUnitView>`
  - `DeliveryUnitService.deploymentUnitOptions(AuthUser, ProjectAccess, Long physicalSubsystemId, String keyword, Long excludeId, PageQuery)` → `PageResult<DeploymentUnitService.RelatedDeploymentUnitView>`
  - `DeploymentUnitService.options(AuthUser, ProjectAccess, String keyword, Long excludeId, Long physicalSubsystemId, PageQuery)`
  - HTTP：`GET/POST/PUT/DELETE /api/architecture/delivery-units`，`GET /api/architecture/delivery-units/deployment-unit-options`，`GET /api/architecture/deployment-units/{id}/delivery-units`，`GET /api/architecture/options/delivery-unit/physical-subsystems`

- [ ] **步骤 1：编写服务层单元测试（先失败）**

在 `DeliveryUnitServiceTest` 中按 `DeploymentUnitServiceTest` 的 Mockito + `RecordingTransactionManager` 方式搭建，至少断言：

```java
// 创建：必须选择启用物理子系统，失败不消耗编号
when(store.findPhysical(TENANT_ID, PROJECT.id(), PHYSICAL_ID))
        .thenReturn(Optional.of(new PhysicalSubsystemProjection(PHYSICAL_ID, "ECIP", "员工渠道", "INACTIVE", false)));
assertThatThrownBy(() -> service.create(operator, PROJECT,
        new DeliveryUnitCommand(PHYSICAL_ID, "统一认证交付包", null, null, List.of(), null), "trace-1"))
        .isInstanceOf(BusinessException.class).hasMessageContaining("物理子系统当前状态不允许");
verify(store, never()).allocateNumber(anyLong(), anyLong(), anyLong(), anyString());

// 修改：携带与当前不同的归属物理子系统 -> 400
assertThatThrownBy(() -> service.update(operator, PROJECT, 9L,
        new DeliveryUnitCommand(PHYSICAL_ID + 1, "统一认证交付包", null, null, List.of(), 0L), "trace-2"))
        .isInstanceOf(BusinessException.class).hasMessageContaining("归属物理子系统不可变更");

// 乐观锁冲突 -> 409
when(store.updateUnitContent(eq(TENANT_ID), eq(PROJECT.id()), eq(9L), eq(3L), anyString(), any(), any(), anyLong()))
        .thenReturn(0);
assertThatThrownBy(() -> service.update(operator, PROJECT, 9L,
        new DeliveryUnitCommand(null, "统一认证交付包", null, null, List.of(), 3L), "trace-3"))
        .isInstanceOf(BusinessException.class).hasMessageContaining("已被其他操作修改");

// 关联越界 -> 409 且不写关联
when(store.findDeploymentUnitsByIds(TENANT_ID, PROJECT.id(), Set.of(31L)))
        .thenReturn(List.of(new DeploymentUnitRef(31L, "DECIP001", "门户_AP", "ACTIVE", false, PHYSICAL_ID + 1)));
assertThatThrownBy(() -> service.replaceDeploymentUnits(operator, PROJECT, 9L, List.of(31L), "trace-4"))
        .isInstanceOf(BusinessException.class).hasMessageContaining("同一物理子系统");
verify(store, never()).replaceDeploymentUnits(anyLong(), anyLong(), anyLong(), anyLong(), any(), anyLong());

// 删除：清理关联 + 软删除 + 审计成功
service.delete(operator, PROJECT, 9L, "trace-5");
verify(store).softDelete(TENANT_ID, PROJECT.id(), 9L, operator.id());
verify(operationAudit).recordSuccess(any(SystemOperationAuditCommand.class));
```

- [ ] **步骤 2：运行检查并确认当前信号**

运行：`mvn -pl :ccb-architecture -am test -Dtest=DeliveryUnitServiceTest -Dsurefire.failIfNoSpecifiedTests=false`
预期：编译失败（`DeliveryUnitService` 尚不存在）。
证据：保存退出码与首个错误行。

- [ ] **步骤 3：实施 `DeliveryUnitService`**

关键规则（与设计一致）：
- `create`：`requireActor` / `requireProject` → 名称必填 2—200 → `findPhysical` 必须存在、未删除且 `status = ACTIVE` → 名称唯一 → 事务内 `allocateNumber` + `insertUnit` + 校验并写入关联 → `recordSuccess`。`DuplicateKeyException` 与 `DeliveryUnitNumberCapacityExceededException` 转 409。
- `update`：命令携带的 `physicalSubsystemId` 非空且不等于当前值 → 400「归属物理子系统不可变更」；`rowVersion` 必填；`updateUnitContent` 返回 0 → 409。
- `replaceDeploymentUnits`：目标 ID 去重；`findDeploymentUnitsByIds` 返回数量不足、存在 `deleted` 或 `status != ACTIVE` 或 `physicalSubsystemId` 与交付单元不一致 → 409「只能关联同一物理子系统下的启用部署单元」；`INACTIVE` 交付单元不允许新增关联 → 409。
- `delete`：软删除 + 清理关联 + 审计，返回空。
- `deactivate`/`reactivate`：`updateUnitStatus` 返回 0 → 409。
- `relatedDeliveryUnits`：校验部署单元存在（`DeploymentUnitRef`）后返回 `findRelatedDeliveryUnits`。
- 审计操作码：`ARCHITECTURE_DELIVERY_UNIT_CREATE` / `_UPDATE` / `_RELATE` / `_DEACTIVATE` / `_REACTIVATE` / `_DELETE`；`RESOURCE_PATH = "/api/architecture/delivery-units"`。

- [ ] **步骤 4：实施控制器与引用守卫**

`DeliveryUnitController` 权限常量：

```java
private static final String VIEW_PERMISSION =
        "hasAnyAuthority('architecture:delivery-unit:view', 'architecture:delivery-unit:manage', "
                + "'architecture:view', 'architecture:apply', 'architecture:manage')";
private static final String MANAGE_PERMISSION = "hasAuthority('architecture:delivery-unit:manage')";
```

`PUT /{id}/deployment-units` 请求体为 `record DeliveryUnitRelationCommand(List<Long> deploymentUnitIds)`。
`DELETE /{id}` 使用 `@DeleteMapping`，返回 `ApiResponse<Void>`。
`DeploymentUnitDeliveryUnitController` 的 `GET /{id}/delivery-units` 使用同一 `VIEW_PERMISSION` 语义（叠加部署单元查看权限）。
`DeliveryUnitDeploymentUnitReferenceChecker implements DeploymentUnitReferenceChecker`：`checkerKey()` 返回 `"architecture-delivery-unit-relation"`；查询到关联时返回 `ReferenceCheckResult.referenced("部署单元仍被交付单元关联")`，否则 `clear("未发现交付单元关联")`；内部异常不抛出，返回 `ReferenceCheckResult.indeterminate("交付单元关联检查暂不可用")`。
`ArchitectureOptionsController` 新增：

```java
@GetMapping("/delivery-unit/physical-subsystems")
@PreAuthorize("hasAnyAuthority('architecture:delivery-unit:view', 'architecture:delivery-unit:manage', "
        + "'architecture:view', 'architecture:apply', 'architecture:manage')")
public ApiResponse<PageResult<PhysicalSubsystemOption>> deliveryUnitPhysicalSubsystems(
        @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "50") long size,
        @RequestParam(required = false) String code, @RequestParam(required = false) String name,
        @RequestParam String projectRef, @AuthenticationPrincipal AuthUser actor) {
    return success(service.physicalSubsystems(actor, project(projectRef, actor), new PageQuery(page, size), code, name));
}
```

`DeploymentUnitService` 新增重载，原有 5 参数方法委托到新 6 参数方法并传 `null`，保证既有调用行为不变。

- [ ] **步骤 5：编写并运行权限与接口测试**

在 `DeliveryUnitControllerTest` 中断言：无权限 → 403；`architecture:delivery-unit:view` 可读不可写（写 → 403）；`architecture:delivery-unit:manage` 可写；`projectRef` 缺失 → 400；反查接口返回只读列表。

运行：`mvn -pl :ccb-architecture -am test -Dtest=DeliveryUnitServiceTest,DeliveryUnitControllerTest,DeploymentUnitServiceTest,DeploymentUnitControllerTest -Dsurefire.failIfNoSpecifiedTests=false`
预期：全部通过，0 失败。
证据：退出码与测试统计行。

- [ ] **步骤 6：建立提交检查点**

```bash
git add server/src/modules/architecture/src/main/java/com/ccb/architecture/ \
        server/src/modules/architecture/src/test/java/com/ccb/architecture/service/DeliveryUnitServiceTest.java \
        server/src/modules/architecture/src/test/java/com/ccb/architecture/web/DeliveryUnitControllerTest.java
git commit -m "feat(architecture): 交付单元服务、接口与作废引用守卫"
```

**验收、证据与回滚**
- 验收：R1—R7 的服务端行为与权限语义成立；部署单元作废在存在交付单元关联时返回 409。
- 证据：两个测试类输出、`DeploymentUnitServiceTest` 等既有测试不回归。
- 回滚：`git revert` 本任务提交；数据库无需补偿。

**停止条件：** 需要放宽既有接口权限才能复用；需要修改 `DeploymentUnitReferenceCheckRequest` 等公开契约；审计入口不可用。
**升级条件：** 权限模型与设计不一致、需要新增平台公共能力、或部署单元作废语义变化影响其他业务模块。

---

### T3 前端：列表、抽屉表单、详情与反向展示

**需求映射：** R1, R2, R3, R4, R5, R6, R8

**前置任务：** T2

**已证实输入事实：**
- 统一抽屉表单组件 `web/src/components/ui/UiFormDrawer.vue` 提供 `modelValue/title/width/loading/confirmText` 与 `submit` 事件。
- 列表与状态范式见 `web/src/modules/architecture/DeploymentUnitPage.vue`；详情抽屉范式见 `components/DeploymentUnitDetailDrawer.vue`。
- 请求统一经 `web/src/modules/architecture/api.ts` 的 `projectHttp`，参数用 `compact()` 过滤空值。
- 菜单按 `routePath` 匹配，新增路由路径必须与迁移中的 `/architecture/delivery-units` 完全一致。

**文件：**
- 修改：`web/src/modules/architecture/types.ts`、`api.ts`、`utils.ts`、`router/index.ts`
- 修改：`web/src/modules/architecture/components/DeploymentUnitDetailDrawer.vue`
- 新建：`web/src/modules/architecture/DeliveryUnitPage.vue`
- 新建：`web/src/modules/architecture/components/DeliveryUnitDetailDrawer.vue`
- 修改（按需）：`web/src/modules/architecture/architecture.css`

**接口：**
- 消费：T2 的 HTTP 接口与响应字段。
- 产出：前端类型 `DeliveryUnit`、`DeliveryUnitPayload`、`RelatedDeploymentUnitRef`、`RelatedDeliveryUnit`、`DeliveryUnitStatus`；API 函数 `listDeliveryUnits`、`getDeliveryUnit`、`createDeliveryUnit`、`updateDeliveryUnit`、`replaceDeliveryUnitDeploymentUnits`、`deactivateDeliveryUnit`、`reactivateDeliveryUnit`、`deleteDeliveryUnit`、`listDeploymentUnitDeliveryUnits`、`searchDeliveryUnitDeploymentUnitOptions`、`loadDeliveryUnitPhysicalSubsystemOptions`。

- [ ] **步骤 1：建立构建基准**

运行：`npm --prefix web run build`
预期：通过（记录当前基线耗时与结果），作为后续回归对照。
证据：退出码与 `vite build` 结尾行。

- [ ] **步骤 2：新增类型、API 与工具函数**

```ts
export type DeliveryUnitStatus = 'ACTIVE' | 'INACTIVE'
export interface DeliveryUnit {
  id: number; code: string
  physicalSubsystemId: number; physicalSubsystemCode: string | null
  physicalSubsystemName: string | null; physicalSubsystemStatus: string | null
  name: string; status: DeliveryUnitStatus
  relatedDeploymentUnits: RelatedDeploymentUnitRef[]
  description: string | null; remark: string | null
  createdBy: number; createdByDisplayName: string | null
  updatedBy: number; updatedByDisplayName: string | null
  createdAt: string; updatedAt: string; rowVersion: number
}
export interface DeliveryUnitPayload {
  physicalSubsystemId: number | null; name: string
  description: string | null; remark: string | null
  relatedDeploymentUnitIds: number[]; rowVersion?: number | null
}
```

`utils.ts` 增加 `deliveryUnitStatusLabels: Record<DeliveryUnitStatus, string> = { ACTIVE: '启用', INACTIVE: '停用' }` 与 `deliveryUnitStatusTone(status)`（`ACTIVE` → `success`，`INACTIVE` → `warning`）。
`router/index.ts` 在 `architecture/deployment-units` 之后新增：

```ts
{ path: 'architecture/delivery-units', name: 'architecture-delivery-units', component: () => import('../modules/architecture/DeliveryUnitPage.vue'), meta: { title: '交付单元' } },
```

- [ ] **步骤 3：实施 `DeliveryUnitPage.vue`**

要求与 `DeploymentUnitPage.vue` 保持一致的结构与状态覆盖：
- 头部：`UiPageHeader` 标题“交付单元”，描述说明归属物理子系统与同子系统关联规则；`canManage` 时显示“新建交付单元”。
- 无 `auth.user` → 加载态；`!canView || forbidden` → 无权限 `el-result`；`loadError` → 失败态带重试；否则展示列表。
- 筛选：名称输入、归属物理子系统下拉（`loadDeliveryUnitPhysicalSubsystemOptions`）、状态下拉；查询/重置/刷新。
- 桌面：`UiDataTable` 列 = 交付单元（名称 + 编号，按钮打开详情）、状态 `UiStatusTag`、归属物理子系统、关联部署单元数量、最后更新、操作（详情 / 维护下拉：修改、停用、启用、删除）。
- 移动：`architecture-mobile-list` 卡片，`dl` 展示同样字段，操作在 footer。
- 新增/修改使用 `UiFormDrawer`；表单字段：归属物理子系统（仅新增可选，编辑态只读展示）、名称（必填 2—200）、关联部署单元（多选，先选物理子系统后加载候选，`physicalSubsystemId` 传入 `searchDeliveryUnitDeploymentUnitOptions`）、描述、备注。编辑提交时 `physicalSubsystemId` 传 `null`。
- 关闭抽屉前比对快照，未保存时用 `ElMessageBox.confirm` 二次确认。
- 删除、停用、启用用 `ElMessageBox.confirm` 说明对象与后果；提交中禁用按钮防重复提交。

- [ ] **步骤 4：实施 `DeliveryUnitDetailDrawer.vue` 与部署单元反查区块**

- `DeliveryUnitDetailDrawer`：`el-drawer`，`dl.architecture-detail-list` 展示编号、名称、状态、归属物理子系统（含状态）、创建人/时间、最后更新、数据版本、描述、备注；分区“关联部署单元”展示当前关联并允许编辑（多选 + 保存，调用 `replaceDeliveryUnitDeploymentUnits`），编辑受 `canManage` 控制；footer 提供修改/停用/启用/删除。
- `DeploymentUnitDetailDrawer`：新增只读分区“关联交付单元”，在 `unit` 变化时调用 `listDeploymentUnitDeliveryUnits(unit.id)`，空态用 `el-empty`，加载失败只提示不阻断其他区块。

- [ ] **步骤 5：运行构建与类型检查**

运行：`npm --prefix web run build`
预期：通过，0 个 TypeScript 错误。
证据：退出码与构建结尾行。

- [ ] **步骤 6：建立提交检查点**

```bash
git add web/src/modules/architecture/ web/src/router/index.ts
git commit -m "feat(architecture): 交付单元页面、抽屉表单与反向关联展示"
```

**验收、证据与回滚**
- 验收：R1—R6、R8 的前端行为成立；桌面与手机视口覆盖加载、空、失败、无权限、提交中。
- 证据：`npm --prefix web run build` 输出、浏览器验收截图/记录（T4）。
- 回滚：`git revert` 本任务提交；菜单未发布时前端入口自动隐藏。

**停止条件：** 需要修改 `web/src/components/ui/**` 公共组件；需要引入新依赖；需要动态表单元数据。
**升级条件：** 交付示范中心范式无法覆盖目标形态而必须新增公共样式或组件。

---

### T4 端到端验收与收敛

**需求映射：** R1—R8

**前置任务：** T1, T2, T3

**已证实输入事实：**
- 本地基础设施容器 `rddmp-dev-mysql`、`rddmp-dev-minio`、`rddmp-dev-kkfileview` 正在运行。
- 治理脚本：`scripts/check-all-governance.mjs`、`scripts/check-flyway-migrations.mjs`、`scripts/check-codex-scope.mjs`。

**文件：** 无源码变更；只写当前前缀账本与需求/计划文档。

**接口：** 消费 T1—T3 的全部产出。

- [ ] **步骤 1：运行治理与迁移检查**

```bash
node scripts/check-all-governance.mjs
node scripts/check-flyway-migrations.mjs
node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260910-073-architecture-delivery-unit/codex-task-scope.yaml --base origin/dev-ivanh --head HEAD --working-tree
```
预期：全部退出码 0；范围检查输出的修改文件集合完全落在 `writable_paths` 内。

- [ ] **步骤 2：运行架构模块完整测试**

```bash
mvn -pl :ccb-architecture -am test
```
预期：0 失败、0 错误；`DeliveryUnit*` 与既有 `DeploymentUnit*` 测试全部通过。

- [ ] **步骤 3：运行前端生产构建**

```bash
npm --prefix web run build
```
预期：退出码 0。

- [ ] **步骤 4：启动本地环境并做真实浏览器验收**

```bash
./scripts/dev.sh
```
按 `design-h5.md` 要求，以 `admin/admin123` 登录，分别在桌面视口与手机视口验证：
1. 架构管理菜单下出现“交付单元”，可进入列表。
2. 新建：不选物理子系统或名称为空时被拦截；创建成功后编号形如 `DU…`，列表出现新记录。
3. 同一物理子系统内重复名称被拒绝并显示可读错误。
4. 修改名称/描述成功；刷新后归属物理子系统不可改。
5. 详情抽屉内关联部署单元：候选只出现同一物理子系统下的启用部署单元；保存后详情立即显示；在部署单元详情抽屉看到该交付单元。
6. 删除需二次确认；删除后列表不再出现，部署单元详情不再显示该交付单元。
7. 无权限账号进入时展示无权限态，接口返回 403。
8. 移动视口无整页横向滚动、无遮挡、操作按钮可达。

证据：记录每条场景的通过/失败、接口状态码与控制台错误。

- [ ] **步骤 5：写收敛产物并更新账本**

写入 `.ai-control/requirements/req-20260910-073-architecture-delivery-unit/convergence.json`（或按当前控制工作流对应的收敛产物），记录 R1—R8 的结果、任务结果、未关闭反馈、残余风险与 `gate_result`。

**验收、证据与回滚**
- 验收：全部 `must` 需求有独立证据；无未关闭反馈；范围审计一致。
- 证据：上述命令真实输出、浏览器验收记录、账本收敛产物。
- 回滚：见各任务回滚说明；整体回退为回退全部提交并停用菜单 816 与权限 8161/8162。

**停止条件：** 治理或迁移检查失败；架构模块测试出现回归；浏览器验收暴露必须改变设计的行为差异。
**升级条件：** 需要修改既有迁移、公共组件、模块边界或需求基准。

---

## 集成检查

| 触发 | 命令 | 预期 |
| --- | --- | --- |
| T1 完成后 | `mvn -pl :ccb-architecture -am test -Dtest=DeliveryUnitMySqlTest,DeploymentUnitLifecycleMySqlTest -Dsurefire.failIfNoSpecifiedTests=false` | 通过，既有部署单元行为无回归 |
| T2 完成后 | `mvn -pl :ccb-architecture -am test` | 通过 |
| T3 完成后 | `npm --prefix web run build` | 通过 |
| 全部完成后 | `node scripts/check-all-governance.mjs && node scripts/check-flyway-migrations.mjs` | 退出码 0 |

## 控制模型种子（仅假设，待 `$model-engineering-system` 验证）

- `plant_boundary_candidates`：`business/architecture` 的交付单元聚合；`arch_delivery_unit*` 三张表；架构管理菜单与权限。
- `state_variable_candidates`：交付单元行（status/deleted/row_version）、编号序列 `next_ordinal`、关联集合。
- `interface_candidates`：`/api/architecture/delivery-units*`、`/api/architecture/deployment-units/{id}/delivery-units`、`/api/architecture/options/delivery-unit/physical-subsystems`。
- `sensor_candidates`：`DeliveryUnitMySqlTest`、`DeliveryUnitServiceTest`、`DeliveryUnitControllerTest`、治理与迁移脚本、浏览器验收。
- `actuator_candidates`：迁移、服务与控制器代码、前端页面与路由、菜单权限种子。
- `disturbance_candidates`：并发编号分配、并发乐观锁、物理子系统/部署单元在提交前被停用或删除、项目上下文未就绪。
- `delay_candidates`：Flyway 迁移在部署时执行；菜单权限在用户重新登录后生效；前端项目上下文异步就绪。
- `assumptions`：
  - `arch_deployment_unit` 的 `(tenant_id, physical_subsystem_id, id)` 唯一，依据为主键 `id`，推翻证据为出现重复组合。
  - 角色 1 与 111 是授权主体，依据为 V97 既有授权模式，推翻证据为目标环境角色定义不同。

## 风险与用户批准

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| 给 `arch_deployment_unit` 追加唯一键的 `ALTER` 在存量库上失败 | 迁移阻塞发布 | 该组合由主键蕴含；在真实 MySQL 上先验证迁移 |
| 新增交付单元权限集合与既有角色不匹配 | 菜单不可见 | 同时授予角色 1、111 与既有架构只读角色；验收核对菜单可见性 |
| 部署单元作废新增引用检查 | 既有作废流程出现新 409 | 仅在存在交付单元关联时拒绝，并回归 `DeploymentUnitLifecycleMySqlTest` |
| 前端复用不足而新增样式 | 偏离设计准入 | 优先复用 architecture.css 既有类；必要时在需求记录说明原因 |

**待你确认：**
1. 是否批准本计划并按序实施 T1—T4？
2. 是否在当前分支 `dev-ivanh` 直接实施（不使用隔离 worktree）？当前工作区存在与本需求无关的未提交改动（`.agents/skills/**`），我会保留并不触碰它们。

---

# 计划修订 2（2026-09-10）：部署单元侧关联编辑（方案 A）

## 状态与来源
- 计划修订：2
- 设计修订：2
- 变更原因：用户追加“部署单元菜单也可以关联交付单元，和交付单元页面使用同样的逻辑”，并在方案 A/B 对比后确认“按 A 执行”。
- 全局约束沿用修订 1，并新增：关联写入统一要求 `architecture:delivery-unit:manage`；部署单元侧关联编辑不发布部署单元新版本、不写 `arch_deployment_unit_relation_history`；不进入部署单元新建/修改表单。

## 新增任务

### T5 后端：部署单元侧关联的候选、校验与覆盖式保存

**需求映射：** R5, R6, R7

**前置任务：** T1, T2

**文件：**
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/persistence/DeliveryUnitStore.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/service/DeliveryUnitService.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/web/DeploymentUnitDeliveryUnitController.java`
- 测试：`server/src/modules/architecture/src/test/java/com/ccb/architecture/service/DeliveryUnitServiceTest.java`
- 测试：`server/src/modules/architecture/src/test/java/com/ccb/architecture/service/DeliveryUnitMySqlTest.java`
- 测试：`server/src/modules/architecture/src/test/java/com/ccb/architecture/web/DeliveryUnitControllerTest.java`

**接口：**
- 产出 `DeliveryUnitStore.findDeliveryUnitsByIds(long tenantId, long projectId, Collection<Long> ids)` → `List<DeliveryUnit>`
- 产出 `DeliveryUnitStore.replaceDeploymentUnitsFromDeploymentSide(long tenantId, long projectId, long physicalSubsystemId, long deploymentUnitId, Set<Long> deliveryUnitIds, long actorId)`
- 产出 `DeliveryUnitService.replaceDeploymentUnitDeliveryUnits(AuthUser, ProjectAccess, long deploymentUnitId, List<Long> deliveryUnitIds, String traceId)` → `List<RelatedDeliveryUnitView>`
- 产出 `DeliveryUnitService.deliveryUnitOptionsForDeploymentUnit(AuthUser, ProjectAccess, long deploymentUnitId, String keyword, PageQuery)` → `PageResult<DeliveryUnitOptionView>`
- 产出 HTTP：`PUT /api/architecture/deployment-units/{id}/delivery-units`、`GET /api/architecture/deployment-units/{id}/delivery-unit-options`

**步骤：**
1. 先补失败的 MySQL 断言：从部署单元侧替换关联后两侧查询一致；跨物理子系统交付单元被拒；已停用交付单元被拒；软删除交付单元被拒。
2. 运行 `mvn -pl :ccb-architecture -am test -Dtest=DeliveryUnitMySqlTest -Dsurefire.failIfNoSpecifiedTests=false` 确认失败信号。
3. 在 `DeliveryUnitStore` 增加按 ID 批量读取交付单元与从部署单元侧替换关联的方法（复用 `arch_delivery_unit_deployment_unit`，删除与新增按差集处理）。
4. 在 `DeliveryUnitService` 增加两个方法：候选查询（同物理子系统、未删除、状态 ACTIVE、关键字分页）与覆盖式保存（先校验部署单元存在且状态为 ACTIVE，再逐一校验候选对象的物理子系统与状态），审计操作码 `ARCHITECTURE_DEPLOYMENT_UNIT_RELATE_DELIVERY_UNIT`，审计路径为部署单元子资源路径。
5. 在 `DeploymentUnitDeliveryUnitController` 增加两个端点：`PUT` 使用 `hasAuthority('architecture:delivery-unit:manage')`，`GET` 使用既有部署单元查看权限。
6. 运行服务/控制器/MySQL 测试与既有部署单元回归。

**验收、证据与回滚**
- 验收：两侧写同一张关系表；非法候选与非法状态均 409 且不写入；不产生部署单元新版本与关系历史行。
- 证据：`DeliveryUnitMySqlTest`、`DeliveryUnitServiceTest`、`DeliveryUnitControllerTest` 输出。
- 回滚：`git revert` 本任务提交；关系表数据由部署单元侧不再写入，无 schema 变更。

**停止条件：** 需要修改 `arch_deployment_unit_relation_history` 或部署单元版本表才能实现。
**升级条件：** 需要放宽部署单元既有授权或引入新的关系历史表。

---

### T6 前端与验收：部署单元详情抽屉内编辑关联

**需求映射：** R6, R8

**前置任务：** T5

**文件：**
- 修改：`web/src/modules/architecture/types.ts`
- 修改：`web/src/modules/architecture/api.ts`
- 修改：`web/src/modules/architecture/components/DeploymentUnitDetailDrawer.vue`
- 修改：`web/src/modules/architecture/DeploymentUnitPage.vue`

**接口：**
- 产出 `replaceDeploymentUnitDeliveryUnits(id, deliveryUnitIds)`、`searchDeploymentUnitDeliveryUnitOptions({ deploymentUnitId, keyword, page, size })`
- 产出 `DeploymentUnitDetailDrawer` 的 `canManageRelations` prop 与 `updated` 事件

**步骤：**
1. 建立构建基准：`npm --prefix web run build`。
2. 增加 API 封装与 `RelatedDeliveryUnit` 复用类型。
3. 把 `DeploymentUnitDetailDrawer` 的“关联交付单元”区块改为可编辑：编辑态使用多选远程搜索下拉（同物理子系统候选）、`#empty` 插槽保留、保存调用新接口并 `emit('updated')`；仅当 `canManageRelations` 为真且部署单元状态为 `ACTIVE` 时展示入口。
4. `DeploymentUnitPage` 传入 `canManageRelations = auth.hasPermission('architecture:delivery-unit:manage')`，并在 `updated` 后刷新列表与详情。
5. 运行 `npm --prefix web run build`。
6. 浏览器回归：部署单元详情抽屉内新增/移除关联后，交付单元详情抽屉立即可见；反向亦成立；无匹配关键字时下拉保持打开。

**验收、证据与回滚**
- 验收：两侧抽屉交互一致；任一侧保存另一侧立即生效；空候选提示与下拉保持打开。
- 证据：构建输出、`/tmp/pwtest/verify-deployment-side-relation.mjs` 输出、既有三组验收复跑。
- 回滚：`git revert` 本任务提交。

**停止条件：** 需要在部署单元新建/修改表单中增加关联选择（超出方案 A）。
**升级条件：** 需要修改公共组件或平台能力。

---

### T7 收敛：回归与账本

**需求映射：** R1–R8

**前置任务：** T5, T6

**步骤：**
1. 运行 `mvn -pl :ccb-architecture -am test`、`npm --prefix web run build`、三组既有浏览器验收与新增 `verify-deployment-side-relation.mjs`。
2. 复跑治理、迁移与范围检查，确认无新增违规。
3. 更新 `convergence.json`（新增采样与回归项，R6 证据更新），重新执行 verifying 门禁。

**停止条件：** 出现与部署单元版本/授权相关的回归。
**升级条件：** 需要修改既有迁移或公共契约。
