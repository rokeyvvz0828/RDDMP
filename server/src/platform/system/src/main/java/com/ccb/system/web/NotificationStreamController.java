package com.ccb.system.web;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.system.service.NotificationSseService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class NotificationStreamController {
    private final NotificationSseService streams;

    public NotificationStreamController(NotificationSseService streams) {
        this.streams = streams;
    }

    @GetMapping(value = "/api/notifications/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(@RequestParam String ticket) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .header("X-Accel-Buffering", "no")
                .body(streams.connect(ticket));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Void> invalidTicket(BusinessException exception) {
        if (exception.code() != ErrorCode.UNAUTHORIZED) throw exception;
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
