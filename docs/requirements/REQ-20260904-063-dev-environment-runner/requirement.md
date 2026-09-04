---
id: REQ-20260904-063
status: ready
owner: rokeyvvz0828
module: governance
---

# 跨平台开发环境一键启动

## 业务目标

让研发人员在 Windows、macOS 和 Linux 上通过统一入口启动 RDDMP 本地开发环境，固定使用可公开的本地开发配置和 `admin/admin123` 测试账号，并同时获得前端热更新、后端自动编译重启和 Flyway 自动迁移能力。

## 范围

### 本次实施

- 提供 Windows PowerShell 和 macOS/Linux Bash 启动入口，二者复用无第三方依赖的 Node.js 编排器。
- 编排器检查 JDK、Maven、Node.js、npm 和 Docker Compose，启动并等待 MySQL、MinIO、kkFileView。
- 仅对子进程注入固定的本地开发配置，显式使用 Spring `local` profile，不读取或覆盖用户 `.env`。
- 固定本地管理员账号为 `admin/admin123`；新库由 Flyway 占位符写入 BCrypt 哈希，已有本地库在后端启动前幂等恢复该哈希。
- 后端通过 Spring Boot DevTools 配合源码监听和 Maven 增量编译实现自动重启；前端使用 Vite HMR。
- Flyway 随后端启动自动执行待应用迁移；启动器等待后端健康检查并输出访问地址。
- 在根 `AGENTS.md` 增加本地研发启动提示和安全边界。

### 本次不实施

- 不容器化前端或后端，不引入 Compose Watch。
- 不新增 npm 依赖、全局工具或独立 Flyway CLI。
- 不修改已发布 Flyway 迁移，不新增数据库结构迁移。
- 不支持生产、测试或联调环境，不连接任何远程数据库或对象存储。
- 不自动删除 Docker 数据卷，不修改或覆盖用户 `.env`。

## 现状与规则

- 当前本地启动需要分别执行 Docker Compose、Maven 和 npm 命令，环境变量依赖人工准备。
- `application.yml` 默认 profile 为 `dev`，但仓库仅存在 `application-local.yml`；启动器必须显式指定 `local`。
- `application-local.yml` 已启用 Flyway，管理员 BCrypt 哈希通过 `bootstrap_admin_password_hash` 占位符传入。
- 前端 `npm run dev` 已提供 Vite HMR；后端尚未配置 DevTools 和脚本级源码编译监听。
- 固定凭据属于公开、虚构且仅限本地的数据，不得复用于任何共享或生产环境。

## 接口与数据

- Windows 入口：`.\scripts\dev.ps1`。
- macOS/Linux 入口：`./scripts/dev.sh`。
- 公共编排入口：`node scripts/dev.mjs`。
- 默认启动命令不接收远程地址、数据库凭据或生产 profile。
- `--down` 仅停止本项目开发容器，不删除数据卷。
- 数据 Owner：本地开发者；数据库仅限 Docker Compose 创建的 `ccb_platform` 本地实例。
- 数据库迁移：不新增迁移；复用 Spring Boot Flyway 自动迁移。已有本地 `sys_user` 管理员密码仅更新 BCrypt 哈希，不修改业务数据结构。

## 验收标准

1. Windows、macOS/Linux 入口都能调用同一 Node 编排器，且 Node 编排器不依赖第三方 npm 包。
2. 启动器缺少 JDK 17、Maven、Node.js 20、npm 或 Docker Compose 时，在启动子进程前给出明确错误并非零退出。
3. 启动器只使用固定本地配置并显式激活 `local` profile，不读取、创建或覆盖 `.env`。
4. 空数据库首次启动后 Flyway 成功完成迁移，可使用 `admin/admin123` 登录。
5. 已有本地数据库启动后 `admin` 密码恢复为 `admin123`，且不修改其他用户密码。
6. 修改前端源码后由 Vite HMR 更新；修改后端 Java 或资源文件后触发防抖 Maven 编译并由 DevTools 重启。
7. `Ctrl+C` 能结束编排器及其前后端子进程；默认保留基础设施容器和数据卷。
8. `--down` 停止本项目开发容器但不删除卷。
9. 根 `AGENTS.md` 明确推荐启动命令、固定开发账号、仅限本地和禁止复用于其他环境的边界。

## 测试与发布

- 必须执行：Node 语法检查、PowerShell 语法检查、Shell 语法检查、治理检查、任务范围检查、后端聚焦测试、前端构建和 `git diff --check`。
- 运行验证：至少在当前 Windows 环境完成基础设施启动、Flyway、健康检查、管理员登录、前端入口、后端源码重启和退出清理；macOS/Linux 由 Shell 静态检查及人工跨平台复核补充。
- 上线验证：本能力不发布到生产；合并后由研发人员在本地执行对应平台入口。
- 回退：回退本需求提交；若 DevTools 引起开发期问题，可先移除其依赖并恢复原手工启动方式。数据库无结构回退，管理员密码恢复仅影响本地 Docker 数据。
- 风险与人工复核人：治理入口、平台启动依赖和本地数据库写入由 Owner `rokeyvvz0828` 专项复核。
