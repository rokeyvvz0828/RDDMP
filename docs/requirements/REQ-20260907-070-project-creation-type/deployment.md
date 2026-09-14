# 本地兼容部署

本次按用户确认的“只更新结构、保留全部原有数据、投产相关表不动”执行。没有清空业务库，没有修改已发布SQL，没有重置或repair迁移历史，没有提交或推送Git。

## 当前结果

2026-09-08：当前开发库实际通过Flyway执行两条新增迁移，服务运行于 http://127.0.0.1:8080，前端保持 http://127.0.0.1:5173。

- V199：`pm_project.creation_type`，默认NEW，允许NEW/CONTINUATION。
- 本地V200：追加兼容字段和索引、新增15张缺失的数迁表；保留旧表、旧列与权限数据。不对现有12张`rel_release_*`表执行DDL/DML。
- 250张原有业务表按原列投影的行数和SHA-256一致；12张投产表元数据一致；原有Flyway历史记录哈希一致。
- 数据库测试验证了提交CONTINUATION后新连接读取，并完整恢复测试数据及时间。
- 后端健康UP、前端200、匿名项目接口401。浏览器登录后实际保存仍待人工验收。

## 兼容目录

规范源码同时存在两份V156。当前库已应用的是菜单改名版本；另一个审计结构通过本地V200补齐，不伪称原审计V156已执行。后续规范SQL包含删表和权限删除，未直接执行。

`local-migrations/manifest.json`固定153个已校验脚本。历史脚本从现有源码按校验和恢复；本地V156及V200保留于本需求目录。`recover-compatible-lane.py`恢复到Git忽略的`.runtime-logs/req070-preserve-data/migrations`，不会删除未知文件或覆盖校验不一致的SQL。

启动时使用该filesystem位置，Flyway及validate-on-migrate均保持开启。不能关闭校验来替代恢复目录。未来与main的规范迁移链统一需另行校验，不把本地V200与远程未来的同版本脚本混用。此次并非全部规范迁移已应用，也未完成所有业务模块回归。

## 启动命令

确认8080空闲后，在仓库根目录运行：

```powershell
$env:JAVA_HOME='C:/Users/吕少伟/.jdks/jbr-17.0.12'
mvn -q -pl :ccb-boot -am '-DskipTests' '-Dspring-boot.repackage.skip=true' install
mvn -q -pl :ccb-boot dependency:build-classpath '-Dmdep.outputFile=D:/ai_project/ccb_rd/.runtime-logs/req070-preserve-data/boot-classpath.txt'
python docs/requirements/REQ-20260907-070-project-creation-type/start-compatible-backend.py
```

构建命令不替代测试。启动脚本自动恢复已批准的兼容目录、读取本地`.env`但不打印凭据，使用Java17后台启动，不自动停止未知进程。关闭Mock同步及演示工作流发布，保留数据库认证、RBAC和审计。

不要在当前库再次运行`prepare-compatible-migrations.py`生成V200；已增加已应用版本保护。已执行的兼容SQL只能保留，后续变更使用新版本。

## 备份与回退

最终备份为`.runtime-logs/req070-preserve-data/before-20260908-090849.sql`；此前两份备份也保留。备份包含开发数据与配置，必须保持本地、Git忽略，不上传到PR或日志。

原有记录、旧表和旧列都保留，优先采用追加式修复。不得为回退删除creation_type或覆盖用户后续数据；如确需整库恢复，须停服并另行确认恢复目标及新数据处理。当前没有执行任何回滚。

验证报告位于同一运行目录的`compatible-trial-verified.json`和`current-preservation-verified.json`。临时测试后端和容器已删除；需重跑隔离验证时，先创建新的测试容器并恢复最新备份，不能连接生产环境。
