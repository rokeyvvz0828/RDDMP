# Workflow 演示数据

后端首次启动时，Flyway 会执行 V30，自动写入一组本地 workflow 演示数据。

## 进入方式

- 前端：http://127.0.0.1:5173
- 账号：admin
- 密码：admin123
- 流程定义：研发费用报销审批、版本投产审批、系统变更审批示例（草稿）
- 流程实例：DEMO-EXP-2026-001、DEMO-EXP-2026-002、DEMO-REL-2026-001、DEMO-REL-2026-002

## 可验证内容

- 流程定义页：查看两个已发布流程和一个草稿流程。
- 审批待办：查看运行中的审批任务并测试审批动作。
- 流程已办：查看同意、拒绝和抄送记录。
- 流程监控：打开实例详情，查看流程图节点状态和审计时间线。

## 清理方式

这组数据只使用 DEMO_ 编码和 900000000000000 以上的 ID。仅在本地开发库清理时执行：

DELETE FROM wf_audit_event WHERE id >= 900000000000000;
DELETE FROM wf_task_action WHERE id >= 900000000000000;
DELETE FROM wf_task WHERE id >= 900000000000000;
DELETE FROM wf_instance WHERE id >= 900000000000000;
DELETE FROM wf_version WHERE id >= 900000000000000;
DELETE FROM wf_definition WHERE id >= 900000000000000;

不要修改 Flyway 已执行记录，也不要将这组演示数据用于生产数据库。
