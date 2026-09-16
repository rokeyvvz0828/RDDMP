package com.ccb.system.web;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.system.service.NotificationSseService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationStreamControllerTest {
    @Test
    void invalidTicketReturnsUnauthorized() throws Exception {
        NotificationSseService streams = mock(NotificationSseService.class);
        when(streams.connect("invalid"))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED, "通知连接凭证无效或已过期"));
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new NotificationStreamController(streams))
                .build();

        mvc.perform(get("/api/notifications/stream").param("ticket", "invalid"))
                .andExpect(status().isUnauthorized());
    }
}
