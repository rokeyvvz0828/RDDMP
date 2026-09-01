package com.ccb.datamigration.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IssueExcelServiceTest {
    private static final AuthUser USER = new AuthUser(7L, 1L, "developer", "", "Developer", 11L, true);

    @Test
    void importContinuesAfterRowFailureAndReturnsCompleteResult() throws Exception {
        IssueService issues = mock(IssueService.class);
        when(issues.create(any(), eq(USER)))
                .thenReturn(Map.of("id", 1L))
                .thenThrow(new BusinessException(ErrorCode.CONFLICT, "问题编号在该项目下已存在"));
        IssueExcelService excel = new IssueExcelService(issues);
        MockMultipartFile file = workbook(
                List.of("问题编号", "问题名称", "问题描述", "颗粒度"),
                List.of(
                        List.of("ISSUE-1", "First", "Description", "项目级"),
                        List.of("ISSUE-1", "Duplicate", "Description", "项目级")
                ));

        Map<String, Object> result = excel.importIssues(10L, file, USER);

        assertEquals(1, result.get("successCount"));
        assertEquals(1, result.get("failureCount"));
        List<?> errors = (List<?>) result.get("rowErrors");
        assertEquals(1, errors.size());
        assertEquals(3, ((Map<?, ?>) errors.get(0)).get("row"));
        verify(issues, org.mockito.Mockito.times(2)).create(any(), eq(USER));
    }

    @Test
    void oversizedWorkbookIsRejectedBeforeParsing() {
        IssueService issues = mock(IssueService.class);
        IssueExcelService excel = new IssueExcelService(issues);
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(50L * 1024 * 1024 + 1);

        BusinessException error = assertThrows(BusinessException.class, () -> excel.importIssues(10L, file, USER));

        assertEquals(ErrorCode.BAD_REQUEST, error.code());
    }

    @Test
    void exportProducesIssueWorkbookFromFilteredRows() throws Exception {
        IssueService issues = mock(IssueService.class);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("asset_code", "ISSUE-1");
        row.put("asset_name", "First");
        row.put("granularity", "PROJECT");
        row.put("issueDescription", "Description");
        row.put("project_name", "Project A");
        when(issues.exportRows(10L, null, null, null, null, null, "First", USER)).thenReturn(List.of(row));
        IssueExcelService excel = new IssueExcelService(issues);

        byte[] bytes = excel.exportIssues(10L, null, null, null, null, null, "First", USER);

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            assertEquals("问题编号", workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
            assertEquals("ISSUE-1", workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue());
            assertEquals("Project A", workbook.getSheetAt(0).getRow(1).getCell(19).getStringCellValue());
        }
    }

    private static MockMultipartFile workbook(List<String> headers, List<List<String>> rows) throws Exception {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("问题清单");
            var header = sheet.createRow(0);
            for (int index = 0; index < headers.size(); index++) header.createCell(index).setCellValue(headers.get(index));
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                var row = sheet.createRow(rowIndex + 1);
                for (int column = 0; column < rows.get(rowIndex).size(); column++) row.createCell(column).setCellValue(rows.get(rowIndex).get(column));
            }
            workbook.write(output);
            return new MockMultipartFile("file", "issues.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }
}
