ALTER TABLE sys_user_notification
    ADD COLUMN archived_at TIMESTAMP NULL COMMENT '当前接收人归档时间' AFTER read_at,
    ADD KEY idx_sys_user_notification_archive
        (tenant_id, user_id, archived_at, is_read, created_at, notification_id);
