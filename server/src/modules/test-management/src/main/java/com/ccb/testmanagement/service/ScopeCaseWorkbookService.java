/*
 * 文件：server/src/modules/test-management/src/main/java/com/ccb/testmanagement/service/ScopeCaseWorkbookService.java
 * 说明：测试管理的服务、策略或接口实现。
 * 用途：承载模块边界内的查询、校验、事务、权限或文件处理职责。
 * 作者：hengguan
 */
package com.ccb.testmanagement.service;

// 关键逻辑：文件输入输出在模块内限制格式、大小和字段边界；异常内容不能绕过既有业务校验。

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 固定 XLSX 契约；领域服务负责业务引用、编号及原子写入校验。 */
@Service
public class ScopeCaseWorkbookService {
    private static final long MAX_SIZE = 50L * 1024 * 1024;
    private static final int MAX_ROWS = 2000;
    private static final List<String> SCOPE_HEADERS = List.of("序号", "功能/用例/批处理名称", "末级菜单名称", "所属系统", "所属目录", "功能类型", "变动状态", "业务重要程度", "是否核算相关");
    private static final List<String> SCOPE_KEYS = List.of("scope_code", "scope_name", "leaf_menu", "physical_system_code", "directory_path", "function_type", "change_status", "importance", "accounting_flag");
    private static final List<String> CASE_HEADERS = List.of("案例编号", "所属范围序号", "案例名称", "案例类型", "案例性质", "案例优先级", "前置条件", "操作步骤", "预期结果", "备注", "所属目录", "核算核对结果");
    private static final List<String> CASE_KEYS = List.of("case_code", "scope_code", "case_name", "case_type", "case_nature", "priority", "precondition_html", "steps_html", "expected_result_html", "remark", "directory_path", "accounting_result");
    private final DataFormatter formatter = new DataFormatter();

    public byte[] scopeTemplate() { return write("测试范围导入", SCOPE_HEADERS, List.of(List.of("CORE_BANKING-0001", "客户信息维护", "客户管理/客户信息维护", "CORE_BANKING", "基础资料\\客户信息", "联机交易", "新增", "高", "否"))); }
    public List<Map<String, Object>> scopes(MultipartFile file) { return parse(file, SCOPE_HEADERS, SCOPE_KEYS); }
    public byte[] scopeExport(List<Map<String, Object>> rows) { return write("测试范围", SCOPE_HEADERS, rows.stream().map(row -> values(row, SCOPE_KEYS)).toList()); }

    public byte[] caseTemplate() { return write("测试案例导入", CASE_HEADERS, List.of(List.of("", "CORE_BANKING-0001", "维护客户基本信息-正常流程", "功能案例", "正向", "高", "已准备可用客户数据", "进入客户管理并修改客户信息", "客户信息保存成功并可查询", "", "功能案例集\\客户信息", "未执行"))); }
    public List<Map<String, Object>> cases(MultipartFile file) { return parse(file, CASE_HEADERS, CASE_KEYS); }
    public byte[] caseExport(List<Map<String, Object>> rows) { return write("测试案例", CASE_HEADERS, rows.stream().map(row -> values(row, CASE_KEYS)).toList()); }

    private List<Map<String, Object>> parse(MultipartFile file, List<String> headers, List<String> keys) {
        validate(file);
        try (Workbook book = new XSSFWorkbook(new ByteArrayInputStream(file.getBytes()))) {
            Sheet sheet = book.getNumberOfSheets() == 0 ? null : book.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) throw bad("导入文件没有工作表数据");
            Map<String, Integer> columns = columns(sheet.getRow(0), headers, keys);
            if (sheet.getLastRowNum() > MAX_ROWS) throw bad("单次最多导入 2000 行");
            List<Map<String, Object>> rows = new ArrayList<>();
            for (int index = 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null || empty(row, columns)) continue;
                Map<String, Object> values = new LinkedHashMap<>();
                values.put("row_number", index + 1);
                columns.forEach((key, column) -> values.put(key, cell(row, column)));
                rows.add(values);
            }
            if (rows.isEmpty()) throw bad("导入文件没有有效数据行");
            return rows;
        } catch (BusinessException exception) { throw exception;
        } catch (IOException | RuntimeException exception) { throw bad("XLSX 解析失败，请使用下载的模板并检查文件内容"); }
    }

    private Map<String, Integer> columns(Row header, List<String> headers, List<String> keys) {
        if (header == null) throw bad("导入模板缺少表头");
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Cell cell : header) {
            int index = headers.indexOf(formatter.formatCellValue(cell).trim());
            if (index >= 0) result.put(keys.get(index), cell.getColumnIndex());
        }
        for (int index = 0; index < keys.size(); index++) if (!result.containsKey(keys.get(index))) throw bad("导入模板缺少表头“" + headers.get(index) + "”");
        return result;
    }

    private byte[] write(String name, List<String> headers, List<List<String>> rows) {
        try (Workbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = book.createSheet(name); Row head = sheet.createRow(0);
            for (int index = 0; index < headers.size(); index++) head.createCell(index).setCellValue(headers.get(index));
            int rowIndex = 1;
            for (List<String> values : rows) { Row row = sheet.createRow(rowIndex++); for (int index = 0; index < values.size(); index++) row.createCell(index).setCellValue(values.get(index)); }
            sheet.createFreezePane(0, 1);
            for (int index = 0; index < headers.size(); index++) { sheet.autoSizeColumn(index); sheet.setColumnWidth(index, Math.min(12000, sheet.getColumnWidth(index) + 512)); }
            book.write(out); return out.toByteArray();
        } catch (IOException exception) { throw new BusinessException(ErrorCode.INTERNAL_ERROR, "工作簿生成失败"); }
    }

    private List<String> values(Map<String, Object> row, List<String> keys) { List<String> values = new ArrayList<>(); for (String key : keys) values.add(value(row, key)); return values; }
    private void validate(MultipartFile file) { if (file == null || file.isEmpty()) throw bad("请选择 XLSX 文件"); if (file.getSize() > MAX_SIZE) throw bad("导入文件不能超过 50MB"); if (file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase().endsWith(".xlsx")) throw bad("仅支持 XLSX 文件"); }
    private String cell(Row row, int index) { Cell cell = row.getCell(index); return cell == null ? "" : formatter.formatCellValue(cell).trim(); }
    private boolean empty(Row row, Map<String, Integer> columns) { for (int column : columns.values()) if (!cell(row, column).isEmpty()) return false; return true; }
    private String value(Map<String, Object> row, String key) { Object value = row.get(key); return value == null ? "" : String.valueOf(value); }
    private BusinessException bad(String message) { return new BusinessException(ErrorCode.BAD_REQUEST, message); }
}
