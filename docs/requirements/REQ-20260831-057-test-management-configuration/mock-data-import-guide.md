# 测试管理模拟数据快速导入指南

适用对象：测试环境。脚本只写入脱敏的“平台能力升级项目”演示数据，不包含附件、账号口令或生产数据。

## 前置条件

1. 已部署包含测试管理模块的应用，并已成功执行 V123 至 V130 Flyway 迁移。
2. `pm_project` 中存在编码为 `RDDRMP-PLATFORM` 的项目；如测试环境编码不同，先修改 SQL 文件开头的 `@project_code`。
3. `arch_physical_subsystem` 中至少有一个未删除的物理子系统；脚本会使用 ID 最小的一条作为参测系统。

## 导入

使用测试环境的数据库账号，在仓库根目录执行。命令不会输出或保存口令：

```bash
mysql -h <数据库主机> -P <端口> -u <用户名> -p <数据库名> \
  < server/src/modules/test-management/scripts/seed-platform-upgrade-mock.sql
```

脚本可重复执行：固定 ID 的模拟行会更新，不会删除任何非模拟数据。

## 导入结果

应用组装测试下将生成：参测系统、2 个轮次和 4 个周期、3 类字典及选项、3 条富文本公告（2 条置顶）、5 条范围、5 条案例、5 条执行记录、2 条缺陷及关联、1 份项目报告和 2 条统计快照。

附件和测试方案版本未模拟，避免生成无法预览或下载的虚假附件记录；可在页面中使用真实测试文件手工上传方案。

## 验证

```sql
SELECT test_domain, COUNT(*) AS scope_count
FROM tm_test_scope WHERE project_id = (SELECT id FROM pm_project WHERE project_code = 'RDDRMP-PLATFORM' LIMIT 1)
GROUP BY test_domain;

SELECT execution_status, COUNT(*) AS execution_count
FROM tm_test_execution WHERE test_domain = 'application-assembly' AND deleted = 0
GROUP BY execution_status;

SELECT defect_code, status FROM tm_test_defect
WHERE test_domain = 'application-assembly' AND deleted = 0;
```

页面验证路径：应用组装测试 → 管理配置、测试公告板、测试范围、测试案例、测试执行、测试缺陷、测试报告、分析统计。
