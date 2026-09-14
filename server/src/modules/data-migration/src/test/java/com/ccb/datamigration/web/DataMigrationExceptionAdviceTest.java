package com.ccb.datamigration.web;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ccb.datamigration.service.IssueExcelService;
import com.ccb.datamigration.service.IssueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * T32 决策 D1/D3 的入参绑定语义测试：{@code projectId} 转为必填后，缺失、非数字以及路径 id 非法
 * 都必须保持 400（40000）语义，不得被平台全局兜底 {@code Exception} 处理器渲染成 500。
 */
class DataMigrationExceptionAdviceTest {
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders
                .standaloneSetup(new IssueController(mock(IssueService.class), mock(IssueExcelService.class)))
                .setControllerAdvice(new DataMigrationExceptionAdvice())
                .build();
    }

    @Test
    void nonNumericProjectIdIsBadRequest() throws Exception {
        mvc.perform(get("/api/data-migration/issues").param("projectId", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void missingRequiredProjectIdOnImportIsBadRequest() throws Exception {
        mvc.perform(multipart("/api/data-migration/issues/import")
                        .file(new MockMultipartFile("file", "issues.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[] {1})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void nonNumericPathIdIsBadRequest() throws Exception {
        mvc.perform(get("/api/data-migration/issues/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }
}
