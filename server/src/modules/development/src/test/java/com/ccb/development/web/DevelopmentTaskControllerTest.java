package com.ccb.development.web;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.development.service.DevelopmentTaskService;
import com.ccb.system.capability.SystemOperationAudit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DevelopmentTaskControllerTest {
    private final DevelopmentTaskService tasks=mock(DevelopmentTaskService.class);
    private MockMvc mvc;

    @BeforeEach
    void prepare(){mvc=MockMvcBuilders.standaloneSetup(new DevelopmentTaskController(tasks))
            .setCustomArgumentResolvers(new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver())
            .setControllerAdvice(new DevelopmentExceptionAdvice(mock(SystemOperationAudit.class))).build();}

    @Test
    void createRejectsClientOwnedTenantStatusNumberAndTimestamp()throws Exception{
        for(String field:new String[]{"tenantId","status","number","createdAt"}){
            mvc.perform(post("/api/development/tasks").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"projectRef\":\"PROJECT-A\",\"sourceMode\":\"STANDALONE\",\"systemId\":42,\"title\":\"任务\",\"requestId\":\"fixture\",\""+field+"\":\"forbidden\"}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40000));
        }
        verifyNoInteractions(tasks);
    }

    @Test
    void businessFailuresPreserveForbiddenMissingAndConflictStatus()throws Exception{
        for(int code:new int[]{ErrorCode.FORBIDDEN,40400,ErrorCode.CONFLICT}){
            when(tasks.detail(any(),eq(42L))).thenThrow(new BusinessException(code,"业务失败"));
            mvc.perform(get("/api/development/tasks/42")).andExpect(status().is(code/100)).andExpect(jsonPath("$.code").value(code));
        }
    }

    @Test
    void updateRequiresExplicitNonNullRowVersion()throws Exception{
        for(String body:new String[]{"{\"title\":\"任务\"}","{\"title\":\"任务\",\"rowVersion\":null}"}){
            mvc.perform(put("/api/development/tasks/42").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40000));
        }
        verifyNoInteractions(tasks);
    }
}
