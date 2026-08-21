/*
 * 文件：server/src/modules/test-management/src/test/java/com/ccb/testmanagement/service/BusinessDayWorkbookServiceTest.java
 * 说明：营业日 XLSX 模板、解析和文件类型测试。
 * 用途：验证模板往返一致并拒绝非 XLSX 输入。
 * 作者：hengguan
 */
package com.ccb.testmanagement.service;

import com.ccb.common.exception.BusinessException;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessDayWorkbookServiceTest {
    private final BusinessDayWorkbookService service = new BusinessDayWorkbookService();

    @Test
    void templateCanBeImportedAgain() {
        byte[] template = service.scheduleTemplate();
        MockMultipartFile file = new MockMultipartFile("file", "template.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", template);

        List<Map<String, Object>> records = service.parseSchedules(file);

        assertEquals(2, records.size());
        assertEquals("PL1", records.get(0).get("env_code"));
        assertEquals("2026-08-12", records.get(0).get("natural_date"));
        assertEquals("20260812", records.get(0).get("business_date"));
        assertEquals(false, records.get(0).get("has_batch"));
        assertEquals(List.of("全部系统"), records.get(1).get("systems"));
        assertTrue((Boolean) records.get(1).get("has_batch"));
    }

    @Test
    void importsRadarDateVariantsWithoutTimezoneShift() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("日历安排模板");
            writeHeaders(sheet.createRow(0), new String[]{"测试环境", "自然日期", "营业日期", "是否跑批", "批处理类型", "跑批时间", "涉及系统", "跑批验证内容"});
            Row excelDate = sheet.createRow(1);
            writeRow(excelDate, "PL1", "", "", "否", "", "", "", "");
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));
            Date expected = Date.from(LocalDate.of(2026, 8, 12).atStartOfDay(ZoneId.systemDefault()).toInstant());
            excelDate.getCell(1).setCellValue(expected); excelDate.getCell(1).setCellStyle(dateStyle);
            excelDate.getCell(2).setCellValue(expected); excelDate.getCell(2).setCellStyle(dateStyle);
            Row serialDate = sheet.createRow(2);
            writeRow(serialDate, "PL1", "", "", "否", "", "", "", "");
            serialDate.getCell(1).setCellValue(DateUtil.getExcelDate(expected));
            serialDate.getCell(2).setCellValue(DateUtil.getExcelDate(expected));
            writeRow(sheet.createRow(3), "PL1", "20260813", "2026-8-14", "否", "", "", "", "");
            writeRow(sheet.createRow(4), "PL1", "2026/8/15", "2026.8.16", "否", "", "", "", "");
            writeRow(sheet.createRow(5), "PL1", "2026年8月17日", "2026年8月18日", "否", "", "", "", "");
            workbook.write(output);

            List<Map<String, Object>> records = service.parseSchedules(file(output.toByteArray()));

            assertEquals(List.of("2026-08-12", "2026-08-12", "2026-08-13", "2026-08-15", "2026-08-17"),
                    records.stream().map(row -> row.get("natural_date")).toList());
            assertEquals(List.of("20260812", "20260812", "20260814", "20260816", "20260818"),
                    records.stream().map(row -> row.get("business_date")).toList());
        }
    }

    @Test
    void importsPublishedRddmpHeaderAliasesAndInfersBatch() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("旧模板");
            writeHeaders(sheet.createRow(0), new String[]{"环境编码", "自然日", "营业日", "是否跑批", "跑批类型", "跑批时间", "涉及系统", "验证内容", "维护人"});
            writeRow(sheet.createRow(1), "SIT1", "2026-08-12", "20260812", "", "增量", "18:00", "核心系统，渠道系统", "日终验证", "维护人甲");
            workbook.write(output);

            Map<String, Object> record = service.parseSchedules(file(output.toByteArray())).get(0);

            assertTrue((Boolean) record.get("has_batch"));
            assertEquals(List.of("核心系统", "渠道系统"), record.get("systems"));
            assertEquals("维护人甲", record.get("maintainer"));
        }
    }

    @Test
    void rejectsNonXlsxInput() {
        MockMultipartFile file = new MockMultipartFile("file", "calendar.csv", "text/csv", "bad".getBytes());
        assertThrows(BusinessException.class, () -> service.parseSchedules(file));
    }

    // 关键逻辑：测试工作簿直接构造不同 Excel 单元格类型，避免只验证字符串路径而遗漏真实日期单元格。
    private MockMultipartFile file(byte[] content) {
        return new MockMultipartFile("file", "template.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);
    }

    private void writeHeaders(Row row, String[] headers) {
        for (int index = 0; index < headers.length; index++) row.createCell(index).setCellValue(headers[index]);
    }

    private void writeRow(Row row, String... values) {
        for (int index = 0; index < values.length; index++) row.createCell(index).setCellValue(values[index]);
    }
}
