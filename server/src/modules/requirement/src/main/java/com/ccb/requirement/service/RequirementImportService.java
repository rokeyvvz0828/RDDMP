package com.ccb.requirement.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.ccb.requirement.support.RequirementEnums;
import com.ccb.requirement.support.RequirementIds;
import com.ccb.requirement.support.RequirementSql;
import com.ccb.requirement.support.RequirementValues;

/** Excel 导入：模板下载、逐行校验与校验报告、确认后事务化导入。 */
@Service
public class RequirementImportService {
    private static final Pattern REQUIREMENT_NO_HINT = Pattern.compile("^[A-Za-z0-9]+-\\d{3}$");
    private static final Map<String, String> DIFF_HEADERS = diffHeaders();
    private static final Map<String, String> LEGACY_HEADERS = legacyHeaders();

    private final JdbcTemplate jdbc;
    private final RequirementSecurityService security;
    private final RequirementSystemService systemService;
    private final RequirementChangeLogService changeLog;
    private final ObjectMapper objectMapper;

    public RequirementImportService(JdbcTemplate jdbc, RequirementSecurityService security,
                                    RequirementSystemService systemService,
                                    RequirementChangeLogService changeLog, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.security = security;
        this.systemService = systemService;
        this.changeLog = changeLog;
        this.objectMapper = objectMapper;
    }

    public byte[] template(String bizType) {
        Map<String, String> headers = headers(bizType);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("模板");
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            int index = 0;
            Row headerRow = sheet.createRow(0);
            for (String header : headers.keySet()) {
                Cell cell = headerRow.createCell(index++);
                cell.setCellValue(header);
                cell.setCellStyle(headerStyle);
            }
            Row exampleRow = sheet.createRow(1);
            int exampleIndex = 0;
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                exampleRow.createCell(exampleIndex++).setCellValue(exampleValue(bizType, entry.getValue()));
            }
            for (int i = 0; i < index; i++) {
                sheet.setColumnWidth(i, 22 * 256);
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "模板生成失败");
        }
    }

    public Map<String, Object> preview(String bizType, Long projectId, MultipartFile file, AuthUser user) {
        requireBizType(bizType);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "上传文件不能为空");
        }
        if (projectId != null) {
            security.requireProjectAccess(user, projectId);
        }
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            Map<String, String> headers = headers(bizType);
            Map<Integer, String> columnField = new LinkedHashMap<>();
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "模板首行必须为字段标题");
            }
            DataFormatter formatter = new DataFormatter();
            for (int column = 0; column < headerRow.getLastCellNum(); column++) {
                String title = formatter.formatCellValue(headerRow.getCell(column)).trim();
                String field = headers.get(title);
                if (field != null) {
                    columnField.put(column, field);
                }
            }
            if (columnField.isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "未识别到模板字段标题，请使用标准模板");
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            List<Map<String, Object>> errors = new ArrayList<>();
            int total = 0;
            int errorRows = 0;
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (isEmptyRow(row)) {
                    continue;
                }
                total++;
                Map<String, Object> values = new LinkedHashMap<>();
                for (Map.Entry<Integer, String> entry : columnField.entrySet()) {
                    String raw = formatter.formatCellValue(row.getCell(entry.getKey())).trim();
                    if (!raw.isEmpty()) {
                        values.put(entry.getValue(), raw);
                    }
                }
                List<String> messages = validateRow(bizType, projectId, values, user, new HashSet<>(), false);
                if (messages.isEmpty()) {
                    rows.add(values);
                } else {
                    errorRows++;
                    Map<String, Object> error = new LinkedHashMap<>();
                    error.put("row", rowIndex + 1);
                    error.put("messages", messages);
                    errors.add(error);
                }
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("bizType", bizType);
            result.put("projectId", projectId);
            result.put("totalRows", total);
            result.put("successRows", rows.size());
            result.put("errorRows", errorRows);
            result.put("errors", errors);
            result.put("rows", rows);
            return result;
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 解析失败，请确认文件格式");
        }
    }

    @Transactional
    public Map<String, Object> confirm(String bizType, Long projectId, String fileName,
                                       List<Map<String, Object>> rows, AuthUser user) {
        requireBizType(bizType);
        if (rows == null || rows.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "没有可导入的数据");
        }
        if (projectId != null) {
            security.requireProjectAccess(user, projectId);
        }
        Set<String> seenNumbers = new HashSet<>();
        List<Map<String, Object>> errors = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            List<String> messages = validateRow(bizType, projectId, rows.get(i), user, seenNumbers, true);
            if (!messages.isEmpty()) {
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("row", i + 1);
                error.put("messages", messages);
                errors.add(error);
            }
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "导入数据校验未通过：" + errors);
        }
        int success = 0;
        if ("DIFF".equals(bizType)) {
            success = importDifferences(projectId, rows, user);
        } else {
            success = importLegacy(rows, user);
        }
        long batchId = RequirementIds.next();
        Map<String, Object> batch = new LinkedHashMap<>();
        batch.put("id", batchId);
        batch.put("tenant_id", user.tenantId());
        batch.put("biz_type", bizType);
        batch.put("project_id", projectId);
        batch.put("file_name", fileName);
        batch.put("template_type", "DIFF".equals(bizType) ? "REQUIREMENT_DIFFERENCE" : "LEGACY_REQUIREMENT");
        batch.put("total_rows", rows.size());
        batch.put("success_rows", success);
        batch.put("error_rows", rows.size() - success);
        batch.put("errors_json", toJson(errors));
        batch.put("status", "IMPORTED");
        batch.put("operator_id", user.id());
        batch.put("operator_name", user.displayName());
        batch.put("deleted", 0);
        RequirementSql.insert(jdbc, "req_import_batch", batch);
        return Map.of("batchId", batchId, "totalRows", rows.size(), "successRows", success);
    }

    public List<Map<String, Object>> listBatches(AuthUser user) {
        return jdbc.queryForList("""
                SELECT id, biz_type, project_id, file_name, template_type, total_rows, success_rows,
                       error_rows, status, operator_id, operator_name, created_at
                FROM req_import_batch WHERE tenant_id = ? AND deleted = 0
                ORDER BY created_at DESC, id DESC
                """, user.tenantId());
    }

    private int importDifferences(Long projectId, List<Map<String, Object>> rows, AuthUser user) {
        Integer projectCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM req_project WHERE tenant_id = ? AND id = ? AND deleted = 0",
                Integer.class, user.tenantId(), projectId);
        if (projectCount == null || projectCount == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "导入项目不存在");
        }
        Long maxSeq = jdbc.queryForObject(
                "SELECT COALESCE(MAX(seq_no), 0) FROM req_difference WHERE tenant_id = ? AND project_id = ? AND deleted = 0",
                Long.class, user.tenantId(), projectId);
        int seq = maxSeq == null ? 0 : maxSeq.intValue();
        for (Map<String, Object> values : rows) {
            seq++;
            long id = RequirementIds.next();
            values.put("id", id);
            values.put("tenant_id", user.tenantId());
            values.put("project_id", projectId);
            values.put("seq_no", values.get("seq_no") == null ? seq : RequirementValues.intOf(values.get("seq_no"), seq));
            values.putIfAbsent("review_status", "待评审");
            values.putIfAbsent("dev_status", "未开始");
            values.putIfAbsent("test_status", "未开始");
            values.put("source", "IMPORT");
            values.put("created_by", user.id());
            values.put("deleted", 0);
            RequirementSql.insert(jdbc, "req_difference", values);
            changeLogImport("NEW_PROJECT_DIFF", id, values, user);
        }
        return rows.size();
    }

    private int importLegacy(List<Map<String, Object>> rows, AuthUser user) {
        for (Map<String, Object> values : rows) {
            long id = RequirementIds.next();
            values.put("id", id);
            values.put("tenant_id", user.tenantId());
            values.putIfAbsent("current_stage", "PROPOSE");
            values.putIfAbsent("propose_stage_status", "未开始");
            values.putIfAbsent("docking_stage_status", "未开始");
            values.putIfAbsent("workload_stage_status", "未开始");
            values.putIfAbsent("project_stage_status", "未开始");
            values.putIfAbsent("soft_stage_status", "未开始");
            values.putIfAbsent("launch_stage_status", "未开始");
            values.put("source", "IMPORT");
            values.put("created_by", user.id());
            values.put("deleted", 0);
            RequirementSql.insert(jdbc, "req_legacy_requirement", values);
            changeLogImport("LEGACY_REQUIREMENT", id, values, user);
        }
        return rows.size();
    }

    private void changeLogImport(String bizType, long bizId, Map<String, Object> values, AuthUser user) {
        // 复用统一改动记录：导入按字段留痕，change_type=IMPORT，来源=IMPORT
        List<String> excluded = List.of("id", "tenant_id", "project_id", "deleted", "created_by",
                "created_at", "updated_by", "updated_at", "review_status", "dev_status", "test_status");
        Map<String, Object> onlyFields = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (!excluded.contains(entry.getKey())) {
                onlyFields.put(entry.getKey(), entry.getValue());
            }
        }
        changeLog.recordFields(bizType, bizId, "IMPORT", Map.of(), onlyFields, user, "IMPORT");
    }

    private List<String> validateRow(String bizType, Long projectId, Map<String, Object> values,
                                     AuthUser user, Set<String> seenNumbers, boolean checkDb) {
        List<String> messages = new ArrayList<>();
        if ("DIFF".equals(bizType)) {
            validateDiffRow(values, user, messages);
        } else {
            validateLegacyRow(values, user, seenNumbers, checkDb, messages);
        }
        return messages;
    }

    private void validateDiffRow(Map<String, Object> values, AuthUser user, List<String> messages) {
        requireImport(values, "name", "名称", messages);
        requireImport(values, "business_group", "业务组", messages);
        requireImport(values, "requirement_no", "需求编号", messages);
        requireImport(values, "category", "分类", messages);
        requireImport(values, "difference_type", "差异类型", messages);
        requireOption(values, "category", "categories", messages);
        requireOption(values, "difference_type", "differenceTypes", messages);
        requireOption(values, "adapt_mode", "adaptModes", messages);
        requireOption(values, "handle_status", "handleStatuses", messages);
        requireOption(values, "decision_level", "decisionLevels", messages);
        requireOption(values, "is_special", "yesNo", messages);
        requireOption(values, "dev_status", "devStatuses", messages);
        requireOption(values, "test_status", "testStatuses", messages);
        String requirementNo = RequirementValues.text(values, "requirement_no");
        if (requirementNo != null && !REQUIREMENT_NO_HINT.matcher(requirementNo).matches()) {
            messages.add("提示：需求编号格式建议为 组件物理子系统编号+三位序号（如 W01812-001），本期不拦截");
        }
        String systemCode = RequirementValues.text(values, "system_code");
        if (systemCode != null) {
            long systemId = systemService.resolveSystemId(systemCode, user);
            if (systemId == 0) {
                messages.add("错误：涉及系统编号不存在：" + systemCode);
            } else {
                values.put("system_id", systemId);
            }
        }
        values.remove("system_code");
    }

    private void validateLegacyRow(Map<String, Object> values, AuthUser user, Set<String> seenNumbers,
                                   boolean checkDb, List<String> messages) {
        requireImport(values, "requirement_no", "需求编号", messages);
        requireImport(values, "requirement_name", "需求名称", messages);
        requireImport(values, "business_group", "业务组", messages);
        String requirementNo = RequirementValues.text(values, "requirement_no");
        if (requirementNo != null && !seenNumbers.add(requirementNo)) {
            messages.add("错误：需求编号在文件内重复：" + requirementNo);
        }
        if (checkDb && requirementNo != null) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM req_legacy_requirement WHERE tenant_id = ? AND requirement_no = ? AND deleted = 0",
                    Integer.class, user.tenantId(), requirementNo);
            if (count != null && count > 0) {
                messages.add("错误：需求编号已存在：" + requirementNo);
            }
        }
        requireOption(values, "requirement_type", "requirementTypes", messages);
        requireOption(values, "regulation_category", "regulationCategories", messages);
        requireOption(values, "requirement_status", "requirementStatuses", messages);
        requireOption(values, "launch_mode", "launchModes", messages);
        requireOption(values, "change_review_conclusion", "changeReviewConclusions", messages);
        requireOption(values, "change_conclusion_status", "changeConclusionStatuses", messages);
        for (String field : List.of("need_jinke_arch_decision", "unified_managed",
                "involve_cooperation", "change_involved", "not_project_developed")) {
            requireOption(values, field, "yesNo", messages);
        }
        for (String dateField : List.of("expected_launch_date", "regulation_launch_date",
                "requirement_received_date", "ba_review_date", "workload_date", "finance_project_date",
                "soft_submit_date", "soft_review_date", "planned_launch_date", "actual_launch_date")) {
            Object raw = values.get(dateField);
            if (raw != null && !String.valueOf(raw).isBlank()) {
                try {
                    values.put(dateField, RequirementValues.date(raw));
                } catch (BusinessException exception) {
                    messages.add("错误：" + exception.getMessage());
                }
            }
        }
        String businessGroup = RequirementValues.text(values, "business_group");
        if (businessGroup != null && !security.isAdmin(user) && !security.isBusinessGroupMember(user, businessGroup)) {
            messages.add("错误：无业务组数据权限：" + businessGroup);
        }
    }

    private void requireImport(Map<String, Object> values, String field, String label, List<String> messages) {
        if (RequirementValues.text(values, field) == null) {
            messages.add("错误：必填项缺失：" + label);
        }
    }

    private void requireOption(Map<String, Object> values, String field, String optionKey, List<String> messages) {
        String value = RequirementValues.text(values, field);
        Object rawOptions = RequirementEnums.OPTIONS.get(optionKey);
        if (value != null && (!(rawOptions instanceof List<?> options) || !options.contains(value))) {
            messages.add("错误：字段取值不在受控枚举内：" + field + " = " + value);
        }
    }

    private Map<String, String> headers(String bizType) {
        return "DIFF".equals(bizType) ? DIFF_HEADERS : LEGACY_HEADERS;
    }

    private void requireBizType(String bizType) {
        if (!"DIFF".equals(bizType) && !"LEGACY".equals(bizType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "模板类型必须为 DIFF 或 LEGACY");
        }
    }

    private boolean isEmptyRow(Row row) {
        if (row == null) {
            return true;
        }
        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null && !new DataFormatter().formatCellValue(cell).trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private String exampleValue(String bizType, String field) {
        if ("DIFF".equals(bizType)) {
            return switch (field) {
                case "seq_no" -> "1";
                case "business_conglomerate" -> "数字金融事业群";
                case "business_section" -> "零售业务板块";
                case "business_group" -> "零售一组";
                case "requirement_no" -> "W01812-001";
                case "category" -> "功能";
                case "name" -> "示例差异点（脱敏）";
                case "system_code" -> "W01812";
                case "difference_type" -> "金科有-蒙商无";
                case "adapt_mode" -> "按原型";
                case "handle_status" -> "双方已确认";
                case "is_special" -> "否";
                case "decision_level" -> "版块内";
                default -> "";
            };
        }
        return switch (field) {
            case "requirement_no" -> "JG-W0332C-240507-001";
            case "requirement_name" -> "示例存量需求（脱敏）";
            case "business_group" -> "零售一组";
            case "requirement_type" -> "业务";
            case "requirement_status" -> "需求分析";
            default -> "";
        };
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "[]";
        }
    }

    private static Map<String, String> diffHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("序号", "seq_no");
        headers.put("事业群", "business_conglomerate");
        headers.put("业务板块", "business_section");
        headers.put("业务组", "business_group");
        headers.put("需求编号", "requirement_no");
        headers.put("分类", "category");
        headers.put("名称", "name");
        headers.put("涉及系统编号", "system_code");
        headers.put("金科做法", "jinke_practice");
        headers.put("差异类型", "difference_type");
        headers.put("蒙商作法", "monshang_practice");
        headers.put("差异描述", "difference_desc");
        headers.put("蒙商分析部门", "monshang_dept");
        headers.put("蒙商分析人", "monshang_analyst");
        headers.put("金科分析人", "jinke_analyst");
        headers.put("适配方式", "adapt_mode");
        headers.put("处理状态", "handle_status");
        headers.put("协同组", "coord_group");
        headers.put("解决方案", "solution");
        headers.put("是否专题", "is_special");
        headers.put("上升决策层级", "decision_level");
        headers.put("决策结论", "decision_conclusion");
        headers.put("蒙商确认部门", "monshang_confirm_dept");
        headers.put("金科确认人", "jinke_confirmer");
        return Map.copyOf(headers);
    }

    private static Map<String, String> legacyHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("业需文档名称", "legacy_doc_name");
        headers.put("需求编号", "requirement_no");
        headers.put("需求名称", "requirement_name");
        headers.put("需求内容简述", "content_summary");
        headers.put("需求提出部门", "propose_dept");
        headers.put("需求提出人及电话", "proposer");
        headers.put("蒙商BA", "monshang_ba");
        headers.put("蒙商架构", "monshang_architect");
        headers.put("业务期望上线时间", "expected_launch_date");
        headers.put("外部监管单位", "regulator");
        headers.put("监管文件名称+文号", "regulation_doc_no");
        headers.put("监管文件内容描述", "regulation_desc");
        headers.put("监管要求上线时间", "regulation_launch_date");
        headers.put("业需入手日", "requirement_received_date");
        headers.put("需求类型", "requirement_type");
        headers.put("监管分类", "regulation_category");
        headers.put("业务组", "business_group");
        headers.put("分组", "sub_group");
        headers.put("金科对接人及电话", "jinke_contact");
        headers.put("是否需要金科架构决策", "need_jinke_arch_decision");
        headers.put("金科架构人员", "jinke_architect");
        headers.put("是否纳入蒙商统一管理", "unified_managed");
        headers.put("业需评审完成日", "ba_review_date");
        headers.put("工作量评估完成日", "workload_date");
        headers.put("财务立项完成日（任务书）", "finance_project_date");
        headers.put("软需文档名称", "soft_doc_name");
        headers.put("主责事业群", "owner_conglomerate");
        headers.put("主责物理子系统编号+名称", "owner_system");
        headers.put("主责项目组联系人及电话", "owner_contact");
        headers.put("是否涉及金科引入组件协同", "involve_cooperation");
        headers.put("协同事业群", "coord_conglomerate");
        headers.put("协同系统名称", "coord_system");
        headers.put("软需提交日", "soft_submit_date");
        headers.put("软需评审完成日", "soft_review_date");
        headers.put("计划上线时间", "planned_launch_date");
        headers.put("实际上线时间", "actual_launch_date");
        headers.put("上线形式", "launch_mode");
        headers.put("需求状态", "requirement_status");
        headers.put("备注", "remark");
        headers.put("是否涉及需求变更", "change_involved");
        headers.put("需求变更信息", "change_info");
        headers.put("变更评审结论", "change_review_conclusion");
        headers.put("变更结论及状态", "change_conclusion_status");
        headers.put("需求变更备注", "change_remark");
        headers.put("未立项已开发", "not_project_developed");
        return Map.copyOf(headers);
    }
}
