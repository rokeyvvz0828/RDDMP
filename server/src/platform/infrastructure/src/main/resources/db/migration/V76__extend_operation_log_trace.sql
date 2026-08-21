ALTER TABLE sys_operation_log
    ADD COLUMN trace_id VARCHAR(64) NULL DEFAULT NULL COMMENT '请求链路追踪标识' AFTER client_ip,
    ADD KEY idx_sys_operation_log_trace (tenant_id, trace_id);
