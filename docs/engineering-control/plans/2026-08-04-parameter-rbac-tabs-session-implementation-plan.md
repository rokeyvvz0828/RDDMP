# 参数、角色权限、页签与会话改造实施计划

> 执行要求：按任务包逐项实施；每个包完成后运行局部验证并记录结果。

**目标：** 在单租户平台中交付统一参数管理、角色动作权限、可选页签和登录失效回跳。

**架构：** 使用现有 Spring Boot/JdbcTemplate/Spring Security 与 Vue/Pinia/Element Plus；权限数据由 MySQL/Flyway 管理，前端通过专用 API 与通用系统 API 消费。

**技术栈：** JDK 17、Spring Boot 3.x、MySQL 8.x、Vue 3、TypeScript、Pinia、Axios、Element Plus。

## 全局约束

- 保留用户已有组织树、头像 MinIO、主题配色、组件示例和组织父级校验。
- 数据迁移只新增字段/表/种子，不删除历史表。
- 所有新增用户界面使用中文；后端权限校验不依赖前端状态。
- API 错误必须可区分 401、403 和业务校验失败。

### T1：数据库与权限/参数契约

**需求映射：** R1 参数管理，R2 用户角色权限，R4 动态会话时效。

**文件：**
- 新建 `server/src/platform/infrastructure/src/main/resources/db/migration/V13__parameter_rbac_support.sql`
- 参考 `server/src/platform/infrastructure/src/main/resources/db/migration/V1__create_system_schema.sql`

**接口：** 新增参数类别/参数关联字段、权限动作表、角色动作权限表；为已有菜单生成查看/新增/修改/删除动作；为超级管理员补齐动作权限；建立默认会话时效参数。

**验收：** Flyway 从 V12 升级到 V13 成功；重复启动不重复插入；默认管理员可保留现有菜单访问和全动作权限。

**回滚：** 停止应用后回滚新迁移文件对应的新增对象；不删除既有数据表。

### T2：后端参数、角色权限、用户角色与会话

**需求映射：** R1、R2、R4。

**文件：**
- 修改 `server/src/modules/system/src/main/java/com/ccb/system/service/SystemService.java`
- 修改 `server/src/modules/system/src/main/java/com/ccb/system/web/SystemController.java`
- 修改 `server/src/platform/security/src/main/java/com/ccb/security/repository/AuthRepository.java`
- 修改 `server/src/platform/security/src/main/java/com/ccb/security/service/AuthService.java`
- 修改 `server/src/platform/security/src/main/java/com/ccb/security/jwt/JwtTokenService.java`
- 修改 `server/src/platform/security/src/main/java/com/ccb/security/web/JwtAuthenticationFilter.java`
- 新建必要的参数/权限服务类与模型类

**接口：** 参数类别和明细 CRUD；角色权限读取/保存；用户角色读取/保存；权限动作校验；登录/刷新令牌从参数读取时效。

**验收：** 多角色权限返回去重并集；无动作权限的创建/修改/删除接口返回 403；角色菜单访问仍能生成动态路由；参数时效边界校验生效。

**回滚：** 后端未启动新版本时可直接使用原 jar；接口新增不改变既有请求格式。

### T3：参数管理与角色/用户管理前端

**需求映射：** R1、R2。

**文件：**
- 修改 `web/src/views/ModuleView.vue`
- 修改 `web/src/api/system.ts`
- 修改 `web/src/types/system.ts`
- 修改 `web/src/views/AppLayout.vue` 菜单标题映射
- 修改 `web/src/styles.css`
- 必要时新增权限矩阵/参数类别 UI 组件

**验收：** 菜单仅显示“参数管理”而无“字典管理/系统配置”；可新增类别和参数；用户可多选角色；角色页能按菜单树勾选四种动作并保存；相同角色权限展示为并集。

**回滚：** 保留旧组件文件，路由资源映射可恢复到上一版本。

### T4：页签工作区与会话失效回跳

**需求映射：** R3、R4。

**文件：**
- 修改 `web/src/types/ui.ts`
- 修改 `web/src/stores/theme.ts`
- 修改 `web/src/components/ui/ThemeSettingsDrawer.vue`
- 修改 `web/src/views/AppLayout.vue`
- 新建 `web/src/stores/tabs.ts` 与必要的页签组件
- 修改 `web/src/api/http.ts`
- 修改 `web/src/stores/auth.ts`
- 修改 `web/src/router/index.ts`
- 修改 `web/src/views/LoginView.vue`

**验收：** 页签开关可持久化；访问三个路由后可切换；关闭当前/其他/全部符合约束；模拟 401 后刷新令牌一次，刷新失败跳转登录并保留完整 query；登录成功回到原路由。

**回滚：** 关闭页签开关即可退回单页导航；401 失败路径仍清理本地令牌。

### T5：集成、构建与运行验收

**需求映射：** R1-R4。

**命令：**
- `npm run build`（工作目录 `web`）
- `mvn -pl ccb-boot -am package -DskipTests`
- `Invoke-WebRequest http://127.0.0.1:8080/actuator/health`
- 浏览器验收参数、角色权限、页签、401 回跳。

**停止条件：** Flyway 失败、403 权限绕过、刷新令牌循环、登录回跳丢失 query、构建失败时停止并修正。

**最终证据：** 构建输出、健康检查、迁移版本、浏览器关键状态和无回归检查。