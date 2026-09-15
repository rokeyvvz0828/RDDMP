# 默认项目初始化与 V209 恢复

本补丁为 `V206_20260915143000__ensure_default_project.sql`，Flyway 将其识别为 `206.20260915143000`，排序在 V207 之前。V207、V209 等历史 SQL 保持原字节与校验和。

## 执行前

1. 停止连接目标库的所有后端实例，备份数据库；确认 JDBC URL、数据库名、租户和备份可恢复。
2. 使用合入补丁的完整迁移目录、与应用一致的 Flyway 10.20.1/MySQL 8.4，以及 Java 17。
3. 数据库应按项目标准使用 `utf8mb4_unicode_ci`。默认使用 MySQL 8.4 的 `utf8mb4_0900_ai_ci` 会在旧 V80 处遇到独立的字符集兼容问题；本补丁不改写 V80。
4. 不运行 Flyway clean，不关闭 validation，不改历史 SQL，不导入他人的整库备份。
5. 本次初始化和恢复验证设置 `MOCK_DATA_ENABLED=false`（Spring 属性为 `ccb.mock-data.enabled=false`），核对启动进程的实际环境变量。本地默认会开启 mock，不能省略此配置。

## 初始化规则

- 租户 1，以及 V207/V209 目标业务表中出现的租户，是初始化范围。
- 已有唯一有效 `RDDMP-PLATFORM` 项目时，保留 ID、名称、状态、负责人、角色和成员，不重新赋权。
- 缺失时使用同租户唯一有效 `admin` 作为负责人/创建人；创建 PM 角色、负责人会员关系、PM 关联和标准七阶段。不会创建用户或设置密码，不向其他成员赋权。
- 存在已删除的默认项目且没有活动默认项目时失败，不自动恢复或创建替代项目。有效 admin 缺失/不唯一时失败，不能随便挑一个普通用户。
- V209 已成功执行的库只记录本补丁为成功，不创建/恢复项目，不改已完成的业务归属。
- 永久数据插入在一个事务中；任一插入失败全部回滚。ID 取各表当前最大值之后的空闲值，因此升级期间必须停止业务写入。

## 新环境

创建空数据库时明确字符集，然后正常启动后端。初始化不依赖手动 INSERT、Mock 开关或应用启动完成后的初始化器。

```sql
CREATE DATABASE ccb_platform CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

完整迁移顺序包含 `V206 -> V206.20260915143000 -> V207 -> V208 -> V209 -> 后续版本`。重复启动不重复创建项目。

### 本地 mock 的独立限制

当前 `MockDataInitializer` 在应用启动后按 mock JSON 的固定 ID 执行 upsert。JSON 中默认项目 ID 为 `910000000003001`，并被测试计划、成员等记录引用；正式迁移保留已有 ID，缺失时分配空闲 ID。两者不能假定相同。项目编码冲突时，mock upsert 可能覆盖正式项目属性，后续子记录仍引用固定 ID，造成错误关联或启动失败。

因此本补丁验收的是关闭 mock 的正式迁移链，并不代表现有 mock 数据集已兼容动态项目 ID。不要为适配 mock 修改正式项目主键或删除项目。需要导入演示数据时，应另行完成 mock 按租户、项目编码解析真实 ID 和重映射引用的改造，再开启 mock。

注意：`scripts/dev.mjs` 会把 `MOCK_DATA_ENABLED` 强制设为 `true`，只在终端设置同名变量不会覆盖它。使用一键启动脚本时，通过优先级更高的 Spring JSON 属性关闭 mock；已有 `SPRING_APPLICATION_JSON` 时合并此属性，不覆盖其他配置。先按上文完成失败记录恢复，并确认启动进程使用 Java 17。

```bash
export SPRING_APPLICATION_JSON='{"ccb":{"mock-data":{"enabled":false}}}'
./scripts/dev.sh
```

PowerShell 对应设置：

```powershell
$env:SPRING_APPLICATION_JSON = '{"ccb":{"mock-data":{"enabled":false}}}'
.\scripts\dev.ps1
```

该配置只关闭演示数据同步，Flyway 仍正常执行。直接启动已打包后端时，也可使用参数 `--ccb.mock-data.enabled=false`。本任务未实际启动业务后端，以上启动方式依赖 Spring 标准属性优先级；上线验收仍需检查有效配置和启动日志中没有执行 mock 同步。

## 旧库尚未失败

已到 V208 或更高版本的库需要允许补跑小版本。先用 Flyway `info` 检查所有 pending/out-of-order 迁移，确认没有意外脚本。现有 application-local.yml 已开启 `spring.flyway.out-of-order: true`，其他 profile 需核对有效配置；不能只凭文件名认定配置生效。

启用 outOfOrder 会影响所有待执行的旧版本，不能盲目全局打开。可以先使用 Flyway CLI 将 target 限定为 `206.20260915143000` 补跑本次迁移，再恢复应用原有启动流程。若本次小版本之前还有其他 pending 迁移，需先审核它们。

## 已因 V209 守卫失败

先在后端实际连接的库里检查：

```sql
SELECT installed_rank, version, script, checksum, success
FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 15;

SELECT id, tenant_id, project_code, deleted
FROM pm_project WHERE project_code='RDDMP-PLATFORM';

SELECT column_name
FROM information_schema.columns
WHERE table_schema=DATABASE()
  AND table_name='arch_network_work_order'
  AND column_name='project_id';
```

只有以下条件全部满足才使用下面的恢复流程：

- 原始异常是 `chk_tmp_arch_v158_default_project_guard`（不是后续校验错误）。
- V209 未成功，没有其他失败迁移；没有校验和不符、缺失迁移或人工修改历史的情况。
- `arch_network_work_order.project_id` 不存在，且没有人工撤销过部分 V209 DDL。守卫位于该字段及所有其他永久 DDL 之前，因此原始守卫失败不会留下永久表结构变化。
- 已审核将归入默认项目的租户及历史数据；已有删除标记或缺失 admin 时先由负责人确认归属。

修复顺序：

1. 使用与原失败运行一致的迁移目录和配置执行 Flyway `repair`，仅清理失败历史。repair 也会调整校验和/缺失记录，因此必须先确认已应用脚本完整且未变更，不把 repair 当作跳过校验的手段。
2. 切换到包含补丁的完整迁移目录，检查 `info`，以 `outOfOrder=true` 补跑 `206.20260915143000`。
3. 正常执行后续 migrate 或启动 Java 17 后端。

例如在已有 Flyway CLI 的环境中，通过环境变量安全提供 JDBC URL、用户名、密码及 bootstrap placeholder，使用同一配置执行：

```bash
flyway info
# 仅在满足上述检查后；此时仍使用原迁移目录。
flyway repair
# 将 FLYWAY_LOCATIONS 改为包含补丁的完整目录后：
flyway -outOfOrder=true info
flyway -outOfOrder=true -target=206.20260915143000 migrate
flyway migrate
```

不要把成功的 V209 改为失败或删除成功记录。若原始报错为 duplicate column、索引/外键错误、数据回填错误，说明可能部分执行，应按实际结构另行修复或恢复经过确认的备份，不能套用守卫恢复流程。

## 验收与回退

```sql
SELECT version, success FROM flyway_schema_history
WHERE version IN ('206.20260915143000','207','209');
SELECT COUNT(*) AS failed_count FROM flyway_schema_history WHERE success=0;
SELECT tenant_id, project_code, COUNT(*) AS active_count
FROM pm_project WHERE project_code='RDDMP-PLATFORM' AND deleted=0
GROUP BY tenant_id, project_code;
```

要求失败数为 0，目标版本均成功，每个需要默认项目的租户有且仅有一条有效记录。用对应负责人登录检查项目可见性和迁移后的架构数据。

补丁尚未执行可回退代码；已经迁移并被业务数据引用的项目不能删除。优先追加纠正迁移；确需恢复库时使用停服前备份，并确认不会丢失升级后的业务写入。
