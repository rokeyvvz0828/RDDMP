/*
 * 文件：server/src/modules/test-management/src/main/java/com/ccb/testmanagement/service/ExecutionDefectWorkbookService.java
 * 说明：测试管理的服务、策略或接口实现。
 * 用途：承载模块边界内的查询、校验、事务、权限或文件处理职责。
 * 作者：hengguan
 */
package com.ccb.testmanagement.service;

// 关键逻辑：文件输入输出在模块内限制格式、大小和字段边界；异常内容不能绕过既有业务校验。

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/** 测试执行、缺陷的只读导出，不在导出层做业务数据查询。 */
@Service
public class ExecutionDefectWorkbookService {
    public byte[] executionExport(List<Map<String, Object>> rows) {
        return write("测试执行", List.of("案例编号", "案例名称", "所属范围", "执行状态", "执行人", "执行时间", "缺陷数", "备注"), rows, List.of("case_code", "case_name", "scope_name", "execution_status", "executor_name", "executed_at", "defect_count", "remark_html"));
    }
    public byte[] defectExport(List<Map<String, Object>> rows) {
        return write("测试缺陷", List.of("缺陷编号", "概述", "所属系统", "分类", "严重程度", "优先级", "紧急程度", "状态", "处理人", "提出人", "提出时间", "关联案例数", "缺陷描述"), rows, List.of("defect_code", "summary", "physical_system_name", "defect_category", "severity", "priority", "urgency", "status", "handler_name", "proposer_name", "proposed_at", "execution_count", "description_html"));
    }
    private byte[] write(String name, List<String> headers, List<Map<String, Object>> rows, List<String> keys) {
        try (Workbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = book.createSheet(name); Row header = sheet.createRow(0);
            for (int index = 0; index < headers.size(); index++) header.createCell(index).setCellValue(headers.get(index));
            int rowIndex = 1;
            for (Map<String, Object> source : rows) { Row row = sheet.createRow(rowIndex++); for (int index = 0; index < keys.size(); index++) row.createCell(index).setCellValue(text(source.get(keys.get(index)))); }
            sheet.createFreezePane(0, 1);
            for (int index = 0; index < headers.size(); index++) { sheet.autoSizeColumn(index); sheet.setColumnWidth(index, Math.min(16000, sheet.getColumnWidth(index) + 512)); }
            book.write(out); return out.toByteArray();
        } catch (IOException exception) { throw new BusinessException(ErrorCode.INTERNAL_ERROR, "工作簿生成失败"); }
    }
    private String text(Object value) { return value == null ? "" : String.valueOf(value).replaceAll("<[^>]*>", " ").replace("&nbsp;", " ").trim(); }
}
