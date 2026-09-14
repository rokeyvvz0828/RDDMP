# 配置管理接入架构主数据工程设计

## 文档状态

- 修订：2
- 状态：已确认
- 用户确认依据：2026-09-11 用户确认启用数据、历史快照规则和“配置管理端口 + Boot 适配器”方向，随后确认架构交付单元制品类型直接统一为镜像/二进制。
- 关联需求：`docs/requirements/REQ-20260911-074-release-architecture-master-data/requirement.md`

## 目标与成功信号

配置管理版本申请不再依赖前端静态 Mock，而是使用当前项目在架构管理维护的物理子系统与交付单元。选择查询和保存都由服务端执行项目隔离、状态及父子关系校验，配置管理继续拥有不可变的申请历史快照。

## 方案裁决

### 方案一：前端直接调用架构 API

改动最少，但配置管理用户可能没有架构页面权限，且保存时无法防止客户端伪造编码、名称和父子关系，不采用。

### 方案二：配置管理直接依赖架构实现

可以服务端校验，但会形成业务模块对另一业务模块私有服务或仓储的硬依赖，违反模块治理，不采用。

### 方案三：消费者端口 + Boot 适配器（采用）

配置管理定义所需的窄端口，架构管理公开只读查询契约，Boot 同时依赖两模块并完成 DTO 转换。该模式与现有 `DevelopmentSystemDirectoryAdapter` 一致，权限、模块边界和替换能力最清晰。

## 组件边界

1. `business/architecture`
   - 拥有 `arch_physical_subsystem`、`arch_delivery_unit` 和制品类型字典。
   - 新增 `ReleaseMasterDataQuery` 公共只读契约及 JDBC 实现。
   - 查询只接受服务端传入的认证用户、平台项目 ID 和分页条件，不暴露仓储或 Web DTO。
2. `business/release`
   - 新增 `ReleaseArchitectureDirectory` 消费端口。
   - 新增配置管理专用主数据 Service/Controller。
   - 创建和更新申请时按 ID 调用端口并生成可信快照。
3. `platform/boot`
   - 新增 `ReleaseArchitectureDirectoryAdapter`，把架构公共投影转换为配置管理投影。
   - 不承载业务校验或数据库访问。
4. `web/src/modules/release`
   - 删除静态选择源，按项目和物理子系统调用配置管理 API。
   - 负责加载、空、失败、过期反显和请求乱序保护。

## 数据契约

### 架构公共查询

```java
interface ReleaseMasterDataQuery {
    PageResult<PhysicalSubsystemRef> searchActivePhysicalSubsystems(
        AuthUser actor, long projectId, PageQuery page, String keyword);
    PageResult<DeliveryUnitRef> searchActiveDeliveryUnits(
        AuthUser actor, long projectId, long physicalSubsystemId, PageQuery page, String keyword);
    Optional<PhysicalSubsystemRef> findPhysicalSubsystem(
        AuthUser actor, long projectId, long physicalSubsystemId);
    List<DeliveryUnitRef> findDeliveryUnits(
        AuthUser actor, long projectId, long physicalSubsystemId, List<Long> deliveryUnitIds);
}
```

物理投影只包含 `id/code/name/status`；交付单元投影只包含 `id/code/name/status/physicalSubsystemId/artifactTypeCode`。

### 配置管理端口

端口使用同等的窄投影，但由配置管理拥有类型定义。Boot 适配器负责 ID、分页和 DTO 转换。架构查询不读取当前请求的架构权限，只验证认证用户有效；配置管理 Controller 自身要求申请查看/创建/更新权限，并先执行 `ProjectAccessService.requireAccessible`。

### HTTP API

```text
GET /api/release/master-data/physical-subsystems
GET /api/release/master-data/physical-subsystems/{subsystemId}/delivery-units
```

参数为 `projectId`、`keyword`、`page`、`size`。响应使用 `PageResult`，默认页大小 20，遵循现有分页上限。交付单元接口若物理子系统不存在、已停用或不属于项目，返回可读冲突错误。

## 写入与快照

创建、保存草稿和更新共用以下准备流程：

1. 根据请求 `projectId` 获取当前用户可访问的 `ProjectAccess`。
2. 把字符串主数据 ID 严格解析为正整数；旧 Mock ID 解析失败即视为失效。
3. 查询物理子系统，要求未删除、属于当前租户和项目且为 `ACTIVE`。
4. 批量查询交付单元，要求数量完整、无重复、全部属于所选物理子系统、状态为 `ACTIVE`，制品类型为 `IMAGE/BINARY`。
5. 使用查询结果覆盖请求中的 `subsystemCode/subsystemName/deliveryUnitCode/deliveryUnitName/artifactType`，只保留用户填写的版本号。
6. 完成全部校验后才进入原有事务写入，失败时不产生部分数据。

配置申请表和交付明细表继续保存快照；已提交记录不随架构改名、停用或删除而变化。

## 制品类型迁移

新增 `V213__normalize_delivery_unit_artifact_types.sql`，不修改 V212：

- 新增或规范化字典项 `IMAGE=镜像`、`BINARY=二进制`。
- `architecture.artifact-type.container` 对应的存量交付单元更新为 `IMAGE`。
- `architecture.artifact-type.archive` 和 `architecture.artifact-type.script` 更新为 `BINARY`。
- 旧三个字典配置项停用，新建和修改只能选择启用的 `IMAGE/BINARY`。
- 迁移使用身份守卫，稳定 ID 或字典归属不符合预期时失败关闭。

## 前端交互

- 抽屉打开且当前项目存在时加载第一页物理子系统；筛选使用远程关键字查询。
- 选择物理子系统后加载其交付单元；切换时清空已有普通交付单元和相关生产版本展示。
- 项目变化或抽屉关闭时取消旧结果生效，通过请求序号避免慢请求覆盖新项目结果。
- 首次加载显示选择器 loading；失败在字段附近持续展示并允许重试；空数据提示先在架构管理维护启用数据。
- 编辑草稿时把保存的快照作为临时禁用选项反显。若真实查询无法确认其仍有效，展示“已停用或不存在”，保存和提交按钮不可通过校验。
- 已提交、审批中、已完成和已取消申请使用既有详情只读展示，不增加主数据请求。
- 760px 以下表单保持单列，错误和状态文案允许换行，不产生页面级横向滚动。

## 错误与安全

- 未认证：401；无配置管理权限或项目权限：403；参数格式错误：400；数据失效、跨项目、父子错误或未知制品类型：409。
- 前端权限仅用于交互，服务端是唯一授权与校验边界。
- 查询不返回负责人、组织、描述或审计信息，减少跨模块数据暴露。
- 不缓存跨用户结果；首版按需分页查询，避免静态全量列表和跨项目残留。

## 验证策略

- 架构查询：租户/项目隔离、ACTIVE 过滤、父子过滤、批量完整性和分页。
- Boot 适配器：字段无损转换，不放宽状态或范围。
- 配置服务：仅配置权限可查；跨项目和无权限失败。
- 申请服务：伪造快照被覆盖，停用/缺失/错父子/未知类型被拒，文件介质行为不变。
- Flyway：V213 顺序、身份守卫和三类存量映射。
- 前端：请求状态、切换清空、乱序保护、旧草稿反显和构建。
- 浏览器：桌面及 375x812、390x844、430x932 视口验证新建和编辑路径。

## 已知基线与风险

- 前端修改前生产构建通过。
- 当前受限测试沙箱禁止 Byte Buddy 附加 JVM，Maven 在 `ccb-infrastructure` 的既有 Mockito 测试处失败；需在允许附加的环境复验。
- 数据迁移改变共享字典与存量交付单元，发布前必须备份相关行并由模块 Owner 复核。
- 原 Mock 草稿无法继续保存是确认后的预期兼容变化；历史只读数据不受影响。
