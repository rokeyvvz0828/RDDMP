package com.ccb.requirement.service;

import com.ccb.requirement.support.StubJdbcTemplate;
import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequirementImportServiceTest {
    private static final AuthUser ADMIN = new AuthUser(1L, 1L, "admin", "", "管理员", 1L, true);

    @Test
    void previewReportsValidAndInvalidRows() throws Exception {
        byte[] content = workbookBytes();
        StubJdbcTemplate jdbc = adminJdbc();
        RequirementChangeLogService changeLog = new RequirementChangeLogService(jdbc);
        RequirementSecurityService security = new RequirementSecurityService(jdbc);
        RequirementSystemService systemService = new RequirementSystemService(jdbc, changeLog) {
            @Override
            public long resolveSystemId(String systemCode, AuthUser user) {
                return "W01812".equals(systemCode) ? 10L : 0L;
            }
        };
        RequirementImportService service = new RequirementImportService(jdbc, security, systemService, changeLog, new ObjectMapper());
        MockMultipartFile file = new MockMultipartFile("file", "diff.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);

        Map<String, Object> report = service.preview("DIFF", 1L, file, ADMIN);

        assertEquals(2, report.get("totalRows"));
        assertEquals(1, report.get("successRows"));
        assertEquals(1, report.get("errorRows"));
        List<Map<String, Object>> errors = (List<Map<String, Object>>) report.get("errors");
        assertTrue(String.valueOf(errors.get(0).get("messages")).contains("必填项缺失"));
    }

    @Test
    void confirmPersistsRowsAndImportBatch() throws Exception {
        StubJdbcTemplate jdbc = adminJdbc();
        RequirementChangeLogService changeLog = new RequirementChangeLogService(jdbc);
        RequirementSecurityService security = new RequirementSecurityService(jdbc);
        RequirementSystemService systemService = new RequirementSystemService(jdbc, changeLog) {
            @Override
            public long resolveSystemId(String systemCode, AuthUser user) {
                return "W01812".equals(systemCode) ? 10L : 0L;
            }
        };
        RequirementImportService service = new RequirementImportService(jdbc, security, systemService, changeLog, new ObjectMapper());
        Map<String, Object> row = new LinkedHashMap<>(Map.of(
                "name", "导入差异", "business_group", "零售一组", "requirement_no", "W01812-001",
                "category", "功能", "difference_type", "无差异", "system_code", "W01812"));

        Map<String, Object> result = service.confirm("DIFF", 1L, "diff.xlsx", List.of(row), ADMIN);

        assertEquals(1, result.get("successRows"));
        assertTrue(jdbc.updates().stream().anyMatch(sql -> sql.contains("INSERT INTO `req_difference`")));
        assertTrue(jdbc.updates().stream().anyMatch(sql -> sql.contains("INSERT INTO `req_import_batch`")));
    }

    private byte[] workbookBytes() throws Exception {
        String[] headers = {
                "序号", "事业群", "业务板块", "业务组", "需求编号", "分类", "名称", "涉及系统编号",
                "金科做法", "差异类型", "蒙商作法", "差异描述", "蒙商分析部门", "蒙商分析人",
                "金科分析人", "适配方式", "处理状态", "协同组", "解决方案", "是否专题",
                "上升决策层级", "决策结论", "蒙商确认部门", "金科确认人"
        };
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("差异清单");
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            Row valid = sheet.createRow(1);
            valid.createCell(0).setCellValue(1);
            valid.createCell(1).setCellValue("数字金融事业群");
            valid.createCell(2).setCellValue("零售业务板块");
            valid.createCell(3).setCellValue("零售一组");
            valid.createCell(4).setCellValue("W01812-001");
            valid.createCell(5).setCellValue("功能");
            valid.createCell(6).setCellValue("导入差异");
            valid.createCell(7).setCellValue("W01812");
            valid.createCell(9).setCellValue("无差异");
            Row invalid = sheet.createRow(2);
            invalid.createCell(4).setCellValue("W01812-002");
            invalid.createCell(5).setCellValue("错误分类");
            invalid.createCell(7).setCellValue("W01812");
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private StubJdbcTemplate adminJdbc() {
        return new StubJdbcTemplate(
                sql -> sql.contains("requirement:admin") || sql.contains("FROM req_project") ? 1L : 0L,
                List.of(),
                Map.of());
    }
}
