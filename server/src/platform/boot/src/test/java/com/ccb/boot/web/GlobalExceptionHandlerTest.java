package com.ccb.boot.web;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    @Test
    void disconnectedAsyncClientDoesNotProduceAnErrorResponse() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DisconnectController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/disconnect"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @RestController
    private static class DisconnectController {
        @GetMapping("/disconnect")
        void disconnect() throws AsyncRequestNotUsableException {
            throw new AsyncRequestNotUsableException("client disconnected");
        }
    }
}
