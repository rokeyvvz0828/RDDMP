---
id: REQ-20260907-070
status: ready
owner: rokeyvvz0828
module: platform/system
---

# 项目创建类型

用户要求项目创建类型为新建和续建，顶部项目切换体现类型；确认已有项目默认新建且允许编辑为续建。

详细范围、R1-R4验收及平台影响见 docs/engineering-control/designs/2026-09-07-project-creation-type-design.md。
设计和实施计划修订1均已获用户确认。用户随后明确授权读取开发环境配置、更新结构并保留全部现有数据及投产表。2026-09-08已完成当前库兼容迁移与后端重启，详见acceptance.md及deployment.md。使用本地兼容迁移目录，保留历史校验；规范classpath的V156冲突未做全局重排，真实浏览器及独立验收仍未完成。
