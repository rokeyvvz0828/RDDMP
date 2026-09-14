package com.ccb.datamigration.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class IssueExcelService {
    private static final long MAX_SIZE = 50L * 1024 * 1024;
    private static final int MAX_ROWS = 5000;
    private static final String[] HEADERS = {
            "问题编号", "问题名称", "颗粒度", "系统编号", "系统名称", "问题来源", "缺陷类型", "问题频率分类",
            "问题描述", "解决方案", "会议结论", "问题处理过程", "所属业务场景", "问题处置方", "问题处置责任主体",
            "问题关键字索引", "关联纪要", "问题相关表", "问题相关字段", "所属项目", "创建人", "创建时间", "更新时间"
    };
    private final IssueService issues;
    private final DataMigrationCodeValueService codeValues;

    public IssueExcelService(IssueService issues, DataMigrationCodeValueService codeValues) {
        this.issues = issues;
        this.codeValues = codeValues;
    }

    public Map<String, Object> importIssues(long projectId, MultipartFile file, AuthUser user) {
        if (file == null || file.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 文件不能为空");
        if (file.getSize() > MAX_SIZE) throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 文件不能超过 50 MB");
        int successCount = 0;
        int failureCount = 0;
        List<Map<String, Object>> rowErrors = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 文件没有工作表");
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getLastRowNum() > MAX_ROWS) throw new BusinessException(ErrorCode.BAD_REQUEST, "单次导入不能超过 5000 行");
            Map<String, Integer> columns = columns(sheet.getRow(0));
            requireColumns(columns, "问题编号", "问题名称", "问题描述");
            DataFormatter formatter = new DataFormatter();
            Map<String, String> granularityLabels = labelToCode(DataMigrationCodeValueService.DM_ISSUE_GRANULARITY, user);
            Map<String, String> sourceLabels = labelToCode(DataMigrationCodeValueService.DM_ISSUE_SOURCE, user);
            Map<String, String> defectLabels = labelToCode(DataMigrationCodeValueService.DM_DEFECT_TYPE, user);
            Map<String, String> frequencyLabels = labelToCode(DataMigrationCodeValueService.DM_ISSUE_FREQUENCY, user);
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (isBlank(row, formatter)) continue;
                try {
                    issues.create(toIssue(projectId, row, columns, formatter, granularityLabels, sourceLabels, defectLabels, frequencyLabels), user);
                    successCount++;
                } catch (BusinessException ex) {
                    failureCount++;
                    rowErrors.add(rowError(rowIndex + 1, ex.getMessage()));
                } catch (RuntimeException ex) {
                    failureCount++;
                    rowErrors.add(rowError(rowIndex + 1, "导入失败"));
                }
            }
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 文件解析失败");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("successCount", successCount);
        result.put("failureCount", failureCount);
        result.put("rowErrors", rowErrors);
        return result;
    }

    public byte[] exportIssues(Long projectId, String granularity, String systemCode, String issueSource, String defectType,
                               String frequency, String keyword, AuthUser user) {
        List<Map<String, Object>> rows = issues.exportRows(projectId, granularity, systemCode, issueSource, defectType, frequency, keyword, user);
        Map<String, String> granularityCodes = codeToLabel(DataMigrationCodeValueService.DM_ISSUE_GRANULARITY, user);
        Map<String, String> sourceCodes = codeToLabel(DataMigrationCodeValueService.DM_ISSUE_SOURCE, user);
        Map<String, String> defectCodes = codeToLabel(DataMigrationCodeValueService.DM_DEFECT_TYPE, user);
        Map<String, String> frequencyCodes = codeToLabel(DataMigrationCodeValueService.DM_ISSUE_FREQUENCY, user);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("问题清单");
            Row header = sheet.createRow(0);
            for (int index = 0; index < HEADERS.length; index++) header.createCell(index).setCellValue(HEADERS[index]);
            int rowIndex = 1;
            for (Map<String, Object> data : rows) {
                Row row = sheet.createRow(rowIndex++);
                String[] values = {
                        value(data, "asset_code"), value(data, "asset_name"), label(granularityCodes, data.get("granularity")),
                        value(data, "systemCode"), value(data, "systemName"), label(sourceCodes, data.get("issueSource")),
                        label(defectCodes, data.get("defectType")), label(frequencyCodes, data.get("frequency")),
                        value(data, "issueDescription"), value(data, "solution"), value(data, "meetingConclusion"),
                        value(data, "processingSteps"), value(data, "businessScenario"), value(data, "handler"),
                        value(data, "responsibleParty"), value(data, "keywords"), value(data, "relatedMeetingMinuteNames"),
                        value(data, "relatedTableNames"), value(data, "relatedFieldNames"), value(data, "project_name"),
                        value(data, "created_by_name"), value(data, "created_at"), value(data, "updated_at")
                };
                for (int index = 0; index < values.length; index++) row.createCell(index).setCellValue(values[index]);
            }
            for (int index = 0; index < HEADERS.length; index++) sheet.setColumnWidth(index, Math.min(40, Math.max(12, HEADERS[index].length() * 2 + 4)) * 256);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "问题清单导出失败");
        }
    }

    private static Map<String, Object> toIssue(long projectId, Row row, Map<String, Integer> columns, DataFormatter formatter,
                                               Map<String, String> granularityLabels, Map<String, String> sourceLabels,
                                               Map<String, String> defectLabels, Map<String, String> frequencyLabels) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("projectId", projectId);
        body.put("issueCode", required(row, columns, formatter, "问题编号"));
        body.put("issueName", required(row, columns, formatter, "问题名称"));
        body.put("issueDescription", required(row, columns, formatter, "问题描述"));
        put(body, "granularity", enumValue(cell(row, columns, formatter, "颗粒度"), granularityLabels, "颗粒度"));
        put(body, "systemCode", cell(row, columns, formatter, "系统编号"));
        put(body, "issueSource", enumValue(cell(row, columns, formatter, "问题来源"), sourceLabels, "问题来源"));
        put(body, "defectType", enumValue(cell(row, columns, formatter, "缺陷类型"), defectLabels, "缺陷类型"));
        put(body, "frequency", enumValue(cell(row, columns, formatter, "问题频率分类"), frequencyLabels, "问题频率分类"));
        put(body, "solution", cell(row, columns, formatter, "解决方案"));
        put(body, "meetingConclusion", cell(row, columns, formatter, "会议结论"));
        put(body, "processingSteps", cell(row, columns, formatter, "问题处理过程"));
        put(body, "businessScenario", cell(row, columns, formatter, "所属业务场景"));
        put(body, "handler", cell(row, columns, formatter, "问题处置方"));
        put(body, "responsibleParty", cell(row, columns, formatter, "问题处置责任主体"));
        String keywords = cell(row, columns, formatter, "问题关键字索引");
        if (!keywords.isBlank()) body.put("keywords", List.of(keywords.split("[,，]")));
        return body;
    }

    private static Map<String, Integer> columns(Row header) {
        if (header == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 表头不能为空");
        DataFormatter formatter = new DataFormatter();
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int index = 0; index < header.getLastCellNum(); index++) {
            String name = formatter.formatCellValue(header.getCell(index)).trim();
            if (!name.isBlank()) result.put(name, index);
        }
        return result;
    }

    private static void requireColumns(Map<String, Integer> columns, String... names) {
        for (String name : names) if (!columns.containsKey(name)) throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少必需列: " + name);
    }

    private static String required(Row row, Map<String, Integer> columns, DataFormatter formatter, String name) {
        String value = cell(row, columns, formatter, name);
        if (value.isBlank()) throw new BusinessException(ErrorCode.BAD_REQUEST, name + "不能为空");
        return value;
    }

    private static String cell(Row row, Map<String, Integer> columns, DataFormatter formatter, String name) {
        Integer index = columns.get(name);
        return row == null || index == null ? "" : formatter.formatCellValue(row.getCell(index)).trim();
    }

    private static boolean isBlank(Row row, DataFormatter formatter) {
        if (row == null) return true;
        for (int index = 0; index < row.getLastCellNum(); index++) if (!formatter.formatCellValue(row.getCell(index)).trim().isEmpty()) return false;
        return true;
    }

    private static String enumValue(String value, Map<String, String> labels, String field) {
        if (value.isBlank()) return "";
        if (labels.containsValue(value)) return value;
        String mapped = labels.get(value);
        if (mapped == null) throw new BusinessException(ErrorCode.BAD_REQUEST, field + "值无效: " + value);
        return mapped;
    }

    private Map<String, String> codeToLabel(String category, AuthUser user) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map<String, Object> option : codeValues.options(category, user)) {
            result.put(String.valueOf(option.get("value")), String.valueOf(option.get("label")));
        }
        return result;
    }

    private Map<String, String> labelToCode(String category, AuthUser user) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map<String, Object> option : codeValues.options(category, user)) {
            result.put(String.valueOf(option.get("label")), String.valueOf(option.get("value")));
        }
        return result;
    }

    private static void put(Map<String, Object> body, String key, String value) { if (value != null && !value.isBlank()) body.put(key, value); }
    private static Map<String, Object> rowError(int row, String message) { return Map.of("row", row, "message", message == null ? "导入失败" : message); }
    private static String value(Map<String, Object> data, String key) { Object value = data.get(key); return value == null ? "" : String.valueOf(value); }
    private static String label(Map<String, String> labels, Object code) {
        if (code == null) return "";
        String text = String.valueOf(code);
        return labels.getOrDefault(text, text);
    }
}
