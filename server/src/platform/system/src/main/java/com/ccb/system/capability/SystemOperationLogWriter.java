package com.ccb.system.capability;

/** 写入平台级 HTTP 操作日志。 */
public interface SystemOperationLogWriter {
    void record(SystemOperationLogCommand command);
}
