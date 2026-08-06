# RDDMP 后端规则

适用于 `server/src`。同时遵循仓库根目录 `AGENTS.md`。

- 保持 Java 17、Spring Boot 3.4.4、现有 Maven artifact 和 `com.ccb.*` 包名。
- Controller 负责 HTTP 适配与输入校验，业务编排进入 service；基础设施访问留在 platform/infrastructure。
- API 使用 `/api` 和统一 `ApiResponse`，异常使用公共错误模型与 trace ID。
- 受保护接口在服务端校验认证、RBAC、数据范围和实体权限，不能信任前端传入的身份或权限。
- 跨 Maven 模块依赖必须在 `governance/modules.yaml` 和目标 `pom.xml` 中声明。不得直接依赖其他业务模块内部实现。
- Flyway 迁移只追加；不编辑已发布 `V*__*.sql`。SQL 面向 MySQL 8.4，并说明存量数据与回退。
- 聚焦测试使用 `mvn -pl :<artifactId> -am test`，完成前执行 `mvn test`。
