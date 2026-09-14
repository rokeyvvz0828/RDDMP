# 系统消息通知接入契约

## 能力边界

`platform/system` 统一拥有站内消息数据以及用户已读、归档状态。需求、研发、测试、版本、迁移、投产、工作流及后续业务模块只能通过 `com.ccb.system.notification.SystemNotificationPublisher` 发布消息，不得直接读写 `sys_notification` 或 `sys_user_notification`。

当前能力仅提供站内消息和 SSE 实时失效通知，不发送短信、邮件、企业微信或浏览器推送。通知动作只允许使用 `/` 开头的应用内路由，不接受外部 URL。

## Java 接入

调用模块先在自身 `pom.xml` 和 `governance/modules.yaml` 声明对 `ccb-system` / `platform/system` 的依赖，再注入公开接口：

```java
import com.ccb.system.notification.NotificationLevel;
import com.ccb.system.notification.NotificationPublishCommand;
import com.ccb.system.notification.SystemNotificationPublisher;
import java.util.List;

public class DeliveryService {
    private final SystemNotificationPublisher notificationPublisher;

    public DeliveryService(SystemNotificationPublisher notificationPublisher) {
        this.notificationPublisher = notificationPublisher;
    }

    public void notifyTestReady(long tenantId, long operatorId, long ownerId, String projectCode) {
        notificationPublisher.publish(new NotificationPublishCommand(
                tenantId,
                "test-ready:" + projectCode,
                "delivery",
                "交付示范中心",
                "DELIVERY_PROJECT",
                projectCode,
                List.of(ownerId),
                "项目已进入测试阶段",
                projectCode + " 已完成研发交付，请安排测试。",
                NotificationLevel.INFO,
                "测试流转",
                "/delivery-showcase/projects",
                operatorId,
                projectCode,
                "平台能力升级项目"));
    }
}
```

## 字段约束

| 字段 | 规则 |
| --- | --- |
| `tenantId` | 必须来自当前业务上下文，不得由前端任意指定 |
| `eventId` | 同一业务事件保持稳定，用于重试幂等，最长 128 字符 |
| `moduleCode` | 必填的稳定业务板块编码；小写字母开头，可包含小写字母、数字、下划线和连字符，最长 64 字符 |
| `moduleName` | 必填的业务板块展示名，最长 128 字符，例如 `配置管理` |
| `businessType` | 稳定的业务类型编码，最长 64 字符 |
| `businessKey` | 可追溯的业务主键，最长 128 字符 |
| `recipientUserIds` | 同租户、启用且未删除的用户，去重后 1 至 500 人 |
| `title` / `content` | 必填，最长分别为 200 / 2000 字符，不放敏感信息 |
| `level` | `INFO`、`SUCCESS`、`WARNING` 或 `ERROR`，为空时按 `INFO` |
| `sourceName` | 消息触发来源，最长 128 字符，例如 `审批中心`；不得代替业务板块 |
| `actionPath` | 可空；非空时必须为 `/` 开头的站内路由 |
| `actorUserId` | 可空；非空时必须是同租户有效用户，用于发布审计 |
| `projectRef` / `projectName` | 可空但必须同时提供或同时为空；长度上限分别为 64 / 128。非空时是通知产生时的项目展示快照，为空表示平台全局消息 |

为保持既有发布方源码兼容，`NotificationPublishCommand` 保留原 13 参数构造器，并将两个项目字段初始化为空。业务明确属于项目时应使用完整构造器传入可靠项目上下文；不得根据标题、路由或业务主键推断项目。

## 幂等与事务

- 幂等范围是 `(tenantId, businessType, eventId)`。业务重试必须复用同一 `eventId`；新事件不得复用旧标识。
- 首次发布时固化消息内容和接收人。重复发布返回原通知编号，不追加或替换接收人。
- 项目上下文同样在首次发布时固化；重复事件不会覆盖首次项目快照。
- 发布方法参与调用方事务；业务事务回滚时通知写入一并回滚。异步消费方应使用自己的稳定事件标识重试。
- 发布成功写入 `sys_operation_log`，日志只记录业务类型和通知编号，不记录通知正文。

`moduleCode/moduleName`、`sourceName` 和 `businessType` 含义独立。以配置管理审批通知为例，三者分别为 `release / 配置管理`、`审批中心`、`release_application`，前端展示为 `配置管理 · 审批中心`。业务模块必须在发布或启动工作流时显式提供板块信息，不得由前端根据路由或标题推导。

消息中心保持租户内、用户维度的全局通知视图，不按顶部当前项目过滤。每条项目消息显示 `所属项目：<projectName>`；没有项目上下文的通知显示 `所属范围：平台全局`。项目字段只用于归属展示，不替代项目访问权限或业务数据范围校验。

## 用户接口

前端统一通过 `web/src/api/notifications.ts` 调用：

- `GET /api/notifications`：当前用户消息分页。`view` 支持 `ALL`、`UNREAD`、`ARCHIVED`，默认 `ALL`；未传 `view` 时保留旧 `unreadOnly=true` 未读筛选兼容。可用 `moduleCode` 按业务板块服务端筛选，未知合法编码返回空分页。
- `GET /api/notifications/modules`：按可选 `view` 返回当前用户可见的业务板块及各板块消息总数、未读数。
- `GET /api/notifications/unread-count`：当前用户未读数。
- `POST /api/notifications/stream-ticket`：认证用户申请 30 秒有效、单次使用的 SSE 票据；返回 `ticket` 和 `expiresAt`。
- `GET /api/notifications/stream?ticket=...`：使用票据建立 SSE。事件 `notification` 表示通知状态需要刷新，`heartbeat` 每 25 秒维持并检测连接；事件不包含通知正文或用户数据。
- `PATCH /api/notifications/{id}/read`：仅更新当前用户的该条消息。
- `PATCH /api/notifications/read-all`：仅更新当前用户的全部未读消息。
- `PATCH /api/notifications/{id}/archive`：仅归档当前用户已读且未归档的该条消息；未读消息返回 409 和明确提示，重复归档幂等。
- `PATCH /api/notifications/{id}/restore`：恢复当前用户的该条归档消息并保持已读，重复恢复幂等。
- `PATCH /api/notifications/archive-read`：归档当前用户全部未归档的已读消息，返回实际变更数量，未读消息不受影响。

接口从认证主体读取租户和用户，不接受客户端身份参数。归档仅设置 `sys_user_notification.archived_at`，不删除共享通知、接收关系或发布审计；恢复只清空归档时间，不修改已读状态。业务板块聚合也从当前用户的通知关联数据开始查询，不暴露其他用户或租户的板块。铃铛角标、`ALL` 和 `UNREAD` 只统计未归档消息；`ARCHIVED` 只统计已归档消息。`read-all` 仍标记当前用户全部板块的活动消息，不自动归档。业务页面需要刷新通知状态时，应复用消息中心能力，不自行查询通知表。

## SSE 刷新约定

- 票据签发必须携带现有 Bearer JWT；原生 `EventSource` 只携带一次性票据，禁止把访问 JWT 放入 URL。
- 票据与签发时的租户、用户绑定，兑换时原子删除；无效、过期和重复兑换统一返回 401。
- 发布、已读、全部已读、归档、恢复和批量归档仅在实际影响数据且事务成功提交后，向该用户的全部本实例连接发送失效事件。事务回滚不发送。
- 页面首次加载查询一次未读数，之后由事件触发刷新；抽屉打开时同时刷新列表和板块统计。页面隐藏时断开，恢复可见后重新申请票据。
- 重连间隔为 1、2、5、15、30 秒，之后保持 30 秒重试。连续失败后启用 60 秒未读数兜底轮询，SSE 恢复即停止轮询，不得恢复秒级轮询。
- SSE 注册表为单实例内存能力。部署多个后端副本前必须增加跨实例广播（例如 Redis Pub/Sub），并保持现有浏览器协议不变。
- 反向代理必须对精确 `/api/notifications/stream` 路径关闭响应缓冲和缓存，并允许至少 10 分钟读取；当前标准配置使用 1 小时读取超时。
