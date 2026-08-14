# Mock 数据契约

统一 mock 数据源为 `mock/mock-data.json`。它服务于本地研发和演示，不是生产初始化脚本，也不替代业务模块自己的接口契约。

## 数据文件结构

```json
{
  "datasetKey": "rddmp-local-platform",
  "datasetVersion": "2026.08.07.1",
  "catalog": [],
  "database": [
    {
      "table": "sys_org",
      "keyColumns": ["id"],
      "rows": []
    }
  ],
  "frontend": []
}
```

- `catalog`：平台功能登记。新功能必须写明模块、入口、可验证场景和数据策略。
- `database`：后端数据库示例数据。每个表声明主键列和行数据，启动时执行白名单校验与幂等 upsert。
- `frontend`：没有后端持久化表的前端示范数据登记。交付示范中心仍保留本地会话 mock 状态，避免把纯演示交互误当成业务数据。
- `datasetVersion`：数据内容发生变化时递增，便于排查启动日志和数据状态。

## 启动行为

使用 `local` profile 启动并保持 `MOCK_DATA_ENABLED=true` 时，Flyway 完成后同步器会读取数据文件。每次启动都会校验并 upsert 数据，状态写入 `sys_mock_dataset_state`。同一数据集重复启动不会产生重复行。

```bash
mvn -pl :ccb-boot -am spring-boot:run -Dspring-boot.run.profiles=local
```

关闭同步：

```dotenv
MOCK_DATA_ENABLED=false
```

非 `local` profile 永远不会执行同步。mock 数据使用高位保留 ID；本地人工修改由文件内容覆盖时属于预期行为，需要临时关闭开关。

数据文件中的 `mock.product`、`mock.engineer`、`mock.qa` 和 `mock.release` 是本地查询用账号，固定测试密码为 `password`。该密码哈希只用于 mock 数据，不得复制到生产环境；生产管理员密码仍由 `BOOTSTRAP_ADMIN_PASSWORD_HASH` 提供。

## 新功能维护规则

1. 新增后端表时，先在 `MockDataInitializer` 的表字段白名单中登记，再在 JSON `catalog` 和 `database` 中加入最小可验证数据。
2. 新增业务功能至少覆盖列表、详情/表单、成功状态、空状态或异常状态中的关键一组数据；业务字段 Mock 由所属模块维护，不登记新的 `biz_form_*` 元数据。
3. 关系表必须与主表同一数据集提供，且所有业务行保留 `tenant_id`。
4. 不写入真实姓名、手机号、邮箱、密码、Token、密钥、生产 URL、真实附件或外部服务凭据。
5. 不修改已发布 Flyway 历史脚本；mock 数据内容只改 JSON，schema 变化另行追加迁移。
6. 删除数据行不会自动删除本地人工数据；需要清理时重建本地数据库或单独提交受控清理脚本。

## 已下线业务表单元数据

“输入项配置/业务表单元数据”已下线，新业务 Mock 不再登记 `biz_form_*` 元数据或扩展字段值。现有相关 Mock 仅作为存量兼容数据保留；除非单独的 `ready` 需求重新启用该能力，Agent 不得扩展其白名单、初始化数据或发布快照。
