/*
 * 文件：server/src/modules/test-management/src/main/java/com/ccb/testmanagement/service/TestAnalyticsWorkbookService.java
 * 说明：测试管理的服务、策略或接口实现。
 * 用途：承载模块边界内的查询、校验、事务、权限或文件处理职责。
 * 作者：hengguan
 */
package com.ccb.testmanagement.service;

// 关键逻辑：文件输入输出在模块内限制格式、大小和字段边界；异常内容不能绕过既有业务校验。
import org.apache.poi.ss.usermodel.*;import org.apache.poi.xssf.usermodel.XSSFWorkbook;import org.springframework.stereotype.Service;import java.io.*;import java.util.*;
@Service public class TestAnalyticsWorkbookService {
    public byte[] export(Map<String,Object> data) {
        try (Workbook book = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = book.createSheet("分析统计");
            int rowNo = 0;
            Row title = sheet.createRow(rowNo++);
            title.createCell(0).setCellValue(String.valueOf(data.getOrDefault("report_name", data.getOrDefault("report_key", data.getOrDefault("key", "分析统计")))));
            Object rawSummary = data.get("summary");
            if (rawSummary instanceof Map<?,?> summary) {
                for (Map.Entry<?,?> entry : summary.entrySet()) {
                    if (entry.getValue() instanceof Collection<?> || entry.getValue() instanceof Map<?,?>) continue;
                    Row row = sheet.createRow(rowNo++);
                    row.createCell(0).setCellValue(label(String.valueOf(entry.getKey())));
                    row.createCell(1).setCellValue(value(entry.getValue()));
                }
            }
            rowNo++;
            Object rawRows = data.containsKey("rows") ? data.get("rows") : data.getOrDefault("table", List.of());
            List<?> rows = rawRows instanceof List<?> list ? list : List.of();
            if (!rows.isEmpty() && rows.get(0) instanceof Map<?,?> first) {
                Row header = sheet.createRow(rowNo++);
                int column = 0;
                for (Object key : first.keySet()) header.createCell(column++).setCellValue(label(String.valueOf(key)));
                for (Object item : rows) {
                    if (!(item instanceof Map<?,?> values)) continue;
                    Row row = sheet.createRow(rowNo++);
                    column = 0;
                    for (Object entry : values.values()) row.createCell(column++).setCellValue(value(entry));
                }
            }
            for (int column = 0; column < 12; column++) sheet.autoSizeColumn(column);
            book.write(output);
            return output.toByteArray();
        } catch (IOException e) { throw new IllegalStateException("统计导出失败", e); }
    }
    private String value(Object value) {
        if (value == null) return "";
        return switch (String.valueOf(value)) {
            case "UNEXECUTED" -> "未执行"; case "IN_PROGRESS", "RUNNING" -> "执行中";
            case "SUCCESS" -> "成功"; case "FAILED" -> "失败"; case "BLOCKED" -> "阻塞";
            case "RAISED" -> "已提出"; case "ANALYZING" -> "分析中"; case "RESOLVED" -> "已解决"; case "CLOSED" -> "已关闭";
            case "FATAL" -> "致命"; case "SERIOUS" -> "严重"; case "NORMAL" -> "一般"; case "MINOR" -> "轻微";
            case "true", "1" -> "是"; case "false", "0" -> "否"; default -> String.valueOf(value);
        };
    }
    private String label(String key) {
        return switch (key) {
            case "dimension" -> "统计维度"; case "row_dimension" -> "行维度"; case "column_dimension" -> "列维度"; case "value" -> "统计值";
            case "scope_total" -> "测试范围数"; case "covered_total" -> "已覆盖范围数"; case "uncovered_total" -> "未覆盖范围数"; case "coverage_rate" -> "范围覆盖率";
            case "case_total" -> "案例总数"; case "effective_case_total" -> "有效案例数"; case "invalid_case_total" -> "无效案例数";
            case "execution_total" -> "已执行案例数"; case "execution_unexecuted", "unexecuted_count" -> "未执行案例数"; case "execution_in_progress", "in_progress_count" -> "执行中案例数";
            case "execution_success", "success_count" -> "成功案例数"; case "execution_failed", "failed_count" -> "失败案例数"; case "execution_blocked", "blocked_count" -> "阻塞案例数";
            case "execution_rate" -> "执行率"; case "success_rate", "case_success_rate" -> "案例成功率"; case "executed_case_success_rate" -> "已执行案例成功率";
            case "defect_total" -> "缺陷总数"; case "defect_open" -> "未关闭缺陷数"; case "defect_density" -> "缺陷密度"; case "defect_repair_rate" -> "缺陷修复率"; case "severe_defect_count" -> "严重缺陷数";
            case "handled_defects" -> "已处理缺陷数"; case "pending_defects" -> "待处理缺陷数"; case "completed_count" -> "已完成数"; case "raised_count" -> "新增缺陷数"; case "resolved_count" -> "已解决数";
            case "defect_code" -> "缺陷编号"; case "summary" -> "缺陷摘要"; case "status" -> "状态"; case "severity" -> "严重程度"; case "urgency" -> "紧急程度"; case "handler_name" -> "处理人"; case "overdue_days" -> "逾期天数";
            case "case_code" -> "案例编号"; case "case_name" -> "案例名称"; case "case_type" -> "案例类型"; case "priority" -> "优先级"; case "invalidated" -> "是否无效"; case "execution_status" -> "执行状态"; case "executed_at" -> "执行时间"; case "proposed_at" -> "提出时间";
            default -> key;
        };
    }
}
