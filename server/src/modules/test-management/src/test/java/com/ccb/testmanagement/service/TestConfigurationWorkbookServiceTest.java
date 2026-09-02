package com.ccb.testmanagement.service;

import com.ccb.common.exception.BusinessException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestConfigurationWorkbookServiceTest {
    private final TestConfigurationWorkbookService service = new TestConfigurationWorkbookService();

    @Test
    void rejectsSystemWorkbookWithoutRequiredEnabledHeader() throws Exception {
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            book.createSheet("导入").createRow(0).createCell(0).setCellValue("物理子系统编号");
            book.write(output);
            assertThrows(BusinessException.class, () -> service.systems(new MockMultipartFile("file", "systems.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray())));
        }
    }

    @Test
    void createsUsableSystemTemplate() {
        assertEquals(0x50, service.systemTemplate()[0] & 0xff);
        assertEquals(0x4b, service.systemTemplate()[1] & 0xff);
    }
}
