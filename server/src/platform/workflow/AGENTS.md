# Workflow 模块规则

职责：流程设计模型、校验、BPMN 编译、Flowable 运行与监控。公开调用基线见 `governance/modules.yaml`。

- 前端提交业务流程模型，后端负责校验、编译和部署 BPMN；业务与前端不得直接操作 Flowable `ACT_*` 表。
- 节点、连线、变量、办理人和表单绑定变更必须覆盖模型校验、编译结果与运行兼容。
- 状态流转、代办和监控查询必须校验身份、数据范围和审计。
- 聚焦测试：`mvn -pl :ccb-workflow -am test`。
