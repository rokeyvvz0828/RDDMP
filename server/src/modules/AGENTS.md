# RDDMP 业务模块规则

适用于 `server/src/modules`，并继承上级规则。

- 每个目录是独立业务模块，拥有自己的模型、服务、接口和测试。
- 业务模块可以依赖清单允许的 shared/platform 能力，不得直接依赖另一个业务模块。
- 跨域协作需要稳定契约、明确数据 Owner 和双方测试；不得直接写其他模块的表或调用私有 repository。
- 新增模块必须同步根 `pom.xml`、`governance/modules.yaml`、`docs/architecture/MODULES.md` 和 CODEOWNERS。
- 修改权限、状态流转或写操作时，至少覆盖正常、异常、无权限和边界测试。
