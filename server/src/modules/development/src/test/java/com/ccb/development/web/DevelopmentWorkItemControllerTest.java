package com.ccb.development.web;

import com.ccb.development.service.DevelopmentWorkItemService;
import com.ccb.system.capability.SystemOperationAudit;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DevelopmentWorkItemControllerTest {
    @Test
    void workItemStatusCannotBeOverwrittenAsAnEditableField()throws Exception{
        var service=mock(DevelopmentWorkItemService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new DevelopmentWorkItemController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new DevelopmentExceptionAdvice(mock(SystemOperationAudit.class))).build();
        mvc.perform(put("/api/development/work-items/42").contentType(MediaType.APPLICATION_JSON)
                .content("{\"taskId\":1,\"title\":\"工作项\",\"rowVersion\":0,\"status\":\"DONE\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40000));
        verifyNoInteractions(service);
    }
}
