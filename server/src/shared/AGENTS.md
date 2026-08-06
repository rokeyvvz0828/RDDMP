# RDDMP 公共后端规则

适用于 `server/src/shared`。

- shared 不拥有业务表或业务状态，不依赖具体业务模块。
- 只放稳定 DTO、响应、异常、追踪、分页和无业务语义工具。
- 变更必须声明公共能力影响、兼容行为、Owner 审批和全部调用方回归。
- 聚焦测试：`mvn -pl :ccb-common -am test`。
