---
id: REQ-20260806-003
title: MinIO 配置外部化与启动校验
status: ready
owner: zhangwei
---

# MinIO 配置外部化与启动校验

## 目标

移除 `MinioStorageProperties` 中的环境相关硬编码默认值，使 MinIO 地址、凭据、桶名和预签名有效期只由 Spring 外部配置提供，并在配置缺失或非法时启动失败。

## 范围

- 删除 Java 属性类中的 endpoint、bucket 和有效期默认值。
- 对 endpoint、access key、secret key、bucket 和有效期增加 Bean Validation 校验。
- 保持 `application-local.yml` 现有环境变量入口和本地默认行为。
- 增加配置属性校验测试。

## 非范围

- 不修改 MinIO 上传、删除、预签名和建桶逻辑。
- 不修改数据库、接口、权限、审计和前端。
- 不提交真实 MinIO 凭据。

## 验收

1. Java 类不包含 MinIO 环境地址、桶名或有效期默认值。
2. 缺少必填配置或有效期越界时校验失败。
3. 合法外部配置校验通过。
4. infrastructure 聚焦测试、后端全量测试和治理检查通过。

## 回退

恢复属性类默认值并移除新增校验和测试；无数据库补偿。
