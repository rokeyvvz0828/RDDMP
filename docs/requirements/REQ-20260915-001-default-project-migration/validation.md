# 验证记录

基线：`origin/main` 的 `f7c6ff7`。Java 17.0.18，MySQL 8.4 Testcontainers，未连接运营数据库。迁移数据集使用 `utf8mb4_unicode_ci`，不执行启动后的 mock 导入。

## 已通过

```bash
mvn --batch-mode --no-transfer-progress -pl :ccb-architecture -am -Dtest=DefaultProjectInitializationMigrationMySqlTest -Dsurefire.failIfNoSpecifiedTests=false test
```

最终输出（2026-09-15 14:41:56 +08:00）：

```text
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

覆盖空库到最新版本、V208 升级、已有项目及成员保留、已成功 V209 后删除项目的 no-op、已删除默认项目拒绝、缺少同租户 admin 拒绝、多租户归属与 ID、防止部分插入以及精确补跑后恢复 V209。失败注入使用测试专用 CHECK 约束，先前触发器方案受测试账户权限限制，已替换并完整重跑。

- `EmptyDatabaseMigrationBaselineMySqlTest`：2 个基线测试通过。
- `node scripts/check-flyway-migrations.mjs --base origin/main --head HEAD`：211 个迁移、1 个 callback、2 个精确例外；已发布 SQL 未修改。
- `node scripts/check-module-boundaries.mjs`：15 个 Maven 模块通过。
- 当前任务 `check-codex-scope.mjs --working-tree`：通过。
- `git diff --check`：通过。
- `npm ci --prefix web --no-audit --no-fund` 和 `npm --prefix web run build`：通过。本次没有前端改动。
- 独立代理审查迁移 SQL、检查器和恢复方案，没有发现明确缺陷。最终运行测试另行验证上述九个场景；审查不能替代测试。

## 主线合入阻碍

`mvn --batch-mode --no-transfer-progress test` 已运行至数据迁移模块，并出现已有 `DataMigrationModuleRegistrationTest` 的 4 个断言失败、4 个文件缺失错误。发现失败后中断剩余全量运行（退出 130），未将未执行的模块记为通过。随后单独运行下面命令取得确定失败结果（退出 1）：

```bash
mvn --batch-mode --no-transfer-progress -pl :ccb-data-migration -am -Dtest=DataMigrationModuleRegistrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

```text
Tests run: 20, Failures: 4, Errors: 4, Skipped: 0
BUILD FAILURE
```

例：测试引用 `V161__data_migration_remove_compatibility_columns.sql`，但基线文件名为 V162；其他旧引用 V156/V157/V169/V174 已分别对应 V157/V158/V170/V175。`git diff --exit-code origin/main -- server/src/modules/data-migration/src/test/java/com/ccb/datamigration/DataMigrationModuleRegistrationTest.java` 无差异，`git ls-tree origin/main` 确认基线已有文件名错位。

`node scripts/check-all-governance.mjs` 退出 1，原因是历史需求目录的 topic、文件名、JSON 或缺失验收材料。例：req-20260813-030-project-plan-groups 的 topic 与目录前缀不一致，req-20260901-059-notification-project-context 缺失所声明的验收材料。没有修复其他需求账本，也没有跳过门禁。

因此本次专项修复已验证，但整体回归和主线治理不能宣称通过，控制阶段保留 observing，不宣称完整收敛或已合入 main。

## 使用限制

本次没有启动业务后端，也没有验证 mock 导入兼容性。恢复时必须按 `recovery.md` 关闭 mock；尤其注意 dev.mjs 会覆盖普通 MOCK_DATA_ENABLED 环境变量。已失败的 V209 仍需先核对失败位置及历史，再执行受限 repair，不能只拉代码后反复重启。
